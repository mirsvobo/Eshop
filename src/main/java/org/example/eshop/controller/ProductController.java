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
        model.addAttribute("brandName", BRAND_NAME);
        logger.info(">>> [ProductController] Vstupuji do productDetail. Slug: {}", slug);
        String currentCurrency = currencyService.getSelectedCurrency();
        model.addAttribute("currentGlobalCurrency", currentCurrency);

        Product product;
        try {
            product = productService.getActiveProductBySlug(slug)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produkt '" + slug + "' nebyl nalezen nebo není aktivní."));
            model.addAttribute("product", product);

            CartItemDto cartItemDto = new CartItemDto();
            cartItemDto.setProductId(product.getId());
            cartItemDto.setCustom(product.isCustomisable());

            BigDecimal priceForEvent = BigDecimal.ZERO;
            ProductConfiguratorDto productConfiguratorDto = null;
            Map<String, Object> priceInfoForPage = null;

            if (product.isCustomisable() && product.getConfigurator() != null) {
                logger.info("[ProductController] Processing Custom Product ID: {}", product.getId());
                ProductConfigurator config = product.getConfigurator();
                productConfiguratorDto = new ProductConfiguratorDto();
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

                Map<String, BigDecimal> initialDimensions = new HashMap<>();
                initialDimensions.put("length", Optional.ofNullable(config.getDefaultLength()).orElse(config.getMinLength()));
                initialDimensions.put("width", Optional.ofNullable(config.getDefaultWidth()).orElse(config.getMinWidth()));
                initialDimensions.put("height", Optional.ofNullable(config.getDefaultHeight()).orElse(config.getMinHeight()));
                cartItemDto.setCustomDimensions(initialDimensions);

                try {
                    String configuratorJsonOutput = objectMapper.writeValueAsString(productConfiguratorDto);
                    model.addAttribute("configuratorDtoJsonString", configuratorJsonOutput); // Správný název pro Thymeleaf
                    logger.debug("[ProductController] Serialized productConfiguratorDto to JSON for JS: {}", configuratorJsonOutput);
                } catch (JsonProcessingException e) {
                    logger.error("[ProductController] Error serializing productConfiguratorDto to JSON for product ID {}: {}", product.getId(), e.getMessage(), e);
                    model.addAttribute("configuratorDtoJsonString", "{}"); // Fallback na prázdný JSON objekt
                    model.addAttribute("configuratorError", "Chyba při přípravě dat konfigurátoru pro JS.");
                }

                try {
                    priceForEvent = productService.calculateDynamicProductPrice(product, initialDimensions, currentCurrency);
                    priceForEvent = Optional.ofNullable(priceForEvent).orElse(BigDecimal.ZERO);
                    model.addAttribute("initialCustomPriceCZK", "CZK".equals(currentCurrency) ? priceForEvent : productService.calculateDynamicProductPrice(product, initialDimensions, "CZK"));
                    model.addAttribute("initialCustomPriceEUR", "EUR".equals(currentCurrency) ? priceForEvent : productService.calculateDynamicProductPrice(product, initialDimensions, "EUR"));
                } catch (Exception e) {
                    logger.error("[ProductController] Error calculating initial price for custom product ID {}: {}", product.getId(), e.getMessage());
                    model.addAttribute("initialCustomPriceError", "Chyba výpočtu výchozí ceny.");
                }
            } else if (!product.isCustomisable()) {
                logger.info("[ProductController] Processing Standard Product ID: {}", product.getId());
                try {
                    priceInfoForPage = productService.calculateFinalProductPrice(product, currentCurrency);
                    model.addAttribute("priceInfo", priceInfoForPage);
                    logger.debug("[ProductController] Price Info Map from Service: {}", priceInfoForPage);

                    if (priceInfoForPage != null) {
                        Object discountedPriceObj = priceInfoForPage.get("discountedPrice");
                        Object originalPriceObj = priceInfoForPage.get("originalPrice");
                        Object priceToUseObj = (discountedPriceObj != null) ? discountedPriceObj : originalPriceObj;

                        if (priceToUseObj instanceof BigDecimal) {
                            priceForEvent = (BigDecimal) priceToUseObj;
                        } else if (priceToUseObj instanceof Number) {
                            priceForEvent = new BigDecimal(priceToUseObj.toString());
                        }
                        priceForEvent = Optional.ofNullable(priceForEvent).orElse(BigDecimal.ZERO);
                    }
                } catch (Exception e) {
                    logger.error("[ProductController] Error calculating price for standard product ID {}: {}", product.getId(), e.getMessage());
                    model.addAttribute("priceInfo", Collections.emptyMap());
                }
            }
            priceForEvent = priceForEvent.setScale(PRICE_SCALE, ROUNDING_MODE).max(BigDecimal.ZERO);

            Map<String, Object> ecommerceData = new HashMap<>();
            Map<String, Object> itemData = new HashMap<>();
            itemData.put("item_id", product.isCustomisable() ? "CUSTOM-" + product.getId() : "STD-" + product.getSlug());
            itemData.put("item_name", product.getName());
            itemData.put("item_brand", BRAND_NAME);
            itemData.put("item_category", "Dřevníky");
            itemData.put("price", priceForEvent);
            itemData.put("quantity", 1);

            ecommerceData.put("currency", currentCurrency);
            ecommerceData.put("value", priceForEvent);
            ecommerceData.put("items", List.of(itemData));

            Map<String, Object> finalViewItemData = new HashMap<>();
            finalViewItemData.put("event", "view_item");
            finalViewItemData.put("ecommerce", ecommerceData);

            try {
                String viewItemJson = objectMapper.writeValueAsString(finalViewItemData);
                model.addAttribute("viewItemDataJson", viewItemJson);
                logger.info("[ProductController] Successfully generated viewItemDataJson for product ID {}: {}", product.getId(), viewItemJson);
            } catch (JsonProcessingException e) {
                logger.error("!!! [ProductController] Error serializing viewItemData to JSON for product ID {}: {}", product.getId(), e.getMessage());
                model.addAttribute("viewItemDataJson", "null"); // Explicitně "null" jako string
            }

            Map<String, Object> trackingData = new HashMap<>();
            trackingData.put("productId", product.getId());
            trackingData.put("variantId", itemData.get("item_id"));
            trackingData.put("productName", product.getName());
            trackingData.put("currency", currentCurrency);
            trackingData.put("priceNoVat", priceForEvent);
            model.addAttribute("trackingData", trackingData);

            try {
                List<String> imageUrls = Collections.emptyList();
                if (product.getImages() != null && !product.getImages().isEmpty()) {
                    String baseUrlStr = request.getScheme() + "://" + request.getServerName() + (request.getServerPort() == 80 || request.getServerPort() == 443 ? "" : ":" + request.getServerPort()) + request.getContextPath();
                    imageUrls = product.getImagesOrdered().stream()
                            .map(img -> {
                                String imageUrl = img.getUrl();
                                if (imageUrl != null && !imageUrl.toLowerCase().startsWith("http")) {
                                    String path = imageUrl.startsWith("/") ? imageUrl.substring(1) : imageUrl;
                                    return baseUrlStr + "/" + path;
                                }
                                return imageUrl;
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
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
                jsonLdMap.put("sku", itemData.get("item_id"));

                Map<String, Object> brandMap = new LinkedHashMap<>();
                brandMap.put("@type", "Brand");
                brandMap.put("name", BRAND_NAME);
                jsonLdMap.put("brand", brandMap);

                Map<String, Object> offersMap = new LinkedHashMap<>();
                offersMap.put("@type", "Offer");
                offersMap.put("url", request.getRequestURL().toString());

                Map<String, Object> priceSpecMap = new LinkedHashMap<>();
                priceSpecMap.put("@type", "UnitPriceSpecification");
                priceSpecMap.put("price", priceForEvent.setScale(PRICE_SCALE, ROUNDING_MODE));
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
            } catch (Exception e) {
                logger.error("!!! Chyba při generování JSON-LD dat: {} !!!", e.getMessage(), e);
                model.addAttribute("jsonLdDataString", "{}");
            }

            Set<TaxRate> availableTaxRates = product.getAvailableTaxRates();
            if (availableTaxRates != null && !availableTaxRates.isEmpty()) {
                model.addAttribute("availableTaxRates", availableTaxRates.stream().sorted(Comparator.comparing(TaxRate::getName)).collect(Collectors.toList()));
            } else {
                logger.warn("[ProductController] Produkt ID {} nemá žádné daňové sazby!", product.getId());
                model.addAttribute("productError", "Produkt nelze objednat, chybí daňové sazby.");
            }

            model.addAttribute("availableDesigns", product.getAvailableDesigns() != null ? product.getAvailableDesigns() : Collections.emptySet());
            model.addAttribute("availableGlazes", product.getAvailableGlazes() != null ? product.getAvailableGlazes() : Collections.emptySet());
            model.addAttribute("availableRoofColors", product.getAvailableRoofColors() != null ? product.getAvailableRoofColors() : Collections.emptySet());
            model.addAttribute("cartItemDto", cartItemDto);

            if (product.isCustomisable()) {
                if (productConfiguratorDto != null) {
                    Set<Addon> activeAddons = product.getAvailableAddons() != null ?
                            product.getAvailableAddons().stream().filter(Addon::isActive).collect(Collectors.toSet())
                            : Collections.emptySet();
                    Map<String, List<Addon>> groupedAddons = activeAddons.stream()
                            .sorted(Comparator.comparing(Addon::getName))
                            .collect(Collectors.groupingBy(Addon::getCategory, TreeMap::new, Collectors.toList()));
                    model.addAttribute("groupedAddons", groupedAddons);
                } else {
                    logger.error("!!! [ProductController] Custom produkt ID {} nemá konfigurátor (DTO je null), ale logika pokračovala? !!!", product.getId());
                    model.addAttribute("configuratorError", "Chybí data konfigurátoru.");
                    model.addAttribute("productError", "Produkt nelze nakonfigurovat.");
                }
                logger.info(">>> [ProductController] Opouštím productDetail (CUSTOM). Vracím 'produkt-detail-custom'. Model keys: {}", model.asMap().keySet());
                return "produkt-detail-custom";
            } else {
                logger.info(">>> [ProductController] Opouštím productDetail (STANDARD). Vracím 'produkt-detail-standard'. Model keys: {}", model.asMap().keySet());
                return "produkt-detail-standard";
            }
        } catch (ResponseStatusException e) {
            logger.warn("[ProductController] ResponseStatusException v productDetail: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("!!! [ProductController] Neočekávaná chyba v productDetail pro slug {}: {} !!!", slug, e.getMessage(), e);
            model.addAttribute("errorMessage", "Při načítání detailu produktu došlo k neočekávané chybě.");
            return "error/500"; // Zajistěte, že máte error/500.html šablonu
        }
    }

    // Pomocná metoda pro zkrácení textu (přidána na konec třídy, pokud již neexistuje)
    private String abbreviate(String text) {
        if (text == null) {
            return ""; // Vrátit prázdný řetězec pro null
        }
        int maxLength = 250; // Max Šířka pro popis v JSON-LD
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