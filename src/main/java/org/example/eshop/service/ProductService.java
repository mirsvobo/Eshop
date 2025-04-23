// src/main/java/org/example/eshop/service/ProductService.java
package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.model.*; // Import všech modelů
import org.example.eshop.repository.ImageRepository;
import org.example.eshop.repository.ProductRepository;
import org.example.eshop.repository.TaxRateRepository; // TaxRateRepository je potřeba
import org.example.eshop.config.PriceConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*; // Import pro Set, Map atd.
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Service
public class ProductService implements PriceConstants {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);
    @Autowired private FileStorageService fileStorageService;

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]+");
    private static final Pattern MULTIPLE_DASHES = Pattern.compile("-{2,}");
    private static final Pattern EDGES_DASHES = Pattern.compile("^-|-$");


    @Autowired private ProductRepository productRepository;
    @Autowired private ImageRepository imageRepository;
    @Autowired private TaxRateRepository taxRateRepository;
    @Autowired private DiscountService discountService;


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
    public Optional<Product> getProductById(Long id){
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

    @Cacheable(value = "productBySlug", key = "T(String).valueOf(#slug).toLowerCase()", unless="#result == null or !#result.isPresent()")
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

    // --- START: Simplified calculateDynamicProductPrice ---
    /**
     * Calculates the BASE price for a customizable product based ONLY on its dimensions
     * and the per-unit prices defined in its ProductConfigurator.
     * Prices for selected attributes (Design, Glaze, RoofColor) and Addons
     * are handled separately (client-side or during cart/order processing).
     *
     * @param product The customizable product.
     * @param dimensions A map containing "length", "width", and "height" keys with BigDecimal values.
     * @param currency The target currency ("CZK" or "EUR").
     * @return The calculated base price based on dimensions.
     * @throws IllegalArgumentException if product is null, inactive, not customizable,
     * missing configurator, dimensions map is null or missing keys,
     * or dimensions are outside the configured range.
     * @throws IllegalStateException if required price configurations are missing in ProductConfigurator.
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateDynamicProductPrice(Product product, Map<String, BigDecimal> dimensions,
                                                   String currency) {
        logger.info(">>> [ProductService] Vstupuji do calculateDynamicProductPrice (Base Dimensions Only). Product ID: {}, Currency: {}", product != null ? product.getId() : "null", currency);
        BigDecimal basePrice = BigDecimal.ZERO;
        try {
            // --- Input Validations ---
            if (product == null) throw new IllegalArgumentException("Product cannot be null.");
            if (!product.isActive()) throw new IllegalArgumentException("Cannot calculate price for inactive product ID: " + product.getId());
            if (!product.isCustomisable()) throw new IllegalArgumentException("Product ID " + product.getId() + " is not customisable.");
            if (product.getConfigurator() == null) throw new IllegalArgumentException("Product ID " + product.getId() + " is missing configurator.");
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
    // --- END: Simplified calculateDynamicProductPrice ---

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
            @CacheEvict(value = "productDetails", key = "#result.id", condition="#result != null"),
            @CacheEvict(value = "productBySlug", key = "T(String).valueOf(#result.slug).toLowerCase()", condition="#result != null")
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
            @CacheEvict(value = "productBySlug", allEntries = true) // Invalidate all slugs on update for simplicity
    })
    @Transactional
    public Optional<Product> updateProduct(Long id, Product productData, Product productToUpdate) {
        logger.info(">>> [ProductService] Vstupuji do updateProduct (s přednačtenou entitou). ID: {}", id);
        try {
            // productToUpdate is the existing entity from DB
            // productData contains new data from the form

            // --- Slug Update & Validation ---
            String newSlug = StringUtils.hasText(productData.getSlug()) ? generateSlug(productData.getSlug()) : generateSlug(productData.getName());
            if (!newSlug.equalsIgnoreCase(productToUpdate.getSlug())) {
                productRepository.findBySlugIgnoreCaseAndIdNot(newSlug, id).ifPresent(existing -> {
                    throw new IllegalArgumentException("Produkt se slugem '" + newSlug + "' již existuje.");
                });
                productToUpdate.setSlug(newSlug);
                logger.debug("Updating slug for product ID {} to '{}'", id, newSlug);
            }

            // --- Update Basic Fields ---
            productToUpdate.setName(productData.getName());
            if (productData.getShortDescription() != null && productData.getShortDescription().length() > 500) {
                throw new IllegalArgumentException("Krátký popis nesmí být delší než 500 znaků.");
            }
            productToUpdate.setShortDescription(productData.getShortDescription());
            productToUpdate.setDescription(productData.getDescription());
            productToUpdate.setBasePriceCZK(productData.getBasePriceCZK());
            productToUpdate.setBasePriceEUR(productData.getBasePriceEUR());
            productToUpdate.setModel(productData.getModel());
            productToUpdate.setMaterial(productData.getMaterial());
            productToUpdate.setHeight(productData.getHeight());
            productToUpdate.setLength(productData.getLength());
            productToUpdate.setWidth(productData.getWidth());
            productToUpdate.setRoofOverstep(productData.getRoofOverstep());
            productToUpdate.setActive(productData.isActive());
            productToUpdate.setMetaTitle(productData.getMetaTitle());
            productToUpdate.setMetaDescription(productData.getMetaDescription());

            // --- Handle Customisable Flag Change ---
            boolean wasCustom = productToUpdate.isCustomisable();
            boolean isCustom = productData.isCustomisable();
            if (wasCustom != isCustom) {
                logger.info("Product ID {} customisable status changed from {} to {}", id, wasCustom, isCustom);
                productToUpdate.setCustomisable(isCustom);
                if (isCustom) { // Becoming custom
                    // Ensure configurator exists and is updated/created
                    if (productToUpdate.getConfigurator() == null) {
                        ProductConfigurator newConfig = (productData.getConfigurator() != null) ? productData.getConfigurator() : new ProductConfigurator();
                        newConfig.setProduct(productToUpdate);
                        initializeDefaultConfiguratorValues(newConfig); // Apply defaults/form data
                        productToUpdate.setConfigurator(newConfig);
                        logger.debug("Creating/Initializing configurator for newly customisable product ID {}", id);
                    } else if (productData.getConfigurator() != null) {
                        updateConfiguratorData(productToUpdate.getConfigurator(), productData.getConfigurator());
                        logger.debug("Updating existing configurator for product ID {}", id);
                    }
                    // Clear standard attributes, keep addons/tax rates
                    productToUpdate.getAvailableDesigns().clear();
                    productToUpdate.getAvailableGlazes().clear();
                    productToUpdate.getAvailableRoofColors().clear();
                    logger.debug("Clearing standard attributes for product ID {} (became custom)", id);
                } else { // Becoming standard
                    productToUpdate.setConfigurator(null); // Remove configurator
                    productToUpdate.getAvailableAddons().clear(); // Clear addons
                    logger.debug("Removing configurator and addons for product ID {} (became standard)", id);
                }
            } else if (isCustom && productData.getConfigurator() != null) {
                // Still custom, update configurator data if provided
                if (productToUpdate.getConfigurator() != null) {
                    updateConfiguratorData(productToUpdate.getConfigurator(), productData.getConfigurator());
                    logger.debug("Updating configurator for existing custom product ID {}", id);
                } else {
                    // Should not happen if consistency is maintained, but handle it
                    ProductConfigurator newConfig = productData.getConfigurator();
                    newConfig.setProduct(productToUpdate);
                    initializeDefaultConfiguratorValues(newConfig);
                    productToUpdate.setConfigurator(newConfig);
                    logger.warn("Configurator was null for custom product ID {}. Creating new one based on form data.", id);
                }
            }

            // --- Update Associations (TaxRates, Addons, Designs, etc.) ---
            // These are typically handled by the controller which fetches IDs from the form,
            // loads the entities, and sets them on productToUpdate *before* calling this service method.
            // Example: productToUpdate.setAvailableTaxRates(loadedTaxRates);
            //          productToUpdate.setAvailableAddons(loadedAddons);
            // Ensure collections are not null before saving
            ensureCollectionsInitialized(productToUpdate);

            // Validation: Check if standard product has standard attributes if needed
            if (!isCustom && (CollectionUtils.isEmpty(productToUpdate.getAvailableDesigns()) || CollectionUtils.isEmpty(productToUpdate.getAvailableGlazes()) || CollectionUtils.isEmpty(productToUpdate.getAvailableRoofColors()))) {
                logger.warn("Standard product ID {} might be missing some standard attributes (Designs/Glazes/RoofColors).", id);
                // Depending on requirements, you might throw an exception or allow saving.
            }
            // Validation: Check if at least one tax rate is present
            if (CollectionUtils.isEmpty(productToUpdate.getAvailableTaxRates())) {
                throw new IllegalArgumentException("Produkt ID " + id + " musí mít přiřazenu alespoň jednu daňovou sazbu.");
            }

            // --- Save ---
            Product savedProduct = productRepository.save(productToUpdate);
            logger.info("[ProductService] Produkt ID {} úspěšně aktualizován.", id);
            return Optional.of(savedProduct);

        } catch (IllegalArgumentException | EntityNotFoundException e) {
            logger.warn("!!! [ProductService] Chyba validace nebo nenalezení entity v updateProduct pro ID {}: {} !!!", id, e.getMessage());
            throw e; // Re-throw
        } catch (Exception e) {
            logger.error("!!! [ProductService] Neočekávaná chyba v updateProduct pro ID {}: {} !!!", id, e.getMessage(), e);
            throw new RuntimeException("Neočekávaná chyba při aktualizaci produktu ID " + id + ": " + e.getMessage(), e);
        }
    }

    // Pomocná metoda pro update konfigurátoru
    private void updateConfiguratorData(ProductConfigurator existingConfig, ProductConfigurator dataConfig) {
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
        existingConfig.setPricePerCmWidthCZK(dataConfig.getPricePerCmWidthCZK()); // Assumes renamed field
        existingConfig.setPricePerCmWidthEUR(dataConfig.getPricePerCmWidthEUR()); // Assumes renamed field

        // Prices for old hardcoded features (might be obsolete if these are addons now)
        existingConfig.setDesignPriceCZK(dataConfig.getDesignPriceCZK());
        existingConfig.setDesignPriceEUR(dataConfig.getDesignPriceEUR());
        existingConfig.setDividerPricePerCmDepthCZK(dataConfig.getDividerPricePerCmDepthCZK());
        existingConfig.setDividerPricePerCmDepthEUR(dataConfig.getDividerPricePerCmDepthEUR());
        existingConfig.setGutterPriceCZK(dataConfig.getGutterPriceCZK());
        existingConfig.setGutterPriceEUR(dataConfig.getGutterPriceEUR());
        existingConfig.setShedPriceCZK(dataConfig.getShedPriceCZK());
        existingConfig.setShedPriceEUR(dataConfig.getShedPriceEUR());

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
        configurator.setDividerPricePerCmDepthCZK(Optional.ofNullable(configurator.getDividerPricePerCmDepthCZK()).orElse(new BigDecimal("13.00")));
        configurator.setDesignPriceCZK(Optional.ofNullable(configurator.getDesignPriceCZK()).orElse(BigDecimal.ZERO));
        configurator.setGutterPriceCZK(Optional.ofNullable(configurator.getGutterPriceCZK()).orElse(new BigDecimal("1000.00")));
        configurator.setShedPriceCZK(Optional.ofNullable(configurator.getShedPriceCZK()).orElse(new BigDecimal("5000.00")));

        configurator.setPricePerCmHeightEUR(Optional.ofNullable(configurator.getPricePerCmHeightEUR()).orElse(new BigDecimal("0.56")));
        configurator.setPricePerCmLengthEUR(Optional.ofNullable(configurator.getPricePerCmLengthEUR()).orElse(new BigDecimal("3.96")));
        configurator.setPricePerCmWidthEUR(Optional.ofNullable(configurator.getPricePerCmWidthEUR()).orElse(new BigDecimal("1.00"))); // Assumes renamed field
        configurator.setDividerPricePerCmDepthEUR(Optional.ofNullable(configurator.getDividerPricePerCmDepthEUR()).orElse(new BigDecimal("0.52")));
        configurator.setDesignPriceEUR(Optional.ofNullable(configurator.getDesignPriceEUR()).orElse(BigDecimal.ZERO));
        configurator.setGutterPriceEUR(Optional.ofNullable(configurator.getGutterPriceEUR()).orElse(new BigDecimal("40.00")));
        configurator.setShedPriceEUR(Optional.ofNullable(configurator.getShedPriceEUR()).orElse(new BigDecimal("200.00")));

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

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + productId + " when adding image."));

        String fileUrl = fileStorageService.storeFile(file, "products"); // Assuming file storage service returns URL or path

        Image newImage = new Image();
        newImage.setUrl(fileUrl); // Store the URL/path returned by storage service
        newImage.setAltText(altText);
        newImage.setTitleText(titleText);
        newImage.setProduct(product);

        // Calculate display order if not provided
        if (displayOrder == null) {
            int maxOrder = product.getImages().stream()
                    .mapToInt(img -> img.getDisplayOrder() != null ? img.getDisplayOrder() : -1)
                    .max()
                    .orElse(-1);
            newImage.setDisplayOrder(maxOrder + 1);
            logger.debug("Calculated next displayOrder for new image: {}", newImage.getDisplayOrder());
        } else {
            newImage.setDisplayOrder(displayOrder);
        }

        // Save the image entity
        Image savedImage = imageRepository.save(newImage);
        // Add to the product's collection (important if not using CascadeType.PERSIST/MERGE from Product side)
        // product.getImages().add(savedImage); // Might not be needed if cascade/mappedBy is correct
        logger.info(">>> [ProductService] Image successfully saved (ID: {}) and associated with product ID: {}", savedImage.getId(), productId);
        return savedImage;
    }

    // addProductImage might be redundant if addImageToProduct does the full logic
    // @Transactional
    // public Image addProductImage(Long productId, Image image) { ... }

    @Caching(evict = {
            @CacheEvict(value = {"activeProductsPage", "activeProductsList", "allProductsList", "productsPage"}, allEntries = true),
            @CacheEvict(value = "productDetails", allEntries = true),
            @CacheEvict(value = "productBySlug", allEntries = true)
    })
    @Transactional
    public void deleteImage(Long imageId) {
        logger.warn(">>> [ProductService] Attempting to DELETE image ID: {}", imageId);
        try {
            Image image = imageRepository.findById(imageId)
                    .orElseThrow(() -> new EntityNotFoundException("Image not found: " + imageId));

            String fileUrl = image.getUrl();
            Long productId = image.getProduct() != null ? image.getProduct().getId() : null;

            // Explicitly remove from owning side if necessary (depends on mapping)
            // if (image.getProduct() != null) {
            //     image.getProduct().getImages().remove(image);
            // }

            // Delete the entity
            imageRepository.delete(image);
            logger.info("[ProductService] Image entity ID {} deleted from database (associated with product ID {}).", imageId, productId);

            // Delete the physical file
            if (StringUtils.hasText(fileUrl)) {
                fileStorageService.deleteFile(fileUrl); // Assumes deleteFile works with the stored URL/path
                logger.info("[ProductService] Physical file deleted for image ID {}: {}", imageId, fileUrl);
            } else {
                logger.warn("Cannot delete physical file for image ID {}: URL is missing.", imageId);
            }

        } catch (EntityNotFoundException e) {
            logger.error("!!! [ProductService] Image ID {} not found for deletion.", imageId);
            throw e;
        } catch (Exception e) {
            logger.error("!!! [ProductService] Error deleting image ID {}: {} !!!", imageId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete image " + imageId, e);
        }
        logger.info(">>> [ProductService] Image deletion process finished for ID: {}", imageId);
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


} // Konec třídy ProductService