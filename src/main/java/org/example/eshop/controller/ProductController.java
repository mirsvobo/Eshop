package org.example.eshop.controller;

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

@Controller
public class ProductController {

    // private static final Logger log = LoggerFactory.getLogger(ProductController.class); // Původní logger
    private static final Logger logger = LoggerFactory.getLogger(ProductController.class); // <-- PŘEJMENOVÁNO NA logger pro konzistenci

    @Autowired
    CurrencyService currencyService;
    @Autowired
    private ProductService productService;
    @Autowired
    private AddonsService addonsService; // Předpokládám, že je stále potřeba pro detail

    @GetMapping("/produkty")
    @Transactional(readOnly = true)
    public String listProducts(Model model, @PageableDefault(size = 9, sort = "slug") Pageable pageable) {
        logger.info(">>> [ProductController] Vstupuji do listProducts. Pageable: {}", pageable);
        String currentCurrency = currencyService.getSelectedCurrency();
        model.addAttribute("currentCurrency", currentCurrency);

        try {
            Page<Product> productPage = productService.getActiveProducts(pageable);
            logger.info("[ProductController] ProductService.getActiveProducts vrátil stránku: TotalElements={}, TotalPages={}, Number={}, Size={}",
                    productPage.getTotalElements(), productPage.getTotalPages(), productPage.getNumber(), productPage.getSize());

            Map<Long, Map<String, Object>> productPrices = new HashMap<>();
            for (Product product : productPage.getContent()) {
                if (!product.isCustomisable()) {
                    Map<String, Object> priceInfo = productService.calculateFinalProductPrice(product, currentCurrency);
                    productPrices.put(product.getId(), priceInfo);
                }
            }

            model.addAttribute("productPage", productPage);
            model.addAttribute("productPrices", productPrices);

        } catch (Exception e) {
            logger.error("!!! [ProductController] Chyba v listProducts: {} !!!", e.getMessage(), e);
            model.addAttribute("errorMessage", "Nepodařilo se načíst produkty.");
            model.addAttribute("productPage", Page.empty(pageable));
            model.addAttribute("productPrices", Collections.emptyMap());
        }
        logger.info(">>> [ProductController] Opouštím listProducts. Obsah modelu před vrácením 'produkty': {}", model.asMap().keySet());
        return "produkty";
    }

