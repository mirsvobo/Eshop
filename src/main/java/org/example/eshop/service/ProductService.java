package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.model.*; // <-- Import všech modelů
import org.example.eshop.repository.ImageRepository;
import org.example.eshop.repository.ProductRepository;
import org.example.eshop.repository.TaxRateRepository; // <<< Zůstává
import org.example.eshop.config.PriceConstants;
import org.slf4j.Logger; // <-- PŘIDÁNO
import org.slf4j.LoggerFactory; // <-- PŘIDÁNO
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*; // Import pro Set, Map atd.
import java.util.regex.Pattern;

import static org.example.eshop.service.CustomerService.log;

@Service
public class ProductService implements PriceConstants {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]+");
    private static final Pattern MULTIPLE_DASHES = Pattern.compile("-{2,}");
    private static final Pattern EDGES_DASHES = Pattern.compile("^-|-$");


    @Autowired private ProductRepository productRepository;
    @Autowired private ImageRepository imageRepository;
    @Autowired private TaxRateRepository taxRateRepository;

    // --- Metody pro čtení produktů ---

    @Transactional(readOnly = true)
    public Page<Product> getAllProducts(Pageable pageable) {
        logger.info(">>> [ProductService] Vstupuji do getAllProducts(Pageable: {}) <<<", pageable);
        Page<Product> result = Page.empty(pageable);
        try {
            result = productRepository.findAll(pageable);
            logger.info("[ProductService] getAllProducts(Pageable): Načtena stránka s {} produkty.", result.getTotalElements());
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v getAllProducts(Pageable): {} !!!", e.getMessage(), e);
            // Vracíme prázdnou stránku
        }
        logger.info(">>> [ProductService] Opouštím getAllProducts(Pageable) <<<");
        return result;
    }
    // TODO (Budoucí): Implementovat robustnější filtrování pro admina
    /*
    @Transactional(readOnly = true)
    public Page<Product> findProductsAdmin(Pageable pageable, String nameFilter, Boolean activeFilter) {
        log.debug("Searching for admin products with filters - Name: '{}', Active: {}, Page: {}", nameFilter, activeFilter, pageable);
        Specification<Product> spec = Specification.where(null);
        if (StringUtils.hasText(nameFilter)) {
            spec = spec.and(ProductSpecifications.nameContains(nameFilter)); // Potřebovali bychom ProductSpecifications
        }
        if (activeFilter != null) {
            spec = spec.and(ProductSpecifications.isActive(activeFilter)); // Potřebovali bychom ProductSpecifications
        }
        return productRepository.findAll(spec, pageable);
    }
    */

    @Transactional(readOnly = true)
    public Page<Product> getActiveProducts(Pageable pageable) {
        logger.info(">>> [ProductService] Vstupuji do getActiveProducts(Pageable: {}) <<<", pageable);
        Page<Product> result = Page.empty(pageable);
        try {
            result = productRepository.findByActiveTrue(pageable);
            logger.info("[ProductService] getActiveProducts(Pageable): Načtena stránka s {} aktivními produkty.", result.getNumberOfElements());
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v getActiveProducts(Pageable): {} !!!", e.getMessage(), e);
            // Vracíme prázdnou stránku
        }
        logger.info(">>> [ProductService] Opouštím getActiveProducts(Pageable) <<<");
        return result;
    }

    @Transactional(readOnly = true)
    public List<Product> getAllActiveProducts() {
        logger.info(">>> [ProductService] Vstupuji do getAllActiveProducts (List) <<<");
        List<Product> result = Collections.emptyList();
        try {
            result = productRepository.findByActiveTrue();
            logger.info("[ProductService] getAllActiveProducts (List): Načteno {} aktivních produktů.", (result != null ? result.size() : "NULL"));
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v getAllActiveProducts (List): {} !!!", e.getMessage(), e);
            // Vracíme prázdný seznam
        }
        logger.info(">>> [ProductService] Opouštím getAllActiveProducts (List) <<<");
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<Product> getProductById(Long id){
        logger.info(">>> [ProductService] Vstupuji do getProductById. ID: {}", id);
        Optional<Product> result = Optional.empty();
        try {
            result = productRepository.findById(id);
            // --- UPRAVENÉ LOGOVÁNÍ ---
            logger.info("[ProductService] getProductById: Produkt ID {} {}.", id, result.isPresent() ? "nalezen" : "nenalezen");
            // --- KONEC ÚPRAVY ---
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v getProductById (ID: {}): {} !!!", id, e.getMessage(), e);
        }
        logger.info(">>> [ProductService] Opouštím getProductById (ID: {}) <<<", id);
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<Product> getActiveProductBySlug(String slug) {
        logger.info(">>> [ProductService] Vstupuji do getActiveProductBySlug. Slug: {}", slug);
        Optional<Product> result = Optional.empty();
        try {
            result = productRepository.findByActiveTrueAndSlugIgnoreCase(slug);
            // --- UPRAVENÉ LOGOVÁNÍ ---
            logger.info("[ProductService] getActiveProductBySlug: Aktivní produkt se slugem '{}' {}.", slug, result.isPresent() ? "nalezen" : "nenalezen");
            // --- KONEC ÚPRAVY ---
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v getActiveProductBySlug (Slug: {}): {} !!!", slug, e.getMessage(), e);
        }
        logger.info(">>> [ProductService] Opouštím getActiveProductBySlug (Slug: {}) <<<", slug);
        return result;
    }

    // --- Metody pro CRUD operace (pro CMS) ---

    @Transactional
    public Product createProduct(Product product) {
        logger.info(">>> [ProductService] Vstupuji do createProduct. Název: {}", product.getName());
        Product savedProduct = null;
        try {
            if (product.getTaxRate() == null || product.getTaxRate().getId() == null) {
                throw new IllegalArgumentException("Product must have a valid TaxRate assigned (with ID).");
            }
            TaxRate taxRate = taxRateRepository.findById(product.getTaxRate().getId())
                    .orElseThrow(() -> new EntityNotFoundException("TaxRate not found with id: " + product.getTaxRate().getId()));
            product.setTaxRate(taxRate);

            if (!StringUtils.hasText(product.getSlug())) {
                product.setSlug(generateSlug(product.getName()));
            } else {
                product.setSlug(generateSlug(product.getSlug()));
            }

            if (productRepository.existsBySlugIgnoreCase(product.getSlug())) {
                throw new IllegalArgumentException("Produkt se slugem '" + product.getSlug() + "' již existuje.");
            }

            if (product.isCustomisable()) {
                ProductConfigurator configurator = new ProductConfigurator();
                // ID se nastaví automaticky díky @MapsId v ProductConfigurator
                configurator.setProduct(product);
                // Nastavení defaultních hodnot konfigurátoru
                configurator.setMinLength(new BigDecimal("100.00")); configurator.setMaxLength(new BigDecimal("500.00"));
                configurator.setMinWidth(new BigDecimal("50.00")); configurator.setMaxWidth(new BigDecimal("200.00"));
                configurator.setMinHeight(new BigDecimal("150.00")); configurator.setMaxHeight(new BigDecimal("300.00"));
                configurator.setPricePerCmHeightCZK(new BigDecimal("14.00")); configurator.setPricePerCmLengthCZK(new BigDecimal("99.00"));
                configurator.setPricePerCmDepthCZK(new BigDecimal("25.00")); configurator.setDividerPricePerCmDepthCZK(new BigDecimal("13.00"));
                configurator.setDesignPriceCZK(BigDecimal.ZERO); configurator.setGutterPriceCZK(new BigDecimal("1000.00"));
                configurator.setShedPriceCZK(new BigDecimal("5000.00"));
                configurator.setPricePerCmHeightEUR(new BigDecimal("0.56")); configurator.setPricePerCmLengthEUR(new BigDecimal("3.96"));
                configurator.setPricePerCmDepthEUR(new BigDecimal("1.00")); configurator.setDividerPricePerCmDepthEUR(new BigDecimal("0.52"));
                configurator.setDesignPriceEUR(BigDecimal.ZERO); configurator.setGutterPriceEUR(new BigDecimal("40.00"));
                configurator.setShedPriceEUR(new BigDecimal("200.00"));
                product.setConfigurator(configurator);
                logger.info("[ProductService] Vytvořen defaultní ProductConfigurator pro nový custom produkt.");
            }

            savedProduct = productRepository.save(product);
            logger.info(">>> [ProductService] Produkt '{}' úspěšně vytvořen s ID: {}. Opouštím createProduct.", savedProduct.getName(), savedProduct.getId());
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v createProduct pro '{}': {} !!!", product.getName(), e.getMessage(), e);
            throw e; // Rethrow
        }
        return savedProduct;
    }

    @Transactional
    public Optional<Product> updateProduct(Long id, Product productData, Product productToUpdate) {
        logger.info(">>> [ProductService] Vstupuji do updateProduct (s přednačtenou entitou). ID: {}", id);
        try {
            // Validace TaxRate (přesunuto sem, abychom měli jistotu)
            if (productData.getTaxRate() == null || productData.getTaxRate().getId() == null) {
                throw new IllegalArgumentException("ID daňové sazby musí být zadáno.");
            }
            if (productToUpdate.getTaxRate() == null || !productToUpdate.getTaxRate().getId().equals(productData.getTaxRate().getId())) {
                TaxRate newTaxRate = taxRateRepository.findById(productData.getTaxRate().getId())
                        .orElseThrow(() -> new EntityNotFoundException("TaxRate not found with id: " + productData.getTaxRate().getId()));
                productToUpdate.setTaxRate(newTaxRate);
                logger.debug("[ProductService] Daňová sazba pro produkt ID {} aktualizována na ID {}.", id, newTaxRate.getId());
            }

            // Validate uniqueness of slug if changed
            if (StringUtils.hasText(productData.getSlug()) && !productToUpdate.getSlug().equalsIgnoreCase(productData.getSlug().trim())) {
                String newSlug = productData.getSlug().trim();
                productRepository.findBySlugIgnoreCase(newSlug)
                        .filter(found -> !found.getId().equals(id))
                        .ifPresent(existing -> {
                            throw new IllegalArgumentException("Produkt se slugem '" + newSlug + "' již používá jiný produkt.");
                        });
                productToUpdate.setSlug(newSlug);
            } else if (!StringUtils.hasText(productToUpdate.getSlug()) && StringUtils.hasText(productData.getName())){
                // Pokud slug nebyl zadán v datech a není ani v existující entitě (nemělo by nastat, ale pro jistotu)
                productToUpdate.setSlug(generateSlug(productData.getName()));
                // Zde by se měla znovu ověřit unikátnost vygenerovaného slugu!
                productRepository.findBySlugIgnoreCase(productToUpdate.getSlug())
                        .filter(found -> !found.getId().equals(id))
                        .ifPresent(existing -> {
                            // Pokud je i generovaný slug duplicitní, přidáme číslo
                            String originalSlug = productToUpdate.getSlug();
                            int counter = 1;
                            while(productRepository.existsBySlugIgnoreCase(originalSlug + "-" + counter)) {
                                counter++;
                            }
                            productToUpdate.setSlug(originalSlug + "-" + counter);
                            log.warn("Generated slug was duplicate, appended counter: {}", productToUpdate.getSlug());
                        });
            }

            // Update ostatních polí z productData na productToUpdate
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
            productToUpdate.setCustomisable(productData.isCustomisable());
            productToUpdate.setMetaTitle(productData.getMetaTitle());
            productToUpdate.setMetaDescription(productData.getMetaDescription());

            // Asociace již byly nastaveny v controlleru na productToUpdate

            // Uložení změn
            Product savedProduct = productRepository.save(productToUpdate);
            logger.info("[ProductService] Produkt ID {} aktualizován (včetně asociací).", id);
            return Optional.of(savedProduct);

        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v updateProduct (s přednačtenou entitou) pro ID {}: {} !!!", id, e.getMessage(), e);
            throw e;
        }
    }



    @Transactional
    public void deleteProduct(Long id) {
        logger.info(">>> [ProductService] Vstupuji do deleteProduct (soft delete). ID: {}", id);
        try {
            Product product = productRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Product with id " + id + " not found for deletion."));
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

    // --- Správa obrázků ---
    @Transactional
    public Image addProductImage(Long productId, Image image) {
        logger.info(">>> [ProductService] Vstupuji do addProductImage. Product ID: {}", productId);
        Image savedImage = null;
        try {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + productId));
            if (image == null || !StringUtils.hasText(image.getUrl())) {
                throw new IllegalArgumentException("Image or URL missing.");
            }
            if (image.getDisplayOrder() == null || image.getDisplayOrder() < 0) {
                image.setDisplayOrder(0);
            }
            // Přiřazení produktu k obrázku PŘED uložením obrázku
            image.setProduct(product);
            savedImage = imageRepository.save(image);
            // Není třeba explicitně přidávat do product.getImages() a volat productRepository.save(),
            // pokud je správně nastaveno cascade a vlastnictví relace (mappedBy v Product).
            logger.info("[ProductService] Obrázek uložen (ID: {}) a přiřazen k produktu ID: {}", savedImage.getId(), productId);
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v addProductImage pro Produkt ID {}: {} !!!", productId, e.getMessage(), e);
            throw e;
        }
        logger.info(">>> [ProductService] Opouštím addProductImage. Product ID: {}", productId);
        return savedImage;
    }

    @Transactional
    public void deleteImage(Long imageId) {
        logger.info(">>> [ProductService] Vstupuji do deleteImage. Image ID: {}", imageId);
        try {
            Image image = imageRepository.findById(imageId)
                    .orElseThrow(() -> new EntityNotFoundException("Image not found: " + imageId));
            // Není třeba odstraňovat z kolekce produktu, pokud je orphanRemoval=true
            imageRepository.delete(image);
            logger.info("[ProductService] Obrázek ID {} smazán.", imageId);
        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v deleteImage pro ID {}: {} !!!", imageId, e.getMessage(), e);
            throw e;
        }
        logger.info(">>> [ProductService] Opouštím deleteImage. Image ID: {}", imageId);
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
            if (product == null) { throw new IllegalArgumentException("Product cannot be null."); }
            if (!product.isActive()) { throw new IllegalArgumentException("Cannot calculate price for inactive product ID: " + product.getId()); }
            if (!product.isCustomisable() || product.getConfigurator() == null) { throw new IllegalArgumentException("Product ID " + product.getId() + " is not customisable or missing configurator."); }

            ProductConfigurator config = product.getConfigurator();
            BigDecimal length = dimensions.get("length"); BigDecimal depth = dimensions.get("width"); BigDecimal height = dimensions.get("height");

            if (length == null || depth == null || height == null) {
                throw new IllegalArgumentException("Missing dimensions for custom product calculation.");
            }

            validateDimension("Length (Sirka)", length, config.getMinLength(), config.getMaxLength());
            validateDimension("Depth (Hloubka)", depth, config.getMinWidth(), config.getMaxWidth());
            validateDimension("Height (Vyska)", height, config.getMinHeight(), config.getMaxHeight());

            BigDecimal pricePerCmH = EURO_CURRENCY.equals(currency) ? config.getPricePerCmHeightEUR() : config.getPricePerCmHeightCZK();
            BigDecimal pricePerCmL = EURO_CURRENCY.equals(currency) ? config.getPricePerCmLengthEUR() : config.getPricePerCmLengthCZK();
            BigDecimal pricePerCmD = EURO_CURRENCY.equals(currency) ? config.getPricePerCmDepthEUR() : config.getPricePerCmDepthCZK();

            if (pricePerCmH == null || pricePerCmL == null || pricePerCmD == null) {
                throw new IllegalStateException("Price config (per cm) missing for currency " + currency + " in product configurator ID " + config.getId());
            }

            BigDecimal priceFromDimensions = height.multiply(pricePerCmH)
                    .add(length.multiply(pricePerCmL))
                    .add(depth.multiply(pricePerCmD));

            BigDecimal designPrice = EURO_CURRENCY.equals(currency) ? config.getDesignPriceEUR() : config.getDesignPriceCZK();
            designPrice = Optional.ofNullable(designPrice).orElse(BigDecimal.ZERO); // Null check

            BigDecimal dividerPrice = BigDecimal.ZERO;
            if (hasDivider) {
                BigDecimal dividerRate = EURO_CURRENCY.equals(currency) ? config.getDividerPricePerCmDepthEUR() : config.getDividerPricePerCmDepthCZK();
                if (dividerRate == null) {
                    throw new IllegalStateException("Divider price (per cm) missing for currency " + currency + " in product configurator ID " + config.getId());
                }
                dividerPrice = depth.multiply(dividerRate);
            }

            BigDecimal gutterPrice = BigDecimal.ZERO;
            if (hasGutter) {
                gutterPrice = EURO_CURRENCY.equals(currency) ? config.getGutterPriceEUR() : config.getGutterPriceCZK();
                if (gutterPrice == null) {
                    throw new IllegalStateException("Gutter price missing for currency " + currency + " in product configurator ID " + config.getId());
                }
                gutterPrice = Optional.ofNullable(gutterPrice).orElse(BigDecimal.ZERO); // Null check
            }

            BigDecimal shedPrice = BigDecimal.ZERO;
            if (hasGardenShed) {
                shedPrice = EURO_CURRENCY.equals(currency) ? config.getShedPriceEUR() : config.getShedPriceCZK();
                if (shedPrice == null) {
                    throw new IllegalStateException("Shed price missing for currency " + currency + " in product configurator ID " + config.getId());
                }
                shedPrice = Optional.ofNullable(shedPrice).orElse(BigDecimal.ZERO); // Null check
            }

            logger.debug("[ProductService] Price from dimensions ({}): {}", currency, priceFromDimensions.setScale(PRICE_SCALE, ROUNDING_MODE));
            logger.debug("[ProductService] Price for design '{}' ({}): {}", customDesign, currency, designPrice.setScale(PRICE_SCALE, ROUNDING_MODE));
            logger.debug("[ProductService] Price for divider ({}): {}", currency, dividerPrice.setScale(PRICE_SCALE, ROUNDING_MODE));
            logger.debug("[ProductService] Price for gutter ({}, fixed): {}", currency, gutterPrice.setScale(PRICE_SCALE, ROUNDING_MODE));
            logger.debug("[ProductService] Price for garden shed ({}, fixed): {}", currency, shedPrice.setScale(PRICE_SCALE, ROUNDING_MODE));

            finalPrice = priceFromDimensions.add(designPrice).add(dividerPrice).add(gutterPrice).add(shedPrice);
            finalPrice = finalPrice.setScale(PRICE_SCALE, ROUNDING_MODE);
            finalPrice = finalPrice.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : finalPrice;

        } catch (Exception e) {
            logger.error("!!! [ProductService] Chyba v calculateDynamicProductPrice pro Produkt ID {} ({}): {} !!!", product.getId(), currency, e.getMessage(), e);
            throw e; // Rethrow
        }
        logger.info(">>> [ProductService] Opouštím calculateDynamicProductPrice. Product ID: {}, Currency: {}. Vypočtená cena: {}", product.getId(), currency, finalPrice);
        return finalPrice;
    }

    // Pomocná metoda pro validaci rozměru (beze změny)
    private void validateDimension(String name, BigDecimal value, BigDecimal min, BigDecimal max) {
        if (min == null || max == null) throw new IllegalStateException("Config error: Missing limits for dimension " + name);
        if (value == null) throw new IllegalArgumentException("Dimension " + name + " cannot be null.");
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException(String.format("%s dimension (%s cm) is outside allowed range [%s, %s] cm.", name, value.stripTrailingZeros().toPlainString(), min.stripTrailingZeros().toPlainString(), max.stripTrailingZeros().toPlainString()));
        }
    }

    // --- Generování slugu ---
    public static String generateSlug(String input) {
        if (input == null || input.isEmpty()) return "";
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
        // Můžeme ponechat trace nebo debug log, pokud je potřeba
        // logger.debug("[ProductService] Vygenerován slug '{}' ze vstupu '{}'", slug, input);
        return slug;
    }
}