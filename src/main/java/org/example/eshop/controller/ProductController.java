package org.example.eshop.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.example.eshop.admin.service.AddonsService;
import org.example.eshop.model.*; // Import všech modelů
import org.example.eshop.service.CurrencyService;
import org.example.eshop.service.ProductService;
import org.example.eshop.dto.CartItemDto;
import org.example.eshop.dto.CustomPriceRequestDto;
import org.example.eshop.dto.CustomPriceResponseDto;
import org.hibernate.Hibernate; // Stále potřeba pro LAZY kolekce
import org.slf4j.Logger; // <-- PŘIDÁNO
import org.slf4j.LoggerFactory; // <-- PŘIDÁNO
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional; // Stále potřeba
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*; // Import pro Set, Map atd.
import java.util.stream.Collectors;

import static org.example.eshop.config.PriceConstants.EURO_CURRENCY;

@Controller
public class ProductController {

    // private static final Logger log = LoggerFactory.getLogger(ProductController.class); // Původní logger
    private static final Logger logger = LoggerFactory.getLogger(ProductController.class); // <-- PŘEJMENOVÁNO NA logger pro konzistenci

    @Autowired ObjectMapper objectMapper;
    @Autowired
    CurrencyService currencyService;
    @Autowired
    private ProductService productService;
    @Autowired
    private AddonsService addonsService; // Předpokládám, že je stále potřeba pro detail