    @GetMapping("/produkt/{slug}")
    @Transactional(readOnly = true)
    public String productDetail(@PathVariable String slug, Model model) {
        logger.info(">>> [ProductController] Vstupuji do productDetail. Slug: {}", slug);
        String currentCurrency = currencyService.getSelectedCurrency(); // Get currency for display
        model.addAttribute("currentGlobalCurrency", currentCurrency); // Add to model for Thymeleaf

        Product product = null;
        try {
            product = productService.getActiveProductBySlug(slug)
                    .orElseThrow(() -> {
                        logger.warn("[ProductController] Produkt se slugem '{}' nenalezen nebo není aktivní.", slug);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Produkt nebyl nalezen");
                    });
            logger.info("[ProductController] Produkt ID {} nalezen pro slug '{}'.", product.getId(), slug);

            model.addAttribute("product", product);

            // Handle Tax Rates
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
                model.addAttribute("productError", "Produkt nelze objednat, chybí daňové sazby."); // Add error message
                // Consider not proceeding if tax rates are mandatory
            }

            CartItemDto cartItemDto = new CartItemDto();
            cartItemDto.setProductId(product.getId());
            cartItemDto.setCustom(product.isCustomisable());

            if (product.isCustomisable()) {
                // --- Custom Produkt ---
                logger.info("[ProductController] Zpracovávám detail pro CUSTOM produkt ID {}", product.getId());
                ProductConfigurator configurator = product.getConfigurator();
                if (configurator != null) {
                    model.addAttribute("configurator", configurator);

                    // --- Addon Grouping ---
                    Set<Addon> activeAddons = product.getAvailableAddons() != null ?
                            product.getAvailableAddons().stream().filter(Addon::isActive).collect(Collectors.toSet())
                            : Collections.emptySet();

                    Map<String, List<Addon>> groupedAddons = activeAddons.stream()
                            .sorted(Comparator.comparing(Addon::getName)) // Sort addons within each category
                            .collect(Collectors.groupingBy(Addon::getCategory,
                                    TreeMap::new, // Use TreeMap to sort categories by name
                                    Collectors.toList()));

                    model.addAttribute("groupedAddons", groupedAddons); // Pass grouped addons
                    logger.debug("[ProductController] Grouped addons: {}", groupedAddons.keySet());
                    // --- End Addon Grouping ---

                    model.addAttribute("availableDesigns", product.getAvailableDesigns() != null ? product.getAvailableDesigns() : Collections.emptySet());
                    model.addAttribute("availableGlazes", product.getAvailableGlazes() != null ? product.getAvailableGlazes() : Collections.emptySet());
                    model.addAttribute("availableRoofColors", product.getAvailableRoofColors() != null ? product.getAvailableRoofColors() : Collections.emptySet());

                    // Calculate initial price (base price for minimum dimensions)
                    BigDecimal initialCustomPriceCZK = BigDecimal.ZERO;
                    BigDecimal initialCustomPriceEUR = BigDecimal.ZERO;
                    String initialPriceError = null;
                    try {
                        Map<String, BigDecimal> minDimensions = new HashMap<>();
                        // Ensure min dimensions are not null before putting them in the map
                        if (configurator.getMinLength() != null) minDimensions.put("length", configurator.getMinLength()); else throw new IllegalStateException("Min Length is null");
                        if (configurator.getMinWidth() != null) minDimensions.put("width", configurator.getMinWidth()); else throw new IllegalStateException("Min Width is null");
                        if (configurator.getMinHeight() != null) minDimensions.put("height", configurator.getMinHeight()); else throw new IllegalStateException("Min Height is null");

                        // We need to pass the list of initially selected addons (none) and attributes (none) to get the real initial price including potential zero-cost defaults
                        // OR rely on the client-side JS to calculate the full initial price after the base price is fetched. Let's stick to the simpler backend base price calculation for now.
                        initialCustomPriceCZK = productService.calculateDynamicProductPrice(product, minDimensions, "CZK");
                        initialCustomPriceEUR = productService.calculateDynamicProductPrice(product, minDimensions, "EUR");

                        // Set initial dimensions in DTO for form population
                        cartItemDto.setCustomDimensions(minDimensions);
                        logger.debug("[ProductController] Výchozí ZÁKLADNÍ cena (bez atributů/doplňků) pro custom produkt: CZK={}, EUR={}", initialCustomPriceCZK, initialCustomPriceEUR);

                    } catch (Exception e) {
                        logger.error("[ProductController] Chyba při výpočtu výchozí základní ceny pro custom produkt ID {}: {}", product.getId(), e.getMessage(), e);
                        initialPriceError = "Nepodařilo se vypočítat výchozí cenu: " + e.getMessage();
                        model.addAttribute("productError", initialPriceError); // Add error message
                    }
                    model.addAttribute("initialCustomPriceCZK", initialCustomPriceCZK);
                    model.addAttribute("initialCustomPriceEUR", initialCustomPriceEUR);
                    model.addAttribute("initialCustomPriceError", initialPriceError);

                } else {
                    logger.error("!!! [ProductController] Custom produkt ID {} nemá konfigurátor !!!", product.getId());
                    model.addAttribute("configuratorError", "Chybí data konfigurátoru.");
                    model.addAttribute("productError", "Produkt nelze nakonfigurovat."); // Add error message
                }
                model.addAttribute("cartItemDto", cartItemDto);
                logger.info(">>> [ProductController] Opouštím productDetail. Vracím 'produkt-detail-custom'. Model keys: {}", model.asMap().keySet());
                return "produkt-detail-custom";

            } else {
                // --- Standardní produkt ---
                logger.info("[ProductController] Zpracovávám detail pro STANDARD produkt ID {}", product.getId());
                Map<String, Object> priceInfo = productService.calculateFinalProductPrice(product, currentCurrency);
                model.addAttribute("priceInfo", priceInfo);

                Set<Design> designs = product.getAvailableDesigns();
                Set<Glaze> glazes = product.getAvailableGlazes();
                Set<RoofColor> roofColors = product.getAvailableRoofColors();
                model.addAttribute("availableDesigns", designs != null ? designs : Collections.emptySet());
                model.addAttribute("availableGlazes", glazes != null ? glazes : Collections.emptySet());
                model.addAttribute("availableRoofColors", roofColors != null ? roofColors : Collections.emptySet());
                model.addAttribute("cartItemDto", cartItemDto);

                logger.info(">>> [ProductController] Opouštím productDetail (STANDARD). Vracím 'produkt-detail-standard'. Model keys: {}", model.asMap().keySet());
                return "produkt-detail-standard";
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("!!! [ProductController] Neočekávaná chyba v productDetail pro slug {}: {} !!!", slug, e.getMessage(), e);
            // Provide a generic error message for the user
            model.addAttribute("errorMessage", "Při načítání detailu produktu došlo k neočekávané chybě.");
            // Optionally, redirect to a generic error page or product list
            return "redirect:/produkty?error=detail"; // Redirect with a specific error query param
        }
    }

