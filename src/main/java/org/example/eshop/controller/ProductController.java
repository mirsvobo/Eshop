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
    @Transactional(readOnly = true) // Pro inicializaci obrázků a slev
    public String listProducts(Model model, @PageableDefault(size = 9, sort = "name") Pageable pageable) {
        logger.info(">>> [ProductController] Vstupuji do listProducts. Pageable: {}", pageable);
        String currentCurrency = currencyService.getSelectedCurrency(); // Získat aktuální měnu
        model.addAttribute("currentCurrency", currentCurrency); // Přidat měnu do modelu

        try {
            Page<Product> productPage = productService.getActiveProducts(pageable);
            logger.info("[ProductController] ProductService.getActiveProducts vrátil stránku: TotalElements={}, TotalPages={}, Number={}, Size={}",
                    productPage.getTotalElements(), productPage.getTotalPages(), productPage.getNumber(), productPage.getSize());

            // Připravíme mapu s cenami po slevě pro každý produkt
            Map<Long, Map<String, Object>> productPrices = new HashMap<>();
            for (Product product : productPage.getContent()) {
                // Inicializace pro jistotu
                if (product.getImages() != null) Hibernate.initialize(product.getImages());

                // Výpočet ceny se slevou (pouze pro standardní produkty)
                if (!product.isCustomisable()) {
                    Map<String, Object> priceInfo = productService.calculateFinalProductPrice(product, currentCurrency);
                    productPrices.put(product.getId(), priceInfo);
                }
            }
            logger.debug("[ProductController] Hibernate.initialize(images) and price calculation done for {} products.", productPage.getNumberOfElements());

            model.addAttribute("productPage", productPage);
            model.addAttribute("productPrices", productPrices); // Předáme mapu cen do šablony

        } catch (Exception e) {
            logger.error("!!! [ProductController] Chyba v listProducts: {} !!!", e.getMessage(), e);
            model.addAttribute("errorMessage", "Nepodařilo se načíst produkty.");
            model.addAttribute("productPage", Page.empty(pageable));
            model.addAttribute("productPrices", Collections.emptyMap()); // Prázdná mapa při chybě
        }
        logger.info(">>> [ProductController] Opouštím listProducts. Obsah modelu před vrácením 'produkty': {}", model.asMap());
        return "produkty";
    }

    @GetMapping("/produkt/{slug}")
    @Transactional(readOnly = true) // Nutné pro inicializaci LAZY kolekcí
    public String productDetail(@PathVariable String slug, Model model) {
        logger.info(">>> [ProductController] Vstupuji do productDetail. Slug: {}", slug);
        Product product = null;
        try {
            product = productService.getActiveProductBySlug(slug)
                    .orElseThrow(() -> {
                        logger.warn("[ProductController] Produkt se slugem '{}' nenalezen nebo není aktivní.", slug);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Produkt nebyl nalezen");
                    });
            logger.info("[ProductController] Produkt ID {} nalezen pro slug '{}'.", product.getId(), slug);

            // Inicializace LAZY kolekcí
            Hibernate.initialize(product.getImages());
            logger.debug("[ProductController] Inicializovány obrázky pro produkt ID {}", product.getId());

            // Společné atributy pro model
            model.addAttribute("product", product);
            CartItemDto cartItemDto = new CartItemDto();
            cartItemDto.setProductId(product.getId());
            cartItemDto.setCustom(product.isCustomisable());

            if (product.isCustomisable()) {
                // --- Custom Produkt ---
                logger.info("[ProductController] Zpracovávám detail pro CUSTOM produkt ID {}", product.getId());
                // Inicializace specifická pro custom
                Hibernate.initialize(product.getConfigurator());
                Hibernate.initialize(product.getAvailableAddons());
                // **** NOVÉ: Inicializace atributů i pro custom ****
                Hibernate.initialize(product.getAvailableDesigns());
                Hibernate.initialize(product.getAvailableGlazes());
                Hibernate.initialize(product.getAvailableRoofColors());
                logger.debug("[ProductController] Inicializován konfigurátor, addony a atributy pro custom produkt ID {}", product.getId());

                ProductConfigurator configurator = product.getConfigurator();
                if (configurator != null) {
                    model.addAttribute("configurator", configurator);
                    // Dostupné Addony
                    List<Addon> availableAddons = product.getAvailableAddons() != null ?
                            product.getAvailableAddons().stream().filter(Addon::isActive).sorted(Comparator.comparing(Addon::getName)).collect(Collectors.toList())
                            : Collections.emptyList();
                    model.addAttribute("availableAddons", availableAddons);

                    // **** NOVÉ: Přidání dostupných atributů do modelu ****
                    model.addAttribute("availableDesigns", product.getAvailableDesigns() != null ? product.getAvailableDesigns() : Collections.emptySet());
                    model.addAttribute("availableGlazes", product.getAvailableGlazes() != null ? product.getAvailableGlazes() : Collections.emptySet());
                    model.addAttribute("availableRoofColors", product.getAvailableRoofColors() != null ? product.getAvailableRoofColors() : Collections.emptySet());
                    logger.debug("[ProductController] Přidány atributy (Design: {}, Glaze: {}, RoofColor: {}) do modelu.",
                            product.getAvailableDesigns() != null ? product.getAvailableDesigns().size() : 0,
                            product.getAvailableGlazes() != null ? product.getAvailableGlazes().size() : 0,
                            product.getAvailableRoofColors() != null ? product.getAvailableRoofColors().size() : 0);

                    // Výpočet výchozí ceny (zůstává stejný - cena za min. rozměry)
                    BigDecimal initialCustomPriceCZK = BigDecimal.ZERO;
                    BigDecimal initialCustomPriceEUR = BigDecimal.ZERO;
                    String initialPriceError = null;
                    try {
                        Map<String, BigDecimal> minDimensions = Map.of(
                                "length", configurator.getMinLength(),
                                "width", configurator.getMinWidth(),
                                "height", configurator.getMinHeight()
                        );
                        // Výpočet ceny JEN za rozměry a volitelné prvky (příčka, okap...)
                        // Design/Lazura/Barva se zde pro výchozí cenu nezohledňuje
                        initialCustomPriceCZK = productService.calculateDynamicProductPrice(product, minDimensions, null, false, false, false, "CZK");
                        initialCustomPriceEUR = productService.calculateDynamicProductPrice(product, minDimensions, null, false, false, false, "EUR");

                        // Nastavení výchozích rozměrů pro formulář
                        Map<String, BigDecimal> initialDims = new HashMap<>();
                        initialDims.put("length", configurator.getMinLength());
                        initialDims.put("width", configurator.getMinWidth());
                        initialDims.put("height", configurator.getMinHeight());
                        cartItemDto.setCustomDimensions(initialDims);

                        logger.debug("[ProductController] Výchozí JEDNOTKOVÉ ceny (bez atributů) pro custom produkt: CZK={}, EUR={}", initialCustomPriceCZK, initialCustomPriceEUR);
                    } catch (Exception e) {
                        logger.error("[ProductController] Chyba při výpočtu výchozí ceny pro custom produkt ID {}: {}", product.getId(), e.getMessage(), e);
                        initialPriceError = "Nepodařilo se vypočítat výchozí cenu: " + e.getMessage();
                    }
                    model.addAttribute("initialCustomPriceCZK", initialCustomPriceCZK); // Toto je cena bez příplatků za atributy
                    model.addAttribute("initialCustomPriceEUR", initialCustomPriceEUR); // Toto je cena bez příplatků za atributy
                    model.addAttribute("initialCustomPriceError", initialPriceError);

                } else {
                    logger.error("!!! [ProductController] Custom produkt ID {} nemá konfigurátor !!!", product.getId());
                    model.addAttribute("configuratorError", "Chybí data konfigurátoru.");
                }
                model.addAttribute("cartItemDto", cartItemDto);
                logger.info(">>> [ProductController] Opouštím productDetail. Obsah modelu před vrácením 'produkt-detail-custom': {}", model.asMap());
                return "produkt-detail-custom";

            } else {
                // --- Standardní produkt ---
                logger.info("[ProductController] Zpracovávám detail pro STANDARD produkt ID {}", product.getId());
                // Inicializace atributů (zůstává)
                Hibernate.initialize(product.getAvailableDesigns());
                Hibernate.initialize(product.getAvailableGlazes());
                Hibernate.initialize(product.getAvailableRoofColors());

                // --- VÝPOČET CENY SE SLEVOU ---
                Map<String, Object> priceInfo = productService.calculateFinalProductPrice(product, currencyService.getSelectedCurrency());
                model.addAttribute("priceInfo", priceInfo); // Přidáme informace o ceně do modelu
                // --- KONEC VÝPOČTU ---

                // Původní kód pro atributy a DTO
                Set<Design> designs = product.getAvailableDesigns();
                Set<Glaze> glazes = product.getAvailableGlazes();
                Set<RoofColor> roofColors = product.getAvailableRoofColors();
                model.addAttribute("availableDesigns", designs != null ? designs : Collections.emptySet());
                model.addAttribute("availableGlazes", glazes != null ? glazes : Collections.emptySet());
                model.addAttribute("availableRoofColors", roofColors != null ? roofColors : Collections.emptySet());
                model.addAttribute("cartItemDto", cartItemDto);

                logger.info(">>> [ProductController] Opouštím productDetail (STANDARD). Model: {}", model.asMap());
                return "produkt-detail-standard";
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("!!! [ProductController] Neočekávaná chyba v productDetail pro slug {}: {} !!!", slug, e.getMessage(), e);
            return "redirect:/produkty?error";
        }
    }

    // V src/main/java/org/example/eshop/controller/ProductController.java

    @PostMapping("/api/product/calculate-price")
    @ResponseBody
    public ResponseEntity<CustomPriceResponseDto> calculateCustomPrice(@Validated @RequestBody CustomPriceRequestDto requestDto) {
        logger.info(">>> [ProductController] Vstupuji do API calculateCustomPrice. Product ID: {}", requestDto.getProductId());
        String dimensionsLog = requestDto.getCustomDimensions() != null ? requestDto.getCustomDimensions().toString() : "null";
        if (dimensionsLog.length() > 100) dimensionsLog = dimensionsLog.substring(0, 97) + "...";
        // POZNÁMKA: Z logu odebrán requestDto.getCustomDesign(), protože už ho z DTO neposíláme/nepotřebujeme zde
        logger.debug("[ProductController] Calc request data: ProductId={}, Dimensions={}, Divider={}, Gutter={}, Shed={}",
                requestDto.getProductId(), dimensionsLog, /* requestDto.getCustomDesign() - ODEBRÁNO Z LOGU */
                requestDto.isCustomHasDivider(), requestDto.isCustomHasGutter(), requestDto.isCustomHasGardenShed());
        try {
            Product product = productService.getProductById(requestDto.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produkt nenalezen: " + requestDto.getProductId()));

            if (!product.isCustomisable() || product.getConfigurator() == null) {
                logger.warn("[ProductController] API Calc: Produkt ID {} není konfigurovatelný nebo chybí konfigurace.", requestDto.getProductId());
                return ResponseEntity.badRequest().body(new CustomPriceResponseDto(null, null, "Tento produkt nelze konfigurovat na míru nebo chybí konfigurace."));
            }

            // Volání productService BEZ customDesign, customGlaze, customRoofColor - ty se řeší jinde
            BigDecimal priceCZK = productService.calculateDynamicProductPrice(
                    product,
                    requestDto.getCustomDimensions(),
                    null, // Design ID/name se zde už nepoužívá pro základní cenu
                    requestDto.isCustomHasDivider(),
                    requestDto.isCustomHasGutter(),
                    requestDto.isCustomHasGardenShed(),
                    "CZK"
            );
            BigDecimal priceEUR = productService.calculateDynamicProductPrice(
                    product,
                    requestDto.getCustomDimensions(),
                    null, // Design ID/name se zde už nepoužívá pro základní cenu
                    requestDto.isCustomHasDivider(),
                    requestDto.isCustomHasGutter(),
                    requestDto.isCustomHasGardenShed(),
                    "EUR"
            );

            logger.info(">>> [ProductController] API calculateCustomPrice (základní cena) úspěšně spočítána. Product ID {}: CZK={}, EUR={}. Vracím OK.", requestDto.getProductId(), priceCZK, priceEUR);
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
    }}