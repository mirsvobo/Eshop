package org.example.eshop.admin.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.eshop.admin.service.AddonsService;
import org.example.eshop.admin.service.DesignService;
import org.example.eshop.admin.service.GlazeService;
import org.example.eshop.admin.service.RoofColorService;
import org.example.eshop.dto.ImageDto;
import org.example.eshop.dto.ImageOrderUpdateRequest;
import org.example.eshop.model.*;
import org.example.eshop.service.ProductService;
import org.example.eshop.service.TaxRateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/products")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private static final Logger log = LoggerFactory.getLogger(AdminProductController.class);

    @Autowired
    private ProductService productService;
    @Autowired
    private TaxRateService taxRateService;
    @Autowired
    private AddonsService addonsService; // Předpokládá se, že tato service má metodu getAllActiveAddons() a getAddonById() vracející Optional
    @Autowired
    private DesignService designService;
    @Autowired
    private GlazeService glazeService;
    @Autowired
    private RoofColorService roofColorService;


    @ModelAttribute("currentUri")
    public String getCurrentUri(HttpServletRequest request) {
        return request.getRequestURI();
    }

    // Metoda pro načtení společných dat (předpokládáme, že funguje správně)
    private void loadCommonFormData(Model model) {
        log.info("Načítám společná data pro formulář...");
        // Použití OPRAVENÝCH názvů metod podle poskytnutých service tříd:
        model.addAttribute("allTaxRates", taxRateService.getAllTaxRates());                     // Opraveno z getAllActiveTaxRatesSorted()
        model.addAttribute("allAddons", addonsService.getAllActiveAddons());                  // Opraveno z findAllActive()
        model.addAttribute("allDesigns", designService.getAllDesignsSortedByName());         // Opraveno z findAllActive()
        model.addAttribute("allGlazes", glazeService.getAllGlazesSortedByName());           // Opraveno z findAllActive()
        model.addAttribute("allRoofColors", roofColorService.getAllRoofColorsSortedByName()); // Opraveno z findAllActive()
        log.info("Společná data načtena.");
    }

    // --- Opravená metoda pro zobrazení EDITAČNÍHO formuláře ---
    @GetMapping("/{id}/edit")
    @Transactional(readOnly = true) // Načítáme asociace
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Requesting edit form for product ID: {}", id);
        try {
            // 1. Načtení produktu
            Product product = productService.getProductById(id) // Používáme findByIdWithDetails nebo ekvivalent z productService
                    .orElseThrow(() -> new EntityNotFoundException("Produkt s ID " + id + " nenalezen."));

            // 2. Načtení všech seznamů pro výběr (lazury, designy atd.)
            loadCommonFormData(model); // Tato metoda načte allDesigns, allGlazes, allRoofColors atd.

            // 3. Seřazení obrázků produktu
            List<Image> sortedImages = new ArrayList<>();
            if (product.getImages() != null) {
                sortedImages = product.getImages().stream()
                        .sorted(Comparator.comparing(
                                Image::getDisplayOrder,
                                Comparator.nullsLast(Comparator.naturalOrder()) // Řadí null hodnoty na konec
                        ).thenComparing(Image::getId, Comparator.nullsLast(Comparator.naturalOrder()))) // Sekundární řazení podle ID
                        .collect(Collectors.toList());
                log.debug("Seřazeno {} obrázků pro produkt ID {}", sortedImages.size(), id);
            }

            // 4. Přidání produktu a seřazených obrázků do modelu
            model.addAttribute("product", product);
            model.addAttribute("sortedImages", sortedImages); // Přidání seřazeného seznamu

            // 5. Určení správné šablony a titulku
            if (product.isCustomisable()) {
                model.addAttribute("pageTitle", "Upravit produkt na míru: " + product.getName());
                log.debug("Returning custom product form for ID: {}", id);
                return "admin/product-form-custom";
            } else {
                model.addAttribute("pageTitle", "Upravit standardní produkt: " + product.getName());
                log.debug("Returning standard product form for ID: {}", id);
                return "admin/product-form-standard";
            }

        } catch (EntityNotFoundException e) {
            log.warn("Product with ID {} not found for editing.", id);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/products";
        } catch (Exception e) {
            log.error("Error loading product ID {} for edit: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Nepodařilo se načíst produkt k úpravě.");
            return "redirect:/admin/products";
        }
    }


    // --- Opravená metoda pro zobrazení formuláře pro PŘIDÁNÍ CUSTOM produktu ---
    @GetMapping("/new/custom")
    public String showCreateCustomForm(Model model) {
        log.info("Requesting new CUSTOM product form.");
        if (!model.containsAttribute("product")) {
            Product product = new Product();
            product.setCustomisable(true); // Přednastavení pro custom produkt
            product.setActive(true);      // Výchozí aktivní
            // Inicializace konfigurátoru, pokud neexistuje
            if (product.getConfigurator() == null) {
                product.setConfigurator(new ProductConfigurator());
                product.getConfigurator().setProduct(product); // Propojení
                // Zde by bylo vhodné zavolat metodu, která nastaví defaultní hodnoty konfigurátoru
                // např. productService.initializeDefaultConfiguratorValues(product.getConfigurator());
                log.info("Initialized new default ProductConfigurator for custom product.");
            }
            model.addAttribute("product", product);
        }

        // Načtení všech seznamů pro výběr
        loadCommonFormData(model);

        // Přidání prázdného seznamu pro obrázky (pro konzistenci s editací)
        model.addAttribute("sortedImages", new ArrayList<Image>());

        model.addAttribute("pageTitle", "Přidat produkt na míru");
        log.debug("Returning new custom product form.");
        return "admin/product-form-custom";
    }

    @GetMapping
    public String listProducts(@PageableDefault(sort = "name") Pageable pageable, Model model) {
        log.info("Requesting product list view. Pageable: {}", pageable);
        Page<Product> productPage = productService.getAllProducts(pageable); //
        model.addAttribute("productPage", productPage);
        return "admin/products-list"; //
    }

    // --- NOVÉ GET METODY PRO VYTVOŘENÍ ---

    @GetMapping("/new/standard")
    public String showCreateStandardForm(Model model) {
        log.info("Requesting new STANDARD product form.");
        if (!model.containsAttribute("product")) { // Check if redirect didn't already add product
            Product product = new Product(); //
            product.setCustomisable(false); // Přednastavení pro standardní produkt
            model.addAttribute("product", product);
        }
        loadCommonFormData(model);
        model.addAttribute("pageTitle", "Přidat standardní produkt");
        // Předpokládá se existence šablony product-form-standard.html
        return "admin/product-form-standard";
    }


    // --- NOVÉ POST METODY PRO VYTVOŘENÍ ---

    @PostMapping("/standard")
    public String createStandardProduct(@Valid @ModelAttribute("product") Product product,
                                        BindingResult bindingResult,
                                        @RequestParam(value = "availableTaxRates", required = false) Set<Long> taxRateIds,
                                        @RequestParam(value = "availableGlazes", required = false) Set<Long> glazeIds,
                                        @RequestParam(value = "availableDesigns", required = false) Set<Long> designIds,
                                        @RequestParam(value = "availableRoofColors", required = false) Set<Long> roofColorIds,
                                        RedirectAttributes redirectAttributes, Model model) {
        log.info("Attempting to create new STANDARD product: {}", product.getName()); //
        product.setCustomisable(false); // Zajistíme, že je standardní
        product.setConfigurator(null); // Standardní produkt nemá configurator
        product.setAvailableAddons(null); // Standardní produkt nemá custom addons

        // Zpracování asociací (TaxRates, Glazes, Designs, RoofColors) - Může vyhodit IllegalArgumentException
        try {
            handleAssociations(product, taxRateIds, null, glazeIds, designIds, roofColorIds); // Addons jsou null
        } catch (IllegalArgumentException e) {
            // Přidání chyby do bindingResult, pokud asociace selžou (např. chybí TaxRate)
            bindingResult.reject("error.product.associations", e.getMessage());
            log.warn("Association error creating standard product '{}': {}", product.getName(), e.getMessage());
        }


        // Specifická validace pro standardní produkt (např. musí mít základní cenu)
        if (product.getBasePriceCZK() == null) { //
            bindingResult.rejectValue("basePriceCZK", "NotNull", "Základní cena CZK je povinná pro standardní produkt.");
        }
        // Může být přidána kontrola, zda má vybrané standardní atributy, pokud jsou povinné

        if (bindingResult.hasErrors()) {
            log.warn("Validation errors creating standard product: {}", bindingResult.getAllErrors());
            loadCommonFormData(model);
            model.addAttribute("pageTitle", "Přidat standardní produkt (Chyba)");
            model.addAttribute("product", product); // Vrátíme data formuláře zpět
            // Předpokládá se existence šablony product-form-standard.html
            return "admin/product-form-standard";
        }

        try {
            Product savedProduct = productService.createProduct(product); //
            redirectAttributes.addFlashAttribute("successMessage", "Standardní produkt '" + savedProduct.getName() + "' byl úspěšně vytvořen.");
            log.info("Standard product '{}' created successfully with ID: {}", savedProduct.getName(), savedProduct.getId()); //
            return "redirect:/admin/products";
        } catch (IllegalArgumentException | DataIntegrityViolationException e) {
            log.warn("Error creating standard product '{}': {}", product.getName(), e.getMessage());
            // Zpracování specifických chyb (např. duplicitní slug)
            if (e.getMessage().toLowerCase().contains("slug")) {
                bindingResult.rejectValue("slug", "error.product.duplicate.slug", e.getMessage());
            } else if (e.getMessage().toLowerCase().contains("tax rate")) {
                bindingResult.rejectValue("availableTaxRates", "error.product.taxrate", e.getMessage());
            } else {
                model.addAttribute("errorMessage", "Chyba při ukládání: " + e.getMessage());
            }
            loadCommonFormData(model);
            model.addAttribute("pageTitle", "Přidat standardní produkt (Chyba)");
            model.addAttribute("product", product); // Vrátíme data formuláře
            // Předpokládá se existence šablony product-form-standard.html
            return "admin/product-form-standard";
        } catch (Exception e) {
            log.error("Unexpected error creating standard product '{}': {}", product.getName(), e.getMessage(), e);
            loadCommonFormData(model);
            model.addAttribute("pageTitle", "Přidat standardní produkt (Chyba)");
            model.addAttribute("product", product);
            model.addAttribute("errorMessage", "Při vytváření produktu nastala neočekávaná chyba.");
            // Předpokládá se existence šablony product-form-standard.html
            return "admin/product-form-standard";
        }
    }

    @PostMapping("/custom")
    public String createCustomProduct(@Valid @ModelAttribute("product") Product product,
                                      BindingResult bindingResult,
                                      @RequestParam(value = "availableTaxRates", required = false) Set<Long> taxRateIds,
                                      @RequestParam(value = "availableAddons", required = false) Set<Long> addonIds,
                                      RedirectAttributes redirectAttributes, Model model) {
        log.info("Attempting to create new CUSTOM product: {}", product.getName()); //
        product.setCustomisable(true); // Zajistíme, že je custom
        // Standardní atributy by měly být prázdné/null pro custom produkt
        product.setAvailableGlazes(null); //
        product.setAvailableDesigns(null); //
        product.setAvailableRoofColors(null); //
        // Základní cena by měla být null nebo 0 pro custom produkt (cena se počítá dynamicky)
        product.setBasePriceCZK(null); //
        product.setBasePriceEUR(null); //

        // Zpracování asociací (TaxRates, Addons) - Může vyhodit IllegalArgumentException
        try {
            handleAssociations(product, taxRateIds, addonIds, null, null, null); // Glaze, Design, RoofColor jsou null
        } catch (IllegalArgumentException e) {
            // Přidání chyby do bindingResult, pokud asociace selžou (např. chybí TaxRate)
            bindingResult.reject("error.product.associations", e.getMessage());
            log.warn("Association error creating custom product '{}': {}", product.getName(), e.getMessage());
        }


        // Validace konfigurátoru (pokud je customisable)
        if (product.getConfigurator() != null) { //
            // Zde může být dodatečná validace hodnot v konfigurátoru, např. min < max
            BindingResult configuratorResult = new BeanPropertyBindingResult(product.getConfigurator(), "configurator"); //
            // TODO: Implementovat validátor pro ProductConfigurator, pokud je potřeba
            // validator.validate(product.getConfigurator(), configuratorResult);
            if (configuratorResult.hasErrors()) {
                configuratorResult.getAllErrors().forEach(error -> {
                    if (error instanceof FieldError fieldError) {
                        assert fieldError.getDefaultMessage() != null;
                        bindingResult.rejectValue("configurator." + fieldError.getField(), Objects.requireNonNull(fieldError.getCode()), fieldError.getDefaultMessage());
                    } else {
                        bindingResult.addError(new ObjectError("configurator", error.getDefaultMessage()));
                    }
                });
            }
        } else {
            bindingResult.reject("error.product.missing.configurator", "Konfigurátor je povinný pro produkt na míru.");
        }


        if (bindingResult.hasErrors()) {
            log.warn("Validation errors creating custom product: {}", bindingResult.getAllErrors());
            loadCommonFormData(model);
            model.addAttribute("pageTitle", "Přidat produkt na míru (Chyba)");
            model.addAttribute("product", product); // Vrátíme data formuláře
            // Předpokládá se existence šablony product-form-custom.html
            return "admin/product-form-custom";
        }

        try {
            // Vytvoření produktu (ProductService by měl správně nastavit propojení s konfigurátorem)
            Product savedProduct = productService.createProduct(product); //
            redirectAttributes.addFlashAttribute("successMessage", "Produkt na míru '" + savedProduct.getName() + "' byl úspěšně vytvořen.");
            log.info("Custom product '{}' created successfully with ID: {}", savedProduct.getName(), savedProduct.getId()); //
            return "redirect:/admin/products";
        } catch (IllegalArgumentException | DataIntegrityViolationException e) {
            log.warn("Error creating custom product '{}': {}", product.getName(), e.getMessage());
            if (e.getMessage().toLowerCase().contains("slug")) {
                bindingResult.rejectValue("slug", "error.product.duplicate.slug", e.getMessage());
            } else if (e.getMessage().toLowerCase().contains("tax rate")) {
                bindingResult.rejectValue("availableTaxRates", "error.product.taxrate", e.getMessage());
            } else {
                model.addAttribute("errorMessage", "Chyba při ukládání: " + e.getMessage());
            }
            loadCommonFormData(model);
            model.addAttribute("pageTitle", "Přidat produkt na míru (Chyba)");
            model.addAttribute("product", product);
            // Předpokládá se existence šablony product-form-custom.html
            return "admin/product-form-custom";
        } catch (Exception e) {
            log.error("Unexpected error creating custom product '{}': {}", product.getName(), e.getMessage(), e);
            loadCommonFormData(model);
            model.addAttribute("pageTitle", "Přidat produkt na míru (Chyba)");
            model.addAttribute("product", product);
            model.addAttribute("errorMessage", "Při vytváření produktu nastala neočekávaná chyba.");
            // Předpokládá se existence šablony product-form-custom.html
            return "admin/product-form-custom";
        }
    }

    // --- UPRAVENÉ METODY PRO EDITACI ---


    // Jedna POST metoda pro update - rozliší typ podle příchozího productData.isCustomisable()
    @PostMapping("/{id}")
    @Transactional // Potřeba pro načtení a uložení
    public String updateProduct(@PathVariable Long id,
                                @Valid @ModelAttribute("product") Product productData,
                                BindingResult bindingResult,
                                @RequestParam(value = "availableTaxRates", required = false) Set<Long> taxRateIds,
                                @RequestParam(value = "availableAddons", required = false) Set<Long> addonIds,
                                @RequestParam(value = "availableGlazes", required = false) Set<Long> glazeIds,
                                @RequestParam(value = "availableDesigns", required = false) Set<Long> designIds,
                                @RequestParam(value = "availableRoofColors", required = false) Set<Long> roofColorIds,
                                RedirectAttributes redirectAttributes, Model model) {

        log.info("Attempting to update product ID: {}. Is custom from form: {}", id, productData.isCustomisable()); //

        // Načtení existujícího produktu z DB (včetně asociací, pokud je potřeba je zachovat/aktualizovat)
        Optional<Product> existingProductOpt = productService.getProductById(id); //
        if (existingProductOpt.isEmpty()) {
            log.error("Product with ID {} not found for update.", id);
            redirectAttributes.addFlashAttribute("errorMessage", "Produkt s ID " + id + " nenalezen pro úpravu.");
            return "redirect:/admin/products";
        }
        Product existingProduct = existingProductOpt.get();


        // Zpracování asociací na základě ID z formuláře - může vyhodit IllegalArgumentException
        try {
            handleAssociations(productData, taxRateIds, addonIds, glazeIds, designIds, roofColorIds);
        } catch (IllegalArgumentException e) {
            // Přidání chyby do bindingResult, pokud asociace selžou
            bindingResult.reject("error.product.associations", e.getMessage());
            log.warn("Association error updating product ID {}: {}", id, e.getMessage());
        }


        // Synchronizace configuratoru, pokud je custom
        if (productData.isCustomisable()) { //
            if (productData.getConfigurator() != null) { //
                productData.getConfigurator().setProduct(existingProduct); // Správné propojení
                productData.getConfigurator().setId(existingProduct.getId()); // Zajistíme ID
            }
            // ProductService.updateProduct by měl ošetřit, aby se nesmazal existující konfigurátor,
            // pokud productData.getConfigurator() je null, ale existingProduct.isCustomisable() je true.
        } else {
            productData.setConfigurator(null); // Zajistíme, že standardní nemá configurator
        }


        // Dodatečné validace specifické pro typ (Standard vs Custom)
        if (!productData.isCustomisable() && productData.getBasePriceCZK() == null) { //
            bindingResult.rejectValue("basePriceCZK", "NotNull", "Základní cena CZK je povinná pro standardní produkt.");
        }
        if (productData.isCustomisable()) { //
            // Validace configuratoru, pokud je potřeba
            // TODO: Implementovat validátor pro ProductConfigurator, pokud je potřeba
            // validator.validate(productData.getConfigurator(), bindingResult);
        }

        if (bindingResult.hasErrors()) {
            log.warn("Validation errors updating product {}: {}", id, bindingResult.getAllErrors());
            loadCommonFormData(model);
            // DŮLEŽITÉ: Nastavit productData zpět do modelu, aby se formulář správně zobrazil s chybami
            // Musíme ale zachovat asociace načtené z existingProduct, pokud productData je nemá (např. obrázky)
            productData.setImages(existingProduct.getImages()); //
            // Podobně pro další lazy-loaded nebo speciálně spravované kolekce, které nejsou přímo ve formuláři

            model.addAttribute("product", productData);
            model.addAttribute("pageTitle", "Upravit produkt (Chyba)");
            // Vrátíme správný formulář podle typu produktu z dat formuláře
            return productData.isCustomisable() ? "admin/product-form-custom" : "admin/product-form-standard";
        }

        try {
            // ProductService.updateProduct porovná productData s existujícím a uloží změny
            // Předáme existingProduct (načtený z DB) a productData (data z formuláře)
            productService.updateProduct(id, productData, existingProduct); //
            redirectAttributes.addFlashAttribute("successMessage", "Produkt '" + productData.getName() + "' byl úspěšně aktualizován."); //
            log.info("Product ID {} updated successfully.", id);
            return "redirect:/admin/products";
        } catch (EntityNotFoundException e) {
            // Tohle by nemělo nastat, protože jsme existingProduct načetli výše
            log.error("Product ID {} disappeared during update process!", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", "Produkt mezitím zmizel.");
            return "redirect:/admin/products";
        } catch (IllegalArgumentException | DataIntegrityViolationException e) {
            log.warn("Error updating product ID {}: {}", id, e.getMessage());
            if (e.getMessage().toLowerCase().contains("slug")) {
                bindingResult.rejectValue("slug", "error.product.duplicate.slug", e.getMessage());
            } else if (e.getMessage().toLowerCase().contains("tax rate")) {
                bindingResult.rejectValue("availableTaxRates", "error.product.taxrate", e.getMessage());
            } else {
                model.addAttribute("errorMessage", "Chyba při ukládání: " + e.getMessage());
            }
            loadCommonFormData(model);
            // Znovu načteme asociace do productData pro zobrazení
            productData.setImages(existingProduct.getImages()); //

            model.addAttribute("product", productData); // Vrátíme data z formuláře
            model.addAttribute("pageTitle", "Upravit produkt (Chyba)");
            return productData.isCustomisable() ? "admin/product-form-custom" : "admin/product-form-standard";
        } catch (Exception e) {
            log.error("Unexpected error updating product ID {}: {}", id, e.getMessage(), e);
            loadCommonFormData(model);
            // Znovu načteme asociace do productData pro zobrazení
            productData.setImages(existingProduct.getImages()); //

            model.addAttribute("product", productData);
            model.addAttribute("pageTitle", "Upravit produkt (Chyba)");
            model.addAttribute("errorMessage", "Při aktualizaci produktu nastala neočekávaná chyba.");
            return productData.isCustomisable() ? "admin/product-form-custom" : "admin/product-form-standard";
        }
    }

    private void handleAssociations(Product product,
                                    Set<Long> taxRateIds, Set<Long> addonIds,
                                    Set<Long> glazeIds, Set<Long> designIds,
                                    Set<Long> roofColorIds) {

        log.debug(">>> [handleAssociations] Vstupuji. Produkt ID: {}, Custom: {}. Příchozí ID: TaxRates={}, Addons={}, Glazes={}, Designs={}, RoofColors={}",
                product.getId() != null ? product.getId() : "NOVÝ",
                product.isCustomisable(),
                taxRateIds, addonIds, glazeIds, designIds, roofColorIds);

        // --- Daňové sazby (povinné pro všechny typy) ---
        if (taxRateIds != null && !taxRateIds.isEmpty()) {
            Set<TaxRate> rates = taxRateIds.stream()
                    .map(taxRateService::getTaxRateById) // Vrací Optional<TaxRate>
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toSet());
            if (rates.isEmpty() && !taxRateIds.isEmpty()) {
                // Pokud jsme měli IDčka, ale žádné jsme nenašli, je to chyba
                log.error("Nebyly nalezeny žádné platné TaxRates pro poskytnutá ID: {}", taxRateIds);
                throw new IllegalArgumentException("Produkt musí mít přiřazenu alespoň jednu platnou daňovou sazbu (zadaná ID nebyla nalezena).");
            }
            // Inicializujeme, pokud je null, před přidáním
            if (product.getAvailableTaxRates() == null) {
                product.setAvailableTaxRates(new HashSet<>());
            }
            product.getAvailableTaxRates().clear(); // Vyčistíme staré
            product.getAvailableTaxRates().addAll(rates); // Přidáme nové
            log.debug("[handleAssociations] Přiřazené TaxRates (ID): {}", rates.stream().map(TaxRate::getId).collect(Collectors.toSet()));
        } else {
            log.error("Nebyly poskytnuty žádné TaxRate IDs pro přiřazení k produktu.");
            // Pokud jsou taxRateIds null nebo prázdné, vyvoláme chybu (validace @NotEmpty by to měla také chytit)
            throw new IllegalArgumentException("Musí být vybrána alespoň jedna daňová sazba.");
        }

        // --- Doplňky (Addons) - pouze pro custom ---
        if (product.isCustomisable()) {
            if (addonIds != null) { // Umožníme i prázdný set pro odebrání všech
                Set<Addon> addons = addonIds.stream()
                        .map(addonsService::getAddonById) // Předpoklad: vrací Optional<Addon>
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toSet());
                // Inicializujeme, pokud je null, před přidáním
                if (product.getAvailableAddons() == null) {
                    product.setAvailableAddons(new HashSet<>());
                }
                product.getAvailableAddons().clear();
                product.getAvailableAddons().addAll(addons);
                log.debug("[handleAssociations] Přiřazené Addons (ID): {}", addons.stream().map(Addon::getId).collect(Collectors.toSet()));
            } else {
                // Pokud addonIds je null (což by nemělo nastat s @RequestParam), pro jistotu vyčistíme
                if (product.getAvailableAddons() != null) {
                    product.getAvailableAddons().clear();
                } else {
                    product.setAvailableAddons(new HashSet<>()); // Nebo Set.of() pokud preferuješ nemodifikovatelný
                }
                log.debug("[handleAssociations] Nebyly poskytnuty Addon IDs, kolekce vyčištěna/inicializována.");
            }
        } else {
            // Pro standardní produkt zajistíme, že nemá žádné custom doplňky
            if (product.getAvailableAddons() == null) {
                product.setAvailableAddons(new HashSet<>());
            } else {
                product.getAvailableAddons().clear();
            }
            log.debug("[handleAssociations] Standardní produkt - kolekce Addons vyčištěna.");
        }

        // --- Designy, Lazury, Barvy střechy ---
        // Zpracováváme je pro *oba* typy produktů, pokud přijdou ID z formuláře.
        // Formulář `product-form-standard.html` by je neměl posílat,
        // formulář `product-form-custom.html` je posílat může.

        // Glazes (Lazury)
        if (glazeIds != null) { // Zpracujeme, pokud přišel parametr (i prázdný)
            Set<Glaze> glazes = glazeIds.stream()
                    .map(glazeService::findById) // Předpoklad: vrací Optional<Glaze>
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toSet());
            if (product.getAvailableGlazes() == null) {
                product.setAvailableGlazes(new HashSet<>());
            }
            product.getAvailableGlazes().clear();
            product.getAvailableGlazes().addAll(glazes);
            log.debug("[handleAssociations] Přiřazené Glazes (ID): {}", glazes.stream().map(Glaze::getId).collect(Collectors.toSet()));
        } else {
            // Pokud parametr nepřišel vůbec, necháme kolekci být (pro update)
            // nebo inicializujeme pro nový produkt
            if (product.getAvailableGlazes() == null) {
                product.setAvailableGlazes(new HashSet<>());
            }
            log.debug("[handleAssociations] Nebyly poskytnuty Glaze IDs, kolekce ponechána/inicializována.");
        }

        // Designs
        if (designIds != null) {
            Set<Design> designs = designIds.stream()
                    .map(designService::findById) // Předpoklad: vrací Optional<Design>
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toSet());
            if (product.getAvailableDesigns() == null) {
                product.setAvailableDesigns(new HashSet<>());
            }
            product.getAvailableDesigns().clear();
            product.getAvailableDesigns().addAll(designs);
            log.debug("[handleAssociations] Přiřazené Designs (ID): {}", designs.stream().map(Design::getId).collect(Collectors.toSet()));
        } else {
            if (product.getAvailableDesigns() == null) {
                product.setAvailableDesigns(new HashSet<>());
            }
            log.debug("[handleAssociations] Nebyly poskytnuty Design IDs, kolekce ponechána/inicializována.");
        }

        // RoofColors (Barvy střechy)
        if (roofColorIds != null) {
            Set<RoofColor> roofColors = roofColorIds.stream()
                    .map(roofColorService::findById) // Předpoklad: vrací Optional<RoofColor>
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toSet());
            if (product.getAvailableRoofColors() == null) {
                product.setAvailableRoofColors(new HashSet<>());
            }
            product.getAvailableRoofColors().clear();
            product.getAvailableRoofColors().addAll(roofColors);
            log.debug("[handleAssociations] Přiřazené RoofColors (ID): {}", roofColors.stream().map(RoofColor::getId).collect(Collectors.toSet()));
        } else {
            if (product.getAvailableRoofColors() == null) {
                product.setAvailableRoofColors(new HashSet<>());
            }
            log.debug("[handleAssociations] Nebyly poskytnuty RoofColor IDs, kolekce ponechána/inicializována.");
        }

        log.debug("<<< [handleAssociations] Opouštím. Produkt ID: {}", product.getId() != null ? product.getId() : "NOVÝ");
    }


    // *** OPRAVENÁ METODA UPLOADIMAGE ***
    @PostMapping("/{productId}/images/upload")
    @ResponseBody // Důležité pro REST odpověď
    // *** ZMĚNA ZDE: Návratový typ je nyní ResponseEntity<ImageDto> ***
    public ResponseEntity<ImageDto> uploadImage(@PathVariable Long productId,
                                                @RequestParam("imageFile") MultipartFile imageFile,
                                                @RequestParam(required = false) String altText,
                                                @RequestParam(required = false) String titleText,
                                                @RequestParam(required = false) Integer displayOrder) {
        log.info("Attempting to upload image for product ID: {}", productId);
        if (imageFile.isEmpty()) {
            // Vracíme chybu 400 Bad Request s JSON tělem
            return ResponseEntity.badRequest().body(createErrorDto("Vyberte prosím soubor k nahrání."));
        }
        try {
            Image savedImage = productService.addImageToProduct(productId, imageFile, altText, titleText, displayOrder);
            log.info("Image successfully uploaded for product {}, image ID: {}", productId, savedImage.getId());
            // Vracíme ImageDto s daty uloženého obrázku a statusem 200 OK
            ImageDto imageDto = new ImageDto(savedImage);
            return ResponseEntity.ok(imageDto);
        } catch (IOException | IllegalArgumentException | EntityNotFoundException e) {
            log.error("Failed to store image file for product {}: {}", productId, e.getMessage(), e);
            // Vracíme JSON s chybou a statusem 500 Internal Server Error
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorDto("Nahrání obrázku selhalo: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error uploading image for product {}: {}", productId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorDto("Neočekávaná chyba při nahrávání obrázku."));
        }
    }
    // *** KONEC OPRAVENÉ METODY UPLOADIMAGE ***

    // Pomocná metoda pro vytvoření chybového DTO (místo Map)
    private ImageDto createErrorDto(String message) {
        ImageDto errorDto = new ImageDto();
        // Můžeme přidat pole pro chybu do ImageDto nebo vytvořit specifické ErrorDto
        // Pro jednoduchost zde nevracíme nic specifického, spoléháme na HTTP status
        log.warn("Returning error DTO (though currently empty) with message: {}", message); // Logování pro debug
        return errorDto; // Vracíme prázdné DTO, JS by měl kontrolovat status
        // Alternativně: Přidat pole `private String errorMessage;` do ImageDto a nastavit ho zde.
    }
    // Příklad úpravy deleteImage
    @PostMapping("/images/{imageId}/delete")
    @ResponseBody // Důležité pro AJAX odpověď
    public ResponseEntity<Void> deleteImage(@PathVariable Long imageId) { // Odebrán RedirectAttributes
        log.warn("Attempting to delete image with ID: {}", imageId);
        try {
            // Získání ID produktu není nutné pro samotnou odpověď AJAXu,
            // ale může být užitečné pro logování nebo jiné operace.
            productService.deleteImage(imageId); // Zavolá service metodu pro smazání
            log.info("Image ID {} deleted successfully via AJAX.", imageId);
            return ResponseEntity.ok().build(); // Nebo noContent()
        } catch (EntityNotFoundException e) {
            log.warn("Cannot delete image ID {}. Not found.", imageId, e);
            // Můžeš vrátit i text v body(), pokud chceš zobrazit detailnější chybu v JS
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error deleting image ID {}: {}", imageId, e.getMessage(), e);
            // Můžeš vrátit i text v body()
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        // Přesměrování zde není potřeba
    }

    @PostMapping("/images/update-order")
    @ResponseBody // Důležité
    public ResponseEntity<?> updateImageOrder(@RequestBody ImageOrderUpdateRequest request) { // Přijímá JSON
        log.info("Updating image order via AJAX for product ID: {}", request.getProductId());
        try {
            // Zavolej upravenou service metodu, která přijme mapu ID->pořadí
            productService.updateImageDisplayOrder(request.getProductId(), request.getOrderMap());
            log.info("Image order updated successfully via AJAX for product ID: {}", request.getProductId());
            return ResponseEntity.ok().body("Pořadí obrázků aktualizováno."); // Můžeš poslat zprávu zpět
        } catch (EntityNotFoundException e) {
            log.warn("Product ID {} not found when updating image order via AJAX.", request.getProductId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Produkt nenalezen.");
        } catch (Exception e) {
            log.error("Error updating image order via AJAX for product {}: {}", request.getProductId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Aktualizace pořadí selhala.");
        }
    }

    // --- Metoda pro smazání produktu (soft delete) ---
    @PostMapping("/{id}/delete")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.warn("Attempting to SOFT DELETE product ID: {}", id);
        try {
            productService.deleteProduct(id); // Zavolá soft delete
            redirectAttributes.addFlashAttribute("successMessage", "Produkt byl úspěšně deaktivován.");
            log.info("Product ID {} successfully deactivated.", id);
        } catch (EntityNotFoundException e) {
            log.warn("Cannot deactivate product. Product not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error deactivating product ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Při deaktivaci produktu nastala chyba: " + e.getMessage());
        }
        return "redirect:/admin/products";
    }
}