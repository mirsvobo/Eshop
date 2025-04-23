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
import org.springframework.dao.DataIntegrityViolationException;
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
            // Invalidace cache při aktualizaci
            @CacheEvict(value = {"activeProductsPage", "activeProductsList", "allProductsList", "productsPage"}, allEntries = true),
            @CacheEvict(value = "productDetails", key = "#id"),
            @CacheEvict(value = "productBySlug", allEntries = true) // Jednodušší invalidovat všechny slugy
    })
    @Transactional
    public Optional<Product> updateProduct(Long id, Product productData, Product existingProduct) {
        // Použijeme existingProduct jako hlavní entitu, kterou budeme měnit a ukládat
        // a productData jako zdroj nových hodnot z formuláře.
        logger.info(">>> [ProductService] Vstupuji do updateProduct. ID: {}, Aktualizuji entitu: {}", id, existingProduct);

        Product productToUpdate = existingProduct; // Alias pro lepší čitelnost

        try {
            // --- Slug Update & Validation ---
            String newSlug = StringUtils.hasText(productData.getSlug()) ? generateSlug(productData.getSlug()) : generateSlug(productData.getName());
            if (!newSlug.equalsIgnoreCase(productToUpdate.getSlug())) {
                productRepository.findBySlugIgnoreCaseAndIdNot(newSlug, id).ifPresent(existingWithSlug -> {
                    // Slug již existuje pro jiný produkt
                    throw new IllegalArgumentException("Produkt se slugem '" + newSlug + "' již existuje.");
                });
                productToUpdate.setSlug(newSlug);
                logger.debug("Aktualizuji slug pro produkt ID {} na '{}'", id, newSlug);
            }

            // --- Update Basic Fields ---
            // Kopírujeme hodnoty z productData (formulář) do productToUpdate (databáze)
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
            // Tyto rozměry jsou pro Standard produkt, u Custom se neukládají zde
            productToUpdate.setHeight(productData.getHeight());
            productToUpdate.setLength(productData.getLength());
            productToUpdate.setWidth(productData.getWidth());
            productToUpdate.setRoofOverstep(productData.getRoofOverstep()); // Předpokládám, že toto je také jen pro standard
            productToUpdate.setActive(productData.isActive());
            productToUpdate.setMetaTitle(productData.getMetaTitle());
            productToUpdate.setMetaDescription(productData.getMetaDescription());

            // --- Handle Customisable Flag Change & Configurator Update ---
            boolean wasCustom = productToUpdate.isCustomisable();
            boolean isCustom = productData.isCustomisable(); // Bereme hodnotu z formuláře

            if (wasCustom != isCustom) {
                logger.info("Produkt ID {} mění stav 'customisable' z {} na {}", id, wasCustom, isCustom);
                productToUpdate.setCustomisable(isCustom);
            }

            // Aktualizace nebo vytvoření/smazání konfigurátoru podle isCustom
            if (isCustom) { // Produkt je nebo se stává Custom
                if (productToUpdate.getConfigurator() == null) {
                    // Pokud konfigurátor neexistoval, vytvoříme nový z dat formuláře nebo defaultní
                    ProductConfigurator newConfig = (productData.getConfigurator() != null) ? productData.getConfigurator() : new ProductConfigurator();
                    newConfig.setProduct(productToUpdate); // Propojení
                    initializeDefaultConfiguratorValues(newConfig); // Nastavení defaultů, pokud chybí
                    productToUpdate.setConfigurator(newConfig);
                    logger.debug("Vytvářím/Inicializuji konfigurátor pro nově/stále custom produkt ID {}", id);
                } else if (productData.getConfigurator() != null) {
                    // Pokud konfigurátor existoval a přišla data z formuláře, aktualizujeme ho
                    updateConfiguratorData(productToUpdate.getConfigurator(), productData.getConfigurator());
                    logger.debug("Aktualizuji existující konfigurátor pro custom produkt ID {}", id);
                }
                // Poznámka: Pokud productData.getConfigurator() je null, ale produkt je custom,
                // stávající konfigurátor v productToUpdate se nemění (není přepsán null hodnotou).
            } else { // Produkt je nebo se stává Standard
                productToUpdate.setConfigurator(null); // Standardní produkt nemá konfigurátor
            }

            // --- OPRAVA: Explicitní aktualizace kolekcí asociací ---
            // Předpokládáme, že handleAssociations v controlleru správně nastavila kolekce na productData

            // Tax Rates (musí být vždy)
            if (productData.getAvailableTaxRates() != null) {
                if (CollectionUtils.isEmpty(productData.getAvailableTaxRates())) {
                    // Toto by mělo být zachyceno už v handleAssociations, ale pro jistotu
                    throw new IllegalArgumentException("Produkt ID " + id + " musí mít přiřazenu alespoň jednu daňovou sazbu.");
                }
                // Vždy aktualizujeme, protože handleAssociations je nastaví podle formuláře
                productToUpdate.setAvailableTaxRates(new HashSet<>(productData.getAvailableTaxRates()));
            } else {
                // Pokud by productData nemělo nastavené TaxRates (což by byla chyba v controlleru)
                if(CollectionUtils.isEmpty(productToUpdate.getAvailableTaxRates())) {
                    // Pokud ani existující produkt nemá sazby (což by nemělo nastat), hodíme chybu
                    throw new IllegalStateException("Chyba: Produkt ID " + id + " nemá přiřazené žádné daňové sazby.");
                }
                // Jinak ponecháme stávající sazby z productToUpdate
                logger.warn("ProductData nemělo nastavené TaxRates pro produkt ID {}. Ponechávám stávající.", id);
            }


            // Ostatní asociace podle typu produktu
            if (isCustom) {
                // Nastavení Addons pro Custom produkt
                if (productData.getAvailableAddons() != null) {
                    productToUpdate.setAvailableAddons(new HashSet<>(productData.getAvailableAddons()));
                    logger.debug("Nastavuji Addons pro custom produkt ID {}: {}", id, productToUpdate.getAvailableAddons().stream().map(Addon::getId).collect(Collectors.toSet()));
                } else {
                    productToUpdate.setAvailableAddons(new HashSet<>()); // Pokud nejsou v datech, nastaví se prázdný set
                    logger.debug("ProductData nemělo nastavené Addons pro custom produkt ID {}. Nastavuji prázdný Set.", id);
                }
                // Vyčištění standardních atributů pro Custom produkt
                if (productToUpdate.getAvailableDesigns() == null) productToUpdate.setAvailableDesigns(new HashSet<>()); else productToUpdate.getAvailableDesigns().clear();
                if (productToUpdate.getAvailableGlazes() == null) productToUpdate.setAvailableGlazes(new HashSet<>()); else productToUpdate.getAvailableGlazes().clear();
                if (productToUpdate.getAvailableRoofColors() == null) productToUpdate.setAvailableRoofColors(new HashSet<>()); else productToUpdate.getAvailableRoofColors().clear();
                logger.debug("Vyčistil jsem standardní atributy (Design, Glaze, RoofColor) pro custom produkt ID {}", id);

            } else { // Produkt je Standard
                // Vyčištění Addons pro Standardní produkt
                if (productToUpdate.getAvailableAddons() == null) productToUpdate.setAvailableAddons(new HashSet<>()); else productToUpdate.getAvailableAddons().clear();
                logger.debug("Vyčistil jsem Addons pro standard produkt ID {}", id);

                // Nastavení standardních atributů (Glazes, Designs, RoofColors)
                if (productData.getAvailableGlazes() != null) {
                    productToUpdate.setAvailableGlazes(new HashSet<>(productData.getAvailableGlazes()));
                    logger.debug("Nastavuji Glazes pro standard produkt ID {}: {}", id, productToUpdate.getAvailableGlazes().stream().map(Glaze::getId).collect(Collectors.toSet()));
                } else {
                    productToUpdate.setAvailableGlazes(new HashSet<>());
                    logger.debug("ProductData nemělo nastavené Glazes pro standard produkt ID {}. Nastavuji prázdný Set.", id);
                }

                if (productData.getAvailableDesigns() != null) {
                    productToUpdate.setAvailableDesigns(new HashSet<>(productData.getAvailableDesigns()));
                    logger.debug("Nastavuji Designs pro standard produkt ID {}: {}", id, productToUpdate.getAvailableDesigns().stream().map(Design::getId).collect(Collectors.toSet()));
                } else {
                    productToUpdate.setAvailableDesigns(new HashSet<>());
                    logger.debug("ProductData nemělo nastavené Designs pro standard produkt ID {}. Nastavuji prázdný Set.", id);
                }

                if (productData.getAvailableRoofColors() != null) {
                    productToUpdate.setAvailableRoofColors(new HashSet<>(productData.getAvailableRoofColors()));
                    logger.debug("Nastavuji RoofColors pro standard produkt ID {}: {}", id, productToUpdate.getAvailableRoofColors().stream().map(RoofColor::getId).collect(Collectors.toSet()));
                } else {
                    productToUpdate.setAvailableRoofColors(new HashSet<>());
                    logger.debug("ProductData nemělo nastavené RoofColors pro standard produkt ID {}. Nastavuji prázdný Set.", id);
                }
            }
            // Kolekce jako Images, Discounts se zde nemění, předpokládáme, že jsou spravovány jinde (AJAX, speciální formuláře)
            // nebo se automaticky zachovají z existingProduct (productToUpdate).

            // --- Uložení ---
            // Díky @Transactional by měl Hibernate detekovat změny na productToUpdate a uložit je.
            // Explicitní save může být pro jistotu.
            Product savedProduct = productRepository.save(productToUpdate);
            logger.info("[ProductService] Produkt ID {} úspěšně aktualizován v DB.", id);
            return Optional.of(savedProduct);

        } catch (IllegalArgumentException | EntityNotFoundException e) {
            // Logování probíhá v controlleru nebo výše
            logger.warn("!!! [ProductService] Chyba validace nebo nenalezení entity v updateProduct pro ID {}: {} !!!", id, e.getMessage());
            throw e; // Propagujeme dál
        } catch (DataIntegrityViolationException e) {
            // Specifické logování pro chyby integrity (např. NOT NULL constrainty)
            logger.error("!!! [ProductService] Chyba integrity dat v updateProduct pro ID {}: {} !!!", id, e.getMessage(), e);
            throw e; // Propagujeme dál
        }
        catch (Exception e) {
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
} // Konec třídy ProductService