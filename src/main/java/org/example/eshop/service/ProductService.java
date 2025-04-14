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
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*; // Import pro Set, Map atd.
import java.util.regex.Pattern;

import static org.example.eshop.service.CustomerService.log;

@Service
public class ProductService implements PriceConstants {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);
    @Autowired private FileStorageService fileStorageService; // <-- Přidat novou závislost

    // Konstanty pro generování slugu
    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]+");
    private static final Pattern MULTIPLE_DASHES = Pattern.compile("-{2,}");
    private static final Pattern EDGES_DASHES = Pattern.compile("^-|-$");


    @Autowired private ProductRepository productRepository;
    @Autowired private ImageRepository imageRepository;
    @Autowired private TaxRateRepository taxRateRepository;
    @Autowired private DiscountService discountService;

    @Transactional(readOnly = true)
    public Page<Product> getActiveProducts(Pageable pageable) {
        logger.info(">>> [ProductService] Vstupuji do getActiveProducts(Pageable: {}) using standard repo method <<<", pageable);
        Page<Product> result = Page.empty(pageable);
        try {
            // Volání STANDARDNÍ repository metody (s EntityGraph)
            result = productRepository.findByActiveTrue(pageable);
            logger.info("[ProductService] getActiveProducts(Pageable): Načtena stránka s {} aktivními produkty (with details).", result.getTotalElements());
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v getActiveProducts(Pageable): {} !!!", e.getMessage(), e);
        }
        logger.info(">>> [ProductService] Opouštím getActiveProducts(Pageable) <<<");
        return result;
    }

    @Transactional(readOnly = true)
    public List<Product> getAllActiveProducts() {
        logger.info(">>> [ProductService] Vstupuji do getAllActiveProducts (List) using standard repo method <<<");
        List<Product> result = Collections.emptyList();
        try {
            // Volání STANDARDNÍ repository metody (s EntityGraph)
            result = productRepository.findAllByActiveTrue();
            logger.info("[ProductService] getAllActiveProducts (List): Načteno {} aktivních produktů (with details).", (result != null ? result.size() : "NULL"));
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v getAllActiveProducts (List): {} !!!", e.getMessage(), e);
        }
        logger.info(">>> [ProductService] Opouštím getAllActiveProducts (List) <<<");
        return result;
    }


    @Transactional(readOnly = true)
    public Optional<Product> getActiveProductBySlug(String slug) {
        logger.info(">>> [ProductService] Vstupuji do getActiveProductBySlug using new repo method. Slug: {}", slug);
        Optional<Product> result = Optional.empty();
        try {
            // Volání NOVÉ repository metody
            result = productRepository.findActiveBySlugWithDetails(slug);
            logger.info("[ProductService] getActiveProductBySlug: Aktivní produkt se slugem '{}' {}.", slug, result.isPresent() ? "nalezen (with details)" : "nenalezen");
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v getActiveProductBySlug (Slug: {}): {} !!!", slug, e.getMessage(), e);
        }
        logger.info(">>> [ProductService] Opouštím getActiveProductBySlug (Slug: {}) <<<", slug);
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<Product> getProductById(Long id){
        logger.info(">>> [ProductService] Vstupuji do getProductById (with details) using new repo method. ID: {}", id);
        Optional<Product> result = Optional.empty();
        try {
            // Volání NOVÉ repository metody
            result = productRepository.findByIdWithDetails(id);
            logger.info("[ProductService] getProductById: Produkt ID {} {}.", id, result.isPresent() ? "nalezen (with details)" : "nenalezen");
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v getProductById (ID: {}): {} !!!", id, e.getMessage(), e);
        }
        logger.info(">>> [ProductService] Opouštím getProductById (ID: {}) <<<", id);
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
            // Vracíme prázdnou stránku, aby volající strana mohla reagovat
        }
        logger.info(">>> [ProductService] Opouštím getAllProducts(Pageable) <<<");
        return result;
    }
    /**
     * Vrátí seznam všech produktů (např. pro výběr ve formulářích).
     * @return Seznam všech produktů.
     */
    @Transactional(readOnly = true)
    public List<Product> getAllProductsList() {
        logger.info(">>> [ProductService] Vstupuji do getAllProductsList <<<");
        List<Product> result = Collections.emptyList();
        try {
            // Voláme findAll() bez Pageable, vrátí List
            result = productRepository.findAll(Sort.by("name")); // Řadíme podle jména pro konzistenci
            logger.info("[ProductService] getAllProductsList: Načteno {} produktů.", (result != null ? result.size() : "NULL"));
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v getAllProductsList: {} !!!", e.getMessage(), e);
        }
        logger.info(">>> [ProductService] Opouštím getAllProductsList <<<");
        return result;
    }
    /**
     * Vypočítá finální prodejní cenu produktu s ohledem na aktivní slevy.
     *
     * @param product Produkt, pro který se počítá cena.
     * @param currency Měna ("CZK" nebo "EUR").
     * @return Mapa obsahující "originalPrice" (původní cena bez slevy),
     * "discountedPrice" (cena po slevě) a "discountApplied" (objekt aplikované slevy, pokud existuje).
     * Vrací ceny bez DPH.
     */
    @Transactional(readOnly = true) // Potřeba pro načtení slev
    public Map<String, Object> calculateFinalProductPrice(Product product, String currency) {
        Map<String, Object> priceInfo = new HashMap<>();
        if (product == null || product.isCustomisable()) {
            // Pro custom produkt vracíme null, cena se počítá dynamicky
            priceInfo.put("originalPrice", null);
            priceInfo.put("discountedPrice", null);
            priceInfo.put("discountApplied", null);
            return priceInfo;
        }

        BigDecimal originalPrice = EURO_CURRENCY.equals(currency) ? product.getBasePriceEUR() : product.getBasePriceCZK();
        originalPrice = (originalPrice != null) ? originalPrice.setScale(PRICE_SCALE, ROUNDING_MODE) : null; // Zajistit škálu

        BigDecimal finalPrice = originalPrice;
        Discount appliedDiscount = null;

        if (originalPrice != null && originalPrice.compareTo(BigDecimal.ZERO) > 0) {
            // 1. Zkusíme procentuální slevu
            BigDecimal priceAfterPercentage = discountService.applyBestPercentageDiscount(originalPrice, product);

            // 2. Zkusíme fixní slevu (TODO: Rozhodnout, zda aplikovat na původní nebo již % sníženou cenu)
            // Prozatím aplikujeme na PŮVODNÍ cenu a vybereme VĚTŠÍ slevu
            BigDecimal priceAfterFixed = discountService.applyBestFixedDiscount(originalPrice, product, currency);

            // Porovnání a výběr nejlepší ceny (nejnižší)
            if (priceAfterPercentage != null && priceAfterPercentage.compareTo(originalPrice) < 0 &&
                    (priceAfterFixed == null || priceAfterPercentage.compareTo(priceAfterFixed) <= 0)) {
                finalPrice = priceAfterPercentage;
                // Najdeme znovu nejlepší % slevu pro informaci
                appliedDiscount = discountService.findActiveDiscountsForProduct(product).stream()
                        .filter(Discount::isPercentage)
                        .filter(d -> d.getValue() != null && d.getValue().compareTo(BigDecimal.ZERO) > 0)
                        .max(Comparator.comparing(Discount::getValue))
                        .orElse(null);

            } else if (priceAfterFixed != null && priceAfterFixed.compareTo(originalPrice) < 0) {
                finalPrice = priceAfterFixed;
                // Najdeme znovu nejlepší fixní slevu pro informaci
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
        priceInfo.put("discountedPrice", (finalPrice != null && finalPrice.compareTo(originalPrice) < 0) ? finalPrice : null); // Zobrazíme jen pokud je sleva
        priceInfo.put("discountApplied", appliedDiscount);

        log.debug("Calculated final price for product ID {} in {}: Original={}, Discounted={}, Discount={}",
                product.getId(), currency, originalPrice, priceInfo.get("discountedPrice"), appliedDiscount != null ? appliedDiscount.getName() : "None");

        return priceInfo;
    }


    @Transactional
    public Product createProduct(Product product) {
        logger.info(">>> [ProductService] Vstupuji do createProduct. Název: {}", product.getName());
        Product savedProduct = null;
        try {
            // Validace daňové sazby
            if (product.getTaxRate() == null || product.getTaxRate().getId() == null) {
                throw new IllegalArgumentException("Product must have a valid TaxRate assigned (with ID).");
            }
            TaxRate taxRate = taxRateRepository.findById(product.getTaxRate().getId())
                    .orElseThrow(() -> new EntityNotFoundException("TaxRate not found with id: " + product.getTaxRate().getId()));
            product.setTaxRate(taxRate); // Použít načtenou sazbu

            // Generování a validace slugu
            if (!StringUtils.hasText(product.getSlug())) {
                product.setSlug(generateSlug(product.getName()));
                logger.debug("Generated slug '{}' from name '{}'", product.getSlug(), product.getName());
            } else {
                product.setSlug(generateSlug(product.getSlug())); // Vyčistit i zadaný slug
            }
            if (productRepository.existsBySlugIgnoreCase(product.getSlug())) {
                // Jednoduchá strategie pro duplicitní slug - přidat ID (až po prvním uložení?) nebo timestamp
                // Prozatím hodíme výjimku
                throw new IllegalArgumentException("Produkt se slugem '" + product.getSlug() + "' již existuje.");
            }

            // Zpracování Customisable & Konfigurátoru
            if (product.isCustomisable()) {
                if (product.getConfigurator() == null) {
                    ProductConfigurator configurator = new ProductConfigurator();
                    configurator.setProduct(product); // Propojení
                    initializeDefaultConfiguratorValues(configurator); // Nastavení defaultních hodnot
                    product.setConfigurator(configurator);
                    logger.info("[ProductService] Vytvořen defaultní ProductConfigurator pro nový custom produkt.");
                } else {
                    // Pokud formulář poslal data konfigurátoru, zajistíme propojení
                    product.getConfigurator().setProduct(product);
                }
                // U custom produktu vynulujeme standardní atributy
                product.setAvailableDesigns(Collections.emptySet());
                product.setAvailableGlazes(Collections.emptySet());
                product.setAvailableRoofColors(Collections.emptySet());
                logger.debug("Cleared standard attributes for custom product '{}'", product.getName());
            } else {
                // U standardního produktu zajistíme, že nemá konfigurátor a addony
                product.setConfigurator(null);
                product.setAvailableAddons(Collections.emptySet());
                logger.debug("Ensured no configurator/addons for standard product '{}'", product.getName());
            }

            // Zajistíme, že kolekce asociací nejsou null
            ensureCollectionsInitialized(product);

            // Uložení produktu (včetně případného nového konfigurátoru díky CascadeType.ALL)
            savedProduct = productRepository.save(product);
            logger.info(">>> [ProductService] Produkt '{}' úspěšně vytvořen s ID: {}. Opouštím createProduct.", savedProduct.getName(), savedProduct.getId());

        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v createProduct pro '{}': {} !!!", product.getName(), e.getMessage(), e);
            throw e; // Rethrow, aby controller mohl reagovat
        }
        return savedProduct;
    }

    @Transactional
    public Optional<Product> updateProduct(Long id, Product productData, Product productToUpdate) {
        logger.info(">>> [ProductService] Vstupuji do updateProduct (s přednačtenou entitou). ID: {}", id);
        try {
            // --- Validace a aktualizace základních polí a vazeb ---
            // Daňová sazba
            if (productData.getTaxRate() == null || productData.getTaxRate().getId() == null) {
                throw new IllegalArgumentException("ID daňové sazby musí být zadáno.");
            }
            if (productToUpdate.getTaxRate() == null || !productToUpdate.getTaxRate().getId().equals(productData.getTaxRate().getId())) {
                TaxRate newTaxRate = taxRateRepository.findById(productData.getTaxRate().getId())
                        .orElseThrow(() -> new EntityNotFoundException("TaxRate not found with id: " + productData.getTaxRate().getId()));
                productToUpdate.setTaxRate(newTaxRate);
                logger.debug("[ProductService] Daňová sazba pro produkt ID {} aktualizována na ID {}.", id, newTaxRate.getId());
            }

            // Slug
            if (StringUtils.hasText(productData.getSlug()) && !productToUpdate.getSlug().equalsIgnoreCase(productData.getSlug().trim())) {
                String newSlug = generateSlug(productData.getSlug().trim()); // Vyčistit slug
                // Ověření unikátnosti nového slugu (pokud se liší od původního)
                if (!newSlug.equalsIgnoreCase(productToUpdate.getSlug())) {
                    productRepository.findBySlugIgnoreCase(newSlug)
                            .filter(found -> !found.getId().equals(id)) // Zkontrolovat, zda nenalezl sám sebe
                            .ifPresent(existing -> {
                                throw new IllegalArgumentException("Produkt se slugem '" + newSlug + "' již používá jiný produkt (ID: " + existing.getId() +").");
                            });
                    productToUpdate.setSlug(newSlug);
                    logger.debug("Slug for product ID {} updated to '{}'.", id, newSlug);
                }
            } else if (!StringUtils.hasText(productToUpdate.getSlug()) && StringUtils.hasText(productData.getName())) {
                // Vygenerovat slug z názvu, pokud chybí
                String generatedSlug = generateSlug(productData.getName());
                productRepository.findBySlugIgnoreCase(generatedSlug)
                        .filter(found -> !found.getId().equals(id))
                        .ifPresent(existing -> {
                            // Pokud je i generovaný slug duplicitní, přidáme číslo (jednoduchá strategie)
                            String originalSlug = generatedSlug;
                            int counter = 1;
                            while(productRepository.existsBySlugIgnoreCase(originalSlug + "-" + counter)) {
                                counter++;
                            }
                            String uniqueSlug = originalSlug + "-" + counter;
                            productToUpdate.setSlug(uniqueSlug);
                            log.warn("Generated slug '{}' was duplicate, used '{}' instead for product ID {}.", originalSlug, uniqueSlug, id);
                        });
                if (!StringUtils.hasText(productToUpdate.getSlug())) { // Pokud nebyl nalezen duplikát
                    productToUpdate.setSlug(generatedSlug);
                    logger.debug("Generated slug '{}' from name for product ID {}.", generatedSlug, id);
                }
            }

            // Ostatní základní pole
            productToUpdate.setName(productData.getName());
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
            boolean customisableChanged = productToUpdate.isCustomisable() != productData.isCustomisable();
            if (customisableChanged) {
                productToUpdate.setCustomisable(productData.isCustomisable());
                logger.info("Customisable flag changed to {} for product ID {}", productData.isCustomisable(), id);
                if (productData.isCustomisable()) {
                    // Stal se custom produktem
                    if (productToUpdate.getConfigurator() == null) {
                        // Vytvoříme a naplníme defaultní konfigurátor
                        ProductConfigurator newConfig = new ProductConfigurator();
                        newConfig.setProduct(productToUpdate);
                        initializeDefaultConfiguratorValues(newConfig);
                        productToUpdate.setConfigurator(newConfig);
                        logger.info("Created and assigned new default configurator for product ID {} during update.", id);
                    }
                    // Vyprázdníme standardní atributy (pokud je to žádoucí)
                    productToUpdate.setAvailableDesigns(new HashSet<>());
                    productToUpdate.setAvailableGlazes(new HashSet<>());
                    productToUpdate.setAvailableRoofColors(new HashSet<>());
                    logger.debug("Cleared standard attributes as product ID {} became customisable.", id);
                } else {
                    // Stal se standardním produktem
                    // Konfigurátor bude smazán díky CascadeType.ALL a orphanRemoval=true, pokud je tak nastaveno
                    // productToUpdate.setConfigurator(null); // Není třeba, JPA se postará
                    // Vyprázdníme custom atributy (doplňky)
                    productToUpdate.setAvailableAddons(new HashSet<>());
                    logger.debug("Cleared custom addons as product ID {} became standard.", id);
                }
            }

            // --- Aktualizace Konfigurátoru (pokud je produkt customisable) ---
            if (productToUpdate.isCustomisable()) {
                if (productData.getConfigurator() != null) {
                    ProductConfigurator existingConfig = productToUpdate.getConfigurator();
                    // Pokud konfigurátor neexistoval (nemělo by nastat, pokud customisableChanged=true), vytvoříme ho
                    if (existingConfig == null) {
                        logger.warn("Product ID {} is customisable but existingConfigurator was null during update. Creating default.", id);
                        existingConfig = new ProductConfigurator();
                        existingConfig.setProduct(productToUpdate);
                        initializeDefaultConfiguratorValues(existingConfig);
                        productToUpdate.setConfigurator(existingConfig);
                    }
                    ProductConfigurator dataConfig = productData.getConfigurator();
                    logger.debug("Updating configurator data for product ID {}", id);
                    // Přenos hodnot z formuláře (productData) do existující entity (existingConfig)
                    existingConfig.setMinLength(dataConfig.getMinLength());
                    existingConfig.setMaxLength(dataConfig.getMaxLength());
                    existingConfig.setMinWidth(dataConfig.getMinWidth());
                    existingConfig.setMaxWidth(dataConfig.getMaxWidth());
                    existingConfig.setMinHeight(dataConfig.getMinHeight());
                    existingConfig.setMaxHeight(dataConfig.getMaxHeight());
                    existingConfig.setPricePerCmLengthCZK(dataConfig.getPricePerCmLengthCZK());
                    existingConfig.setPricePerCmLengthEUR(dataConfig.getPricePerCmLengthEUR());
                    existingConfig.setPricePerCmDepthCZK(dataConfig.getPricePerCmDepthCZK());
                    existingConfig.setPricePerCmDepthEUR(dataConfig.getPricePerCmDepthEUR());
                    existingConfig.setPricePerCmHeightCZK(dataConfig.getPricePerCmHeightCZK());
                    existingConfig.setPricePerCmHeightEUR(dataConfig.getPricePerCmHeightEUR());
                    existingConfig.setDesignPriceCZK(dataConfig.getDesignPriceCZK());
                    existingConfig.setDesignPriceEUR(dataConfig.getDesignPriceEUR());
                    existingConfig.setDividerPricePerCmDepthCZK(dataConfig.getDividerPricePerCmDepthCZK());
                    existingConfig.setDividerPricePerCmDepthEUR(dataConfig.getDividerPricePerCmDepthEUR());
                    existingConfig.setGutterPriceCZK(dataConfig.getGutterPriceCZK());
                    existingConfig.setGutterPriceEUR(dataConfig.getGutterPriceEUR());
                    existingConfig.setShedPriceCZK(dataConfig.getShedPriceCZK());
                    existingConfig.setShedPriceEUR(dataConfig.getShedPriceEUR());
                } else {
                    logger.warn("Product ID {} is customisable, but received null configurator data in update request.", id);
                    // Ponechat stávající konfigurátor, pokud existuje, nebo vytvořit defaultní, pokud neexistuje
                    if (productToUpdate.getConfigurator() == null) {
                        ProductConfigurator newConfig = new ProductConfigurator();
                        newConfig.setProduct(productToUpdate);
                        initializeDefaultConfiguratorValues(newConfig);
                        productToUpdate.setConfigurator(newConfig);
                        logger.warn("Created default configurator for product ID {} because received data was null.", id);
                    }
                }
            }

            // --- Aktualizace Asociací ---
            // Asociace byly nastaveny přímo na `productToUpdate` v controlleru
            // před voláním této metody. JPA se postará o aktualizaci spojovacích tabulek.

            // Zajistíme inicializaci kolekcí pro případnou kontrolu níže
            ensureCollectionsInitialized(productToUpdate);

            // --- Uložení ---
            Product savedProduct = productRepository.save(productToUpdate);
            logger.info("[ProductService] Produkt ID {} aktualizován (včetně asociací a konfigurátoru).", id);
            return Optional.of(savedProduct);

        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v updateProduct (s přednačtenou entitou) pro ID {}: {} !!!", id, e.getMessage(), e);
            throw e; // Rethrow
        }
    }


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
            product.setActive(false);
            productRepository.save(product);
            logger.info("[ProductService] Produkt ID {} ('{}') označen jako neaktivní (soft delete).", id, product.getName());
        } catch (EntityNotFoundException e) {
            logger.error("!!! [ProductService] Produkt ID {} nenalezen pro smazání (soft delete).", id);
            throw e;
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v deleteProduct (soft delete) pro ID {}: {} !!!", id, e.getMessage(), e);
            throw e;
        }
        logger.info(">>> [ProductService] Opouštím deleteProduct (soft delete). ID: {}", id);
    }



    // --- Výpočet ceny produktu "Na míru" ---
    @Transactional(readOnly = true)
    public BigDecimal calculateDynamicProductPrice(Product product, Map<String, BigDecimal> dimensions,
                                                   String customDesign, boolean hasDivider,
                                                   boolean hasGutter, boolean hasGardenShed,
                                                   String currency) {
        logger.info(">>> [ProductService] Vstupuji do calculateDynamicProductPrice. Product ID: {}, Currency: {}", product != null ? product.getId() : "null", currency);
        BigDecimal finalPrice = BigDecimal.ZERO;
        try {
            // Validace vstupů
            if (product == null) throw new IllegalArgumentException("Product cannot be null.");
            if (!product.isActive()) throw new IllegalArgumentException("Cannot calculate price for inactive product ID: " + product.getId());
            if (!product.isCustomisable() || product.getConfigurator() == null) throw new IllegalArgumentException("Product ID " + product.getId() + " is not customisable or missing configurator.");
            if (dimensions == null) throw new IllegalArgumentException("Dimensions map cannot be null.");

            ProductConfigurator config = product.getConfigurator();
            BigDecimal length = dimensions.get("length");
            BigDecimal depth = dimensions.get("width"); // Pozor na konzistenci názvů (width/depth)
            BigDecimal height = dimensions.get("height");

            if (length == null || depth == null || height == null) {
                throw new IllegalArgumentException("Missing one or more dimensions (length, width, height) for custom product calculation.");
            }

            // Validace rozměrů oproti limitům
            validateDimension("Length (Délka)", length, config.getMinLength(), config.getMaxLength());
            validateDimension("Depth (Hloubka)", depth, config.getMinWidth(), config.getMaxWidth());
            validateDimension("Height (Výška)", height, config.getMinHeight(), config.getMaxHeight());

            // Načtení cenových konstant pro danou měnu
            BigDecimal pricePerCmH = getPriceForCurrency(config.getPricePerCmHeightCZK(), config.getPricePerCmHeightEUR(), currency, "Height Price/cm");
            BigDecimal pricePerCmL = getPriceForCurrency(config.getPricePerCmLengthCZK(), config.getPricePerCmLengthEUR(), currency, "Length Price/cm");
            BigDecimal pricePerCmD = getPriceForCurrency(config.getPricePerCmDepthCZK(), config.getPricePerCmDepthEUR(), currency, "Depth Price/cm");

            // Výpočet ceny z rozměrů
            BigDecimal priceFromDimensions = height.multiply(pricePerCmH)
                    .add(length.multiply(pricePerCmL))
                    .add(depth.multiply(pricePerCmD));

            // Ceny volitelných prvků
            BigDecimal designPrice = getPriceForCurrency(config.getDesignPriceCZK(), config.getDesignPriceEUR(), currency, "Design Price");
            BigDecimal dividerPrice = BigDecimal.ZERO;
            if (hasDivider) {
                BigDecimal dividerRate = getPriceForCurrency(config.getDividerPricePerCmDepthCZK(), config.getDividerPricePerCmDepthEUR(), currency, "Divider Price/cm");
                dividerPrice = depth.multiply(dividerRate);
            }
            BigDecimal gutterPrice = BigDecimal.ZERO;
            if (hasGutter) {
                gutterPrice = getPriceForCurrency(config.getGutterPriceCZK(), config.getGutterPriceEUR(), currency, "Gutter Price");
            }
            BigDecimal shedPrice = BigDecimal.ZERO;
            if (hasGardenShed) {
                shedPrice = getPriceForCurrency(config.getShedPriceCZK(), config.getShedPriceEUR(), currency, "Shed Price");
            }

            logger.debug("[ProductService] Price from dimensions ({}): {}", currency, priceFromDimensions.setScale(PRICE_SCALE, ROUNDING_MODE));
            logger.debug("[ProductService] Price for design '{}' ({}): {}", customDesign, currency, designPrice.setScale(PRICE_SCALE, ROUNDING_MODE));
            logger.debug("[ProductService] Price for divider ({}): {}", currency, dividerPrice.setScale(PRICE_SCALE, ROUNDING_MODE));
            logger.debug("[ProductService] Price for gutter ({}, fixed): {}", currency, gutterPrice.setScale(PRICE_SCALE, ROUNDING_MODE));
            logger.debug("[ProductService] Price for garden shed ({}, fixed): {}", currency, shedPrice.setScale(PRICE_SCALE, ROUNDING_MODE));

            // Celková cena = rozměry + volitelné prvky
            finalPrice = priceFromDimensions.add(designPrice).add(dividerPrice).add(gutterPrice).add(shedPrice);
            // Zaokrouhlení a zajištění nezápornosti
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

    // Pomocná metoda pro získání ceny ve správné měně
    private BigDecimal getPriceForCurrency(BigDecimal priceCZK, BigDecimal priceEUR, String currency, String priceName) {
        BigDecimal price = EURO_CURRENCY.equals(currency) ? priceEUR : priceCZK;
        if (price == null) {
            // Pokud cena pro danou měnu není nastavena, můžeme buď hodit výjimku, nebo vrátit 0
            // Pro většinu volitelných prvků je asi bezpečnější vrátit 0 a logovat varování
            if (!priceName.contains("/cm")) { // Pro ceny za cm je to kritická chyba
                logger.warn("Optional price '{}' missing for currency {}. Using 0.", priceName, currency);
                return BigDecimal.ZERO;
            } else {
                throw new IllegalStateException(String.format("Required price configuration '%s' missing for currency %s", priceName, currency));
            }
        }
        return price;
    }


    // Pomocná metoda pro validaci rozměru
    private void validateDimension(String name, BigDecimal value, BigDecimal min, BigDecimal max) {
        if (min == null || max == null) throw new IllegalStateException("Config error: Missing limits for dimension " + name);
        if (value == null) throw new IllegalArgumentException("Dimension " + name + " cannot be null.");
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException(String.format("%s dimension (%s cm) is outside allowed range [%s, %s] cm.",
                    name, value.stripTrailingZeros().toPlainString(),
                    min.stripTrailingZeros().toPlainString(), max.stripTrailingZeros().toPlainString()));
        }
    }

    // Pomocná metoda pro inicializaci kolekcí
    private void ensureCollectionsInitialized(Product product) {
        if (product == null) return;
        if (product.getAvailableDesigns() == null) product.setAvailableDesigns(new HashSet<>());
        if (product.getAvailableGlazes() == null) product.setAvailableGlazes(new HashSet<>());
        if (product.getAvailableRoofColors() == null) product.setAvailableRoofColors(new HashSet<>());
        if (product.getAvailableAddons() == null) product.setAvailableAddons(new HashSet<>());
        // if (product.getImages() == null) product.setImages(new ArrayList<>()); // Pokud by bylo potřeba
    }

    // Pomocná metoda pro nastavení defaultních hodnot konfigurátoru
    private void initializeDefaultConfiguratorValues(ProductConfigurator configurator) {
        configurator.setMinLength(new BigDecimal("100.00")); configurator.setMaxLength(new BigDecimal("500.00"));
        configurator.setMinWidth(new BigDecimal("50.00")); configurator.setMaxWidth(new BigDecimal("200.00"));
        configurator.setMinHeight(new BigDecimal("150.00")); configurator.setMaxHeight(new BigDecimal("300.00"));
        // Nastavení cen - pozor na null, pokud by nemusely být povinné
        configurator.setPricePerCmHeightCZK(Optional.ofNullable(configurator.getPricePerCmHeightCZK()).orElse(new BigDecimal("14.00")));
        configurator.setPricePerCmLengthCZK(Optional.ofNullable(configurator.getPricePerCmLengthCZK()).orElse(new BigDecimal("99.00")));
        configurator.setPricePerCmDepthCZK(Optional.ofNullable(configurator.getPricePerCmDepthCZK()).orElse(new BigDecimal("25.00")));
        configurator.setDividerPricePerCmDepthCZK(Optional.ofNullable(configurator.getDividerPricePerCmDepthCZK()).orElse(new BigDecimal("13.00")));
        configurator.setDesignPriceCZK(Optional.ofNullable(configurator.getDesignPriceCZK()).orElse(BigDecimal.ZERO));
        configurator.setGutterPriceCZK(Optional.ofNullable(configurator.getGutterPriceCZK()).orElse(new BigDecimal("1000.00")));
        configurator.setShedPriceCZK(Optional.ofNullable(configurator.getShedPriceCZK()).orElse(new BigDecimal("5000.00")));
        configurator.setPricePerCmHeightEUR(Optional.ofNullable(configurator.getPricePerCmHeightEUR()).orElse(new BigDecimal("0.56")));
        configurator.setPricePerCmLengthEUR(Optional.ofNullable(configurator.getPricePerCmLengthEUR()).orElse(new BigDecimal("3.96")));
        configurator.setPricePerCmDepthEUR(Optional.ofNullable(configurator.getPricePerCmDepthEUR()).orElse(new BigDecimal("1.00")));
        configurator.setDividerPricePerCmDepthEUR(Optional.ofNullable(configurator.getDividerPricePerCmDepthEUR()).orElse(new BigDecimal("0.52")));
        configurator.setDesignPriceEUR(Optional.ofNullable(configurator.getDesignPriceEUR()).orElse(BigDecimal.ZERO));
        configurator.setGutterPriceEUR(Optional.ofNullable(configurator.getGutterPriceEUR()).orElse(new BigDecimal("40.00")));
        configurator.setShedPriceEUR(Optional.ofNullable(configurator.getShedPriceEUR()).orElse(new BigDecimal("200.00")));
        logger.info("Initialized default values for configurator linked to product ID {}", configurator.getProduct() != null ? configurator.getProduct().getId() : "(unlinked)");
    }

    // --- Generování slugu ---
    public static String generateSlug(String input) {
        if (input == null || input.trim().isEmpty()) {
            // Vrátit timestamp nebo náhodný řetězec, pokud je vstup prázdný? Nebo hodit výjimku?
            // Prozatím vrátíme prázdný řetězec, ale logujeme varování.
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
            // Znovu odstranit pomlčky na konci po oříznutí
            slug = EDGES_DASHES.matcher(slug).replaceAll("");
        }
        // Logování vygenerovaného slugu může být užitečné pro debugging
        logger.trace("Generated slug '{}' from input '{}'", slug, input);
        return slug;
    }
    /**
     * Uloží nahraný soubor, vytvoří Image entitu a přiřadí ji k produktu.
     *
     * @param productId ID produktu.
     * @param file Nahraný soubor.
     * @param altText Alternativní text obrázku.
     * @param titleText Titulek obrázku.
     * @param displayOrder Pořadí zobrazení.
     * @return Vytvořená a uložená Image entita.
     * @throws IOException Pokud dojde k chybě při ukládání souboru.
     * @throws EntityNotFoundException Pokud produkt s daným ID neexistuje.
     * @throws IllegalArgumentException Pokud je soubor neplatný.
     */
    @Transactional
    public Image addImageToProduct(Long productId, MultipartFile file, String altText, String titleText, Integer displayOrder) throws IOException {
        logger.info(">>> [ProductService] Attempting to add image to product ID: {}", productId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot add an empty image file.");
        }

        // Uložení souboru pomocí FileStorageService
        String fileUrl = fileStorageService.storeFile(file, "products"); // Ukládáme do podadresáře "products"

        // Vytvoření nové Image entity
        Image newImage = new Image();
        newImage.setUrl(fileUrl);
        newImage.setAltText(altText);
        newImage.setTitleText(titleText);
        newImage.setDisplayOrder(displayOrder != null ? displayOrder : 0); // Default na 0 pokud není zadáno

        // Zavolání stávající metody pro přiřazení k produktu a uložení entity Image
        Image savedImage = addProductImage(productId, newImage); // Tato metoda již loguje
        logger.info(">>> [ProductService] Image successfully added and associated with product ID: {}", productId);
        return savedImage;
    }

    // Stávající metoda addProductImage (zůstává pro použití novou metodou)
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
            // --- ZMĚNA: Výpočet displayOrder, pokud není nastaveno ---
            if (image.getDisplayOrder() == null) {
                // Najdeme maximální displayOrder pro daný produkt a přičteme 1
                int maxOrder = product.getImages().stream()
                        .mapToInt(img -> img.getDisplayOrder() != null ? img.getDisplayOrder() : -1)
                        .max()
                        .orElse(-1); // Pokud nejsou žádné obrázky, max je -1
                image.setDisplayOrder(maxOrder + 1);
                logger.debug("Calculated next displayOrder: {}", image.getDisplayOrder());
            }
            // --- KONEC ZMĚNY ---

            image.setProduct(product); // Asociace PŘED uložením
            savedImage = imageRepository.save(image);

            // --- DŮLEŽITÉ: Aktualizace kolekce v Product entitě (pokud není řízeno JPA automaticky) ---
            // Někdy je potřeba explicitně přidat, záleží na konfiguraci Cascade a Fetch
            // if (product.getImages() == null) {
            //     product.setImages(new ArrayList<>());
            // }
            // if (!product.getImages().contains(savedImage)) { // Zabráníme duplicitám, pokud by JPA přidalo samo
            //     product.getImages().add(savedImage);
            //     productRepository.save(product); // Uložíme i produkt pro aktualizaci kolekce
            //     logger.debug("Explicitly added image to product's image collection.");
            // }
            // Pokud používáte CascadeType.ALL a orphanRemoval=true na @OneToMany v Product,
            // toto explicitní přidávání a ukládání produktu by nemělo být nutné.

            logger.info("[ProductService] Obrázek (Image entity) uložen (ID: {}) a přiřazen k produktu ID: {}", savedImage.getId(), productId);
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v addProductImage (Image entity) pro Produkt ID {}: {} !!!", productId, e.getMessage(), e);
            throw e;
        }
        logger.info(">>> [ProductService] Opouštím addProductImage (Image entity). Product ID: {}", productId);
        return savedImage;
    }

    /**
     * Smaže obrázek produktu.
     * Zahrnuje i smazání fyzického souboru.
     * @param imageId ID obrázku ke smazání.
     */
    @Transactional
    public void deleteImage(Long imageId) {
        logger.warn(">>> [ProductService] Attempting to DELETE image ID: {}", imageId);
        try {
            Image image = imageRepository.findById(imageId)
                    .orElseThrow(() -> new EntityNotFoundException("Image not found: " + imageId));

            String fileUrl = image.getUrl(); // Získáme URL před smazáním entity

            // Smazání entity z DB (orphanRemoval by měl fungovat, pokud je nastaven v Product)
            imageRepository.delete(image);
            // Alternativně, pokud není orphanRemoval, museli bychom najít produkt a odebrat z kolekce:
            // Product product = image.getProduct();
            // if (product != null) {
            //     product.getImages().remove(image);
            //     image.setProduct(null); // Odebrat vazbu
            //     imageRepository.delete(image); // Pak smazat obrázek
            //     productRepository.save(product); // Uložit produkt
            // } else {
            //     imageRepository.delete(image); // Smazat jen obrázek, pokud nemá produkt
            // }

            log.info("[ProductService] Image entity ID {} deleted from database.", imageId);

            // Smazání souboru (pokud máme URL)
            if (StringUtils.hasText(fileUrl)) {
                fileStorageService.deleteFile(fileUrl);
            } else {
                log.warn("Cannot delete physical file for image ID {}: URL is missing.", imageId);
            }

        } catch (EntityNotFoundException e) {
            logger.error("!!! [ProductService] Image ID {} not found for deletion.", imageId);
            throw e; // Necháme projít výjimku
        } catch (Exception e) {
            logger.error("!!! [ProductService] Error deleting image ID {}: {} !!!", imageId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete image " + imageId, e); // Obecná chyba
        }
        logger.info(">>> [ProductService] Image deletion process finished for ID: {}", imageId);
    }
}