    @PostMapping("/api/product/calculate-price")
    @ResponseBody
    public ResponseEntity<CustomPriceResponseDto> calculateCustomPrice(@Validated @RequestBody CustomPriceRequestDto requestDto) {
        logger.info(">>> [ProductController] Vstupuji do API calculateCustomPrice. Product ID: {}", requestDto.getProductId());
        String dimensionsLog = requestDto.getCustomDimensions() != null ? requestDto.getCustomDimensions().toString() : "null";
        // Updated log to show actual boolean values from DTO
        logger.debug("[ProductController] Calc request data: ProductId={}, Dimensions={}, Divider={}, Gutter={}, Shed={}",
                requestDto.getProductId(), dimensionsLog,
                requestDto.isCustomHasDivider(), requestDto.isCustomHasGutter(), requestDto.isCustomHasGardenShed()); // Using DTO getters

        try {
            Product product = productService.getProductById(requestDto.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produkt nenalezen: " + requestDto.getProductId()));

            if (!product.isCustomisable() || product.getConfigurator() == null) {
                logger.warn("[ProductController] API Calc: Produkt ID {} není konfigurovatelný nebo chybí konfigurace.", requestDto.getProductId());
                return ResponseEntity.badRequest().body(new CustomPriceResponseDto(null, null, "Tento produkt nelze konfigurovat na míru nebo chybí konfigurace."));
            }

            // NOTE: Now calling the simplified ProductService method which only calculates based on dimensions.
            // Addon/Attribute pricing is handled client-side or during cart processing.
            // The boolean flags (customHasDivider etc.) are now ignored by the backend service,
            // they should be treated as standard addons if they exist.
            BigDecimal priceCZK = productService.calculateDynamicProductPrice(
                    product,
                    requestDto.getCustomDimensions(),
                    "CZK" // Pass currency
            );
            BigDecimal priceEUR = productService.calculateDynamicProductPrice(
                    product,
                    requestDto.getCustomDimensions(),
                    "EUR" // Pass currency
            );

            logger.info(">>> [ProductController] API calculateCustomPrice (ZÁKLADNÍ cena z rozměrů) úspěšně spočítána. Product ID {}: CZK={}, EUR={}. Vracím OK.", requestDto.getProductId(), priceCZK, priceEUR);
            return ResponseEntity.ok(new CustomPriceResponseDto(priceCZK, priceEUR, null));

        } catch (ResponseStatusException e) {
            logger.warn("[ProductController] API Calc: Chyba stavu {}: {}", e.getStatusCode(), e.getReason());
            return ResponseEntity.status(e.getStatusCode()).body(new CustomPriceResponseDto(null, null, e.getReason()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.warn("[ProductController] API Calc: Neplatný požadavek: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new CustomPriceResponseDto(null, null, e.getMessage()));
        } catch (Exception e) {
            logger.error("!!! [ProductController] API Calc: Neočekávaná chyba: {} !!!", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new CustomPriceResponseDto(null, null, "Došlo k neočekávané chybě při výpočtu ceny."));
        }
    }
}