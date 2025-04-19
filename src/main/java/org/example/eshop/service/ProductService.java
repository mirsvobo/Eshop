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
// Importy pro cachování zůstávají stejné
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

// Odstraněn import 'log' z CustomerService, používáme logger definovaný zde
// import static org.example.eshop.service.CustomerService.log;


@Service
public class ProductService implements PriceConstants {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);
    @Autowired private FileStorageService fileStorageService;

    // Konstanty pro generování slugu
    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]+");
    private static final Pattern MULTIPLE_DASHES = Pattern.compile("-{2,}");
    private static final Pattern EDGES_DASHES = Pattern.compile("^-|-$");


    @Autowired private ProductRepository productRepository;
    @Autowired private ImageRepository imageRepository;
    @Autowired private TaxRateRepository taxRateRepository;
    @Autowired private DiscountService discountService;
    // @Autowired private CacheManager cacheManager; // Pokud potřebuješ manuální invalidaci

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

    // Upraveno getProductById -> findByIdWithDetails pro konzistenci s admin controllerem
    // @Cacheable(value = "productById", key = "#id", unless = "#result == null or !#result.isPresent()") // Původní
    @Cacheable(value = "productDetails", key = "#id", unless = "#result == null or !#result.isPresent()") // Změna názvu cache na "productDetails"
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

    // Metoda pro získání produktu podle slug, upraven název cache
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


    // Zůstává stejné
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

    // Zůstává stejné
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

    // Zůstává stejné (výpočet ceny standardního produktu)
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

    // Zůstává stejné (vytvoření produktu)
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
            if (productRepository.existsBySlugIgnoreCase(product.getSlug())) {
                throw new IllegalArgumentException("Produkt se slugem '" + product.getSlug() + "' již existuje.");
            }

            if (product.getAvailableTaxRates() == null || product.getAvailableTaxRates().isEmpty()) {
                throw new IllegalArgumentException("Produkt musí mít přiřazenu alespoň jednu daňovou sazbu.");
            }

            if (product.isCustomisable()) {
                if (product.getConfigurator() == null) {
                    ProductConfigurator defaultConfig = new ProductConfigurator();
                    defaultConfig.setProduct(product);
                    // Nastavení výchozích hodnot, pokud nejsou z formuláře (příklad)
                    initializeDefaultConfiguratorValues(defaultConfig); // Použití pomocné metody
                    product.setConfigurator(defaultConfig);
                    logger.info("Creating default configurator for customisable product '{}'", product.getName());
                } else {
                    product.getConfigurator().setProduct(product);
                }
                product.setAvailableDesigns(Collections.emptySet());
                product.setAvailableGlazes(Collections.emptySet());
                product.setAvailableRoofColors(Collections.emptySet());
                logger.debug("Clearing standard attributes for customisable product '{}'", product.getName());
            } else {
                product.setConfigurator(null);
                product.setAvailableAddons(Collections.emptySet());
                logger.debug("Removing configurator and addons for standard product '{}'", product.getName());
            }

            ensureCollectionsInitialized(product);

            savedProduct = productRepository.save(product);
            logger.info(">>> [ProductService] Produkt '{}' úspěšně vytvořen s ID: {}. Opouštím createProduct.", savedProduct.getName(), savedProduct.getId());

        } catch (IllegalArgumentException | EntityNotFoundException e) {
            logger.warn("!!! [ProductService] Chyba validace nebo nenalezení entity v createProduct pro '{}': {} !!!", product.getName(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("!!! [ProductService] Neočekávaná chyba v createProduct pro '{}': {} !!!", product.getName(), e.getMessage(), e);
            throw new RuntimeException("Neočekávaná chyba při vytváření produktu: " + e.getMessage(), e);
        }
        return savedProduct;
    }


    // Zůstává stejné (aktualizace produktu)
    @Caching(evict = {
            @CacheEvict(value = {"activeProductsPage", "activeProductsList", "allProductsList", "productsPage"}, allEntries = true),
            // Invalidujeme podle ID a také podle SLUGU (protože se mohl změnit)
            @CacheEvict(value = "productDetails", key = "#id"),
            // Pro slug potřebujeme načíst produkt NEBO použít složitější SpEL
            // Pokud máme #productToUpdate, můžeme použít jeho slug
            @CacheEvict(value = "productBySlug", key = "T(String).valueOf(#productToUpdate.slug).toLowerCase()", condition = "#productToUpdate != null"),
            // Případně invalidovat všechny slugy (méně efektivní):
            // @CacheEvict(value = "productBySlug", allEntries = true)
    })
    @Transactional
    public Optional<Product> updateProduct(Long id, Product productData, Product productToUpdate) {
        logger.info(">>> [ProductService] Vstupuji do updateProduct (s přednačtenou entitou). ID: {}", id);
        try {
            // productToUpdate je entita načtená v controlleru, má nastavené ID a asociace (včetně TaxRates)
            // productData obsahuje data z formuláře

            // --- Validace slugu (pokud se mění) ---
            String newSlug = StringUtils.hasText(productData.getSlug()) ? generateSlug(productData.getSlug()) : generateSlug(productData.getName());
            if (!newSlug.equalsIgnoreCase(productToUpdate.getSlug())) {
                if (productRepository.existsBySlugIgnoreCaseAndIdNot(newSlug, id)) {
                    throw new IllegalArgumentException("Produkt se slugem '" + newSlug + "' již existuje.");
                }
                productToUpdate.setSlug(newSlug);
                logger.debug("Updating slug for product ID {} to '{}'", id, newSlug);
            }

            // --- Aktualizace základních polí z productData ---
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

            // --- Zpracování změny Customisable ---
            boolean wasCustom = productToUpdate.isCustomisable();
            boolean isCustom = productData.isCustomisable(); // Překlep opraven na customisable
            if (wasCustom != isCustom) {
                logger.info("Product ID {} customisable status changed from {} to {}", id, wasCustom, isCustom);
                productToUpdate.setCustomisable(isCustom); // Překlep opraven na customisable
                if (isCustom) { // Stal se custom
                    if (productData.getConfigurator() != null) {
                        if (productToUpdate.getConfigurator() != null) {
                            updateConfiguratorData(productToUpdate.getConfigurator(), productData.getConfigurator());
                            logger.debug("Updating existing configurator for product ID {}", id);
                        } else {
                            ProductConfigurator newConfig = productData.getConfigurator();
                            newConfig.setProduct(productToUpdate); // Nastavíme vazbu
                            productToUpdate.setConfigurator(newConfig);
                            logger.debug("Creating new configurator for product ID {}", id);
                        }
                    } else if (productToUpdate.getConfigurator() == null) {
                        ProductConfigurator defaultConfig = new ProductConfigurator();
                        defaultConfig.setProduct(productToUpdate); // Nastavíme vazbu
                        initializeDefaultConfiguratorValues(defaultConfig); // Inicializujeme hodnoty
                        productToUpdate.setConfigurator(defaultConfig);
                        logger.debug("Creating default configurator for newly customisable product ID {}", id);
                    }
                    productToUpdate.getAvailableDesigns().clear();
                    productToUpdate.getAvailableGlazes().clear();
                    productToUpdate.getAvailableRoofColors().clear();
                    logger.debug("Clearing standard attributes for product ID {}", id);
                } else { // Stal se standardním
                    productToUpdate.setConfigurator(null);
                    productToUpdate.getAvailableAddons().clear();
                    logger.debug("Removing configurator and addons for newly standard product ID {}", id);
                }
            } else if (isCustom && productData.getConfigurator() != null) {
                // Pokud zůstal custom a přišla data konfigurátoru, aktualizujeme je
                if (productToUpdate.getConfigurator() != null) {
                    updateConfiguratorData(productToUpdate.getConfigurator(), productData.getConfigurator());
                    logger.debug("Updating configurator for existing custom product ID {}", id);
                } else {
                    ProductConfigurator newConfig = productData.getConfigurator();
                    newConfig.setProduct(productToUpdate); // Nastavíme vazbu
                    productToUpdate.setConfigurator(newConfig);
                    logger.warn("Configurator was null for custom product ID {}. Creating new one based on form data.", id);
                }
            }

            // --- Aktualizace Asociací (včetně TaxRates) ---
            // Předpokládáme, že asociace byly nastaveny na `productToUpdate` v controlleru

            ensureCollectionsInitialized(productToUpdate);

            // --- Uložení ---
            Product savedProduct = productRepository.save(productToUpdate);
            logger.info("[ProductService] Produkt ID {} aktualizován.", id);
            return Optional.of(savedProduct);

        } catch (IllegalArgumentException | EntityNotFoundException e) {
            logger.warn("!!! [ProductService] Chyba validace nebo nenalezení entity v updateProduct pro ID {}: {} !!!", id, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("!!! [ProductService] Neočekávaná chyba v updateProduct pro ID {}: {} !!!", id, e.getMessage(), e);
            throw new RuntimeException("Neočekávaná chyba při aktualizaci produktu ID " + id + ": " + e.getMessage(), e);
        }
    }


    // *** Začátek upravené metody ***
    // Pomocná metoda pro update konfigurátoru - použije správné settery
    private void updateConfiguratorData(ProductConfigurator existingConfig, ProductConfigurator dataConfig) {
        if (existingConfig == null || dataConfig == null) {
            logger.warn("Skipping configurator update due to null existing or data object.");
            return;
        }
        // Můžeme přidat logování ID produktu pro lepší sledování
        Long productId = (existingConfig.getProduct() != null) ? existingConfig.getProduct().getId() : null;
        logger.debug("Updating ProductConfigurator data for product ID: {}", productId);

        // Limity rozměrů
        existingConfig.setMinLength(dataConfig.getMinLength());
        existingConfig.setMaxLength(dataConfig.getMaxLength());
        existingConfig.setMinWidth(dataConfig.getMinWidth());   // Použije setMinWidth
        existingConfig.setMaxWidth(dataConfig.getMaxWidth());   // Použije setMaxWidth
        existingConfig.setMinHeight(dataConfig.getMinHeight());
        existingConfig.setMaxHeight(dataConfig.getMaxHeight());
        logger.trace("Product ID {}: Updated dimension limits.", productId);

        // Kroky a výchozí hodnoty
        existingConfig.setStepLength(dataConfig.getStepLength());
        existingConfig.setStepWidth(dataConfig.getStepWidth());   // Použije setStepWidth
        existingConfig.setStepHeight(dataConfig.getStepHeight());
        existingConfig.setDefaultLength(dataConfig.getDefaultLength());
        existingConfig.setDefaultWidth(dataConfig.getDefaultWidth());   // Použije setDefaultWidth
        existingConfig.setDefaultHeight(dataConfig.getDefaultHeight());
        logger.trace("Product ID {}: Updated dimension steps and defaults.", productId);

        // Ceny za rozměry (za cm) - použije setPricePerCmWidth* (po přejmenování v entitě)
        existingConfig.setPricePerCmHeightCZK(dataConfig.getPricePerCmHeightCZK());
        existingConfig.setPricePerCmHeightEUR(dataConfig.getPricePerCmHeightEUR());
        existingConfig.setPricePerCmLengthCZK(dataConfig.getPricePerCmLengthCZK());
        existingConfig.setPricePerCmLengthEUR(dataConfig.getPricePerCmLengthEUR());
        existingConfig.setPricePerCmWidthCZK(dataConfig.getPricePerCmWidthCZK()); // PŘEDPOKLÁDÁ PŘEJMENOVÁNÍ v entitě
        existingConfig.setPricePerCmWidthEUR(dataConfig.getPricePerCmWidthEUR()); // PŘEDPOKLÁDÁ PŘEJMENOVÁNÍ v entitě
        logger.trace("Product ID {}: Updated dimension prices.", productId);

        // Ceny za volitelné prvky
        existingConfig.setDesignPriceCZK(dataConfig.getDesignPriceCZK());
        existingConfig.setDesignPriceEUR(dataConfig.getDesignPriceEUR());
        // Cena příčky stále používá 'Depth' - zkontroluj, zda je to záměr a zda pole existuje
        existingConfig.setDividerPricePerCmDepthCZK(dataConfig.getDividerPricePerCmDepthCZK());
        existingConfig.setDividerPricePerCmDepthEUR(dataConfig.getDividerPricePerCmDepthEUR());
        existingConfig.setGutterPriceCZK(dataConfig.getGutterPriceCZK());
        existingConfig.setGutterPriceEUR(dataConfig.getGutterPriceEUR());
        existingConfig.setShedPriceCZK(dataConfig.getShedPriceCZK());
        existingConfig.setShedPriceEUR(dataConfig.getShedPriceEUR());
        logger.trace("Product ID {}: Updated feature prices.", productId);
    }
    // *** Konec upravené metody ***


    // Zůstává stejné (inicializace kolekcí)
    private void ensureCollectionsInitialized(Product product) {
        if (product == null) return;
        if (product.getAvailableDesigns() == null) product.setAvailableDesigns(new HashSet<>());
        if (product.getAvailableGlazes() == null) product.setAvailableGlazes(new HashSet<>());
        if (product.getAvailableRoofColors() == null) product.setAvailableRoofColors(new HashSet<>());
        if (product.getAvailableAddons() == null) product.setAvailableAddons(new HashSet<>());
        if (product.getAvailableTaxRates() == null) product.setAvailableTaxRates(new HashSet<>());
        if (product.getImages() == null) product.setImages(new HashSet<>());
        if (product.getDiscounts() == null) product.setDiscounts(new HashSet<>());
        // Inicializace konfigurátoru se teď děje v controlleru nebo create/update metodách service
        // if (product.getConfigurator() == null && product.isCustomisable()) {
        //     product.setConfigurator(new ProductConfigurator()); // Neinicializujeme zde, aby se nepřepsala načtená data
        // }
    }

    // Zůstává stejné (soft delete)
    @Caching(evict = {
            @CacheEvict(value = {"activeProductsPage", "activeProductsList", "allProductsList", "productsPage"}, allEntries = true),
            @CacheEvict(value = "productDetails", key = "#id"),
            // Manuální invalidace podle slugu (složitější, pokud neznáme slug)
            // @CacheEvict(value = "productBySlug", key = "#{#root.target.getProductSlug(#id)}", condition="#id != null")
            // Jednodušší, ale méně efektivní:
            @CacheEvict(value = "productBySlug", allEntries = true)
    })
    @Transactional
    public void deleteProduct(Long id) {
        logger.info(">>> [ProductService] Vstupuji do deleteProduct (soft delete). ID: {}", id);
        try {
            Product product = productRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Product with id " + id + " not found for deletion."));
            if (!product.isActive()) {
                logger.warn("[ProductService] Produkt ID {} ('{}') je již neaktivní.", id, product.getName());
                return;
            }
            product.setActive(false);
            productRepository.save(product);
            logger.info("[ProductService] Produkt ID {} ('{}') označen jako neaktivní (soft delete).", id, product.getName());
        } catch (EntityNotFoundException e) {
            logger.error("!!! [ProductService] Produkt ID {} nenalezen pro smazání (soft delete).", id);
            throw e;
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v deleteProduct (soft delete) pro ID {}: {} !!!", id, e.getMessage(), e);
            throw new RuntimeException("Error deactivating product ID " + id, e); // Zabalíme do RuntimeException
        }
        logger.info(">>> [ProductService] Opouštím deleteProduct (soft delete). ID: {}", id);
    }


    // --- Výpočet ceny produktu "Na míru" - PŘEJMENOVÁNO ---
    // *** Začátek upravené metody ***
    @Transactional(readOnly = true)
    public BigDecimal calculateDynamicProductPrice(Product product, Map<String, BigDecimal> dimensions,
                                                   String customDesign, boolean hasDivider, // Tyto parametry pro doplňky mohou být zastaralé, pokud řešíme doplňky jinak
                                                   boolean hasGutter, boolean hasGardenShed, // Tyto parametry pro doplňky mohou být zastaralé
                                                   String currency) {
        logger.info(">>> [ProductService] Vstupuji do calculateDynamicProductPrice. Product ID: {}, Currency: {}", product != null ? product.getId() : "null", currency);
        BigDecimal finalPrice = BigDecimal.ZERO;
        try {
            if (product == null) throw new IllegalArgumentException("Product cannot be null.");
            if (!product.isActive()) throw new IllegalArgumentException("Cannot calculate price for inactive product ID: " + product.getId());
            if (!product.isCustomisable() || product.getConfigurator() == null) throw new IllegalArgumentException("Product ID " + product.getId() + " is not customisable or missing configurator.");
            if (dimensions == null) throw new IllegalArgumentException("Dimensions map cannot be null.");

            ProductConfigurator config = product.getConfigurator();
            BigDecimal length = dimensions.get("length");
            BigDecimal width = dimensions.get("width"); // Očekáváme 'width'
            BigDecimal height = dimensions.get("height");

            if (length == null || width == null || height == null) {
                throw new IllegalArgumentException("Missing one or more dimensions (length, width, height) for custom product calculation.");
            }

            // Validace rozměrů - opraven label pro šířku
            validateDimension("Length (Délka)", length, config.getMinLength(), config.getMaxLength());
            validateDimension("Width (Šířka/Hloubka)", width, config.getMinWidth(), config.getMaxWidth()); // Opraven label
            validateDimension("Height (Výška)", height, config.getMinHeight(), config.getMaxHeight());

            // Načtení cenových konstant - opraveno na pricePerCmWidth*
            BigDecimal pricePerCmH = getPriceForCurrency(config.getPricePerCmHeightCZK(), config.getPricePerCmHeightEUR(), currency, "Height Price/cm");
            BigDecimal pricePerCmL = getPriceForCurrency(config.getPricePerCmLengthCZK(), config.getPricePerCmLengthEUR(), currency, "Length Price/cm");
            BigDecimal pricePerCmW = getPriceForCurrency(config.getPricePerCmWidthCZK(), config.getPricePerCmWidthEUR(), currency, "Width Price/cm"); // Opraveno

            // Výpočet ceny z rozměrů - opraveno na pricePerCmW
            BigDecimal priceFromDimensions = height.multiply(pricePerCmH)
                    .add(length.multiply(pricePerCmL))
                    .add(width.multiply(pricePerCmW)); // Opraveno

            // --- Zpracování cen za doplňky (tato část může být zastaralá, pokud doplňky řešíš jinak) ---
            BigDecimal designPrice = getPriceForCurrency(config.getDesignPriceCZK(), config.getDesignPriceEUR(), currency, "Design Price");
            BigDecimal dividerPrice = BigDecimal.ZERO;
            if (hasDivider) {
                BigDecimal dividerRate = getPriceForCurrency(config.getDividerPricePerCmDepthCZK(), config.getDividerPricePerCmDepthEUR(), currency, "Divider Price/cm");
                dividerPrice = width.multiply(dividerRate); // Cena příčky podle šířky/hloubky
            }
            BigDecimal gutterPrice = BigDecimal.ZERO;
            if (hasGutter) {
                gutterPrice = getPriceForCurrency(config.getGutterPriceCZK(), config.getGutterPriceEUR(), currency, "Gutter Price");
            }
            BigDecimal shedPrice = BigDecimal.ZERO;
            if (hasGardenShed) {
                shedPrice = getPriceForCurrency(config.getShedPriceCZK(), config.getShedPriceEUR(), currency, "Shed Price");
            }
            // --- Konec zpracování cen za doplňky ---

            logger.debug("[ProductService] Price from dimensions ({}): {}", currency, priceFromDimensions.setScale(PRICE_SCALE, ROUNDING_MODE));
            // Logování cen doplňků (pokud se používají)
            logger.debug("[ProductService] Price for design '{}' ({}): {}", customDesign, currency, designPrice.setScale(PRICE_SCALE, ROUNDING_MODE));
            logger.debug("[ProductService] Price for divider ({}): {}", currency, dividerPrice.setScale(PRICE_SCALE, ROUNDING_MODE));
            logger.debug("[ProductService] Price for gutter ({}, fixed): {}", currency, gutterPrice.setScale(PRICE_SCALE, ROUNDING_MODE));
            logger.debug("[ProductService] Price for garden shed ({}, fixed): {}", currency, shedPrice.setScale(PRICE_SCALE, ROUNDING_MODE));

            // Celková cena = rozměry + (ceny doplňků, pokud se používají)
            finalPrice = priceFromDimensions.add(designPrice).add(dividerPrice).add(gutterPrice).add(shedPrice);
            finalPrice = finalPrice.setScale(PRICE_SCALE, ROUNDING_MODE);
            finalPrice = finalPrice.max(BigDecimal.ZERO);

        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v calculateDynamicProductPrice pro Produkt ID {} ({}): {} !!!",
                    product != null ? product.getId() : "null", currency, e.getMessage(), e);
            throw e; // Rethrow
        }
        logger.info(">>> [ProductService] Opouštím calculateDynamicProductPrice. Product ID: {}, Currency: {}. Vypočtená cena: {}",
                product != null ? product.getId() : "null", currency, finalPrice);
        return finalPrice;
    }
    // *** Konec upravené metody ***


    // Zůstává stejné
    private BigDecimal getPriceForCurrency(BigDecimal priceCZK, BigDecimal priceEUR, String currency, String priceName) {
        BigDecimal price = EURO_CURRENCY.equals(currency) ? priceEUR : priceCZK;
        if (price == null) {
            if (!priceName.contains("/cm")) {
                logger.warn("Optional price '{}' missing for currency {}. Using 0.", priceName, currency);
                return BigDecimal.ZERO;
            } else {
                throw new IllegalStateException(String.format("Required price configuration '%s' missing for currency %s", priceName, currency));
            }
        }
        return price;
    }


    // *** Začátek upravené metody ***
    // Pomocná metoda pro validaci rozměru - upraven label v chybě
    private void validateDimension(String name, BigDecimal value, BigDecimal min, BigDecimal max) {
        if (min == null || max == null) throw new IllegalStateException("Config error: Missing limits for dimension " + name);
        if (value == null) throw new IllegalArgumentException("Dimension " + name + " cannot be null.");
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            // Použije název dimenze (např. "Width (Šířka/Hloubka)") v chybové zprávě
            throw new IllegalArgumentException(String.format("%s (%s cm) is outside allowed range [%s, %s] cm.",
                    name, // Obsahuje název dimenze
                    value.stripTrailingZeros().toPlainString(),
                    min.stripTrailingZeros().toPlainString(), max.stripTrailingZeros().toPlainString()));
        }
    }
    // *** Konec upravené metody ***


    // Zůstává stejné (výchozí hodnoty konfigurátoru)
    private void initializeDefaultConfiguratorValues(ProductConfigurator configurator) {
        if (configurator == null) return; // Pojistka
        // Příklad hodnot, upravte podle reality
        configurator.setMinLength(Optional.ofNullable(configurator.getMinLength()).orElse(new BigDecimal("100.00")));
        configurator.setMaxLength(Optional.ofNullable(configurator.getMaxLength()).orElse(new BigDecimal("500.00")));
        configurator.setMinWidth(Optional.ofNullable(configurator.getMinWidth()).orElse(new BigDecimal("50.00")));
        configurator.setMaxWidth(Optional.ofNullable(configurator.getMaxWidth()).orElse(new BigDecimal("200.00")));
        configurator.setMinHeight(Optional.ofNullable(configurator.getMinHeight()).orElse(new BigDecimal("150.00")));
        configurator.setMaxHeight(Optional.ofNullable(configurator.getMaxHeight()).orElse(new BigDecimal("300.00")));

        // Ceny - použijí se přejmenovaná pole *Width*
        configurator.setPricePerCmHeightCZK(Optional.ofNullable(configurator.getPricePerCmHeightCZK()).orElse(new BigDecimal("14.00")));
        configurator.setPricePerCmLengthCZK(Optional.ofNullable(configurator.getPricePerCmLengthCZK()).orElse(new BigDecimal("99.00")));
        configurator.setPricePerCmWidthCZK(Optional.ofNullable(configurator.getPricePerCmWidthCZK()).orElse(new BigDecimal("25.00"))); // Opraveno zde
        configurator.setDividerPricePerCmDepthCZK(Optional.ofNullable(configurator.getDividerPricePerCmDepthCZK()).orElse(new BigDecimal("13.00"))); // Pro příčku zůstává Depth?
        configurator.setDesignPriceCZK(Optional.ofNullable(configurator.getDesignPriceCZK()).orElse(BigDecimal.ZERO));
        configurator.setGutterPriceCZK(Optional.ofNullable(configurator.getGutterPriceCZK()).orElse(new BigDecimal("1000.00")));
        configurator.setShedPriceCZK(Optional.ofNullable(configurator.getShedPriceCZK()).orElse(new BigDecimal("5000.00")));

        configurator.setPricePerCmHeightEUR(Optional.ofNullable(configurator.getPricePerCmHeightEUR()).orElse(new BigDecimal("0.56")));
        configurator.setPricePerCmLengthEUR(Optional.ofNullable(configurator.getPricePerCmLengthEUR()).orElse(new BigDecimal("3.96")));
        configurator.setPricePerCmWidthEUR(Optional.ofNullable(configurator.getPricePerCmWidthEUR()).orElse(new BigDecimal("1.00"))); // Opraveno zde
        configurator.setDividerPricePerCmDepthEUR(Optional.ofNullable(configurator.getDividerPricePerCmDepthEUR()).orElse(new BigDecimal("0.52"))); // Pro příčku zůstává Depth?
        configurator.setDesignPriceEUR(Optional.ofNullable(configurator.getDesignPriceEUR()).orElse(BigDecimal.ZERO));
        configurator.setGutterPriceEUR(Optional.ofNullable(configurator.getGutterPriceEUR()).orElse(new BigDecimal("40.00")));
        configurator.setShedPriceEUR(Optional.ofNullable(configurator.getShedPriceEUR()).orElse(new BigDecimal("200.00")));

        // Kroky a výchozí hodnoty
        configurator.setStepLength(Optional.ofNullable(configurator.getStepLength()).orElse(BigDecimal.TEN));
        configurator.setStepWidth(Optional.ofNullable(configurator.getStepWidth()).orElse(BigDecimal.TEN));
        configurator.setStepHeight(Optional.ofNullable(configurator.getStepHeight()).orElse(BigDecimal.valueOf(5)));
        // Pro výchozí hodnoty je lepší nechat null, pokud nejsou explicitně zadány,
        // aby se použil minimální rozměr, pokud není default nastaven.
        // configurator.setDefaultLength(Optional.ofNullable(configurator.getDefaultLength()).orElse(configurator.getMinLength()));
        // configurator.setDefaultWidth(Optional.ofNullable(configurator.getDefaultWidth()).orElse(configurator.getMinWidth()));
        // configurator.setDefaultHeight(Optional.ofNullable(configurator.getDefaultHeight()).orElse(configurator.getMinHeight()));


        logger.info("Initialized default values for configurator linked to product ID {}", configurator.getProduct() != null ? configurator.getProduct().getId() : "(unlinked)");
    }

    // Zůstává stejné (generování slugu)
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

    // Zůstává stejné (přidání obrázku)
    @Caching(evict = {
            @CacheEvict(value = {"activeProductsPage", "activeProductsList", "allProductsList", "productsPage"}, allEntries = true),
            @CacheEvict(value = "productDetails", key = "#productId"),
            @CacheEvict(value = "productBySlug", allEntries = true) // Jednodušší invalidace pro slug
    })
    @Transactional
    public Image addImageToProduct(Long productId, MultipartFile file, String altText, String titleText, Integer displayOrder) throws IOException {
        logger.info(">>> [ProductService] Attempting to add image to product ID: {}", productId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot add an empty image file.");
        }

        String fileUrl = fileStorageService.storeFile(file, "products");

        Image newImage = new Image();
        newImage.setUrl(fileUrl);
        newImage.setAltText(altText);
        newImage.setTitleText(titleText);
        // Výpočet displayOrder přesuneme do addProductImage(Long, Image)

        Image savedImage = addProductImage(productId, newImage);
        logger.info(">>> [ProductService] Image successfully added and associated with product ID: {}", productId);
        return savedImage;
    }

    // Zůstává stejné (pomocná metoda pro přidání obrázku)
    @Transactional
    public Image addProductImage(Long productId, Image image) {
        logger.info(">>> [ProductService] Vstupuji do addProductImage (Image entity). Product ID: {}", productId);
        Image savedImage = null;
        try {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + productId));
            if (image == null || !StringUtils.hasText(image.getUrl())) {
                throw new IllegalArgumentException("Image or URL missing.");
            }
            if (image.getDisplayOrder() == null) {
                int maxOrder = product.getImages().stream()
                        .mapToInt(img -> img.getDisplayOrder() != null ? img.getDisplayOrder() : -1)
                        .max()
                        .orElse(-1);
                image.setDisplayOrder(maxOrder + 1);
                logger.debug("Calculated next displayOrder: {}", image.getDisplayOrder());
            }

            image.setProduct(product);
            savedImage = imageRepository.save(image);

            // Explicitní přidání do kolekce produktu zde není nutné kvůli CascadeType.ALL a orphanRemoval=true

            logger.info("[ProductService] Obrázek (Image entity) uložen (ID: {}) a přiřazen k produktu ID: {}", savedImage.getId(), productId);
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v addProductImage (Image entity) pro Produkt ID {}: {} !!!", productId, e.getMessage(), e);
            throw e; // Propagujeme výjimku
        }
        logger.info(">>> [ProductService] Opouštím addProductImage (Image entity). Product ID: {}", productId);
        return savedImage;
    }

    // Zůstává stejné (smazání obrázku)
    @Caching(evict = {
            @CacheEvict(value = {"activeProductsPage", "activeProductsList", "allProductsList", "productsPage"}, allEntries = true),
            @CacheEvict(value = "productDetails", allEntries = true), // Pro jistotu invalidujeme všechny detaily
            @CacheEvict(value = "productBySlug", allEntries = true)
    })
    @Transactional
    public void deleteImage(Long imageId) {
        logger.warn(">>> [ProductService] Attempting to DELETE image ID: {}", imageId);
        try {
            Image image = imageRepository.findById(imageId)
                    .orElseThrow(() -> new EntityNotFoundException("Image not found: " + imageId));

            String fileUrl = image.getUrl();

            // Smazání entity z DB
            // Pokud je Product.images nastaveno s orphanRemoval=true, stačí odebrat z kolekce
            // Pokud ne, musíme smazat přímo
            if (image.getProduct() != null) {
                // Je bezpečnější nejdřív odebrat z kolekce (pokud je spravovaná)
                // a pak smazat, nebo nechat na orphanRemoval
                logger.debug("Deleting image {} associated with product ID {}", imageId, image.getProduct().getId());
            }
            imageRepository.delete(image);
            logger.info("[ProductService] Image entity ID {} deleted from database.", imageId);

            if (StringUtils.hasText(fileUrl)) {
                fileStorageService.deleteFile(fileUrl);
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


    // Zůstává stejné (pomocná metoda pro načtení TaxRates)
    private Set<TaxRate> loadAndAssignTaxRates(Set<Long> taxRateIds) {
        if (CollectionUtils.isEmpty(taxRateIds)) {
            throw new IllegalArgumentException("Produkt musí mít přiřazenu alespoň jednu daňovou sazbu.");
        }
        List<TaxRate> foundRates = taxRateRepository.findAllById(taxRateIds);
        if (foundRates.size() != taxRateIds.size()) {
            Set<Long> foundIds = foundRates.stream().map(TaxRate::getId).collect(Collectors.toSet());
            Set<Long> missingIds = new HashSet<>(taxRateIds); // Vytvoříme kopii pro úpravu
            missingIds.removeAll(foundIds);
            logger.warn("Some tax rates were not found when assigning to product: IDs {}", missingIds);
            throw new EntityNotFoundException("One or more tax rates not found: " + missingIds);
        }
        return new HashSet<>(foundRates);
    }

} // Konec třídy ProductService