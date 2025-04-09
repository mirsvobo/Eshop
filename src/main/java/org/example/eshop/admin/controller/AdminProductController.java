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
import org.springframework.transaction.annotation.Transactional; // <-- Přidat pro updateProduct
import org.springframework.ui.Model;
import org.springframework.util.CollectionUtils; // <-- Přidat
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections; // <-- Přidat
import java.util.HashSet; // <-- Přidat
import java.util.List;
import java.util.Optional;
import java.util.Set; // <-- Přidat
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/products")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private static final Logger log = LoggerFactory.getLogger(AdminProductController.class);

    private final ProductService productService;
    private final TaxRateService taxRateService;
    // --- Přidané závislosti ---
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

    // --- NOVÁ Pomocná metoda pro načtení asociací ---
    private void addAssociationAttributesToModel(Model model) {
        model.addAttribute("allDesigns", designRepository.findAll(Sort.by("name")));
        model.addAttribute("allGlazes", glazeRepository.findAll(Sort.by("name")));
        model.addAttribute("allRoofColors", roofColorRepository.findAll(Sort.by("name")));
        model.addAttribute("allAddons", addonsRepository.findAll(Sort.by("name"))); // Pro custom produkty
    }

    // --- Seznam produktů (beze změny) ---
    @GetMapping
    public String listProducts(Model model,
                               @PageableDefault(size = 15, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
                               @RequestParam Optional<String> name,
                               @RequestParam Optional<Boolean> active) {
        // ... (kód metody listProducts zůstává stejný) ...
        String nameFilter = name.filter(StringUtils::hasText).orElse(null);
        Boolean activeFilter = active.orElse(null);
        log.info("Requesting admin product list view. Filters: name={}, active={}. Pageable: {}", nameFilter, activeFilter, pageable);
        try {
            Page<Product> productPage;
            // TODO: Implementovat lepší filtrování v service/repo
            if (nameFilter != null || activeFilter != null) { // Zjednodušená podmínka
                log.warn("Product filtering not fully implemented yet. Showing basic list.");
                productPage = productService.getAllProducts(pageable);
            }
            else {
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
        model.addAttribute("product", new Product());
        addCommonFormAttributes(model);
        addAssociationAttributesToModel(model); // << Přidat asociace
        model.addAttribute("pageTitle", "Vytvořit nový produkt");
        return "admin/product-form";
    }

    /**
     * Zobrazí formulář pro úpravu existujícího produktu.
     */
    @GetMapping("/{id}/edit")
    @Transactional(readOnly = true) // Pro lazy loading asociací produktu
    public String showEditProductForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Requesting edit form for product ID: {}", id);
        try {
            Product product = productService.getProductById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Produkt s ID " + id + " nenalezen."));

            // Explicitní inicializace pro formulář (není nutně potřeba s @Transactional, ale jistota)
            Hibernate.initialize(product.getAvailableDesigns());
            Hibernate.initialize(product.getAvailableGlazes());
            Hibernate.initialize(product.getAvailableRoofColors());
            Hibernate.initialize(product.getAvailableAddons());
            Hibernate.initialize(product.getConfigurator()); // Pokud editujeme i konfigurátor

            model.addAttribute("product", product);
            addCommonFormAttributes(model);
            addAssociationAttributesToModel(model); // << Přidat všechny dostupné asociace
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
    // Potřeba pro práci s asociacemi
    public String createProduct(@Valid @ModelAttribute("product") Product product,
                                // Parametry pro asociace
                                @RequestParam(required = false) List<Long> designIds,
                                @RequestParam(required = false) List<Long> glazeIds,
                                @RequestParam(required = false) List<Long> roofColorIds,
                                @RequestParam(required = false) List<Long> addonIds,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        log.info("Attempting to create new product: {}", product.getName());
        updateAssociationsFromIds(product, designIds, glazeIds, roofColorIds, addonIds); // Aktualizujeme sety v objektu product

        if (bindingResult.hasErrors()) {
            log.warn("Validation errors creating product: {}", bindingResult.getAllErrors());
            addCommonFormAttributes(model);
            addAssociationAttributesToModel(model); // Znovu přidat data pro selecty/checkboxy
            model.addAttribute("pageTitle", "Vytvořit nový produkt");
            return "admin/product-form"; // Zobrazit formulář znovu s chybami
        }
        try {
            // ProductService by měl být schopný uložit produkt i s aktualizovanými asociacemi
            Product savedProduct = productService.createProduct(product);
            redirectAttributes.addFlashAttribute("successMessage", "Produkt '" + savedProduct.getName() + "' byl úspěšně vytvořen.");
            log.info("Product '{}' created successfully with ID: {}", savedProduct.getName(), savedProduct.getId());
            return "redirect:/admin/products";
        } catch (IllegalArgumentException e) {
            log.warn("Error creating product '{}': {}", product.getName(), e.getMessage());
            // Chybovou hlášku pro duplicitní slug by měl ideálně vracet ProductService
            bindingResult.reject("error.product", e.getMessage()); // Přidání globální chyby
            addCommonFormAttributes(model);
            addAssociationAttributesToModel(model);
            model.addAttribute("pageTitle", "Vytvořit nový produkt");
            return "admin/product-form";
        }
        catch (Exception e) {
            log.error("Unexpected error creating product '{}': {}", product.getName(), e.getMessage(), e);
            addCommonFormAttributes(model);
            addAssociationAttributesToModel(model);
            model.addAttribute("errorMessage", "Při vytváření produktu nastala neočekávaná chyba: " + e.getMessage());
            model.addAttribute("pageTitle", "Vytvořit nový produkt");
            return "admin/product-form";
        }
    }


    /**
     * Zpracuje úpravu existujícího produktu (včetně asociací).
     */
    @PostMapping("/{id}")
    // Potřeba pro načtení a aktualizaci asociací
    public String updateProduct(@PathVariable Long id,
                                @Valid @ModelAttribute("product") Product productData,
                                BindingResult bindingResult,
                                // Parametry pro asociace
                                @RequestParam(required = false) List<Long> designIds,
                                @RequestParam(required = false) List<Long> glazeIds,
                                @RequestParam(required = false) List<Long> roofColorIds,
                                @RequestParam(required = false) List<Long> addonIds,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        log.info("Attempting to update product ID: {}", id);
        productData.setId(id); // Pro případ zobrazení chyb

        if (bindingResult.hasErrors()) {
            log.warn("Validation errors updating product {}: {}", id, bindingResult.getAllErrors());
            addCommonFormAttributes(model);
            addAssociationAttributesToModel(model); // Přidáme data pro selecty/checkboxy
            model.addAttribute("pageTitle", "Upravit produkt (chyba)");
            // Musíme vrátit formulář, bindingResult obsahuje chyby
            return "admin/product-form";
        }

        try {
            // Načteme existující produkt v rámci transakce
            Product existingProduct = productService.getProductById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Produkt s ID " + id + " nenalezen pro aktualizaci."));

            // Aktualizujeme asociace na existujícím produktu
            updateAssociationsFromIds(existingProduct, designIds, glazeIds, roofColorIds, addonIds);

            // ProductService aktualizuje ostatní pole a uloží včetně asociací
            Product updatedProduct = productService.updateProduct(id, productData, existingProduct) // Přeposíláme i existující s upravenými asociacemi
                    .orElseThrow(() -> new EntityNotFoundException("Produkt s ID " + id + " nenalezen pro aktualizaci (po pokusu o update)."));


            redirectAttributes.addFlashAttribute("successMessage", "Produkt '" + updatedProduct.getName() + "' byl úspěšně aktualizován.");
            log.info("Product ID {} updated successfully.", id);
            return "redirect:/admin/products"; // Nebo zpět na editaci: "redirect:/admin/products/" + id + "/edit";

        } catch (EntityNotFoundException e) {
            log.warn("Cannot update product. Product not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/products";
        } catch (IllegalArgumentException e) { // Např. pro duplicitní slug
            log.warn("Error updating product ID {}: {}", id, e.getMessage());
            // Jelikož používáme @ModelAttribute, chyby by se měly navázat na něj
            // bindingResult.rejectValue("slug", "duplicate.slug", e.getMessage()); // Můžeme přidat globální
            addCommonFormAttributes(model);
            addAssociationAttributesToModel(model);
            model.addAttribute("pageTitle", "Upravit produkt (chyba)");
            model.addAttribute("errorMessage", e.getMessage()); // Zobrazíme obecnou chybu
            return "admin/product-form"; // Zobrazit formulář znovu
        }
        catch (Exception e) {
            log.error("Unexpected error updating product ID {}: {}", id, e.getMessage(), e);
            addCommonFormAttributes(model);
            addAssociationAttributesToModel(model);
            model.addAttribute("errorMessage", "Při aktualizaci produktu nastala neočekávaná chyba: " + e.getMessage());
            model.addAttribute("pageTitle", "Upravit produkt (chyba)");
            return "admin/product-form";
        }
    }

    // Pomocná metoda pro aktualizaci asociací na Product entitě
    private void updateAssociationsFromIds(Product product, List<Long> designIds, List<Long> glazeIds, List<Long> roofColorIds, List<Long> addonIds) {
        // Použijeme Set pro efektivní práci
        Set<Design> designs = CollectionUtils.isEmpty(designIds) ? Collections.emptySet() : new HashSet<>(designRepository.findAllById(designIds));
        Set<Glaze> glazes = CollectionUtils.isEmpty(glazeIds) ? Collections.emptySet() : new HashSet<>(glazeRepository.findAllById(glazeIds));
        Set<RoofColor> roofColors = CollectionUtils.isEmpty(roofColorIds) ? Collections.emptySet() : new HashSet<>(roofColorRepository.findAllById(roofColorIds));
        Set<Addon> addons = CollectionUtils.isEmpty(addonIds) ? Collections.emptySet() : new HashSet<>(addonsRepository.findAllById(addonIds));

        // Aktualizace kolekcí v Product entitě
        // Pokud ManyToMany nemá cascade persist/merge, stačí jen nastavit novou kolekci
        // Pokud má cascade, je bezpečnější spravovat kolekci explicitně (clear + addAll),
        // nebo spoléhat na JPA merge (což děláme v ProductService.updateProduct)
        product.setAvailableDesigns(designs);
        product.setAvailableGlazes(glazes);
        product.setAvailableRoofColors(roofColors);
        product.setAvailableAddons(addons);
        log.debug("Updated associations for product: designs={}, glazes={}, roofColors={}, addons={}",
                designIds, glazeIds, roofColorIds, addonIds);
    }


    // --- Metoda deleteProduct zůstává stejná ---
    @PostMapping("/{id}/delete")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.warn("Attempting to SOFT DELETE product ID: {}", id);
        try {
            productService.deleteProduct(id); // ProductService provede soft delete
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