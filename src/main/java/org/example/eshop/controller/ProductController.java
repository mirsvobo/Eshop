package org.example.eshop.controller;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.example.eshop.config.PriceConstants.*;

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

            model.addAttribute("product", product); // Přidáváme celou entitu pro ostatní části šablony

            // Příprava DTO pro formulář košíku
            CartItemDto cartItemDto = new CartItemDto();
            cartItemDto.setProductId(product.getId());
            cartItemDto.setCustom(product.isCustomisable());

            // Výpočet počáteční/minimální ceny pro JSON-LD a zobrazení
            BigDecimal initialCustomPriceCZK = null;
            BigDecimal initialCustomPriceEUR = null;
            BigDecimal finalPriceForSchema = null; // Cena pro JSON-LD
            String initialPriceError = null;
            Map<String, Object> standardPriceInfo = null;
            ProductConfiguratorDto productConfiguratorDto = null; // DTO pro JavaScript

            if (product.isCustomisable() && product.getConfigurator() != null) {
                // --- Custom Produkt ---
                ProductConfigurator configurator = product.getConfigurator(); // Získáme entitu konfigurátoru
                productConfiguratorDto = new ProductConfiguratorDto(); // Vytvoříme DTO

                try {
                    // Mapování dat z entity konfigurátoru do DTO
                    productConfiguratorDto.setMinLength(configurator.getMinLength());
                    productConfiguratorDto.setMaxLength(configurator.getMaxLength());
                    productConfiguratorDto.setStepLength(configurator.getStepLength());
                    productConfiguratorDto.setDefaultLength(configurator.getDefaultLength());
                    productConfiguratorDto.setMinWidth(configurator.getMinWidth());
                    productConfiguratorDto.setMaxWidth(configurator.getMaxWidth());
                    productConfiguratorDto.setStepWidth(configurator.getStepWidth());
                    productConfiguratorDto.setDefaultWidth(configurator.getDefaultWidth());
                    productConfiguratorDto.setMinHeight(configurator.getMinHeight());
                    productConfiguratorDto.setMaxHeight(configurator.getMaxHeight());
                    productConfiguratorDto.setStepHeight(configurator.getStepHeight());
                    productConfiguratorDto.setDefaultHeight(configurator.getDefaultHeight());

                    Map<String, BigDecimal> minDimensions = new HashMap<>();
                    // Výchozí rozměry bereme z konfigurátoru, pokud nejsou, bereme min
                    minDimensions.put("length", configurator.getDefaultLength() != null ? configurator.getDefaultLength() : configurator.getMinLength());
                    minDimensions.put("width", configurator.getDefaultWidth() != null ? configurator.getDefaultWidth() : configurator.getMinWidth());
                    minDimensions.put("height", configurator.getDefaultHeight() != null ? configurator.getDefaultHeight() : configurator.getMinHeight());

                    if (minDimensions.get("length") == null || minDimensions.get("width") == null || minDimensions.get("height") == null) {
                        throw new IllegalStateException("Chybí minimální nebo výchozí rozměry v konfigurátoru produktu ID: " + product.getId());
                    }

                    // Počítáme ceny pro obě měny
                    initialCustomPriceCZK = productService.calculateDynamicProductPrice(product, minDimensions, "CZK");
                    initialCustomPriceEUR = productService.calculateDynamicProductPrice(product, minDimensions, "EUR");

                    // Nastavení výchozích rozměrů do DTO pro formulář
                    cartItemDto.setCustomDimensions(minDimensions);
                    logger.debug("[ProductController] Výchozí ZÁKLADNÍ cena (bez doplňků/atributů) pro custom produkt ID {}: CZK={}, EUR={}", product.getId(), initialCustomPriceCZK, initialCustomPriceEUR);

                    // Cena pro schema bude cena v aktuální měně
                    finalPriceForSchema = EURO_CURRENCY.equals(currentCurrency) ? initialCustomPriceEUR : initialCustomPriceCZK;

                } catch (Exception e) {
                    logger.error("[ProductController] Chyba při výpočtu výchozí základní ceny pro custom produkt ID {}: {}", product.getId(), e.getMessage(), e);
                    initialPriceError = "Nepodařilo se vypočítat výchozí cenu: " + e.getMessage();
                    finalPriceForSchema = BigDecimal.ZERO; // Fallback pro schema
                }
                model.addAttribute("initialCustomPriceCZK", initialCustomPriceCZK);
                model.addAttribute("initialCustomPriceEUR", initialCustomPriceEUR);
                model.addAttribute("initialCustomPriceError", initialPriceError);

            } else if (!product.isCustomisable()) {
                // --- Standardní produkt ---
                try {
                    standardPriceInfo = productService.calculateFinalProductPrice(product, currentCurrency);
                    model.addAttribute("priceInfo", standardPriceInfo);
                    // Cena pro schema je konečná (zlevněná nebo původní)
                    finalPriceForSchema = (BigDecimal) standardPriceInfo.getOrDefault("discountedPrice", standardPriceInfo.get("originalPrice"));
                    if (finalPriceForSchema == null) {
                        finalPriceForSchema = BigDecimal.ZERO; // Fallback pro schema
                        logger.warn("Missing price for standard product ID {} in schema calculation.", product.getId());
                    }
                } catch (Exception e) {
                    logger.error("[ProductController] Chyba při výpočtu ceny pro standardní produkt ID {}: {}", product.getId(), e.getMessage());
                    model.addAttribute("priceInfo", Collections.emptyMap()); // Prázdná mapa při chybě
                    finalPriceForSchema = BigDecimal.ZERO; // Fallback pro schema
                }
            } else {
                finalPriceForSchema = BigDecimal.ZERO; // Fallback pro schema, pokud je custom bez konfigurátoru
            }

            // --- Generování JSON-LD dat ---
            try {
                List<String> imageUrls = Collections.emptyList();
                if (product.getImages() != null && !product.getImages().isEmpty()) {
                    String baseUrl = request.getScheme() + "://" + request.getServerName() + (request.getServerPort() == 80 || request.getServerPort() == 443 ? "" : ":" + request.getServerPort()) + request.getContextPath();
                    logger.debug("Base URL pro obrázky JSON-LD: {}", baseUrl);
                    imageUrls = product.getImagesOrdered().stream()
                            .map(img -> {
                                String imageUrl = img.getUrl();
                                if (imageUrl != null && !imageUrl.toLowerCase().startsWith("http")) {
                                    return baseUrl + (imageUrl.startsWith("/") ? imageUrl : "/" + imageUrl);
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

                Map<String, Object> priceSpecMap = new LinkedHashMap<>();
                priceSpecMap.put("@type", "UnitPriceSpecification");
                priceSpecMap.put("price", finalPriceForSchema.setScale(PRICE_SCALE, ROUNDING_MODE));
                priceSpecMap.put("priceCurrency", currentCurrency);
                priceSpecMap.put("valueAddedTaxIncluded", false);
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
                model.addAttribute("jsonLdDataString", "{}");
            }
            // --- Konec generování JSON-LD ---

            // --- Zpracování sazeb DPH ---
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

            // Rozlišení podle typu produktu
            if (product.isCustomisable()) {
                // --- Custom Produkt (další logika) ---
                logger.info("[ProductController] Zpracovávám detail pro CUSTOM produkt ID {}", product.getId());
                if (productConfiguratorDto != null) { // Kontrolujeme DTO
                    model.addAttribute("configuratorDto", productConfiguratorDto); // Přidáme DTO do modelu

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
                    logger.error("!!! [ProductController] Custom produkt ID {} nemá konfigurátor (DTO je null) !!!", product.getId());
                    model.addAttribute("configuratorError", "Chybí data konfigurátoru.");
                    model.addAttribute("productError", "Produkt nelze nakonfigurovat.");
                }
                model.addAttribute("cartItemDto", cartItemDto); // Přidání DTO formuláře do modelu
                logger.info(">>> [ProductController] Opouštím productDetail (CUSTOM). Vracím 'produkt-detail-custom'. Model keys: {}", model.asMap().keySet());
                return "produkt-detail-custom";

            } else {
                // --- Standardní produkt (další logika) ---
                logger.info("[ProductController] Zpracovávám detail pro STANDARD produkt ID {}", product.getId());
                model.addAttribute("cartItemDto", cartItemDto); // Přidání DTO formuláře do modelu
                logger.info(">>> [ProductController] Opouštím productDetail (STANDARD). Vracím 'produkt-detail-standard'. Model keys: {}", model.asMap().keySet());
                return "produkt-detail-standard";
            }
        } catch (ResponseStatusException e) {
            logger.warn("ResponseStatusException v productDetail: {}", e.getMessage());
            throw e; // Necháme projít, aby se zobrazila chybová stránka (např. 404)
        } catch (Exception e) {
            logger.error("!!! [ProductController] Neočekávaná chyba v productDetail pro slug {}: {} !!!", slug, e.getMessage(), e);
            model.addAttribute("errorMessage", "Při načítání detailu produktu došlo k neočekávané chybě.");
            // Můžeme vrátit obecnou chybovou stránku nebo přesměrovat
            return "error/500"; // Například, pokud máš šablonu pro obecné chyby serveru
            // return "redirect:/produkty?error=detail_unexpected"; // Nebo přesměrování
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