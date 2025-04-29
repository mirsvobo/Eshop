// src/main/java/org/example/eshop/service/ProductService.java
package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.config.PriceConstants;
import org.example.eshop.dto.CustomPriceRequestDto;
import org.example.eshop.dto.CustomPriceResponseDto;
import org.example.eshop.model.*;
import org.example.eshop.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Service
public class ProductService implements PriceConstants {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);
    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]+");
    private static final Pattern MULTIPLE_DASHES = Pattern.compile("-{2,}");
    private static final Pattern EDGES_DASHES = Pattern.compile("^-|-$");
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ImageRepository imageRepository;
    @Autowired
    private TaxRateRepository taxRateRepository;
    @Autowired
    private DiscountService discountService;
    @Autowired
    private DesignRepository designRepository;
    @Autowired
    private GlazeRepository glazeRepository;
    @Autowired
    private RoofColorRepository roofColorRepository;
    @Autowired
    private AddonsRepository addonsRepository;

    public static String generateSlug(String input) {
        if (input == null || input.trim().isEmpty()) {
            logger.warn("Attempted to generate slug from empty or null input. Returning empty string.");
            return "";
        }
        String nowhitespace = WHITESPACE.matcher(input.trim()).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");
        slug = slug.toLowerCase(Locale.ENGLISH);
        slug = MULTIPLE_DASHES.matcher(slug).replaceAll("-");
        slug = EDGES_DASHES.matcher(slug).replaceAll("");
        if (slug.length() > 150) {
            slug = slug.substring(0, 150);
            slug = EDGES_DASHES.matcher(slug).replaceAll("");
        }
        logger.trace("Generated slug '{}' from input '{}'", slug, input);
        return slug;
    }
    public BigDecimal calculateSingleAddonPriceBackend(Addon addon, Map<String, BigDecimal> dimensions, String currency) { // <-- Změna na public
        BigDecimal zero = BigDecimal.ZERO.setScale(PRICE_SCALE, ROUNDING_MODE);
        if (addon == null || dimensions == null) return zero;

        String pricingType = addon.getPricingType();
        BigDecimal price = zero; // Použijeme zero místo null pro jistotu
        BigDecimal unitPrice;

        BigDecimal lengthCm = dimensions.get("length");
        BigDecimal widthCm = dimensions.get("width");
        BigDecimal heightCm = dimensions.get("height");

        if ("FIXED".equals(pricingType)) {
            // Použijeme getPriceForCurrency pro získání ceny, vrátí 0 pokud není nalezena
            price = getPriceForCurrency(addon.getPriceCZK(), addon.getPriceEUR(), currency, "Addon '" + addon.getName() + "' Fixed Price");
        } else {
            unitPrice = getPriceForCurrency(addon.getPricePerUnitCZK(), addon.getPricePerUnitEUR(), currency, "Addon '" + addon.getName() + "' Unit Price");
            if (unitPrice.compareTo(zero) <= 0) return zero; // Nulová nebo chybějící jednotková cena nic nepřidá

            // Kontrola platnosti rozměrů pro dimenzionální ceny
            if (lengthCm == null || widthCm == null || heightCm == null ||
                    lengthCm.compareTo(BigDecimal.ZERO) <= 0 || widthCm.compareTo(BigDecimal.ZERO) <= 0 || heightCm.compareTo(BigDecimal.ZERO) <= 0) {
                logger.warn("Některý z rozměrů ({}, {}, {}) není platný pro výpočet dimenzionální ceny doplňku '{}'.", lengthCm, widthCm, heightCm, addon.getName());
                return zero; // Nelze vypočítat
            }


            switch (pricingType) {
                case "PER_CM_WIDTH":
                    price = unitPrice.multiply(widthCm);
                    break;
                case "PER_CM_LENGTH":
                    price = unitPrice.multiply(lengthCm);
                    break;
                case "PER_CM_HEIGHT":
                    price = unitPrice.multiply(heightCm);
                    break;
                case "PER_SQUARE_METER":
                    BigDecimal lengthM = lengthCm.divide(new BigDecimal("100"), CALCULATION_SCALE, ROUNDING_MODE);
                    BigDecimal widthM = widthCm.divide(new BigDecimal("100"), CALCULATION_SCALE, ROUNDING_MODE);
                    price = unitPrice.multiply(lengthM).multiply(widthM);
                    break;
                default:
                    logger.warn("Neznámý PricingType '{}' pro doplněk '{}'", pricingType, addon.getName());
                    break; // Neznámý typ, cena zůstane 0
            }
        }

        return price.setScale(PRICE_SCALE, ROUNDING_MODE).max(zero); // Zaokrouhlení a zajištění nezápornosti
    }
    @Cacheable(value = "activeProductsPage", key = "#pageable.toString()")
    @Transactional(readOnly = true)
    public Page<Product> getActiveProducts(Pageable pageable) {
        logger.info(">>> [ProductService] Vstupuji do getActiveProducts(Pageable: {}) using standard repo method <<<", pageable);
        Page<Product> result = Page.empty(pageable);
        try {
            result = productRepository.findByActiveTrue(pageable);
            logger.info("[ProductService] getActiveProducts(Pageable): Načtena stránka s {} aktivními produkty (with details).", result.getTotalElements());
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v getActiveProducts(Pageable): {} !!!", e.getMessage(), e);
        }
        logger.info(">>> [ProductService] Opouštím getActiveProducts(Pageable) <<<");
        return result;
    }

    @Cacheable("activeProductsList")
    @Transactional(readOnly = true)
    public List<Product> getAllActiveProducts() {
        logger.info(">>> [ProductService] Vstupuji do getAllActiveProducts (List) using standard repo method <<<");
        List<Product> result = Collections.emptyList();
        try {
            result = productRepository.findAllByActiveTrue();
            logger.info("[ProductService] getAllActiveProducts (List): Načteno {} aktivních produktů (with details).", (result != null ? result.size() : "NULL"));
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v getAllActiveProducts (List): {} !!!", e.getMessage(), e);
        }
        logger.info(">>> [ProductService] Opouštím getAllActiveProducts (List) <<<");
        return result;
    }

    @Cacheable(value = "productDetails", key = "#id", unless = "#result == null or !#result.isPresent()")
    @Transactional(readOnly = true)
    public Optional<Product> getProductById(Long id) {
        logger.info(">>> [ProductService] Vstupuji do getProductById (with details) using findByIdWithDetails. ID: {}", id);
        Optional<Product> result = Optional.empty();
        try {
            result = productRepository.findByIdWithDetails(id);
            logger.info("[ProductService] getProductById: Produkt ID {} {}.", id, result.isPresent() ? "nalezen (with details)" : "nenalezen");
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v getProductById (ID: {}): {} !!!", id, e.getMessage(), e);
        }
        logger.info(">>> [ProductService] Opouštím getProductById (ID: {}) <<<", id);
        return result;
    }

    @Cacheable(value = "productBySlug", key = "T(String).valueOf(#slug).toLowerCase()", unless = "#result == null or !#result.isPresent()")
    @Transactional(readOnly = true)
    public Optional<Product> getActiveProductBySlug(String slug) {
        logger.info(">>> [ProductService] Vstupuji do getActiveProductBySlug using findActiveBySlugWithDetails. Slug: {}", slug);
        Optional<Product> result = Optional.empty();
        try {
            result = productRepository.findActiveBySlugWithDetails(slug);
            logger.info("[ProductService] getActiveProductBySlug: Aktivní produkt se slugem '{}' {}.", slug, result.isPresent() ? "nalezen (with details)" : "nenalezen");
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v getActiveProductBySlug (Slug: {}): {} !!!", slug, e.getMessage(), e);
        }
        logger.info(">>> [ProductService] Opouštím getActiveProductBySlug (Slug: {}) <<<", slug);
        return result;
    }

    @Transactional(readOnly = true)
    public Page<Product> getAllProducts(Pageable pageable) {
        logger.info(">>> [ProductService] Vstupuji do getAllProducts(Pageable: {}) <<<", pageable);
        Page<Product> result = Page.empty(pageable);
        try {
            result = productRepository.findAll(pageable);
            logger.info("[ProductService] getAllProducts(Pageable): Načtena stránka s {} produkty.", result.getTotalElements());
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v getAllProducts(Pageable): {} !!!", e.getMessage(), e);
        }
        logger.info(">>> [ProductService] Opouštím getAllProducts(Pageable) <<<");
        return result;
    }

    @Cacheable("allProductsList")
    @Transactional(readOnly = true)
    public List<Product> getAllProductsList() {
        logger.info(">>> [ProductService] Vstupuji do getAllProductsList <<<");
        List<Product> result = Collections.emptyList();
        try {
            result = productRepository.findAll(Sort.by("name"));
            logger.info("[ProductService] getAllProductsList: Načteno {} produktů.", (result != null ? result.size() : "NULL"));
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v getAllProductsList: {} !!!", e.getMessage(), e);
        }
        logger.info(">>> [ProductService] Opouštím getAllProductsList <<<");
        return result;
    }

    // --- START: Simplified calculateDynamicProductPrice ---

    @Transactional(readOnly = true)
    public Map<String, Object> calculateFinalProductPrice(Product product, String currency) {
        Map<String, Object> priceInfo = new HashMap<>();
        if (product == null || product.isCustomisable()) {
            priceInfo.put("originalPrice", null);
            priceInfo.put("discountedPrice", null);
            priceInfo.put("discountApplied", null);
            return priceInfo;
        }

        BigDecimal originalPrice = EURO_CURRENCY.equals(currency) ? product.getBasePriceEUR() : product.getBasePriceCZK();
        originalPrice = (originalPrice != null) ? originalPrice.setScale(PRICE_SCALE, ROUNDING_MODE) : null;

        BigDecimal finalPrice = originalPrice;
        Discount appliedDiscount = null;

        if (originalPrice != null && originalPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal priceAfterPercentage = discountService.applyBestPercentageDiscount(originalPrice, product);
            BigDecimal priceAfterFixed = discountService.applyBestFixedDiscount(originalPrice, product, currency);

            if (priceAfterPercentage != null && priceAfterPercentage.compareTo(originalPrice) < 0 &&
                    (priceAfterFixed == null || priceAfterPercentage.compareTo(priceAfterFixed) <= 0)) {
                finalPrice = priceAfterPercentage;
                appliedDiscount = discountService.findActiveDiscountsForProduct(product).stream()
                        .filter(Discount::isPercentage)
                        .filter(d -> d.getValue() != null && d.getValue().compareTo(BigDecimal.ZERO) > 0)
                        .max(Comparator.comparing(Discount::getValue))
                        .orElse(null);

            } else if (priceAfterFixed != null && priceAfterFixed.compareTo(originalPrice) < 0) {
                finalPrice = priceAfterFixed;
                appliedDiscount = discountService.findActiveDiscountsForProduct(product).stream()
                        .filter(d -> !d.isPercentage())
                        .filter(d -> {
                            BigDecimal val = EURO_CURRENCY.equals(currency) ? d.getValueEUR() : d.getValueCZK();
                            return val != null && val.compareTo(BigDecimal.ZERO) > 0;
                        })
                        .max(Comparator.comparing(d -> EURO_CURRENCY.equals(currency) ? Optional.ofNullable(d.getValueEUR()).orElse(BigDecimal.ZERO) : Optional.ofNullable(d.getValueCZK()).orElse(BigDecimal.ZERO)))
                        .orElse(null);
            }
        }

        priceInfo.put("originalPrice", originalPrice);
        priceInfo.put("discountedPrice", (finalPrice != null && originalPrice != null && finalPrice.compareTo(originalPrice) < 0) ? finalPrice : null);
        priceInfo.put("discountApplied", appliedDiscount);

        logger.debug("Calculated final price for product ID {} in {}: Original={}, Discounted={}, Discount={}",
                product.getId(), currency, originalPrice, priceInfo.get("discountedPrice"), appliedDiscount != null ? appliedDiscount.getName() : "None");

        return priceInfo;
    }
    // --- END: Simplified calculateDynamicProductPrice ---

    /**
     * Calculates the BASE price for a customizable product based ONLY on its dimensions
     * and the per-unit prices defined in its ProductConfigurator.
     * Prices for selected attributes (Design, Glaze, RoofColor) and Addons
     * are handled separately (client-side or during cart/order processing).
     *
     * @param product    The customizable product.
     * @param dimensions A map containing "length", "width", and "height" keys with BigDecimal values.
     * @param currency   The target currency ("CZK" or "EUR").
     * @return The calculated base price based on dimensions.
     * @throws IllegalArgumentException if product is null, inactive, not customizable,
     *                                  missing configurator, dimensions map is null or missing keys,
     *                                  or dimensions are outside the configured range.
     * @throws IllegalStateException    if required price configurations are missing in ProductConfigurator.
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateDynamicProductPrice(Product product, Map<String, BigDecimal> dimensions,
                                                   String currency) {
        logger.info(">>> [ProductService] Vstupuji do calculateDynamicProductPrice (Base Dimensions Only). Product ID: {}, Currency: {}", product != null ? product.getId() : "null", currency);
        BigDecimal basePrice = BigDecimal.ZERO;
        try {
            // --- Input Validations ---
            if (product == null) throw new IllegalArgumentException("Product cannot be null.");
            if (!product.isActive())
                throw new IllegalArgumentException("Cannot calculate price for inactive product ID: " + product.getId());
            if (!product.isCustomisable())
                throw new IllegalArgumentException("Product ID " + product.getId() + " is not customisable.");
            if (product.getConfigurator() == null)
                throw new IllegalArgumentException("Product ID " + product.getId() + " is missing configurator.");
            if (dimensions == null) throw new IllegalArgumentException("Dimensions map cannot be null.");

            ProductConfigurator config = product.getConfigurator();
            BigDecimal length = dimensions.get("length");
            BigDecimal width = dimensions.get("width"); // Expecting 'width' key for depth/width
            BigDecimal height = dimensions.get("height");

            if (length == null || width == null || height == null) {
                throw new IllegalArgumentException("Missing one or more dimensions (length, width, height) for custom product calculation.");
            }

            // --- Dimension Range Validation ---
            validateDimension("Length (Délka)", length, config.getMinLength(), config.getMaxLength());
            validateDimension("Width (Šířka/Hloubka)", width, config.getMinWidth(), config.getMaxWidth());
            validateDimension("Height (Výška)", height, config.getMinHeight(), config.getMaxHeight());

            // --- Price Calculation from Dimensions ---
            BigDecimal pricePerCmH = getPriceForCurrency(config.getPricePerCmHeightCZK(), config.getPricePerCmHeightEUR(), currency, "Height Price/cm");
            BigDecimal pricePerCmL = getPriceForCurrency(config.getPricePerCmLengthCZK(), config.getPricePerCmLengthEUR(), currency, "Length Price/cm");
            BigDecimal pricePerCmW = getPriceForCurrency(config.getPricePerCmWidthCZK(), config.getPricePerCmWidthEUR(), currency, "Width Price/cm");

            basePrice = height.multiply(pricePerCmH)
                    .add(length.multiply(pricePerCmL))
                    .add(width.multiply(pricePerCmW));

            basePrice = basePrice.setScale(PRICE_SCALE, ROUNDING_MODE);
            basePrice = basePrice.max(BigDecimal.ZERO); // Ensure price is not negative

            logger.debug("[ProductService] Calculated BASE price from dimensions ({}): {}", currency, basePrice);

        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.error("!!! [ProductService] Validation/Configuration Error in calculateDynamicProductPrice for Product ID {} ({}): {} !!!",
                    product != null ? product.getId() : "null", currency, e.getMessage());
            throw e; // Re-throw validation/state exceptions
        } catch (Exception e) {
            logger.error("!!! [ProductService] Unexpected Error in calculateDynamicProductPrice for Product ID {} ({}): {} !!!",
                    product != null ? product.getId() : "null", currency, e.getMessage(), e);
            // Re-throw as a runtime exception or a more specific custom exception if needed
            throw new RuntimeException("Unexpected error calculating dynamic base price for product " + (product != null ? product.getId() : "null"), e);
        }
        logger.info(">>> [ProductService] Opouštím calculateDynamicProductPrice (Base Dimensions Only). Product ID: {}, Currency: {}. Vypočtená ZÁKLADNÍ cena: {}",
                product != null ? product.getId() : "null", currency, basePrice);
        return basePrice;
    }

    // --- Helper method: getPriceForCurrency (Remains the same) ---
    private BigDecimal getPriceForCurrency(BigDecimal priceCZK, BigDecimal priceEUR, String currency, String priceName) {
        BigDecimal price = EURO_CURRENCY.equals(currency) ? priceEUR : priceCZK;
        if (price == null) {
            // Differentiate between optional (e.g., fixed addon price) and required (e.g., price per cm)
            boolean isRequired = priceName.contains("/cm") || priceName.contains("Unit"); // Assume unit/cm prices are required
            if (isRequired) {
                logger.error("CRITICAL: Required price configuration '{}' missing for currency {}. Cannot proceed.", priceName, currency);
                throw new IllegalStateException(String.format("Required price configuration '%s' missing for currency %s", priceName, currency));
            } else {
                logger.warn("Optional price '{}' missing for currency {}. Using 0.", priceName, currency);
                return BigDecimal.ZERO;
            }
        }
        return price;
    }

    // --- Helper method: validateDimension (Remains the same) ---
    private void validateDimension(String name, BigDecimal value, BigDecimal min, BigDecimal max) {
        if (min == null || max == null) {
            logger.error("Config error for dimension '{}': Missing min ({}) or max ({}) limit.", name, min, max);
            throw new IllegalStateException("Config error: Missing limits for dimension " + name);
        }
        if (value == null) {
            throw new IllegalArgumentException("Dimension " + name + " cannot be null.");
        }
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException(String.format("%s (%s cm) is outside allowed range [%s, %s] cm.",
                    name,
                    value.stripTrailingZeros().toPlainString(),
                    min.stripTrailingZeros().toPlainString(), max.stripTrailingZeros().toPlainString()));
        }
    }

    @Caching(evict = {
            @CacheEvict(value = {"activeProductsPage", "activeProductsList", "allProductsList", "productsPage"}, allEntries = true),
            @CacheEvict(value = "productDetails", key = "#result.id", condition = "#result != null"),
            @CacheEvict(value = "productBySlug", key = "T(String).valueOf(#result.slug).toLowerCase()", condition = "#result != null")
    })
    @Transactional
    public Product createProduct(Product product) {
        logger.info(">>> [ProductService] Vstupuji do createProduct. Název: {}", product.getName());
        Product savedProduct = null;
        try {
            if (product.getShortDescription() != null && product.getShortDescription().length() > 500) {
                throw new IllegalArgumentException("Krátký popis nesmí být delší než 500 znaků.");
            }

            if (!StringUtils.hasText(product.getSlug())) {
                product.setSlug(generateSlug(product.getName()));
            } else {
                product.setSlug(generateSlug(product.getSlug()));
            }
            // Check for slug uniqueness before saving
            productRepository.findBySlugIgnoreCase(product.getSlug()).ifPresent(existing -> {
                throw new IllegalArgumentException("Produkt se slugem '" + product.getSlug() + "' již existuje (ID: " + existing.getId() + ").");
            });

            // Ensure at least one tax rate is assigned
            if (CollectionUtils.isEmpty(product.getAvailableTaxRates())) {
                // Attempt to load from IDs if provided, otherwise throw error
                // This logic is usually handled in the controller before calling service
                throw new IllegalArgumentException("Produkt musí mít přiřazenu alespoň jednu daňovou sazbu.");
            }

            if (product.isCustomisable()) {
                if (product.getConfigurator() == null) {
                    ProductConfigurator defaultConfig = new ProductConfigurator();
                    defaultConfig.setProduct(product);
                    initializeDefaultConfiguratorValues(defaultConfig);
                    product.setConfigurator(defaultConfig);
                    logger.info("Creating default configurator for customisable product '{}'", product.getName());
                } else {
                    product.getConfigurator().setProduct(product);
                    // Maybe validate configurator values here?
                    initializeDefaultConfiguratorValues(product.getConfigurator()); // Ensure defaults are set if some fields are null
                }
                // Clear standard attributes when creating a custom product
                product.setAvailableDesigns(Collections.emptySet());
                product.setAvailableGlazes(Collections.emptySet());
                product.setAvailableRoofColors(Collections.emptySet());
                logger.debug("Clearing standard attributes for new customisable product '{}'", product.getName());
            } else {
                // Clear custom attributes when creating a standard product
                product.setConfigurator(null);
                product.setAvailableAddons(Collections.emptySet());
                logger.debug("Removing configurator and addons for new standard product '{}'", product.getName());
            }

            ensureCollectionsInitialized(product); // Initialize all collection fields if null

            savedProduct = productRepository.save(product);
            logger.info(">>> [ProductService] Produkt '{}' úspěšně vytvořen s ID: {}. Opouštím createProduct.", savedProduct.getName(), savedProduct.getId());

        } catch (IllegalArgumentException | EntityNotFoundException e) {
            logger.warn("!!! [ProductService] Chyba validace nebo nenalezení entity v createProduct pro '{}': {} !!!", product.getName(), e.getMessage());
            throw e; // Re-throw to be handled by controller/advice
        } catch (Exception e) {
            logger.error("!!! [ProductService] Neočekávaná chyba v createProduct pro '{}': {} !!!", product.getName(), e.getMessage(), e);
            // Wrap in a runtime exception or handle appropriately
            throw new RuntimeException("Neočekávaná chyba při vytváření produktu: " + e.getMessage(), e);
        }
        return savedProduct;
    }

    @Caching(evict = {
            @CacheEvict(value = {"activeProductsPage", "activeProductsList", "allProductsList", "productsPage"}, allEntries = true),
            @CacheEvict(value = "productDetails", key = "#id"),
            // Invalidujeme všechny slugy pro jistotu, protože neznáme starý slug bez dalšího dotazu
            @CacheEvict(value = "productBySlug", allEntries = true)
    })
    @Transactional
    public Optional<Product> updateProduct(Long id, Product productData, Product existingProduct) {
        logger.info(">>> [ProductService] Vstupuji do updateProduct. ID: {}, Aktualizuji entitu: {}", id, existingProduct);
        // Použijeme existující produkt jako základ pro update
        Product productToUpdate = existingProduct;

        try {
            // --- Slug Update & Validation ---
            String newSlug = StringUtils.hasText(productData.getSlug()) ? generateSlug(productData.getSlug()) : generateSlug(productData.getName());
            if (!Objects.equals(newSlug, productToUpdate.getSlug())) { // Bezpečnější porovnání
                // Ověření unikátnosti nového slugu (ignoruje aktuální produkt)
                productRepository.findBySlugIgnoreCaseAndIdNot(newSlug, id).ifPresent(existingWithSlug -> {
                    throw new IllegalArgumentException("Produkt se slugem '" + newSlug + "' již existuje.");
                });
                productToUpdate.setSlug(newSlug);
                logger.debug("Aktualizuji slug pro produkt ID {} na '{}'", id, newSlug);
            }

            // --- Update Basic Fields ---
            // (Tato část zůstává stejná jako v předchozí verzi, kopíruje základní atributy)
            if (!Objects.equals(productData.getName(), productToUpdate.getName())) {
                logger.debug("Product ID {}: Změna Name", id);
                productToUpdate.setName(productData.getName());
            }
            if (productData.getShortDescription() != null && productData.getShortDescription().length() > 500) {
                throw new IllegalArgumentException("Krátký popis nesmí být delší než 500 znaků.");
            }
            if (!Objects.equals(productData.getShortDescription(), productToUpdate.getShortDescription())) {
                logger.debug("Product ID {}: Změna ShortDescription", id);
                productToUpdate.setShortDescription(productData.getShortDescription());
            }
            if (!Objects.equals(productData.getDescription(), productToUpdate.getDescription())) {
                logger.debug("Product ID {}: Změna Description", id);
                productToUpdate.setDescription(productData.getDescription());
            }
            if (!Objects.equals(productData.getBasePriceCZK(), productToUpdate.getBasePriceCZK())) {
                logger.debug("Product ID {}: Změna BasePriceCZK", id);
                productToUpdate.setBasePriceCZK(productData.getBasePriceCZK());
            }
            if (!Objects.equals(productData.getBasePriceEUR(), productToUpdate.getBasePriceEUR())) {
                logger.debug("Product ID {}: Změna BasePriceEUR", id);
                productToUpdate.setBasePriceEUR(productData.getBasePriceEUR());
            }
            if (!Objects.equals(productData.getModel(), productToUpdate.getModel())) {
                logger.debug("Product ID {}: Změna Model", id);
                productToUpdate.setModel(productData.getModel());
            }
            if (!Objects.equals(productData.getMaterial(), productToUpdate.getMaterial())) {
                logger.debug("Product ID {}: Změna Material", id);
                productToUpdate.setMaterial(productData.getMaterial());
            }
            if (!Objects.equals(productData.getHeight(), productToUpdate.getHeight())) {
                logger.debug("Product ID {}: Změna Height", id);
                productToUpdate.setHeight(productData.getHeight());
            }
            if (!Objects.equals(productData.getLength(), productToUpdate.getLength())) {
                logger.debug("Product ID {}: Změna Length", id);
                productToUpdate.setLength(productData.getLength());
            }
            if (!Objects.equals(productData.getWidth(), productToUpdate.getWidth())) {
                logger.debug("Product ID {}: Změna Width", id);
                productToUpdate.setWidth(productData.getWidth());
            }
            if (!Objects.equals(productData.getRoofOverstep(), productToUpdate.getRoofOverstep())) {
                logger.debug("Product ID {}: Změna RoofOverstep", id);
                productToUpdate.setRoofOverstep(productData.getRoofOverstep());
            }
            if (productData.isActive() != productToUpdate.isActive()) {
                logger.debug("Product ID {}: Změna Active", id);
                productToUpdate.setActive(productData.isActive());
            }
            // Customisable flag se nastavuje níže, zde neměnit
            if (!Objects.equals(productData.getMetaTitle(), productToUpdate.getMetaTitle())) {
                logger.debug("Product ID {}: Změna MetaTitle", id);
                productToUpdate.setMetaTitle(productData.getMetaTitle());
            }
            if (!Objects.equals(productData.getMetaDescription(), productToUpdate.getMetaDescription())) {
                logger.debug("Product ID {}: Změna MetaDescription", id);
                productToUpdate.setMetaDescription(productData.getMetaDescription());
            }


            // --- Handle Customisable Flag Change & Configurator Update ---
            boolean wasCustom = productToUpdate.isCustomisable();
            boolean isCustom = productData.isCustomisable(); // Zda má být custom podle dat z formuláře

            if (wasCustom != isCustom) {
                logger.info("Produkt ID {} mění stav 'customisable' z {} na {}", id, wasCustom, isCustom);
                productToUpdate.setCustomisable(isCustom);
            }

            // Správa konfigurátoru
            if (isCustom) {
                // Pokud má být custom a nemá konfigurátor, vytvoříme/aktualizujeme ho
                if (productToUpdate.getConfigurator() == null) {
                    ProductConfigurator newConfig = (productData.getConfigurator() != null) ? productData.getConfigurator() : new ProductConfigurator();
                    newConfig.setProduct(productToUpdate);
                    initializeDefaultConfiguratorValues(newConfig); // Nastaví případné defaulty
                    productToUpdate.setConfigurator(newConfig);
                    logger.debug("Vytvářím/Inicializuji konfigurátor pro custom produkt ID {}", id);
                } else if (productData.getConfigurator() != null) {
                    // Pokud má být custom a konfigurátor existuje, aktualizujeme data
                    logger.debug("Volám updateConfiguratorData pro custom produkt ID {}", id);
                    updateConfiguratorData(productToUpdate.getConfigurator(), productData.getConfigurator());
                    logger.debug("Aktualizoval jsem existující konfigurátor pro custom produkt ID {}", id);
                }
                // Pokud productData.getConfigurator() je null, necháme existující konfigurátor být (pokud existuje)
            } else {
                // Pokud nemá být custom, odstraníme konfigurátor (pokud existuje)
                if (productToUpdate.getConfigurator() != null) {
                    logger.debug("Odstraňuji konfigurátor pro standard produkt ID {}", id);
                    productToUpdate.setConfigurator(null); // orphanRemoval=true se postará o smazání z DB
                }
            }

            // --- Explicitní aktualizace kolekcí asociací ---

            // Tax Rates (povinné)
            if (productData.getAvailableTaxRates() == null || productData.getAvailableTaxRates().isEmpty()) {
                throw new IllegalArgumentException("Produkt ID " + id + " musí mít přiřazenu alespoň jednu daňovou sazbu.");
            }
            Set<TaxRate> incomingTaxRates = productData.getAvailableTaxRates();
            if (productToUpdate.getAvailableTaxRates() == null) productToUpdate.setAvailableTaxRates(new HashSet<>());
            Set<Long> existingTaxRateIds = productToUpdate.getAvailableTaxRates().stream().map(TaxRate::getId).collect(Collectors.toSet());
            Set<Long> incomingTaxRateIds = incomingTaxRates.stream().map(TaxRate::getId).collect(Collectors.toSet());
            if (!existingTaxRateIds.equals(incomingTaxRateIds)) {
                logger.debug("Product ID {}: Měním TaxRates z {} na {}", id, existingTaxRateIds, incomingTaxRateIds);
                productToUpdate.getAvailableTaxRates().clear();
                productToUpdate.getAvailableTaxRates().addAll(incomingTaxRates);
            } else {
                logger.debug("Product ID {}: TaxRates se nemění.", id);
            }

            // Addons (Pouze pro custom, jinak čistíme)
            if (isCustom) {
                Set<Addon> incomingAddons = productData.getAvailableAddons() != null ? productData.getAvailableAddons() : Collections.emptySet();
                if (productToUpdate.getAvailableAddons() == null) productToUpdate.setAvailableAddons(new HashSet<>());
                Set<Long> existingAddonIds = productToUpdate.getAvailableAddons().stream().map(Addon::getId).collect(Collectors.toSet());
                Set<Long> incomingAddonIds = incomingAddons.stream().map(Addon::getId).collect(Collectors.toSet());
                if (!existingAddonIds.equals(incomingAddonIds)) {
                    logger.debug("Product ID {}: Měním Addons z {} na {}", id, existingAddonIds, incomingAddonIds);
                    productToUpdate.getAvailableAddons().clear();
                    productToUpdate.getAvailableAddons().addAll(incomingAddons);
                } else {
                    logger.debug("Product ID {}: Addons se nemění.", id);
                }
            } else { // Není custom, vyčistíme Addons
                if (productToUpdate.getAvailableAddons() == null) productToUpdate.setAvailableAddons(new HashSet<>());
                if (!productToUpdate.getAvailableAddons().isEmpty()) {
                    logger.debug("Product ID {}: Čistím Addons pro standardní produkt.", id);
                    productToUpdate.getAvailableAddons().clear();
                }
            }

            // *** ZAČÁTEK OPRAVY: Synchronizace Designs, Glazes, RoofColors ***
            // Tyto kolekce nyní aktualizujeme vždy, pokud přišly z productData (controller je naplnil)

            // Designs
            Set<Design> incomingDesigns = productData.getAvailableDesigns() != null ? productData.getAvailableDesigns() : Collections.emptySet();
            if (productToUpdate.getAvailableDesigns() == null) productToUpdate.setAvailableDesigns(new HashSet<>());
            Set<Long> existingDesignIds = productToUpdate.getAvailableDesigns().stream().map(Design::getId).collect(Collectors.toSet());
            Set<Long> incomingDesignIds = incomingDesigns.stream().map(Design::getId).collect(Collectors.toSet());
            if (!existingDesignIds.equals(incomingDesignIds)) {
                logger.debug("Product ID {}: Měním Designs z {} na {}", id, existingDesignIds, incomingDesignIds);
                productToUpdate.getAvailableDesigns().clear();
                productToUpdate.getAvailableDesigns().addAll(incomingDesigns);
            } else {
                logger.debug("Product ID {}: Designs se nemění.", id);
            }

            // Glazes (Lazury)
            Set<Glaze> incomingGlazes = productData.getAvailableGlazes() != null ? productData.getAvailableGlazes() : Collections.emptySet();
            if (productToUpdate.getAvailableGlazes() == null) productToUpdate.setAvailableGlazes(new HashSet<>());
            Set<Long> existingGlazeIds = productToUpdate.getAvailableGlazes().stream().map(Glaze::getId).collect(Collectors.toSet());
            Set<Long> incomingGlazeIds = incomingGlazes.stream().map(Glaze::getId).collect(Collectors.toSet());
            if (!existingGlazeIds.equals(incomingGlazeIds)) {
                logger.debug("Product ID {}: Měním Glazes z {} na {}", id, existingGlazeIds, incomingGlazeIds);
                productToUpdate.getAvailableGlazes().clear();
                productToUpdate.getAvailableGlazes().addAll(incomingGlazes);
            } else {
                logger.debug("Product ID {}: Glazes se nemění.", id);
            }

            // RoofColors (Barvy střechy)
            Set<RoofColor> incomingRoofColors = productData.getAvailableRoofColors() != null ? productData.getAvailableRoofColors() : Collections.emptySet();
            if (productToUpdate.getAvailableRoofColors() == null)
                productToUpdate.setAvailableRoofColors(new HashSet<>());
            Set<Long> existingRoofColorIds = productToUpdate.getAvailableRoofColors().stream().map(RoofColor::getId).collect(Collectors.toSet());
            Set<Long> incomingRoofColorIds = incomingRoofColors.stream().map(RoofColor::getId).collect(Collectors.toSet());
            if (!existingRoofColorIds.equals(incomingRoofColorIds)) {
                logger.debug("Product ID {}: Měním RoofColors z {} na {}", id, existingRoofColorIds, incomingRoofColorIds);
                productToUpdate.getAvailableRoofColors().clear();
                productToUpdate.getAvailableRoofColors().addAll(incomingRoofColors);
            } else {
                logger.debug("Product ID {}: RoofColors se nemění.", id);
            }
            // *** KONEC OPRAVY ***


            // --- Logování PŘED uložením (pro kontrolu) ---
            logger.info("--- Stav productToUpdate PŘED uložením (ID: {}) ---", id);
            logger.info("Name: '{}', Active: {}, Customisable: {}", productToUpdate.getName(), productToUpdate.isActive(), productToUpdate.isCustomisable());
            logger.info("TaxRates: {}", productToUpdate.getAvailableTaxRates().stream().map(TaxRate::getId).collect(Collectors.toSet()));
            logger.info("Addons: {}", productToUpdate.getAvailableAddons().stream().map(Addon::getId).collect(Collectors.toSet()));
            // Nyní by měly logovat správné hodnoty i pro tyto:
            logger.info("Designs: {}", productToUpdate.getAvailableDesigns().stream().map(Design::getId).collect(Collectors.toSet()));
            logger.info("Glazes: {}", productToUpdate.getAvailableGlazes().stream().map(Glaze::getId).collect(Collectors.toSet()));
            logger.info("RoofColors: {}", productToUpdate.getAvailableRoofColors().stream().map(RoofColor::getId).collect(Collectors.toSet()));
            if (productToUpdate.getConfigurator() != null) {
                ProductConfigurator cfg = productToUpdate.getConfigurator();
                logger.info("Configurator: MinLen={}, MaxLen={}, PriceLenCZK={}", cfg.getMinLength(), cfg.getMaxLength(), cfg.getPricePerCmLengthCZK());
            } else {
                logger.info("Configurator: null");
            }
            logger.info("-----------------------------------------------------");


            // --- Uložení ---
            // Uložíme productToUpdate, který má nyní všechny změny z productData
            Product savedProduct = productRepository.save(productToUpdate);
            logger.info("[ProductService] Produkt ID {} úspěšně aktualizován v DB (voláno save).", id);
            return Optional.of(savedProduct);

        } catch (IllegalArgumentException | EntityNotFoundException e) {
            logger.warn("!!! [ProductService] Chyba validace nebo nenalezení entity v updateProduct pro ID {}: {} !!!", id, e.getMessage());
            throw e; // Propagujeme výjimku pro zpracování v controlleru/advice
        } catch (DataIntegrityViolationException e) {
            // Specifická chyba pro porušení unikátních omezení (např. slug)
            logger.error("!!! [ProductService] Chyba integrity dat v updateProduct pro ID {}: {} !!!", id, e.getMessage(), e);
            // Můžeme zkusit zjistit, co přesně selhalo, např. podle zprávy výjimky
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("slug")) {
                throw new IllegalArgumentException("Produkt se slugem '" + productToUpdate.getSlug() + "' již existuje (chyba DB).");
            }
            throw e; // Propagujeme obecnější chybu integrity
        } catch (Exception e) {
            logger.error("!!! [ProductService] Neočekávaná chyba v updateProduct pro ID {}: {} !!!", id, e.getMessage(), e);
            // Zabalíme do RuntimeException
            throw new RuntimeException("Neočekávaná chyba při aktualizaci produktu ID " + id + ": " + e.getMessage(), e);
        }
    }

    // Pomocná metoda pro update konfigurátoru (zůstává stejná)
    private void updateConfiguratorData(ProductConfigurator existingConfig, ProductConfigurator dataConfig) {
        // ... (kód metody zůstává stejný jako v tvém posledním souboru) ...
        if (existingConfig == null || dataConfig == null) {
            logger.warn("Skipping configurator update due to null existing or data object.");
            return;
        }
        Long productId = (existingConfig.getProduct() != null) ? existingConfig.getProduct().getId() : null;
        logger.debug("Updating ProductConfigurator data for product ID: {}", productId);

        existingConfig.setMinLength(dataConfig.getMinLength());
        existingConfig.setMaxLength(dataConfig.getMaxLength());
        existingConfig.setMinWidth(dataConfig.getMinWidth());
        existingConfig.setMaxWidth(dataConfig.getMaxWidth());
        existingConfig.setMinHeight(dataConfig.getMinHeight());
        existingConfig.setMaxHeight(dataConfig.getMaxHeight());

        existingConfig.setStepLength(dataConfig.getStepLength());
        existingConfig.setStepWidth(dataConfig.getStepWidth());
        existingConfig.setStepHeight(dataConfig.getStepHeight());
        existingConfig.setDefaultLength(dataConfig.getDefaultLength());
        existingConfig.setDefaultWidth(dataConfig.getDefaultWidth());
        existingConfig.setDefaultHeight(dataConfig.getDefaultHeight());

        existingConfig.setPricePerCmHeightCZK(dataConfig.getPricePerCmHeightCZK());
        existingConfig.setPricePerCmHeightEUR(dataConfig.getPricePerCmHeightEUR());
        existingConfig.setPricePerCmLengthCZK(dataConfig.getPricePerCmLengthCZK());
        existingConfig.setPricePerCmLengthEUR(dataConfig.getPricePerCmLengthEUR());
        existingConfig.setPricePerCmWidthCZK(dataConfig.getPricePerCmWidthCZK());
        existingConfig.setPricePerCmWidthEUR(dataConfig.getPricePerCmWidthEUR());

        logger.trace("Product ID {}: Updated configurator settings.", productId);
    }

    private void ensureCollectionsInitialized(Product product) {
        if (product == null) return;
        if (product.getAvailableDesigns() == null) product.setAvailableDesigns(new HashSet<>());
        if (product.getAvailableGlazes() == null) product.setAvailableGlazes(new HashSet<>());
        if (product.getAvailableRoofColors() == null) product.setAvailableRoofColors(new HashSet<>());
        if (product.getAvailableAddons() == null) product.setAvailableAddons(new HashSet<>());
        if (product.getAvailableTaxRates() == null) product.setAvailableTaxRates(new HashSet<>());
        if (product.getImages() == null) product.setImages(new HashSet<>());
        if (product.getDiscounts() == null) product.setDiscounts(new HashSet<>());
    }

    private void initializeDefaultConfiguratorValues(ProductConfigurator configurator) {
        if (configurator == null) return;
        // Apply defaults only if the field is currently null
        configurator.setMinLength(Optional.ofNullable(configurator.getMinLength()).orElse(new BigDecimal("100.00")));
        configurator.setMaxLength(Optional.ofNullable(configurator.getMaxLength()).orElse(new BigDecimal("500.00")));
        configurator.setMinWidth(Optional.ofNullable(configurator.getMinWidth()).orElse(new BigDecimal("50.00")));
        configurator.setMaxWidth(Optional.ofNullable(configurator.getMaxWidth()).orElse(new BigDecimal("200.00")));
        configurator.setMinHeight(Optional.ofNullable(configurator.getMinHeight()).orElse(new BigDecimal("150.00")));
        configurator.setMaxHeight(Optional.ofNullable(configurator.getMaxHeight()).orElse(new BigDecimal("300.00")));

        configurator.setPricePerCmHeightCZK(Optional.ofNullable(configurator.getPricePerCmHeightCZK()).orElse(new BigDecimal("14.00")));
        configurator.setPricePerCmLengthCZK(Optional.ofNullable(configurator.getPricePerCmLengthCZK()).orElse(new BigDecimal("99.00")));
        configurator.setPricePerCmWidthCZK(Optional.ofNullable(configurator.getPricePerCmWidthCZK()).orElse(new BigDecimal("25.00"))); // Assumes renamed field

        configurator.setPricePerCmHeightEUR(Optional.ofNullable(configurator.getPricePerCmHeightEUR()).orElse(new BigDecimal("0.56")));
        configurator.setPricePerCmLengthEUR(Optional.ofNullable(configurator.getPricePerCmLengthEUR()).orElse(new BigDecimal("3.96")));
        configurator.setPricePerCmWidthEUR(Optional.ofNullable(configurator.getPricePerCmWidthEUR()).orElse(new BigDecimal("1.00"))); // Assumes renamed field


        configurator.setStepLength(Optional.ofNullable(configurator.getStepLength()).orElse(BigDecimal.TEN));
        configurator.setStepWidth(Optional.ofNullable(configurator.getStepWidth()).orElse(BigDecimal.TEN));
        configurator.setStepHeight(Optional.ofNullable(configurator.getStepHeight()).orElse(BigDecimal.valueOf(5)));

        // Default dimensions should only be set if within min/max range, otherwise leave null (JS will use min)
        if (configurator.getDefaultLength() != null && (configurator.getMinLength() != null && configurator.getDefaultLength().compareTo(configurator.getMinLength()) < 0 || configurator.getMaxLength() != null && configurator.getDefaultLength().compareTo(configurator.getMaxLength()) > 0)) {
            configurator.setDefaultLength(null); // Invalidate if out of range
        }
        if (configurator.getDefaultWidth() != null && (configurator.getMinWidth() != null && configurator.getDefaultWidth().compareTo(configurator.getMinWidth()) < 0 || configurator.getMaxWidth() != null && configurator.getDefaultWidth().compareTo(configurator.getMaxWidth()) > 0)) {
            configurator.setDefaultWidth(null); // Invalidate if out of range
        }
        if (configurator.getDefaultHeight() != null && (configurator.getMinHeight() != null && configurator.getDefaultHeight().compareTo(configurator.getMinHeight()) < 0 || configurator.getMaxHeight() != null && configurator.getDefaultHeight().compareTo(configurator.getMaxHeight()) > 0)) {
            configurator.setDefaultHeight(null); // Invalidate if out of range
        }

        logger.info("Applied/verified default values for configurator linked to product ID {}", configurator.getProduct() != null ? configurator.getProduct().getId() : "(unlinked)");
    }

    @Caching(evict = {
            @CacheEvict(value = {"activeProductsPage", "activeProductsList", "allProductsList", "productsPage"}, allEntries = true),
            @CacheEvict(value = "productDetails", key = "#productId"),
            @CacheEvict(value = "productBySlug", allEntries = true)
    })
    @Transactional
    public Image addImageToProduct(Long productId, MultipartFile file, String altText, String titleText, Integer displayOrder) throws IOException {
        logger.info(">>> [ProductService] Attempting to add image to product ID: {}", productId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot add an empty image file.");
        }

        // *** OPRAVA: Znovu načteme produkt včetně obrázků v rámci transakce ***
        Product product = productRepository.findByIdWithDetails(productId) // Použijeme metodu, která načte i obrázky (nebo zajistíme EAGER loading)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + productId + " when adding image."));
        logger.debug("Product ID {} found with {} existing images (loaded eagerly).", productId, product.getImages().size());

        // Uložení fyzického souboru
        String fileUrl = fileStorageService.storeFile(file, "products");

        Image newImage = new Image();
        newImage.setUrl(fileUrl);
        newImage.setAltText(altText);
        newImage.setTitleText(titleText);
        newImage.setProduct(product); // Přiřadíme načtený produkt

        // Výpočet displayOrder - nyní by měl `product.getImages()` fungovat korektně
        if (displayOrder == null) {
            try {
                int maxOrder = product.getImages().stream() // Přístup ke kolekci obrázků
                        .mapToInt(img -> img.getDisplayOrder() != null ? img.getDisplayOrder() : -1)
                        .max()
                        .orElse(-1);
                newImage.setDisplayOrder(maxOrder + 1);
                logger.debug("Calculated next displayOrder for new image: {}", newImage.getDisplayOrder());
            } catch (Exception e) {
                logger.error("!!! Error accessing product images stream for product ID {}: {} !!!", productId, e.getMessage(), e);
                // Zde bychom měli buď hodit specifickou výjimku, nebo nastavit defaultní order
                // Vzhledem k EntityNotFoundException v logu je pravděpodobné, že problém je v datech/cache
                // Můžeme zkusit nastavit default 0, ale je to jen workaround
                newImage.setDisplayOrder(0);
                logger.warn("Could not calculate max display order due to error. Setting displayOrder to 0 for new image.");
                // Důležité: Pokud tato chyba nastane, je nutné prošetřit konzistenci dat/cache!
            }
        } else {
            newImage.setDisplayOrder(displayOrder);
        }

        // Uložení entity Image
        Image savedImage = imageRepository.save(newImage);

        // *** DŮLEŽITÉ: Přidání do kolekce v paměti a uložení produktu ***
        // Toto zajistí, že vztah je konzistentní a cache (pokud se používá) se aktualizuje.
        // product.getImages().add(savedImage); // Přidáme do setu v paměti
        // productRepository.save(product); // Uložíme produkt, aby se aktualizovala cache kolekce
        // Poznámka: Pokud save(newImage) správně nastaví vztah a cache se invaliduje, explicitní save produktu nemusí být nutné.
        // Ale pro jistotu může pomoci. Pokud by to způsobovalo problémy, zakomentuj productRepository.save(product).

        logger.info(">>> [ProductService] Image successfully saved (ID: {}) and associated with product ID: {}", savedImage.getId(), productId);
        return savedImage;
    }


    // --- NOVÁ METODA pro přidání obrázku s URL ---
    /**
     * Přidá záznam o obrázku k produktu s již existující URL (např. z GCS).
     * Neřeší fyzické nahrání souboru.
     *
     * @param productId    ID produktu.
     * @param fileUrl      URL obrázku (např. GCS URL).
     * @param altText      Alternativní text.
     * @param titleText    Titulní text.
     * @param displayOrder Pořadí zobrazení (null pro automatické).
     * @return Uložená entita Image.
     * @throws EntityNotFoundException Pokud produkt neexistuje.
     * @throws IllegalArgumentException Pokud je URL neplatná.
     */
    @Caching(evict = {
            @CacheEvict(value = {"activeProductsPage", "activeProductsList", "allProductsList", "productsPage"}, allEntries = true),
            @CacheEvict(value = "productDetails", key = "#productId"),
            @CacheEvict(value = "productBySlug", allEntries = true) // Invalidujeme slug cache pro jistotu
    })
    @Transactional
    public Image addImageToProductWithUrl(Long productId, String fileUrl, String altText, String titleText, Integer displayOrder) {
        logger.info(">>> [ProductService] Associating image URL '{}' with product ID: {}", fileUrl, productId);
        if (!StringUtils.hasText(fileUrl)) {
            throw new IllegalArgumentException("Image URL cannot be empty.");
        }

        Product product = productRepository.findByIdWithDetails(productId) // Načteme i obrázky pro výpočet pořadí
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + productId + " when adding image URL."));

        Image newImage = new Image();
        newImage.setUrl(fileUrl); // Nastavíme GCS URL
        newImage.setAltText(altText);
        newImage.setTitleText(titleText);
        newImage.setProduct(product);

        // Výpočet displayOrder
        if (displayOrder == null) {
            int maxOrder = product.getImages().stream()
                    .mapToInt(img -> img.getDisplayOrder() != null ? img.getDisplayOrder() : -1)
                    .max()
                    .orElse(-1);
            newImage.setDisplayOrder(maxOrder + 1);
            logger.debug("Calculated next displayOrder for new image URL: {}", newImage.getDisplayOrder());
        } else {
            newImage.setDisplayOrder(displayOrder);
        }

        // Uložení entity Image
        Image savedImage = imageRepository.save(newImage);

        // Není třeba volat productRepository.save(product), pokud je kaskádování nastaveno správně,
        // ale pro jistotu aktualizace cache to může být někdy užitečné (můžete odkomentovat, pokud by byly problémy).
        // product.getImages().add(savedImage); // Hibernate by měl zvládnout automaticky
        // productRepository.save(product);

        logger.info(">>> [ProductService] Image entity successfully saved (ID: {}) with GCS URL for product ID: {}", savedImage.getId(), productId);
        return savedImage;
    }


    // --- UPRAVENÁ METODA deleteImage ---
    /**
     * Smaže POUZE záznam o obrázku z databáze.
     * Fyzické smazání souboru z úložiště musí být provedeno zvlášť (např. v controlleru).
     *
     * @param imageId ID obrázku ke smazání z DB.
     * @throws EntityNotFoundException Pokud obrázek s daným ID neexistuje.
     */
    @Caching(evict = {
            @CacheEvict(value = {"activeProductsPage", "activeProductsList", "allProductsList", "productsPage"}, allEntries = true),
            @CacheEvict(value = "productDetails", allEntries = true), // Jednodušší invalidovat vše zde
            @CacheEvict(value = "productBySlug", allEntries = true)
    })
    @Transactional
    public void deleteImage(Long imageId) { // Ponecháme původní název metody
        logger.warn(">>> [ProductService] Deleting ONLY DB record for image ID: {}", imageId);
        try {
            // 1. Najít obrázek NEBO vyhodit výjimku
            // Nepotřebujeme zde nutně načítat produkt, pokud je orphanRemoval=true na @OneToMany v Product
            // Pokud by mazání selhávalo kvůli vazbám, možná bude potřeba načíst produkt a odstranit obrázek z kolekce manuálně PŘED delete.
            if (!imageRepository.existsById(imageId)) {
                throw new EntityNotFoundException("Image record not found: " + imageId);
            }

            // 2. Smazat entitu Image z databáze
            imageRepository.deleteById(imageId);
            imageRepository.flush(); // Zajistí provedení delete SQL ihned

            logger.info("[ProductService] Image entity record ID {} deleted from database.", imageId);

            // 3. Fyzické mazání souboru se zde již NEPROVÁDÍ
            // SMAZÁNO: fileStorageService.deleteFile(fileUrl);

        } catch (EntityNotFoundException e) {
            logger.error("!!! [ProductService] Image record ID {} not found for DB deletion.", imageId);
            throw e;
        } catch (Exception e) {
            logger.error("!!! [ProductService] Error deleting image DB record for ID {}: {} !!!", imageId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete image DB record " + imageId, e);
        }
        logger.info(">>> [ProductService] DB record deletion process finished for image ID: {}", imageId);
    }

    // --- NOVĚ PŘIDANÁ METODA deleteProduct ---
    @Caching(evict = {
            @CacheEvict(value = {"activeProductsPage", "activeProductsList", "allProductsList", "productsPage"}, allEntries = true),
            @CacheEvict(value = "productDetails", key = "#id"),
            @CacheEvict(value = "productBySlug", allEntries = true) // Jednodušší invalidace pro slug
    })
    @Transactional
    public void deleteProduct(Long id) {
        logger.info(">>> [ProductService] Vstupuji do deleteProduct (soft delete). ID: {}", id);
        try {
            Product product = productRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Product with id " + id + " not found for deletion."));
            if (!product.isActive()) {
                logger.warn("[ProductService] Produkt ID {} ('{}') je již neaktivní.", id, product.getName());
                return; // Není co dělat
            }
            product.setActive(false); // Označíme jako neaktivní
            productRepository.save(product); // Uložíme změnu
            logger.info("[ProductService] Produkt ID {} ('{}') označen jako neaktivní (soft delete).", id, product.getName());
        } catch (EntityNotFoundException e) {
            logger.error("!!! [ProductService] Produkt ID {} nenalezen pro smazání (soft delete).", id);
            throw e; // Propagujeme výjimku
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v deleteProduct (soft delete) pro ID {}: {} !!!", id, e.getMessage(), e);
            throw new RuntimeException("Error deactivating product ID " + id, e); // Zabalíme do RuntimeException
        }
        logger.info(">>> [ProductService] Opouštím deleteProduct (soft delete). ID: {}", id);
    }

    @Caching(evict = {
            // Invaliduje cache související s produkty, protože se mění data obrázků
            @CacheEvict(value = {"activeProductsPage", "activeProductsList", "allProductsList", "productsPage"}, allEntries = true),
            @CacheEvict(value = "productDetails", key = "#productId"),
            @CacheEvict(value = "productBySlug", allEntries = true) // Jednodušší invalidovat všechny slugy
    })
    @Transactional // Zajistí, že všechny změny se uloží nebo žádná
    public void updateImageDisplayOrder(Long productId, Map<String, Integer> orderMap) {
        logger.info(">>> [ProductService] Vstupuji do updateImageDisplayOrder. Product ID: {}, Počet položek v mapě: {}", productId, orderMap != null ? orderMap.size() : "null");

        if (orderMap == null) {
            throw new IllegalArgumentException("Mapa pořadí (orderMap) nesmí být null.");
        }

        // 1. Najdi produkt včetně jeho obrázků
        // Použijeme findByIdWithDetails, abychom měli jistotu, že obrázky jsou načtené
        Product product = productRepository.findByIdWithDetails(productId)
                .orElseThrow(() -> new EntityNotFoundException("Produkt s ID " + productId + " nenalezen pro aktualizaci pořadí obrázků."));

        Set<Image> images = product.getImages();
        if (images == null || images.isEmpty()) {
            logger.warn("[ProductService] Produkt ID {} nemá žádné obrázky k aktualizaci pořadí.", productId);
            // Není co dělat, pokud nejsou obrázky
            return;
        }

        // 2. Projdi mapu a aktualizuj pořadí u nalezených obrázků
        int updatedCount = 0;
        for (Map.Entry<String, Integer> entry : orderMap.entrySet()) {
            String imageIdStr = entry.getKey();
            Integer newOrder = entry.getValue();

            if (newOrder == null) {
                logger.warn("[ProductService] Přeskakuji obrázek ID '{}' - hodnota pořadí je null.", imageIdStr);
                continue; // Přeskoč, pokud je pořadí null
            }

            try {
                Long imageId = Long.parseLong(imageIdStr);

                // Najdi obrázek v kolekci produktu (efektivnější než dotaz do DB pro každý obrázek)
                Optional<Image> imageOpt = images.stream()
                        .filter(img -> img.getId().equals(imageId))
                        .findFirst();

                if (imageOpt.isPresent()) {
                    Image imageToUpdate = imageOpt.get();
                    // Zkontroluj, zda se aktuální pořadí liší od nového
                    if (!newOrder.equals(imageToUpdate.getDisplayOrder())) {
                        imageToUpdate.setDisplayOrder(newOrder);
                        logger.debug("[ProductService] Aktualizuji pořadí obrázku ID {} na {}.", imageId, newOrder);
                        updatedCount++;
                        // Změna se uloží automaticky na konci transakce díky dirty checking
                        // nebo explicitním save níže
                    } else {
                        logger.trace("[ProductService] Pořadí obrázku ID {} se nezměnilo ({}).", imageId, newOrder);
                    }
                } else {
                    logger.warn("[ProductService] Obrázek s ID {} nebyl nalezen v kolekci obrázků produktu ID {}. Přeskakuji.", imageId, productId);
                }

            } catch (NumberFormatException e) {
                logger.error("!!! [ProductService] Neplatný formát ID obrázku '{}' v mapě pořadí. Přeskakuji.", imageIdStr);
            }
        }

        // 3. Ulož změny (volitelné, pokud spoléháš na @Transactional a dirty checking,
        // ale explicitní save může být jistější nebo nutné podle konfigurace)
        // Pokud máš CascadeType.MERGE nebo ALL na kolekci images v Product entitě,
        // uložení produktu by mělo stačit. Jinak můžeš uložit přímo změněné obrázky (méně efektivní).
        if (updatedCount > 0) {
            // productRepository.save(product); // Zkus tuto variantu, pokud máš kaskádování
            // Alternativně: imageRepository.saveAll(images); // Pokud chceš uložit všechny obrázky znovu
            logger.info("[ProductService] Pořadí bylo aktualizováno pro {} obrázků produktu ID {}. Změny budou uloženy.", updatedCount, productId);
        } else {
            logger.info("[ProductService] Nebyly provedeny žádné změny v pořadí obrázků pro produkt ID {}.", productId);
        }


        logger.info(">>> [ProductService] Opouštím updateImageDisplayOrder. Product ID: {}", productId);
    }

    @Transactional(readOnly = true)
    public CustomPriceResponseDto calculateDetailedCustomPrice(CustomPriceRequestDto requestDto) {
        logger.info(">>> [ProductService] Vstupuji do calculateDetailedCustomPrice. Product ID: {}", requestDto.getProductId());
        BigDecimal zero = BigDecimal.ZERO.setScale(PRICE_SCALE, ROUNDING_MODE);
        CustomPriceResponseDto response = new CustomPriceResponseDto();
        response.setAddonPricesCZK(new HashMap<>()); // Inicializace map
        response.setAddonPricesEUR(new HashMap<>());
        // Inicializace všech číselných hodnot na 0 pro případ chyby
        response.setBasePriceCZK(zero);
        response.setBasePriceEUR(zero);
        response.setDesignPriceCZK(zero);
        response.setDesignPriceEUR(zero);
        response.setGlazePriceCZK(zero);
        response.setGlazePriceEUR(zero);
        response.setRoofColorPriceCZK(zero);
        response.setRoofColorPriceEUR(zero);
        response.setTotalPriceCZK(zero);
        response.setTotalPriceEUR(zero);


        try {
            // --- Validace a načtení produktu ---
            Product product = productRepository.findByIdWithDetails(requestDto.getProductId())
                    .orElseThrow(() -> new EntityNotFoundException("Produkt nenalezen: " + requestDto.getProductId()));

            if (!product.isActive() || !product.isCustomisable() || product.getConfigurator() == null) {
                throw new IllegalArgumentException("Produkt ID " + requestDto.getProductId() + " není aktivní, konfigurovatelný, nebo chybí konfigurace.");
            }
            ProductConfigurator config = product.getConfigurator();

            // --- Výpočet základní ceny (z rozměrů) ---
            Map<String, BigDecimal> dimensions = requestDto.getCustomDimensions();
            if (dimensions == null || dimensions.get("length") == null || dimensions.get("width") == null || dimensions.get("height") == null) {
                throw new IllegalArgumentException("Chybí kompletní rozměry.");
            }
            BigDecimal length = dimensions.get("length");
            BigDecimal width = dimensions.get("width");
            BigDecimal height = dimensions.get("height");

            validateDimension("Length (Délka)", length, config.getMinLength(), config.getMaxLength());
            validateDimension("Width (Šířka/Hloubka)", width, config.getMinWidth(), config.getMaxWidth());
            validateDimension("Height (Výška)", height, config.getMinHeight(), config.getMaxHeight());

            // Výpočet pro CZK
            BigDecimal pricePerCmH_CZK = getPriceForCurrency(config.getPricePerCmHeightCZK(), null, "CZK", "Height Price/cm");
            BigDecimal pricePerCmL_CZK = getPriceForCurrency(config.getPricePerCmLengthCZK(), null, "CZK", "Length Price/cm");
            BigDecimal pricePerCmW_CZK = getPriceForCurrency(config.getPricePerCmWidthCZK(), null, "CZK", "Width Price/cm");
            BigDecimal basePriceCZK = height.multiply(pricePerCmH_CZK).add(length.multiply(pricePerCmL_CZK)).add(width.multiply(pricePerCmW_CZK));
            response.setBasePriceCZK(basePriceCZK.setScale(PRICE_SCALE, ROUNDING_MODE).max(zero));

            // Výpočet pro EUR
            BigDecimal pricePerCmH_EUR = getPriceForCurrency(null, config.getPricePerCmHeightEUR(), "EUR", "Height Price/cm");
            BigDecimal pricePerCmL_EUR = getPriceForCurrency(null, config.getPricePerCmLengthEUR(), "EUR", "Length Price/cm");
            BigDecimal pricePerCmW_EUR = getPriceForCurrency(null, config.getPricePerCmWidthEUR(), "EUR", "Width Price/cm");
            BigDecimal basePriceEUR = height.multiply(pricePerCmH_EUR).add(length.multiply(pricePerCmL_EUR)).add(width.multiply(pricePerCmW_EUR));
            response.setBasePriceEUR(basePriceEUR.setScale(PRICE_SCALE, ROUNDING_MODE).max(zero));

            logger.debug("[calculateDetailedCustomPrice] Base price calculated: CZK={}, EUR={}", response.getBasePriceCZK(), response.getBasePriceEUR());

            // --- Výpočet ceny za atributy (Design, Lazura, Střecha) ---
            BigDecimal designPriceCZK = zero;
            BigDecimal designPriceEUR = zero;
            if (requestDto.getSelectedDesignId() != null) {
                Design design = designRepository.findById(requestDto.getSelectedDesignId()) // Opraveno: Použití designRepository
                        .orElseThrow(() -> new EntityNotFoundException("Design nenalezen: " + requestDto.getSelectedDesignId()));
                // Použití správných getterů getPriceSurchargeCZK/EUR
                designPriceCZK = Optional.ofNullable(design.getPriceSurchargeCZK()).orElse(zero); // Opraveno
                designPriceEUR = Optional.ofNullable(design.getPriceSurchargeEUR()).orElse(zero); // Opraveno
            }
            response.setDesignPriceCZK(designPriceCZK.setScale(PRICE_SCALE, ROUNDING_MODE));
            response.setDesignPriceEUR(designPriceEUR.setScale(PRICE_SCALE, ROUNDING_MODE));
            logger.debug("[calculateDetailedCustomPrice] Design price: CZK={}, EUR={}", response.getDesignPriceCZK(), response.getDesignPriceEUR());

            BigDecimal glazePriceCZK = zero;
            BigDecimal glazePriceEUR = zero;
            if (requestDto.getSelectedGlazeId() != null) {
                Glaze glaze = glazeRepository.findById(requestDto.getSelectedGlazeId()) // Opraveno: Použití glazeRepository
                        .orElseThrow(() -> new EntityNotFoundException("Lazura nenalezena: " + requestDto.getSelectedGlazeId()));
                // Použití správných getterů getPriceSurchargeCZK/EUR
                glazePriceCZK = Optional.ofNullable(glaze.getPriceSurchargeCZK()).orElse(zero); // Opraveno
                glazePriceEUR = Optional.ofNullable(glaze.getPriceSurchargeEUR()).orElse(zero); // Opraveno
            }
            response.setGlazePriceCZK(glazePriceCZK.setScale(PRICE_SCALE, ROUNDING_MODE));
            response.setGlazePriceEUR(glazePriceEUR.setScale(PRICE_SCALE, ROUNDING_MODE));
            logger.debug("[calculateDetailedCustomPrice] Glaze price: CZK={}, EUR={}", response.getGlazePriceCZK(), response.getGlazePriceEUR());

            BigDecimal roofColorPriceCZK = zero;
            BigDecimal roofColorPriceEUR = zero;
            if (requestDto.getSelectedRoofColorId() != null) {
                RoofColor roofColor = roofColorRepository.findById(requestDto.getSelectedRoofColorId()) // Opraveno: Použití roofColorRepository
                        .orElseThrow(() -> new EntityNotFoundException("Barva střechy nenalezena: " + requestDto.getSelectedRoofColorId()));
                // Použití správných getterů getPriceSurchargeCZK/EUR
                roofColorPriceCZK = Optional.ofNullable(roofColor.getPriceSurchargeCZK()).orElse(zero); // Opraveno
                roofColorPriceEUR = Optional.ofNullable(roofColor.getPriceSurchargeEUR()).orElse(zero); // Opraveno
            }
            response.setRoofColorPriceCZK(roofColorPriceCZK.setScale(PRICE_SCALE, ROUNDING_MODE));
            response.setRoofColorPriceEUR(roofColorPriceEUR.setScale(PRICE_SCALE, ROUNDING_MODE));
            logger.debug("[calculateDetailedCustomPrice] RoofColor price: CZK={}, EUR={}", response.getRoofColorPriceCZK(), response.getRoofColorPriceEUR());

            // --- Výpočet ceny za doplňky ---
            BigDecimal totalAddonsCZK = zero;
            BigDecimal totalAddonsEUR = zero;
            if (requestDto.getSelectedAddonIds() != null && !requestDto.getSelectedAddonIds().isEmpty()) {
                List<Addon> selectedAddons = addonsRepository.findAllById(requestDto.getSelectedAddonIds()); // Opraveno: Použití addonsRepository
                if (selectedAddons.size() != requestDto.getSelectedAddonIds().size()) {
                    Set<Long> foundIds = selectedAddons.stream().map(Addon::getId).collect(Collectors.toSet());
                    requestDto.getSelectedAddonIds().stream()
                            .filter(id -> !foundIds.contains(id))
                            .findFirst()
                            .ifPresent(notFoundId -> {
                                logger.warn("Doplněk s ID {} nebyl nalezen.", notFoundId);
                                // Můžeme zde hodit chybu, nebo jen logovat a pokračovat s nalezenými
                                // throw new EntityNotFoundException("Doplněk nenalezen: " + notFoundId);
                            });
                }

                for (Addon addon : selectedAddons) {
                    if (!addon.isActive()) {
                        logger.warn("Vybraný doplněk '{}' (ID: {}) není aktivní, přeskakuji výpočet ceny.", addon.getName(), addon.getId());
                        continue; // Přeskočit neaktivní
                    }
                    // Ověřit, zda je doplněk povolený pro tento produkt? product.getAvailableAddons().contains(addon)
                    // Prozatím ne, spoléháme na frontend.

                    BigDecimal addonPriceCZK = calculateSingleAddonPriceBackend(addon, dimensions, "CZK");
                    BigDecimal addonPriceEUR = calculateSingleAddonPriceBackend(addon, dimensions, "EUR");

                    // Přidáme do mapy rozpisu, jen pokud je cena > 0
                    if (addonPriceCZK.compareTo(zero) > 0) {
                        response.getAddonPricesCZK().put(addon.getName(), addonPriceCZK);
                        totalAddonsCZK = totalAddonsCZK.add(addonPriceCZK);
                    }
                    if (addonPriceEUR.compareTo(zero) > 0) {
                        response.getAddonPricesEUR().put(addon.getName(), addonPriceEUR);
                        totalAddonsEUR = totalAddonsEUR.add(addonPriceEUR);
                    }
                    logger.debug("[calculateDetailedCustomPrice] Addon '{}' price: CZK={}, EUR={}", addon.getName(), addonPriceCZK, addonPriceEUR);
                }
            }
            logger.debug("[calculateDetailedCustomPrice] Total Addons price: CZK={}, EUR={}", totalAddonsCZK, totalAddonsEUR);

            // --- Výpočet celkové ceny ---
            BigDecimal totalPriceCZK = response.getBasePriceCZK()
                    .add(response.getDesignPriceCZK())
                    .add(response.getGlazePriceCZK())
                    .add(response.getRoofColorPriceCZK())
                    .add(totalAddonsCZK);
            response.setTotalPriceCZK(totalPriceCZK.setScale(PRICE_SCALE, ROUNDING_MODE));

            BigDecimal totalPriceEUR = response.getBasePriceEUR()
                    .add(response.getDesignPriceEUR())
                    .add(response.getGlazePriceEUR())
                    .add(response.getRoofColorPriceEUR())
                    .add(totalAddonsEUR);
            response.setTotalPriceEUR(totalPriceEUR.setScale(PRICE_SCALE, ROUNDING_MODE));

            logger.info("[calculateDetailedCustomPrice] Total price calculated: CZK={}, EUR={}", response.getTotalPriceCZK(), response.getTotalPriceEUR());

        } catch (IllegalArgumentException | EntityNotFoundException | IllegalStateException e) {
            logger.error("!!! [ProductService] Chyba v calculateDetailedCustomPrice for Product ID {}: {} !!!", requestDto.getProductId(), e.getMessage());
            response.setErrorMessage(e.getMessage()); // Nastavíme chybovou zprávu do DTO
            // Ceny zůstanou inicializované na 0
        } catch (Exception e) {
            logger.error("!!! [ProductService] Neočekávaná chyba v calculateDetailedCustomPrice for Product ID {}: {} !!!", requestDto.getProductId(), e.getMessage(), e);
            response.setErrorMessage("Došlo k neočekávané chybě při výpočtu ceny.");
        }

        logger.info(">>> [ProductService] Opouštím calculateDetailedCustomPrice. Product ID: {}. Response has error: {}", requestDto.getProductId(), response.getErrorMessage() != null);
        return response;
    }

} // Konec třídy ProductService