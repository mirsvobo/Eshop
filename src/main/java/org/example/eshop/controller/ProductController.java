package org.example.eshop.controller;

import org.example.eshop.admin.service.AddonsService;
import org.example.eshop.model.*; // Import všech modelů
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
    private ProductService productService;
    @Autowired
    private AddonsService addonsService; // Předpokládám, že je stále potřeba pro detail

    @GetMapping("/produkty")
    @Transactional(readOnly = true) // Pro inicializaci obrázků v seznamu
    public String listProducts(Model model, @PageableDefault(size = 9, sort = "name") Pageable pageable) {
        // log.info("Requesting product list page - Page: {}, Size: {}, Sort: {}", pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort()); // Původní log
        logger.info(">>> [ProductController] Vstupuji do listProducts. Pageable: {}", pageable); // <-- NOVÝ LOG
        try {
            Page<Product> productPage = productService.getActiveProducts(pageable);
            // Logování výsledku ze service
            logger.info("[ProductController] ProductService.getActiveProducts vrátil stránku: TotalElements={}, TotalPages={}, Number={}, Size={}",
                    productPage.getTotalElements(), productPage.getTotalPages(), productPage.getNumber(), productPage.getSize());

            // Původní inicializace obrázků
            productPage.getContent().forEach(p -> Hibernate.initialize(p.getImages()));
            logger.debug("[ProductController] Hibernate.initialize(images) dokončeno pro {} produktů na stránce.", productPage.getNumberOfElements());

            model.addAttribute("productPage", productPage);
            // log.debug("Found {} active products for page {}", productPage.getNumberOfElements(), pageable.getPageNumber()); // Původní log
        } catch (Exception e) {
            // log.error("Error fetching active products for page {}", pageable, e); // Původní log
            logger.error("!!! [ProductController] Chyba v listProducts při načítání produktů: {} !!!", e.getMessage(), e); // <-- NOVÝ LOG
            model.addAttribute("errorMessage", "Nepodařilo se načíst produkty.");
            model.addAttribute("productPage", Page.empty(pageable)); // Přidání prázdné stránky i při chybě
        }
        logger.info(">>> [ProductController] Opouštím listProducts. Obsah modelu před vrácením 'produkty': {}", model.asMap()); // <-- NOVÝ LOG
        return "produkty";
    }

    @GetMapping("/produkt/{slug}")
    @Transactional(readOnly = true) // Nutné pro inicializaci LAZY kolekcí
    public String productDetail(@PathVariable String slug, Model model) {
        // log.info("Requesting product detail for slug: {}", slug); // Původní log
        logger.info(">>> [ProductController] Vstupuji do productDetail. Slug: {}", slug); // <-- NOVÝ LOG
        Product product = null; // Inicializace
        try {
            product = productService.getActiveProductBySlug(slug)
                    .orElseThrow(() -> {
                        logger.warn("[ProductController] Produkt se slugem '{}' nenalezen nebo není aktivní.", slug); // <-- LOG PŘI CHYBĚ
                        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Produkt nebyl nalezen");
                    });
            logger.info("[ProductController] Produkt ID {} nalezen pro slug '{}'.", product.getId(), slug);

            // Původní inicializace kolekcí
            Hibernate.initialize(product.getImages());
            logger.debug("[ProductController] Inicializovány obrázky pro produkt ID {}", product.getId());
            if (product.isCustomisable()) {
                Hibernate.initialize(product.getConfigurator());
                Hibernate.initialize(product.getAvailableAddons());
                logger.debug("[ProductController] Inicializován konfigurátor a doplňky pro custom produkt ID {}", product.getId());
            } else {
                Hibernate.initialize(product.getAvailableDesigns()); // *** Stále zde ***
                Hibernate.initialize(product.getAvailableGlazes()); // *** Stále zde ***
                Hibernate.initialize(product.getAvailableRoofColors()); // *** Stále zde ***
                logger.debug("[ProductController] Inicializovány designy, lazury a barvy střech pro standardní produkt ID {}", product.getId());
            }

            model.addAttribute("product", product);

            CartItemDto cartItemDto = new CartItemDto();
            cartItemDto.setProductId(product.getId());
            cartItemDto.setCustom(product.isCustomisable());

            if (product.isCustomisable()) {
                // --- Custom Produkt ---
                // log.debug("Product '{}' (ID: {}) is customisable...", product.getName(), product.getId()); // Původní log
                logger.info("[ProductController] Zpracovávám detail pro CUSTOM produkt ID {}", product.getId()); // <-- NOVÝ LOG
                ProductConfigurator configurator = product.getConfigurator();
                if (configurator != null) {
                    model.addAttribute("configurator", configurator);
                    Map<String, Object> productJsDataMap = new HashMap<>(); productJsDataMap.put("id", product.getId()); model.addAttribute("productJsData", productJsDataMap);
                    List<Addon> availableAddons = product.getAvailableAddons() != null ? product.getAvailableAddons().stream().filter(Addon::isActive).sorted(Comparator.comparing(Addon::getName)).collect(Collectors.toList()) : Collections.emptyList();
                    model.addAttribute("availableAddons", availableAddons);
                    // Původní výpočet initial prices ...
                    BigDecimal initialCustomPriceCZK = BigDecimal.ZERO; BigDecimal initialCustomPriceEUR = BigDecimal.ZERO; String initialPriceError = null;
                    try { Map<String, BigDecimal> minDimensions = Map.of( "length", configurator.getMinLength(), "width", configurator.getMinWidth(), "height", configurator.getMinHeight() ); initialCustomPriceCZK = productService.calculateDynamicProductPrice(product, minDimensions, null, false, false, false, "CZK"); initialCustomPriceEUR = productService.calculateDynamicProductPrice(product, minDimensions, null, false, false, false, "EUR"); Map<String, BigDecimal> initialDims = new HashMap<>(); initialDims.put("length", configurator.getMinLength()); initialDims.put("width", configurator.getMinWidth()); initialDims.put("height", configurator.getMinHeight()); cartItemDto.setCustomDimensions(initialDims); logger.debug("[ProductController] Výchozí ceny pro custom produkt: CZK={}, EUR={}", initialCustomPriceCZK, initialCustomPriceEUR); } catch (Exception e) { logger.error("[ProductController] Chyba při výpočtu výchozí ceny pro custom produkt ID {}: {}", product.getId(), e.getMessage(), e); initialPriceError = "Nepodařilo se vypočítat výchozí cenu: " + e.getMessage(); } model.addAttribute("initialCustomPriceCZK", initialCustomPriceCZK); model.addAttribute("initialCustomPriceEUR", initialCustomPriceEUR); model.addAttribute("initialCustomPriceError", initialPriceError);
                } else {
                    logger.error("!!! [ProductController] Custom produkt ID {} nemá konfigurátor !!!", product.getId());
                    model.addAttribute("configuratorError", "Chybí data konfigurátoru.");
                }
                model.addAttribute("cartItemDto", cartItemDto);
                logger.info(">>> [ProductController] Opouštím productDetail. Obsah modelu před vrácením 'produkt-detail-custom': {}", model.asMap()); // <-- NOVÝ LOG
                return "produkt-detail-custom";
            } else {
                // --- Standardní produkt ---
                // log.debug("Product '{}' (ID: {}) is standard...", product.getName(), product.getId()); // Původní log
                logger.info("[ProductController] Zpracovávám detail pro STANDARD produkt ID {}", product.getId()); // <-- NOVÝ LOG

                Set<Design> designs = product.getAvailableDesigns();
                Set<Glaze> glazes = product.getAvailableGlazes();
                Set<RoofColor> roofColors = product.getAvailableRoofColors();

                // log.debug("Available Designs (initialized): {}", designs != null ? designs.stream().map(Design::getName).collect(Collectors.toList()) : "null"); // Původní logy
                // log.debug("Available Glazes (initialized): {}", glazes != null ? glazes.stream().map(Glaze::getName).collect(Collectors.toList()) : "null");
                // log.debug("Available Roof Colors (initialized): {}", roofColors != null ? roofColors.stream().map(RoofColor::getName).collect(Collectors.toList()) : "null");

                model.addAttribute("basePriceCZK", product.getBasePriceCZK());
                model.addAttribute("basePriceEUR", product.getBasePriceEUR());
                model.addAttribute("availableDesigns", designs != null ? designs : Collections.emptySet());
                model.addAttribute("availableGlazes", glazes != null ? glazes : Collections.emptySet());
                model.addAttribute("availableRoofColors", roofColors != null ? roofColors : Collections.emptySet());

                model.addAttribute("cartItemDto", cartItemDto);
                logger.info(">>> [ProductController] Opouštím productDetail. Obsah modelu před vrácením 'produkt-detail-standard': {}", model.asMap()); // <-- NOVÝ LOG
                return "produkt-detail-standard";
            }
        } catch (ResponseStatusException e) {
            // Logování již proběhlo výše
            throw e; // Znovu vyhodit výjimku pro standardní zpracování Springem
        } catch (Exception e) {
            logger.error("!!! [ProductController] Neočekávaná chyba v productDetail pro slug {}: {} !!!", slug, e.getMessage(), e);
            // Můžeš přesměrovat na obecnou chybovou stránku nebo zpět na seznam produktů
            // throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Došlo k chybě při zpracování detailu produktu.", e);
            return "redirect:/produkty?error"; // Příklad přesměrování s chybovým parametrem
        }
    }

    @PostMapping("/api/product/calculate-price")
    @ResponseBody
    public ResponseEntity<CustomPriceResponseDto> calculateCustomPrice(@Validated @RequestBody CustomPriceRequestDto requestDto) {
        // log.info("API request to calculate custom price for product ID: {}", requestDto.getProductId()); // Původní log
        logger.info(">>> [ProductController] Vstupuji do API calculateCustomPrice. Product ID: {}", requestDto.getProductId()); // <-- NOVÝ LOG
        // Původní kód metody... (logy uvnitř zůstávají)
        String dimensionsLog = requestDto.getCustomDimensions() != null ? requestDto.getCustomDimensions().toString() : "null";
        if (dimensionsLog.length() > 100) dimensionsLog = dimensionsLog.substring(0, 97) + "...";
        logger.debug("[ProductController] Calc request data: ProductId={}, Dimensions={}, Design={}, Divider={}, Gutter={}, Shed={}", requestDto.getProductId(), dimensionsLog, requestDto.getCustomDesign(), requestDto.isCustomHasDivider(), requestDto.isCustomHasGutter(), requestDto.isCustomHasGardenShed());
        try {
            Product product = productService.getProductById(requestDto.getProductId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produkt nenalezen: " + requestDto.getProductId()));
            if (!product.isCustomisable() || product.getConfigurator() == null) { logger.warn("[ProductController] API Calc: Produkt ID {} není konfigurovatelný nebo chybí konfigurace.", requestDto.getProductId()); return ResponseEntity.badRequest().body(new CustomPriceResponseDto(null, null, "Tento produkt nelze konfigurovat na míru nebo chybí konfigurace.")); }
            BigDecimal priceCZK = productService.calculateDynamicProductPrice(product, requestDto.getCustomDimensions(), requestDto.getCustomDesign(), requestDto.isCustomHasDivider(), requestDto.isCustomHasGutter(), requestDto.isCustomHasGardenShed(), "CZK");
            BigDecimal priceEUR = productService.calculateDynamicProductPrice(product, requestDto.getCustomDimensions(), requestDto.getCustomDesign(), requestDto.isCustomHasDivider(), requestDto.isCustomHasGutter(), requestDto.isCustomHasGardenShed(), "EUR");
            // log.info("API calculated custom price for product ID {}: CZK={}, EUR={}", requestDto.getProductId(), priceCZK, priceEUR); // Původní log
            logger.info(">>> [ProductController] API calculateCustomPrice úspěšně spočítána. Product ID {}: CZK={}, EUR={}. Vracím OK.", requestDto.getProductId(), priceCZK, priceEUR); // <-- NOVÝ LOG
            return ResponseEntity.ok(new CustomPriceResponseDto(priceCZK, priceEUR, null));
        } catch (ResponseStatusException e) { logger.warn("[ProductController] API Calc: Chyba stavu {}: {}", e.getStatusCode(), e.getReason()); return ResponseEntity.status(e.getStatusCode()).body(new CustomPriceResponseDto(null, null, e.getReason())); }
        catch (IllegalArgumentException | IllegalStateException e) { logger.warn("[ProductController] API Calc: Neplatný požadavek: {}", e.getMessage()); return ResponseEntity.badRequest().body(new CustomPriceResponseDto(null, null, e.getMessage())); }
        catch (Exception e) { logger.error("!!! [ProductController] API Calc: Neočekávaná chyba: {} !!!", e.getMessage(), e); return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new CustomPriceResponseDto(null, null, "Došlo k neočekávané chybě při výpočtu ceny.")); }
    }
}