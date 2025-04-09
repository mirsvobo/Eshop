// src/main/java/org/example/eshop/admin/controller/AdminProductController.java

package org.example.eshop.admin.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.eshop.model.*; // Model importy
import org.example.eshop.repository.AddonsRepository;
import org.example.eshop.repository.DesignRepository;
import org.example.eshop.repository.GlazeRepository;
import org.example.eshop.repository.RoofColorRepository;
import org.example.eshop.service.*; // Service importy
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.HashSet; // <-- Zajištěn import
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/products")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private static final Logger log = LoggerFactory.getLogger(AdminProductController.class);

    private final ProductService productService;
    private final TaxRateService taxRateService;
    private final DesignRepository designRepository;
    private final GlazeRepository glazeRepository;
    private final RoofColorRepository roofColorRepository;
    private final AddonsRepository addonsRepository;

    @Autowired
    public AdminProductController(ProductService productService,
                                  TaxRateService taxRateService,
                                  DesignRepository designRepository,
                                  GlazeRepository glazeRepository,
                                  RoofColorRepository roofColorRepository,
                                  AddonsRepository addonsRepository) {
        this.productService = productService;
        this.taxRateService = taxRateService;
        this.designRepository = designRepository;
        this.glazeRepository = glazeRepository;
        this.roofColorRepository = roofColorRepository;
        this.addonsRepository = addonsRepository;
    }

    @ModelAttribute("currentUri")
    public String getCurrentUri(HttpServletRequest request) {
        return request.getRequestURI();
    }

    // Pomocná metoda pro přidání společných dat do modelu pro formulář
    private void addCommonFormAttributes(Model model) {
        List<TaxRate> taxRates = taxRateService.getAllTaxRates();
        model.addAttribute("allTaxRates", taxRates);
    }

    // Pomocná metoda pro načtení všech dostupných asociací
    private void addAssociationAttributesToModel(Model model) {
        model.addAttribute("allDesigns", designRepository.findAll(Sort.by("name")));
        model.addAttribute("allGlazes", glazeRepository.findAll(Sort.by("name")));
        model.addAttribute("allRoofColors", roofColorRepository.findAll(Sort.by("name")));
        model.addAttribute("allAddons", addonsRepository.findAll(Sort.by("name"))); // Pro custom produkty
    }

    @GetMapping
    public String listProducts(Model model,
                               @PageableDefault(size = 15, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
                               @RequestParam Optional<String> name,
                               @RequestParam Optional<Boolean> active) {
        String nameFilter = name.filter(StringUtils::hasText).orElse(null);
        Boolean activeFilter = active.orElse(null);
        log.info("Requesting admin product list view. Filters: name={}, active={}. Pageable: {}", nameFilter, activeFilter, pageable);
        try {
            Page<Product> productPage;
            // TODO: Implementovat lepší filtrování v service/repo
            if (nameFilter != null || activeFilter != null) {
                log.warn("Product filtering not fully implemented yet. Showing basic list.");
                productPage = productService.getAllProducts(pageable);
            } else {
                productPage = productService.getAllProducts(pageable);
            }
            model.addAttribute("productPage", productPage);
            model.addAttribute("nameFilter", nameFilter);
            model.addAttribute("activeFilter", activeFilter);
            String currentSort = pageable.getSort().stream().map(order -> order.getProperty() + "," + order.getDirection()).collect(Collectors.joining("&sort="));
            if (!StringUtils.hasText(currentSort) || ",".equals(currentSort)) { currentSort = "id,DESC"; }
            model.addAttribute("currentSort", currentSort);
        } catch (Exception e) {
            log.error("Error fetching products for admin view: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Nepodařilo se načíst produkty.");
            model.addAttribute("productPage", Page.empty(pageable));
            model.addAttribute("currentSort", "id,DESC");
        }
        return "admin/products-list";
    }

    /**
     * Zobrazí formulář pro vytvoření nového produktu.
     */
    @GetMapping("/new")
    public String showCreateProductForm(Model model) {
        log.info("Requesting new product form.");
        Product product = new Product();
        // Inicializace kolekcí, aby nebyly null v šabloně
        product.setAvailableDesigns(new HashSet<>());
        product.setAvailableGlazes(new HashSet<>());
        product.setAvailableRoofColors(new HashSet<>());
        product.setAvailableAddons(new HashSet<>());

        model.addAttribute("product", product);
        addCommonFormAttributes(model);
        addAssociationAttributesToModel(model);
        model.addAttribute("pageTitle", "Vytvořit nový produkt");
        return "admin/product-form";
    }

    /**
     * Zobrazí formulář pro úpravu existujícího produktu.
     */
    @GetMapping("/{id}/edit")
    @Transactional(readOnly = true)
    public String showEditProductForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Requesting edit form for product ID: {}", id);
        try {
            Product product = productService.getProductById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Produkt s ID " + id + " nenalezen."));

            // Explicitní inicializace pro formulář (pro jistotu, i když @Transactional pomáhá)
            Hibernate.initialize(product.getAvailableDesigns());
            Hibernate.initialize(product.getAvailableGlazes());
            Hibernate.initialize(product.getAvailableRoofColors());
            Hibernate.initialize(product.getAvailableAddons());
            if (product.getConfigurator() != null) {
                Hibernate.initialize(product.getConfigurator());
            }

            model.addAttribute("product", product);
            addCommonFormAttributes(model);
            addAssociationAttributesToModel(model);
            model.addAttribute("pageTitle", "Upravit produkt: " + product.getName());
            return "admin/product-form";
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


    /**
     * Zpracuje vytvoření nového produktu (včetně asociací).
     */
    @PostMapping
    public String createProduct(@Valid @ModelAttribute("product") Product product,
                                @RequestParam(required = false) List<Long> designIds,
                                @RequestParam(required = false) List<Long> glazeIds,
                                @RequestParam(required = false) List<Long> roofColorIds,
                                @RequestParam(required = false) List<Long> addonIds,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        log.info("Attempting to create new product: {}", product.getName());
        // Nastavení asociací podle ID z requestu
        updateAssociationsFromIds(product, designIds, glazeIds, roofColorIds, addonIds);

        if (bindingResult.hasErrors()) {
            log.warn("Validation errors creating product: {}", bindingResult.getAllErrors());
            // --- OPRAVA: Volání metody ---
            ensureCollectionsInitialized(product);
            // --- KONEC OPRAVY ---
            addCommonFormAttributes(model);
            addAssociationAttributesToModel(model);
            model.addAttribute("pageTitle", "Vytvořit nový produkt");
            return "admin/product-form"; // VRACÍ VIEW SE STATUSEM 200 OK
        }
        try {
            Product savedProduct = productService.createProduct(product);
            redirectAttributes.addFlashAttribute("successMessage", "Produkt '" + savedProduct.getName() + "' byl úspěšně vytvořen.");
            log.info("Product '{}' created successfully with ID: {}", savedProduct.getName(), savedProduct.getId());
            return "redirect:/admin/products";
        } catch (IllegalArgumentException e) {
            log.warn("Error creating product '{}': {}", product.getName(), e.getMessage());
            bindingResult.reject("error.product", e.getMessage());
            // --- OPRAVA: Volání metody ---
            ensureCollectionsInitialized(product);
            // --- KONEC OPRAVY ---
            addCommonFormAttributes(model);
            addAssociationAttributesToModel(model);
            model.addAttribute("pageTitle", "Vytvořit nový produkt");
            return "admin/product-form"; // VRACÍ VIEW SE STATUSEM 200 OK
        }
        catch (Exception e) {
            log.error("Unexpected error creating product '{}': {}", product.getName(), e.getMessage(), e);
            // --- OPRAVA: Volání metody ---
            ensureCollectionsInitialized(product);
            // --- KONEC OPRAVY ---
            addCommonFormAttributes(model);
            addAssociationAttributesToModel(model);
            model.addAttribute("errorMessage", "Při vytváření produktu nastala neočekávaná chyba: " + e.getMessage());
            model.addAttribute("pageTitle", "Vytvořit nový produkt");
            return "admin/product-form"; // VRACÍ VIEW SE STATUSEM 200 OK
        }
    }


    /**
     * Zpracuje úpravu existujícího produktu (včetně asociací).
     */
    @PostMapping("/{id}")
    public String updateProduct(@PathVariable Long id,
                                @Valid @ModelAttribute("product") Product productData,
                                BindingResult bindingResult,
                                @RequestParam(required = false) List<Long> designIds,
                                @RequestParam(required = false) List<Long> glazeIds,
                                @RequestParam(required = false) List<Long> roofColorIds,
                                @RequestParam(required = false) List<Long> addonIds,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        log.info("Attempting to update product ID: {}", id);
        productData.setId(id); // Nastavíme ID pro případné zobrazení chyb

        if (bindingResult.hasErrors()) {
            log.warn("Validation errors updating product {}: {}", id, bindingResult.getAllErrors());
            // --- OPRAVA: Volání metody ---
            ensureCollectionsInitialized(productData);
            // --- KONEC OPRAVY ---
            addCommonFormAttributes(model);
            addAssociationAttributesToModel(model);
            model.addAttribute("pageTitle", "Upravit produkt (chyba)");
            return "admin/product-form"; // VRACÍ VIEW SE STATUSEM 200 OK
        }

        try {
            // Načteme existující produkt jen pro nastavení asociací PŘED voláním service
            Product existingProduct = productService.getProductById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Produkt s ID " + id + " nenalezen pro aktualizaci."));

            // Aktualizujeme asociace na načteném existujícím produktu
            updateAssociationsFromIds(existingProduct, designIds, glazeIds, roofColorIds, addonIds);

            // Voláme service s daty z formuláře a s načtenou/upravenou entitou
            Product updatedProduct = productService.updateProduct(id, productData, existingProduct)
                    .orElseThrow(() -> new EntityNotFoundException("Produkt s ID " + id + " nenalezen pro aktualizaci (po pokusu o update)."));

            redirectAttributes.addFlashAttribute("successMessage", "Produkt '" + updatedProduct.getName() + "' byl úspěšně aktualizován.");
            log.info("Product ID {} updated successfully.", id);
            return "redirect:/admin/products";

        } catch (EntityNotFoundException e) {
            log.warn("Cannot update product. Product not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/products";
        } catch (IllegalArgumentException e) {
            log.warn("Error updating product ID {}: {}", id, e.getMessage());
            // --- OPRAVA: Volání metody ---
            ensureCollectionsInitialized(productData);
            // --- KONEC OPRAVY ---
            addCommonFormAttributes(model);
            addAssociationAttributesToModel(model);
            model.addAttribute("pageTitle", "Upravit produkt (chyba)");
            model.addAttribute("errorMessage", e.getMessage());
            return "admin/product-form"; // VRACÍ VIEW SE STATUSEM 200 OK
        }
        catch (Exception e) {
            log.error("Unexpected error updating product ID {}: {}", id, e.getMessage(), e);
            // --- OPRAVA: Volání metody ---
            ensureCollectionsInitialized(productData);
            // --- KONEC OPRAVY ---
            addCommonFormAttributes(model);
            addAssociationAttributesToModel(model);
            model.addAttribute("errorMessage", "Při aktualizaci produktu nastala neočekávaná chyba: " + e.getMessage());
            model.addAttribute("pageTitle", "Upravit produkt (chyba)");
            return "admin/product-form"; // VRACÍ VIEW SE STATUSEM 200 OK
        }
    }

    // Pomocná metoda pro aktualizaci asociací na Product entitě
    private void updateAssociationsFromIds(Product product, List<Long> designIds, List<Long> glazeIds, List<Long> roofColorIds, List<Long> addonIds) {
        Set<Design> designs = CollectionUtils.isEmpty(designIds) ? Collections.emptySet() : new HashSet<>(designRepository.findAllById(designIds));
        Set<Glaze> glazes = CollectionUtils.isEmpty(glazeIds) ? Collections.emptySet() : new HashSet<>(glazeRepository.findAllById(glazeIds));
        Set<RoofColor> roofColors = CollectionUtils.isEmpty(roofColorIds) ? Collections.emptySet() : new HashSet<>(roofColorRepository.findAllById(roofColorIds));
        Set<Addon> addons = CollectionUtils.isEmpty(addonIds) ? Collections.emptySet() : new HashSet<>(addonsRepository.findAllById(addonIds));

        product.setAvailableDesigns(designs);
        product.setAvailableGlazes(glazes);
        product.setAvailableRoofColors(roofColors);
        product.setAvailableAddons(addons);
        log.debug("Updated associations for product {}: designs={}, glazes={}, roofColors={}, addons={}",
                product.getId(), designIds, glazeIds, roofColorIds, addonIds);
    }

    // --- OPRAVA: Nová pomocná metoda ---
    private void ensureCollectionsInitialized(Product product) {
        if (product == null) return;
        if (product.getAvailableDesigns() == null) product.setAvailableDesigns(new HashSet<>());
        if (product.getAvailableGlazes() == null) product.setAvailableGlazes(new HashSet<>());
        if (product.getAvailableRoofColors() == null) product.setAvailableRoofColors(new HashSet<>());
        if (product.getAvailableAddons() == null) product.setAvailableAddons(new HashSet<>());
    }
    // --- KONEC OPRAVY ---

    @PostMapping("/{id}/delete")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.warn("Attempting to SOFT DELETE product ID: {}", id);
        try {
            productService.deleteProduct(id);
            redirectAttributes.addFlashAttribute("successMessage", "Produkt byl úspěšně deaktivován.");
            log.info("Product ID {} successfully deactivated (soft deleted).", id);
        } catch (EntityNotFoundException e) {
            log.warn("Cannot delete product. Product not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error deactivating product ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Při deaktivaci produktu nastala chyba: " + e.getMessage());
        }
        return "redirect:/admin/products";
    }
}