    @GetMapping("/produkty")
    @Transactional(readOnly = true)
    public String listProducts(Model model, org.springframework.data.domain.Pageable pageable) { // Explicitní Pageable
        logger.info(">>> [ProductController] Vstupuji do listProducts. Pageable: {}", pageable);
        String currentCurrency = currencyService.getSelectedCurrency();
        model.addAttribute("currentCurrency", currentCurrency);

        try {
            org.springframework.data.domain.Page<Product> productPage = productService.getActiveProducts(pageable);
            logger.info("[ProductController] ProductService.getActiveProducts vrátil stránku: TotalElements={}, TotalPages={}, Number={}, Size={}",
                    productPage.getTotalElements(), productPage.getTotalPages(), productPage.getNumber(), productPage.getSize());

            Map<Long, Map<String, Object>> productPrices = new HashMap<>();
            for (Product product : productPage.getContent()) {
                if (!product.isCustomisable()) {
                    Map<String, Object> priceInfo = productService.calculateFinalProductPrice(product, currentCurrency);
                    productPrices.put(product.getId(), priceInfo);
                } else {
                    // Zde můžeme načíst výchozí cenu i pro custom produkty pro zobrazení "Cena od..."
                    // Ale vyžadovalo by to výpočet s min. rozměry pro každý produkt na stránce
                    // Prozatím necháme ceny jen pro standardní produkty
                }
            }

            model.addAttribute("productPage", productPage);
            model.addAttribute("productPrices", productPrices);

        } catch (Exception e) {
            logger.error("!!! [ProductController] Chyba v listProducts: {} !!!", e.getMessage(), e);
            model.addAttribute("errorMessage", "Nepodařilo se načíst produkty.");
            model.addAttribute("productPage", org.springframework.data.domain.Page.empty(pageable));
            model.addAttribute("productPrices", Collections.emptyMap());
        }
        logger.info(">>> [ProductController] Opouštím listProducts. Obsah modelu před vrácením 'produkty': {}", model.asMap().keySet());
        return "produkty";
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

            model.addAttribute("product", product);

            // Příprava DTO pro formulář košíku
            CartItemDto cartItemDto = new CartItemDto();
            cartItemDto.setProductId(product.getId());
            cartItemDto.setCustom(product.isCustomisable());

            // Výpočet počáteční/minimální ceny (potřebné pro JSON-LD i model)
            BigDecimal initialCustomPriceCZK = BigDecimal.ZERO;
            BigDecimal initialCustomPriceEUR = BigDecimal.ZERO;
            String initialPriceError = null;
            if (product.isCustomisable() && product.getConfigurator() != null) {
                try {
                    ProductConfigurator config = product.getConfigurator();
                    Map<String, BigDecimal> minDimensions = new HashMap<>();
                    // Výchozí rozměry bereme z konfigurátoru, pokud nejsou, bereme min
                    minDimensions.put("length", config.getDefaultLength() != null ? config.getDefaultLength() : config.getMinLength());
                    minDimensions.put("width", config.getDefaultWidth() != null ? config.getDefaultWidth() : config.getMinWidth());
                    minDimensions.put("height", config.getDefaultHeight() != null ? config.getDefaultHeight() : config.getMinHeight());

                    // Zvalidujme, jestli minimální rozměry nejsou null
                    if (minDimensions.get("length") == null || minDimensions.get("width") == null || minDimensions.get("height") == null) {
                        throw new IllegalStateException("Chybí minimální nebo výchozí rozměry v konfigurátoru produktu ID: " + product.getId());
                    }

                    initialCustomPriceCZK = productService.calculateDynamicProductPrice(product, minDimensions, "CZK");
                    initialCustomPriceEUR = productService.calculateDynamicProductPrice(product, minDimensions, "EUR");

                    // Nastavení výchozích rozměrů do DTO pro formulář
                    cartItemDto.setCustomDimensions(minDimensions);
                    logger.debug("[ProductController] Výchozí ZÁKLADNÍ cena (bez doplňků/atributů) pro custom produkt ID {}: CZK={}, EUR={}", product.getId(), initialCustomPriceCZK, initialCustomPriceEUR);

                } catch (Exception e) {
                    logger.error("[ProductController] Chyba při výpočtu výchozí základní ceny pro custom produkt ID {}: {}", product.getId(), e.getMessage(), e);
                    initialPriceError = "Nepodařilo se vypočítat výchozí cenu: " + e.getMessage();
                }
            }
            // Přidání počátečních cen do modelu pro Thymeleaf (např. pro zobrazení ceny)
            model.addAttribute("initialCustomPriceCZK", initialCustomPriceCZK);
            model.addAttribute("initialCustomPriceEUR", initialCustomPriceEUR);
            model.addAttribute("initialCustomPriceError", initialPriceError);


            // --- Generování JSON-LD dat ---
            try {
                List<String> imageUrls = Collections.emptyList();
                if (product.getImages() != null && !product.getImages().isEmpty()) {
                    String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();
                    logger.debug("Base URL pro obrázky JSON-LD: {}", baseUrl);
                    imageUrls = product.getImages().stream()
                            .sorted(Comparator.comparing(Image::getDisplayOrder, Comparator.nullsLast(Integer::compareTo))
                                    .thenComparing(Image::getId, Comparator.nullsLast(Long::compareTo)))
                            .map(img -> {
                                String imageUrl = img.getUrl();
                                if (imageUrl != null && !imageUrl.startsWith("http")) {
                                    return baseUrl + (imageUrl.startsWith("/") ? imageUrl : "/" + imageUrl);
                                }
                                return imageUrl;
                            })
                            .filter(Objects::nonNull) // Odstraní null hodnoty
                            .collect(Collectors.toList());
                }

                Map<String, Object> jsonLdMap = new LinkedHashMap<>();
                jsonLdMap.put("@context", "https://schema.org/");
                jsonLdMap.put("@type", "Product");
                jsonLdMap.put("name", product.getName());
                jsonLdMap.put("description", product.getShortDescription() != null ? product.getShortDescription() : abbreviate(product.getDescription(), 250));
                jsonLdMap.put("image", imageUrls);
                jsonLdMap.put("sku", (product.isCustomisable() ? "CUSTOM-" : "STD-") + product.getId()); // Rozlišení SKU

                Map<String, Object> brandMap = new LinkedHashMap<>();
                brandMap.put("@type", "Brand");
                brandMap.put("name", "Dřevníky Kolář");
                jsonLdMap.put("brand", brandMap);

                Map<String, Object> offersMap = new LinkedHashMap<>();
                offersMap.put("@type", "Offer");
                offersMap.put("url", request.getRequestURL().toString());

                Map<String, Object> priceSpecMap = new LinkedHashMap<>();
                priceSpecMap.put("@type", "UnitPriceSpecification");
                // Pro standardní produkt vezmeme základní cenu, pro custom počáteční
                BigDecimal offerPrice;
                if (product.isCustomisable()) {
                    offerPrice = EURO_CURRENCY.equals(currentCurrency) ? initialCustomPriceEUR : initialCustomPriceCZK;
                    priceSpecMap.put("priceType", "StartingPrice"); // U custom je to cena "od"
                } else {
                    Map<String, Object> standardPriceInfo = productService.calculateFinalProductPrice(product, currentCurrency);
                    offerPrice = (BigDecimal) standardPriceInfo.getOrDefault("discountedPrice", standardPriceInfo.get("originalPrice"));
                    // priceType zde můžeme nechat, nebo specifikovat jinak
                }
                priceSpecMap.put("price", offerPrice != null ? offerPrice : BigDecimal.ZERO); // Zajistíme, že cena není null
                priceSpecMap.put("priceCurrency", currentCurrency);
                priceSpecMap.put("valueAddedTaxIncluded", false); // Cena je bez DPH
                offersMap.put("priceSpecification", priceSpecMap);

                offersMap.put("availability", "https://schema.org/InStock"); // Nebo jiný stav dostupnosti
                offersMap.put("itemCondition", "https://schema.org/NewCondition");
                jsonLdMap.put("offers", offersMap);

                List<Map<String, Object>> additionalProperties = new ArrayList<>();
                additionalProperties.add(Map.of("@type", "PropertyValue", "name", "Materiál", "value", product.getMaterial() != null ? product.getMaterial() : "Smrkové dřevo"));
                additionalProperties.add(Map.of("@type", "PropertyValue", "name", "Konfigurace", "value", product.isCustomisable() ? "Na míru dle zákazníka" : "Standardní"));
                additionalProperties.add(Map.of("@type", "PropertyValue", "name", "Montáž zdarma", "value", "Ano")); // Může být konfigurovatelné?
                additionalProperties.add(Map.of("@type", "PropertyValue", "name", "Dodání do", "value", "5 týdnů")); // Může být konfigurovatelné?
                jsonLdMap.put("additionalProperty", additionalProperties);

                // Převod Map na JSON String
                String jsonLdString = objectMapper.writeValueAsString(jsonLdMap);
                model.addAttribute("jsonLdData", jsonLdString);
                logger.debug("JSON-LD data vygenerována a přidána do modelu.");

            } catch (Exception e) {
                logger.error("!!! Chyba při generování JSON-LD dat: {} !!!", e.getMessage(), e);
                model.addAttribute("jsonLdData", "{}"); // Přidat prázdný objekt v případě chyby
            }
            // --- Konec generování JSON-LD ---


            // Zpracování společných věcí pro oba typy produktů (např. sazby DPH)
            Set<TaxRate> availableTaxRates = product.getAvailableTaxRates();
            logger.debug("DEBUG: Načtené availableTaxRates pro produkt ID {}: {}", product.getId(), availableTaxRates != null ? availableTaxRates.size() : "null");
            if (availableTaxRates != null && !availableTaxRates.isEmpty()) {
                List<TaxRate> sortedTaxRates = availableTaxRates.stream()
                        .sorted(Comparator.comparing(TaxRate::getName))
                        .collect(Collectors.toList());
                logger.debug("DEBUG: Přidávám sortedTaxRates do modelu, velikost: {}", sortedTaxRates.size());
                model.addAttribute("availableTaxRates", sortedTaxRates);
            } else {
                logger.warn("[ProductController] Produkt ID {} nemá žádné daňové sazby!", product.getId());
                model.addAttribute("productError", "Produkt nelze objednat, chybí daňové sazby.");
            }

            // Načtení dostupných Designů, Lazur, Barev střech pro select boxy
            // (Potřebujeme je pro custom i standard, pokud je custom má také vybírat)
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


            // Rozlišení podle typu produktu
            if (product.isCustomisable()) {
                // --- Custom Produkt ---
                logger.info("[ProductController] Zpracovávám detail pro CUSTOM produkt ID {}", product.getId());
                ProductConfigurator configurator = product.getConfigurator();
                if (configurator != null) {
                    model.addAttribute("configurator", configurator);
                    // Zpracování doplňků
                    Set<Addon> activeAddons = product.getAvailableAddons() != null ?
                            product.getAvailableAddons().stream().filter(Addon::isActive).collect(Collectors.toSet())
                            : Collections.emptySet();
                    Map<String, List<Addon>> groupedAddons = activeAddons.stream()
                            .sorted(Comparator.comparing(Addon::getName))
                            .collect(Collectors.groupingBy(Addon::getCategory, TreeMap::new, Collectors.toList()));
                    model.addAttribute("groupedAddons", groupedAddons);
                    logger.debug("[ProductController] Grouped addons: {}", groupedAddons.keySet());
                } else {
                    logger.error("!!! [ProductController] Custom produkt ID {} nemá konfigurátor !!!", product.getId());
                    model.addAttribute("configuratorError", "Chybí data konfigurátoru.");
                    model.addAttribute("productError", "Produkt nelze nakonfigurovat."); // Přidáno
                }
                model.addAttribute("cartItemDto", cartItemDto); // Přidání DTO do modelu
                logger.info(">>> [ProductController] Opouštím productDetail (CUSTOM). Vracím 'produkt-detail-custom'. Model keys: {}", model.asMap().keySet());
                return "produkt-detail-custom";

            } else {
                // --- Standardní produkt ---
                logger.info("[ProductController] Zpracovávám detail pro STANDARD produkt ID {}", product.getId());
                Map<String, Object> priceInfo = productService.calculateFinalProductPrice(product, currentCurrency);
                model.addAttribute("priceInfo", priceInfo);
                // Dostupne atributy jsou již načteny výše a přidány do modelu
                model.addAttribute("cartItemDto", cartItemDto); // Přidání DTO do modelu
                logger.info(">>> [ProductController] Opouštím productDetail (STANDARD). Vracím 'produkt-detail-standard'. Model keys: {}", model.asMap().keySet());
                return "produkt-detail-standard";
            }
        } catch (ResponseStatusException e) {
            // Pokud produkt nebyl nalezen, vyvolá se tato výjimka
            logger.warn("ResponseStatusException v productDetail: {}", e.getMessage());
            throw e; // Necháme Spring, ať zobrazí chybovou stránku (např. 404)
        } catch (Exception e) {
            // Zachycení všech ostatních neočekávaných chyb
            logger.error("!!! [ProductController] Neočekávaná chyba v productDetail pro slug {}: {} !!!", slug, e.getMessage(), e);
            model.addAttribute("errorMessage", "Při načítání detailu produktu došlo k neočekávané chybě.");
            // Můžeme přesměrovat na obecnou chybovou stránku nebo zpět na seznam produktů
            return "redirect:/produkty?error=detail_unexpected";
        }
    }
    private String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
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