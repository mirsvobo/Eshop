package org.example.eshop.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.example.eshop.dto.CartItemDto;
import org.example.eshop.dto.CustomPriceRequestDto;
import org.example.eshop.dto.CustomPriceResponseDto;
import org.example.eshop.dto.ProductConfiguratorDto;
import org.example.eshop.model.*;
import org.example.eshop.service.CurrencyService;
import org.example.eshop.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static org.example.eshop.config.PriceConstants.*;
import static org.example.eshop.service.FeedGenerationService.BRAND_NAME;

@Controller
public class ProductController {

    // private static final Logger log = LoggerFactory.getLogger(ProductController.class); // Původní logger
    private static final Logger logger = LoggerFactory.getLogger(ProductController.class); // <-- PŘEJMENOVÁNO NA logger pro konzistenci

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    CurrencyService currencyService;
    @Autowired
    private ProductService productService;


    @GetMapping("/produkty")
    @Transactional(readOnly = true)
// Použijeme PageableDefault pro základní nastavení, řazení přepíšeme níže
    public String listProducts(Model model, @PageableDefault(size = 12) Pageable pageable) {
        logger.info(">>> [ProductController] Vstupuji do listProducts (STANDARD SORTED). Pageable: {}", pageable);
        String currentCurrency = currencyService.getSelectedCurrency();
        model.addAttribute("currentGlobalCurrency", currentCurrency); // Pro layout

        try {
            // 1. Určení pole pro řazení podle aktuální měny
            String priceField = EURO_CURRENCY.equals(currentCurrency) ? "basePriceEUR" : "basePriceCZK";
            Sort priceSort = Sort.by(Sort.Direction.ASC, priceField);

            // 2. Vytvoření Pageable s naším řazením podle ceny
            // POZNÁMKA: Pokud byste chtěli umožnit uživateli řadit i jinak (např. podle názvu) přes URL parametry,
            // logika pro kombinaci řazení by byla složitější. Prozatím nastavíme řazení jen podle ceny.
            Pageable sortedPageable = PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    priceSort // Použijeme naše vynucené řazení podle ceny
            );

            logger.debug("Požaduji aktivní standardní produkty seřazené podle {} ASC. Finální Pageable: {}", priceField, sortedPageable);

            // 3. Zavolání NOVÉ service metody pro načtení standardních seřazených produktů
            // Předpokládá se, že tato metoda v ProductService volá novou metodu
            // findByActiveTrueAndCustomisableFalse(sortedPageable) v ProductRepository
            // NÁZEV METODY SI PŘIZPŮSOBTE PODLE SVÉ IMPLEMENTACE V ProductService!
            Page<Product> productPage = productService.getActiveStandardProducts(sortedPageable);

            logger.info("[ProductController] ProductService.getActiveStandardProducts vrátil stránku: TotalElements={}, TotalPages={}, Number={}, Size={}",
                    productPage.getTotalElements(), productPage.getTotalPages(), productPage.getNumber(), productPage.getSize());

            // 4. Výpočet finálních cen pro zobrazení
            Map<Long, Map<String, Object>> productPrices = new HashMap<>();
            for (Product product : productPage.getContent()) {
                // Již NENÍ potřeba kontrolovat product.isCustomisable(), protože načítáme jen standardní
                if (product != null && product.getId() != null) { // Kontrola null pro jistotu
                    try {
                        Map<String, Object> priceInfo = productService.calculateFinalProductPrice(product, currentCurrency);
                        productPrices.put(product.getId(), priceInfo);
                    } catch (Exception priceEx) {
                        logger.error("Chyba při výpočtu ceny pro produkt ID {} (standard): {}", product.getId(), priceEx.getMessage());
                        productPrices.put(product.getId(), Collections.emptyMap()); // Přidat prázdnou mapu při chybě
                    }
                }
            }

            // 5. Přidání dat do modelu
            model.addAttribute("productPage", productPage);
            model.addAttribute("productPrices", productPrices);
            // Přidáme i informaci o aktuálním řazení pro případné použití v šabloně (např. pro odkazy v hlavičce tabulky)
            model.addAttribute("currentSort", sortedPageable.getSort().toString());

        } catch (Exception e) {
            // Zpracování obecné chyby zůstává stejné
            logger.error("!!! [ProductController] Chyba v listProducts: {} !!!", e.getMessage(), e);
            model.addAttribute("errorMessage", "Nepodařilo se načíst produkty.");
            model.addAttribute("productPage", Page.empty(pageable)); // Použijeme původní pageable pro prázdnou stránku
            model.addAttribute("productPrices", Collections.emptyMap());
            model.addAttribute("currentSort", Sort.unsorted().toString()); // Default na neřazeno při chybě
        }
        logger.info(">>> [ProductController] Opouštím listProducts.");
        return "produkty"; // Název Thymeleaf šablony
    }

    @GetMapping("/produkt/{slug}")
    @Transactional(readOnly = true)
    public String productDetail(@PathVariable String slug, Model model, HttpServletRequest request) {
        logger.info(">>> [ProductController] Vstupuji do productDetail. Slug: {}", slug);
        String currentCurrency = currencyService.getSelectedCurrency();
        model.addAttribute("currentGlobalCurrency", currentCurrency);

        Product product = null;
        try {
            // Načtení produktu
            product = productService.getActiveProductBySlug(slug)
                    .orElseThrow(() -> {
                        logger.warn("[ProductController] Produkt se slugem '{}' nenalezen nebo není aktivní.", slug);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Produkt nebyl nalezen");
                    });
            logger.info("[ProductController] Produkt ID {} nalezen pro slug '{}'.", product.getId(), slug);

            model.addAttribute("product", product); // Přidáváme celou entitu pro ostatní části šablony

            // Příprava DTO pro formulář košíku
            CartItemDto cartItemDto = new CartItemDto();
            cartItemDto.setProductId(product.getId());
            cartItemDto.setCustom(product.isCustomisable());

            // Příprava dat pro tracking (GA4, Sklik, atd.)
            Map<String, Object> trackingData = new HashMap<>();
            trackingData.put("productId", product.getId());
            trackingData.put("productName", product.getName());
            trackingData.put("currency", currentCurrency);
            trackingData.put("brandName", "Dřevníky Kolář");
            trackingData.put("variantId", product.isCustomisable() ? "CUSTOM-" + product.getId() : "STD-" + product.getSlug());
            trackingData.put("categoryName", "Dřevníky"); // Doplnit z produktu, pokud je kategorie dostupná

            // Inicializace cenových proměnných
            BigDecimal priceForTrackingNoVat = BigDecimal.ZERO;
            BigDecimal priceForTrackingWithVat = BigDecimal.ZERO;
            Map<String, Object> priceInfoForPage = null; // Jen pro standardní produkt
            BigDecimal finalPriceForSchema = BigDecimal.ZERO; // Cena pro JSON-LD (vždy bez DPH)
            ProductConfiguratorDto productConfiguratorDto = null; // Pro custom produkt


            if (product.isCustomisable() && product.getConfigurator() != null) {
                // --- Custom Produkt ---
                logger.info("[ProductController] Processing Custom Product ID: {}", product.getId());
                ProductConfigurator config = product.getConfigurator();
                productConfiguratorDto = new ProductConfiguratorDto();

                // Mapování dat z entity konfigurátoru do DTO
                productConfiguratorDto.setMinLength(config.getMinLength());
                productConfiguratorDto.setMaxLength(config.getMaxLength());
                productConfiguratorDto.setStepLength(config.getStepLength());
                productConfiguratorDto.setDefaultLength(config.getDefaultLength());
                productConfiguratorDto.setMinWidth(config.getMinWidth());
                productConfiguratorDto.setMaxWidth(config.getMaxWidth());
                productConfiguratorDto.setStepWidth(config.getStepWidth());
                productConfiguratorDto.setDefaultWidth(config.getDefaultWidth());
                productConfiguratorDto.setMinHeight(config.getMinHeight());
                productConfiguratorDto.setMaxHeight(config.getMaxHeight());
                productConfiguratorDto.setStepHeight(config.getStepHeight());
                productConfiguratorDto.setDefaultHeight(config.getDefaultHeight());
                model.addAttribute("configuratorDto", productConfiguratorDto);

                // Výpočet výchozí/minimální ceny
                Map<String, BigDecimal> initialDimensions = new HashMap<>();
                initialDimensions.put("length", Optional.ofNullable(config.getDefaultLength()).orElse(config.getMinLength()));
                initialDimensions.put("width", Optional.ofNullable(config.getDefaultWidth()).orElse(config.getMinWidth()));
                initialDimensions.put("height", Optional.ofNullable(config.getDefaultHeight()).orElse(config.getMinHeight()));

                logger.debug("[ProductController] Calculating initial price for tracking/schema. Product ID: {}, Initial Dimensions: {}, Currency: {}", product.getId(), initialDimensions, currentCurrency);

                try {
                    // Výpočet základní ceny bez DPH (jen rozměry) pro tracking A schema
                    BigDecimal initialBasePrice = productService.calculateDynamicProductPrice(product, initialDimensions, currentCurrency);
                    // Zde předpokládáme, že pro zobrazení a tracking chceme základní cenu (bez atributů/addonů)
                    // Pokud byste chtěli zahrnout i výchozí atributy/addony, logika by musela být složitější
                    priceForTrackingNoVat = Optional.ofNullable(initialBasePrice).orElse(BigDecimal.ZERO);
                    finalPriceForSchema = priceForTrackingNoVat; // Use the same price for schema

                    logger.debug("[ProductController] Initial price calculated (No VAT): {}", priceForTrackingNoVat);

                    // Výpočet ceny s DPH pro tracking
                    if (priceForTrackingNoVat.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal defaultVatRate = new BigDecimal("0.21"); // FALLBACK
                        Set<TaxRate> rates = product.getAvailableTaxRates();
                        if (rates != null && !rates.isEmpty()) {
                            defaultVatRate = rates.iterator().next().getRate(); // Use first available rate
                        } else {
                            logger.warn("Missing tax rates for custom product ID {}, using fallback rate {} for tracking price with VAT.", product.getId(), defaultVatRate);
                        }
                        priceForTrackingWithVat = priceForTrackingNoVat.multiply(BigDecimal.ONE.add(defaultVatRate)).setScale(PRICE_SCALE, ROUNDING_MODE);
                        logger.debug("[ProductController] Initial price calculated (With VAT): {}", priceForTrackingWithVat);
                    } else {
                        priceForTrackingWithVat = BigDecimal.ZERO;
                    }

                    // Nastavení výchozích rozměrů do DTO formuláře košíku
                    cartItemDto.setCustomDimensions(initialDimensions);

                    // Ceny pro zobrazení na stránce (počáteční) - vypočítáme obě měny
                    model.addAttribute("initialCustomPriceCZK", productService.calculateDynamicProductPrice(product, initialDimensions, "CZK"));
                    model.addAttribute("initialCustomPriceEUR", productService.calculateDynamicProductPrice(product, initialDimensions, "EUR"));

                } catch (Exception e) {
                    logger.error("[ProductController] Error calculating initial price for custom product ID {}: {}", product.getId(), e.getMessage());
                    // Ceny zůstanou inicializovány na 0
                    priceForTrackingNoVat = BigDecimal.ZERO;
                    priceForTrackingWithVat = BigDecimal.ZERO;
                    finalPriceForSchema = BigDecimal.ZERO;
                    model.addAttribute("initialCustomPriceCZK", BigDecimal.ZERO);
                    model.addAttribute("initialCustomPriceEUR", BigDecimal.ZERO);
                    model.addAttribute("initialCustomPriceError", "Chyba výpočtu výchozí ceny.");
                }

            } else if (!product.isCustomisable()) {
                // --- Standardní produkt ---
                logger.info("[ProductController] Processing Standard Product ID: {}", product.getId());
                try {
                    // Získání finální ceny (po slevách)
                    priceInfoForPage = productService.calculateFinalProductPrice(product, currentCurrency);
                    model.addAttribute("priceInfo", priceInfoForPage); // Pro zobrazení na stránce
                    logger.debug("[ProductController] Price Info Map from Service: {}", priceInfoForPage);

                    // Získání ceny BEZ DPH (zlevněné nebo původní) - BEZPEČNĚJŠÍ PŘEVOD
                    BigDecimal priceNoVat = BigDecimal.ZERO; // Default na nulu
                    if (priceInfoForPage != null) {
                        Object discountedPriceObj = priceInfoForPage.get("discountedPrice");
                        Object originalPriceObj = priceInfoForPage.get("originalPrice");
                        Object priceToUseObj = (discountedPriceObj != null) ? discountedPriceObj : originalPriceObj;

                        if (priceToUseObj instanceof BigDecimal bd) {
                            priceNoVat = bd;
                        } else if (priceToUseObj instanceof Number num) {
                            try {
                                priceNoVat = new BigDecimal(num.toString());
                                logger.trace("Converted price from Number ({}) to BigDecimal: {}", priceToUseObj.getClass().getSimpleName(), priceNoVat);
                            } catch (NumberFormatException nfe) {
                                logger.error("Failed to convert Number {} to BigDecimal for product ID {}", priceToUseObj, product.getId());
                                priceNoVat = BigDecimal.ZERO;
                            }
                        } else if (priceToUseObj != null) {
                            logger.warn("Price object in map is not a recognized Number type: {}. Class: {}", priceToUseObj, priceToUseObj.getClass().getName());
                            priceNoVat = BigDecimal.ZERO;
                        } else {
                            logger.warn("Both discountedPrice and originalPrice are missing or null in priceInfoForPage for product ID {}.", product.getId());
                            priceNoVat = BigDecimal.ZERO;
                        }
                    } else {
                        logger.warn("priceInfoForPage map is null for product ID {}.", product.getId());
                        priceNoVat = BigDecimal.ZERO;
                    }

                    // Nastavení cen pro tracking a schema - použijeme stejnou hodnotu priceNoVat
                    priceForTrackingNoVat = priceNoVat.setScale(PRICE_SCALE, ROUNDING_MODE).max(BigDecimal.ZERO);
                    finalPriceForSchema = priceForTrackingNoVat; // <-- Použijeme stejnou hodnotu

                    // Logování výsledku
                    if (priceNoVat.compareTo(BigDecimal.ZERO) <= 0) {
                        logger.warn("Final price (No VAT) calculated as ZERO or less for standard product ID {}. Check priceInfoMap or conversion.", product.getId());
                        if (finalPriceForSchema.compareTo(BigDecimal.ZERO) <= 0){
                            logger.warn("Missing or zero price determined for standard product ID {} in schema calculation.", product.getId());
                        }
                    } else {
                        logger.debug("[ProductController] Valid priceNoVat extracted and processed: {}", priceForTrackingNoVat);
                    }

                    // Výpočet ceny s DPH pro tracking (pouze pokud máme platnou cenu bez DPH)
                    if (priceForTrackingNoVat.compareTo(BigDecimal.ZERO) > 0) {
                        Set<TaxRate> rates = product.getAvailableTaxRates();
                        if (rates != null && !rates.isEmpty()) {
                            BigDecimal vatRate = rates.iterator().next().getRate(); // Assume one rate or take first
                            priceForTrackingWithVat = priceForTrackingNoVat.multiply(BigDecimal.ONE.add(vatRate)).setScale(PRICE_SCALE, ROUNDING_MODE);
                            logger.debug("[ProductController] Calculated priceWithVat for tracking: {}", priceForTrackingWithVat);
                        } else {
                            logger.warn("Missing tax rate for standard product ID {}, cannot calculate price with VAT for tracking.", product.getId());
                            priceForTrackingWithVat = BigDecimal.ZERO; // Fallback
                        }
                    } else {
                        priceForTrackingWithVat = BigDecimal.ZERO; // Pokud je cena bez DPH nula, cena s DPH je taky nula
                    }

                } catch (Exception e) {
                    logger.error("[ProductController] Error calculating price for standard product ID {}: {}", product.getId(), e.getMessage(), e);
                    model.addAttribute("priceInfo", Collections.emptyMap()); // Prázdná mapa při chybě
                    priceForTrackingNoVat = BigDecimal.ZERO;
                    priceForTrackingWithVat = BigDecimal.ZERO;
                    finalPriceForSchema = BigDecimal.ZERO; // Nastavení fallbacku i zde
                }
            }
            // else { // customisable == true but configurator == null -> ceny zůstanou 0 }

            // Naplnění a logování trackingData
            trackingData.put("priceForTrackingNoVat", priceForTrackingNoVat);
            trackingData.put("priceForTrackingWithVat", priceForTrackingWithVat);
            model.addAttribute("trackingData", trackingData);
            logger.debug("Prepared tracking data for view_item: {}", trackingData); // Log až po naplnění

            String viewItemDataJson = prepareViewItemJson(product, currentCurrency, priceForTrackingNoVat, priceForTrackingWithVat);
            model.addAttribute("viewItemDataJson", viewItemDataJson);
            // Generování JSON-LD dat
            try {
                List<String> imageUrls = Collections.emptyList();
                if (product.getImages() != null && !product.getImages().isEmpty()) {
                    String baseUrlStr = request.getScheme() + "://" + request.getServerName() + (request.getServerPort() == 80 || request.getServerPort() == 443 ? "" : ":" + request.getServerPort()) + request.getContextPath();
                    logger.debug("Base URL for JSON-LD image paths: {}", baseUrlStr);
                    imageUrls = product.getImagesOrdered().stream()
                            .map(img -> {
                                String imageUrl = img.getUrl();
                                if (imageUrl != null && !imageUrl.toLowerCase().startsWith("http")) {
                                    // Zajistíme, aby tam nebylo dvojité lomítko
                                    String path = imageUrl.startsWith("/") ? imageUrl.substring(1) : imageUrl;
                                    return baseUrlStr + "/" + path;
                                }
                                return imageUrl;
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    logger.debug("Image URLs for JSON-LD: {}", imageUrls);
                }

                Map<String, Object> jsonLdMap = new LinkedHashMap<>();
                jsonLdMap.put("@context", "https://schema.org/");
                jsonLdMap.put("@type", "Product");
                jsonLdMap.put("name", product.getName());
                String schemaDescription = StringUtils.hasText(product.getShortDescription())
                        ? product.getShortDescription()
                        : abbreviate(product.getDescription());
                jsonLdMap.put("description", schemaDescription);
                if (!imageUrls.isEmpty()) { jsonLdMap.put("image", imageUrls); }
                jsonLdMap.put("sku", (product.isCustomisable() ? "CUSTOM-" : "STD-") + product.getId());

                Map<String, Object> brandMap = new LinkedHashMap<>();
                brandMap.put("@type", "Brand");
                brandMap.put("name", "Dřevníky Kolář");
                jsonLdMap.put("brand", brandMap);

                Map<String, Object> offersMap = new LinkedHashMap<>();
                offersMap.put("@type", "Offer");
                offersMap.put("url", request.getRequestURL().toString());

                // Použijeme finalPriceForSchema, která je už bez DPH a zaokrouhlená
                Map<String, Object> priceSpecMap = new LinkedHashMap<>();
                priceSpecMap.put("@type", "UnitPriceSpecification");
                priceSpecMap.put("price", finalPriceForSchema.setScale(PRICE_SCALE, ROUNDING_MODE));
                priceSpecMap.put("priceCurrency", currentCurrency);
                priceSpecMap.put("valueAddedTaxIncluded", false); // Explicitně false
                offersMap.put("priceSpecification", priceSpecMap);

                offersMap.put("availability", "https://schema.org/InStock");
                offersMap.put("itemCondition", "https://schema.org/NewCondition");
                jsonLdMap.put("offers", offersMap);

                List<Map<String, Object>> additionalProperties = new ArrayList<>();
                additionalProperties.add(Map.of("@type", "PropertyValue", "name", "Materiál", "value", StringUtils.hasText(product.getMaterial()) ? product.getMaterial() : "Smrkové dřevo"));
                additionalProperties.add(Map.of("@type", "PropertyValue", "name", "Konfigurace", "value", product.isCustomisable() ? "Na míru dle zákazníka" : "Standardní"));
                additionalProperties.add(Map.of("@type", "PropertyValue", "name", "Montáž zdarma", "value", "Ano"));
                additionalProperties.add(Map.of("@type", "PropertyValue", "name", "Dodání do", "value", "5 týdnů"));
                jsonLdMap.put("additionalProperty", additionalProperties);

                String jsonLdString = objectMapper.writeValueAsString(jsonLdMap);
                model.addAttribute("jsonLdDataString", jsonLdString);
                logger.debug("JSON-LD data vygenerována a přidána do modelu.");

            } catch (Exception e) {
                logger.error("!!! Chyba při generování JSON-LD dat: {} !!!", e.getMessage(), e);
                model.addAttribute("jsonLdDataString", "{}"); // Prázdný JSON objekt při chybě
            }
            // --- Konec generování JSON-LD ---

            // --- Zpracování sazeb DPH ---
            Set<TaxRate> availableTaxRates = product.getAvailableTaxRates();
            if (availableTaxRates != null && !availableTaxRates.isEmpty()) {
                List<TaxRate> sortedTaxRates = availableTaxRates.stream()
                        .sorted(Comparator.comparing(TaxRate::getName))
                        .collect(Collectors.toList());
                model.addAttribute("availableTaxRates", sortedTaxRates);
                logger.debug("DEBUG: Přidány dostupné sazby DPH do modelu: {}", sortedTaxRates.stream().map(TaxRate::getId).toList());
            } else {
                logger.warn("[ProductController] Produkt ID {} nemá žádné daňové sazby!", product.getId());
                model.addAttribute("productError", "Produkt nelze objednat, chybí daňové sazby.");
            }
            // --- Konec zpracování DPH ---

            // --- Načtení dostupných atributů ---
            Set<Design> designs = product.getAvailableDesigns();
            Set<Glaze> glazes = product.getAvailableGlazes();
            Set<RoofColor> roofColors = product.getAvailableRoofColors();
            model.addAttribute("availableDesigns", designs != null ? designs : Collections.emptySet());
            model.addAttribute("availableGlazes", glazes != null ? glazes : Collections.emptySet());
            model.addAttribute("availableRoofColors", roofColors != null ? roofColors : Collections.emptySet());
            logger.debug("Available designs: {}, glazes: {}, roofColors: {}",
                    designs != null ? designs.size() : 0,
                    glazes != null ? glazes.size() : 0,
                    roofColors != null ? roofColors.size() : 0);
            // --- Konec načtení atributů ---

            // Přidání DTO formuláře do modelu pro oba typy produktů
            model.addAttribute("cartItemDto", cartItemDto);

            // Rozlišení view podle typu produktu
            if (product.isCustomisable()) {
                // --- Další logika pro custom produkt ---
                if (productConfiguratorDto != null) {
                    // --- Zpracování doplňků ---
                    Set<Addon> activeAddons = product.getAvailableAddons() != null ?
                            product.getAvailableAddons().stream().filter(Addon::isActive).collect(Collectors.toSet())
                            : Collections.emptySet();
                    Map<String, List<Addon>> groupedAddons = activeAddons.stream()
                            .sorted(Comparator.comparing(Addon::getName))
                            .collect(Collectors.groupingBy(Addon::getCategory, TreeMap::new, Collectors.toList()));
                    model.addAttribute("groupedAddons", groupedAddons);
                    logger.debug("[ProductController] Grouped addons: {}", groupedAddons.keySet());
                    // --- Konec zpracování doplňků ---
                } else {
                    logger.error("!!! [ProductController] Custom produkt ID {} nemá konfigurátor (DTO je null), ale logika pokračovala? !!!", product.getId());
                    model.addAttribute("configuratorError", "Chybí data konfigurátoru.");
                    model.addAttribute("productError", "Produkt nelze nakonfigurovat.");
                }
                logger.info(">>> [ProductController] Opouštím productDetail (CUSTOM). Vracím 'produkt-detail-custom'. Model keys: {}", model.asMap().keySet());
                return "produkt-detail-custom";

            } else {
                // --- Standardní produkt (další logika není potřeba) ---
                logger.info(">>> [ProductController] Opouštím productDetail (STANDARD). Vracím 'produkt-detail-standard'. Model keys: {}", model.asMap().keySet());
                return "produkt-detail-standard";
            }
        } catch (ResponseStatusException e) {
            logger.warn("ResponseStatusException v productDetail: {}", e.getMessage());
            throw e; // Necháme projít, aby se zobrazila chybová stránka (např. 404)
        } catch (Exception e) {
            logger.error("!!! [ProductController] Neočekávaná chyba v productDetail pro slug {}: {} !!!", slug, e.getMessage(), e);
            model.addAttribute("errorMessage", "Při načítání detailu produktu došlo k neočekávané chybě.");
            return "error/500"; // Vrátíme obecnou chybovou stránku
        }
    }
    private String prepareViewItemJson(Product product, String currency, BigDecimal priceNoVat, BigDecimal priceWithVat) {
        Map<String, Object> viewItemDataMap = new HashMap<>();
        viewItemDataMap.put("item_name", product.getName());
        viewItemDataMap.put("price", priceNoVat.setScale(2, RoundingMode.HALF_UP)); // Zaokrouhlení
        viewItemDataMap.put("currency", currency);
        viewItemDataMap.put("item_brand", BRAND_NAME);
        viewItemDataMap.put("item_category", "Dřevníky");
        // Případně přidat další data dle potřeby

        try {
            // Struktura pro GA4 view_item (obsahuje pole 'items')
            Map<String, Object> ecommerceData = new HashMap<>();
            ecommerceData.put("currency", currency);
            ecommerceData.put("value", priceNoVat.setScale(2, RoundingMode.HALF_UP)); // Celková hodnota zobrazené položky
            ecommerceData.put("items", List.of(viewItemDataMap)); // Vložíme mapu produktu jako jedinou položku v poli

            // Celý objekt pro dataLayer
            Map<String, Object> dataLayerPush = new HashMap<>();
            dataLayerPush.put("event", "view_item");
            dataLayerPush.put("ecommerce", ecommerceData);

            String json = objectMapper.writeValueAsString(dataLayerPush);
            logger.debug("Prepared viewItemDataJson for product ID {}: {}", product.getId(), json);
            return json;
        } catch (JsonProcessingException e) {
            logger.error("!!! Failed to serialize viewItemDataMap to JSON for product ID {}: {}", product.getId(), e.getMessage());
            return "{\"event\":\"view_item\", \"ecommerce\": null, \"error\": \"Serialization failed\"}"; // Vrátí platný, ale chybový JSON
        }
    }

    // Pomocná metoda pro zkrácení textu (přidána na konec třídy, pokud již neexistuje)
    private String abbreviate(String text) {
        if (text == null) {
            return ""; // Vrátit prázdný řetězec pro null
        }
        int maxLength = 250; // Max délka pro popis v JSON-LD
        if (text.length() <= maxLength) {
            return text;
        }
        // Jednoduché zkrácení, Apache Commons Lang StringUtils.abbreviate je robustnější
        return text.substring(0, maxLength - 3) + "...";
    }


    @PostMapping("/api/product/calculate-price")
    @ResponseBody
    public ResponseEntity<CustomPriceResponseDto> calculateCustomPrice(@RequestBody CustomPriceRequestDto requestDto) { // Nyní přijímáme nové DTO
        logger.info(">>> [ProductController] Vstupuji do API calculateCustomPrice (detailed). Product ID: {}", requestDto.getProductId());
        String dimensionsLog = requestDto.getCustomDimensions() != null ? requestDto.getCustomDimensions().toString() : "null";
        String addonIdsLog = requestDto.getSelectedAddonIds() != null ? requestDto.getSelectedAddonIds().toString() : "[]";
        logger.debug("[ProductController] Calc request data: ProductId={}, Dimensions={}, DesignId={}, GlazeId={}, RoofColorId={}, AddonIds={}",
                requestDto.getProductId(), dimensionsLog, requestDto.getSelectedDesignId(), requestDto.getSelectedGlazeId(),
                requestDto.getSelectedRoofColorId(), addonIdsLog);

        try {
            // Voláme novou servisní metodu, která vrací detailní DTO
            CustomPriceResponseDto responseDto = productService.calculateDetailedCustomPrice(requestDto);

            if (responseDto.getErrorMessage() != null) {
                // Pokud servisní metoda vrátila chybu, vrátíme BadRequest
                logger.warn("[ProductController] API Calc: Servisní vrstva vrátila chybu: {}", responseDto.getErrorMessage());
                // Zde bychom mohli rozlišit typ chyby (např. 404 pokud EntityNotFound), ale pro jednoduchost vracíme 400
                return ResponseEntity.badRequest().body(responseDto);
            }

            logger.info(">>> [ProductController] API calculateCustomPrice (detailed) úspěšně spočítána. Product ID {}: Total CZK={}, Total EUR={}. Vracím OK.",
                    requestDto.getProductId(), responseDto.getTotalPriceCZK(), responseDto.getTotalPriceEUR());
            return ResponseEntity.ok(responseDto);

        } catch (Exception e) {
            // Zachycení neočekávaných chyb, které nebyly ošetřeny v service vrstvě
            logger.error("!!! [ProductController] API Calc (detailed): Neočekávaná chyba: {} !!!", e.getMessage(), e);
            CustomPriceResponseDto errorResponse = new CustomPriceResponseDto();
            errorResponse.setErrorMessage("Došlo k neočekávané systémové chybě při výpočtu ceny.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}