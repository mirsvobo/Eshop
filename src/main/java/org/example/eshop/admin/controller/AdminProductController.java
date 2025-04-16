// src/main/java/org/example/eshop/admin/controller/AdminProductController.java

package org.example.eshop.admin.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid; // Potřeba pro updateProduct
import org.example.eshop.model.*; // Model importy
import org.example.eshop.repository.*;
import org.example.eshop.service.*; // Service importy
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

    private final ProductService productService;
    private final TaxRateService taxRateService;
    private final TaxRateRepository taxRateRepository; // <-- PŘIDÁNO
    private final DesignRepository designRepository;
    private final GlazeRepository glazeRepository;
    private final RoofColorRepository roofColorRepository;
    private final AddonsRepository addonsRepository;
    private final ImageRepository imageRepository;

    @Autowired
    public AdminProductController(ProductService productService,
                                  TaxRateService taxRateService,
                                  TaxRateRepository taxRateRepository, // <-- PŘIDÁNO
                                  DesignRepository designRepository,
                                  GlazeRepository glazeRepository,
                                  RoofColorRepository roofColorRepository,
                                  AddonsRepository addonsRepository,
                                  ImageRepository imageRepository) {
        this.productService = productService;
        this.taxRateService = taxRateService;
        this.taxRateRepository = taxRateRepository; // <-- PŘIDÁNO
        this.designRepository = designRepository;
        this.glazeRepository = glazeRepository;
        this.roofColorRepository = roofColorRepository;
        this.addonsRepository = addonsRepository;
        this.imageRepository = imageRepository;
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

    // Pomocná metoda pro načtení všech dostupných asociací pro checkboxy/selecty
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
        ensureCollectionsInitialized(product); // Použijeme pomocnou metodu

        model.addAttribute("product", product);
        addCommonFormAttributes(model);
        addAssociationAttributesToModel(model);
        model.addAttribute("pageTitle", "Vytvořit nový produkt");
        return "admin/product-form";
    }

    @GetMapping("/{id}/edit")
    @Transactional(readOnly = true)
    public String showEditProductForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Requesting edit form for product ID: {}", id);
        try {
            Product product = productService.getProductById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Produkt s ID " + id + " nenalezen."));

            ensureCollectionsInitialized(product);

            log.debug("Product ID: {}, Images loaded: {}", product.getId(), product.getImages() != null ? product.getImages().size() : "null");

            model.addAttribute("product", product);
            model.addAttribute("newImage", new Image());
            addCommonFormAttributes(model);
            addAssociationAttributesToModel(model);
            model.addAttribute("pageTitle", "Upravit produkt: " + product.getName());

            // Přidáme ID vybraných asociací do modelu pro správné zaškrtnutí checkboxů
            model.addAttribute("selectedDesignIds", product.getAvailableDesigns().stream().map(Design::getId).collect(Collectors.toSet()));
            model.addAttribute("selectedGlazeIds", product.getAvailableGlazes().stream().map(Glaze::getId).collect(Collectors.toSet()));
            model.addAttribute("selectedRoofColorIds", product.getAvailableRoofColors().stream().map(RoofColor::getId).collect(Collectors.toSet()));
            model.addAttribute("selectedAddonIds", product.getAvailableAddons().stream().map(Addon::getId).collect(Collectors.toSet()));
            model.addAttribute("selectedTaxRateIds", product.getAvailableTaxRates().stream().map(TaxRate::getId).collect(Collectors.toSet()));

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

    @PostMapping("/{productId}/images/upload")
    public String uploadProductImage(@PathVariable Long productId,
                                     @RequestParam("imageFile") MultipartFile imageFile,
                                     @RequestParam(required = false) String altText,
                                     @RequestParam(required = false) String titleText,
                                     @RequestParam(required = false) Integer displayOrder,
                                     RedirectAttributes redirectAttributes) {
        log.info("Attempting to upload image for product ID: {}", productId);
        if (imageFile.isEmpty()) {
            redirectAttributes.addFlashAttribute("imageError", "Nebyl vybrán žádný soubor k nahrání.");
            return "redirect:/admin/products/" + productId + "/edit";
        }

        try {
            productService.addImageToProduct(productId, imageFile, altText, titleText, displayOrder);
            redirectAttributes.addFlashAttribute("imageSuccess", "Obrázek byl úspěšně nahrán.");
            log.info("Image uploaded successfully for product ID: {}", productId);
        } catch (IOException e) {
            log.error("Failed to store image file for product ID {}: {}", productId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("imageError", "Chyba při ukládání souboru: " + e.getMessage());
        } catch (EntityNotFoundException e) {
            log.warn("Cannot upload image. Product not found: ID={}", productId, e);
            redirectAttributes.addFlashAttribute("errorMessage", "Produkt nenalezen.");
            return "redirect:/admin/products";
        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument during image upload for product ID {}: {}", productId, e.getMessage());
            redirectAttributes.addFlashAttribute("imageError", "Chyba nahrávání: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error uploading image for product ID {}: {}", productId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("imageError", "Při nahrávání obrázku nastala neočekávaná chyba.");
        }

        return "redirect:/admin/products/" + productId + "/edit";
    }

    @PostMapping("/images/{imageId}/delete")
    public String deleteProductImage(@PathVariable Long imageId, RedirectAttributes redirectAttributes) {
        log.warn("Attempting to DELETE image ID: {}", imageId);
        Long productId = null;
        try {
            Optional<Image> imageOpt = imageRepository.findById(imageId);
            if (imageOpt.isPresent() && imageOpt.get().getProduct() != null) {
                productId = imageOpt.get().getProduct().getId();
            }

            productService.deleteImage(imageId);
            redirectAttributes.addFlashAttribute("imageSuccess", "Obrázek byl úspěšně smazán.");
            log.info("Image ID {} deleted successfully.", imageId);

        } catch (EntityNotFoundException e) {
            log.warn("Cannot delete image. Image not found: ID={}", imageId, e);
            redirectAttributes.addFlashAttribute("imageError", e.getMessage());
            return "redirect:/admin/products";
        } catch (Exception e) {
            log.error("Error deleting image ID {}: {}", imageId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("imageError", "Při mazání obrázku nastala chyba: " + e.getMessage());
        }
        return productId != null ? "redirect:/admin/products/" + productId + "/edit" : "redirect:/admin/products";
    }

    @PostMapping("/images/update-order")
    @Transactional
    public String updateImageOrder(@RequestParam Map<String, String> params, RedirectAttributes redirectAttributes) {
        Long productId = null;
        log.info("Attempting to update image display order. Params: {}", params);
        try {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (entry.getKey().startsWith("displayOrder_")) {
                    Long imageId = Long.parseLong(entry.getKey().substring("displayOrder_".length()));
                    Integer order = null;
                    try {
                        order = Integer.parseInt(entry.getValue());
                        if (order < 0) order = 0;
                    } catch (NumberFormatException e) {
                        log.warn("Invalid display order value '{}' for image ID {}. Skipping.", entry.getValue(), imageId);
                        continue;
                    }

                    Image image = imageRepository.findById(imageId)
                            .orElseThrow(() -> new EntityNotFoundException("Image not found: " + imageId));
                    if (productId == null && image.getProduct() != null) {
                        productId = image.getProduct().getId();
                    }
                    if (!Objects.equals(image.getDisplayOrder(), order)) {
                        image.setDisplayOrder(order);
                        imageRepository.save(image);
                        log.debug("Updated display order for image ID {} to {}", imageId, order);
                    }
                }
            }
            redirectAttributes.addFlashAttribute("imageSuccess", "Pořadí obrázků bylo aktualizováno.");
            log.info("Image display order update process finished.");

        } catch (EntityNotFoundException e) {
            log.warn("Cannot update image order. Image not found.", e);
            redirectAttributes.addFlashAttribute("imageError", e.getMessage());
            return productId != null ? "redirect:/admin/products/" + productId + "/edit" : "redirect:/admin/products";
        } catch (Exception e) {
            log.error("Error updating image order: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("imageError", "Při aktualizaci pořadí obrázků nastala chyba.");
        }
        return productId != null ? "redirect:/admin/products/" + productId + "/edit" : "redirect:/admin/products";
    }

    // *** Metoda createProduct - OPRAVENO: Typy parametrů a odstraněno @Valid ***
    @PostMapping
    public String createProduct(@ModelAttribute("product") Product product, // Odstraněno @Valid
                                @RequestParam(required = false) List<Long> designIds,   // <-- Změněno zpět na List
                                @RequestParam(required = false) List<Long> glazeIds,    // <-- Změněno zpět na List
                                @RequestParam(required = false) List<Long> roofColorIds,// <-- Změněno zpět na List
                                @RequestParam(required = false) List<Long> addonIds,    // <-- Změněno zpět na List
                                @RequestParam(required = false) Set<Long> taxRateIds,    // <-- Zůstává Set
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        log.info("Attempting to create new product: {}", product.getName());
        boolean hasErrors = false;

        // Manuální validace základních polí
        if (!StringUtils.hasText(product.getName())) {
            bindingResult.rejectValue("name", "NotBlank", "Název produktu nesmí být prázdný.");
            hasErrors = true;
        }
        // Zde můžeš přidat validaci pro product.getShortDescription()

        // Pokus o nastavení asociací
        try {
            // Voláme původní metodu s List<Long>
            updateAssociationsFromIds(product, designIds, glazeIds, roofColorIds, addonIds);
            // Voláme NOVOU metodu se Set<Long>
            updateTaxRateAssociationsFromIds(product, taxRateIds);
        } catch (IllegalArgumentException | EntityNotFoundException e) {
            bindingResult.reject("global.error", e.getMessage());
            log.warn("Error setting associations: {}", e.getMessage());
            hasErrors = true;
        }

        // Dodatečná kontrola pro TaxRates (pro jistotu)
        if (!hasErrors && (product.getAvailableTaxRates() == null || product.getAvailableTaxRates().isEmpty())) {
            bindingResult.reject("global.error", "Musí být vybrána alespoň jedna daňová sazba.");
            hasErrors = true;
        }

        // Kontrola chyb a návrat formuláře
        if (bindingResult.hasErrors() || hasErrors) {
            log.warn("Validation errors creating product: {}", bindingResult.getAllErrors());
            ensureCollectionsInitialized(product);
            addCommonFormAttributes(model);
            addAssociationAttributesToModel(model);
            // Přidáme IDčka zpět do modelu
            model.addAttribute("selectedDesignIds", designIds != null ? designIds : Collections.emptyList()); // List
            model.addAttribute("selectedGlazeIds", glazeIds != null ? glazeIds : Collections.emptyList()); // List
            model.addAttribute("selectedRoofColorIds", roofColorIds != null ? roofColorIds : Collections.emptyList()); // List
            model.addAttribute("selectedAddonIds", addonIds != null ? addonIds : Collections.emptyList()); // List
            model.addAttribute("selectedTaxRateIds", taxRateIds != null ? taxRateIds : Collections.emptySet()); // Set
            model.addAttribute("pageTitle", "Vytvořit nový produkt (Chyba)");
            return "admin/product-form";
        }

        // Volání service
        try {
            Product savedProduct = productService.createProduct(product);
            redirectAttributes.addFlashAttribute("successMessage", "Produkt '" + savedProduct.getName() + "' byl úspěšně vytvořen.");
            log.info("Product '{}' created successfully with ID: {}", savedProduct.getName(), savedProduct.getId());
            return "redirect:/admin/products";
        } catch (IllegalArgumentException e) {
            log.warn("Error creating product '{}': {}", product.getName(), e.getMessage());
            if (e.getMessage().contains("slug")) {
                bindingResult.rejectValue("slug", "error.product.duplicateSlug", e.getMessage());
            } else {
                bindingResult.reject("global.error", e.getMessage());
            }
            // Návrat formuláře s chybou
            ensureCollectionsInitialized(product);
            addCommonFormAttributes(model);
            addAssociationAttributesToModel(model);
            model.addAttribute("selectedDesignIds", designIds != null ? designIds : Collections.emptyList());
            model.addAttribute("selectedGlazeIds", glazeIds != null ? glazeIds : Collections.emptyList());
            model.addAttribute("selectedRoofColorIds", roofColorIds != null ? roofColorIds : Collections.emptyList());
            model.addAttribute("selectedAddonIds", addonIds != null ? addonIds : Collections.emptyList());
            model.addAttribute("selectedTaxRateIds", taxRateIds != null ? taxRateIds : Collections.emptySet());
            model.addAttribute("pageTitle", "Vytvořit nový produkt (Chyba)");
            return "admin/product-form";
        }
        catch (Exception e) {
            log.error("Unexpected error creating product '{}': {}", product.getName(), e.getMessage(), e);
            ensureCollectionsInitialized(product);
            addCommonFormAttributes(model);
            addAssociationAttributesToModel(model);
            model.addAttribute("errorMessage", "Při vytváření produktu nastala neočekávaná chyba: " + e.getMessage());
            model.addAttribute("selectedDesignIds", designIds != null ? designIds : Collections.emptyList());
            model.addAttribute("selectedGlazeIds", glazeIds != null ? glazeIds : Collections.emptyList());
            model.addAttribute("selectedRoofColorIds", roofColorIds != null ? roofColorIds : Collections.emptyList());
            model.addAttribute("selectedAddonIds", addonIds != null ? addonIds : Collections.emptyList());
            model.addAttribute("selectedTaxRateIds", taxRateIds != null ? taxRateIds : Collections.emptySet());
            model.addAttribute("pageTitle", "Vytvořit nový produkt (Chyba)");
            return "admin/product-form";
        }
    }

    // *** Metoda updateProduct - OPRAVENO: Odstraněno @Valid z Product productData ***
    @PostMapping("/{id}")
    public String updateProduct(@PathVariable Long id,
                                @ModelAttribute("product") Product productData, // <-- ZDE BYLO ODSTRANĚNO @Valid
                                BindingResult bindingResult, // Zůstává pro zachycení databinding chyb (např. špatný formát čísla) a manuálních chyb
                                @RequestParam(required = false) List<Long> designIds,
                                @RequestParam(required = false) List<Long> glazeIds,
                                @RequestParam(required = false) List<Long> roofColorIds,
                                @RequestParam(required = false) List<Long> addonIds,
                                @RequestParam(required = false) Set<Long> taxRateIds,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        log.info("Attempting to update product ID: {}", id);
        productData.setId(id); // Nastavíme ID pro případné zobrazení chyb
        boolean hasAssociationErrors = false; // Příznak pro chyby z asociací

        // Kontrola základních databinding chyb (např. špatný formát čísla pro cenu)
        // @Valid jsme odstranili, takže musíme případně validovat manuálně nebo spoléhat na DB/Service
        // Příklad manuální validace názvu:
        if (!StringUtils.hasText(productData.getName())) {
            bindingResult.rejectValue("name", "NotBlank", "Název produktu nesmí být prázdný.");
            // Nastavíme i hasAssociationErrors, abychom přeskočili další kroky, pokud je název prázdný
            hasAssociationErrors = true;
        }
        // Zde můžeš přidat další manuální validace základních polí productData...


        if (bindingResult.hasErrors()) {
            log.warn("Data binding errors updating product {}: {}", id, bindingResult.getAllErrors());
            // Vracíme formulář hned
            ensureCollectionsInitialized(productData); // Použijeme data z formuláře pro zobrazení
            addCommonFormAttributes(model);
            addAssociationAttributesToModel(model);
            // Přidáme i vybraná ID zpět, aby checkboxy zůstaly
            model.addAttribute("selectedDesignIds", designIds != null ? designIds : Collections.emptyList());
            model.addAttribute("selectedGlazeIds", glazeIds != null ? glazeIds : Collections.emptyList());
            model.addAttribute("selectedRoofColorIds", roofColorIds != null ? roofColorIds : Collections.emptyList());
            model.addAttribute("selectedAddonIds", addonIds != null ? addonIds : Collections.emptyList());
            model.addAttribute("selectedTaxRateIds", taxRateIds != null ? taxRateIds : Collections.emptySet());
            model.addAttribute("pageTitle", "Upravit produkt (chyba)");
            // Předáme productData zpět, aby se zobrazily chyby z bindingResult
            model.addAttribute("product", productData);
            return "admin/product-form";
        }

        Product existingProduct = null;
        try {
            // Načteme existující produkt pro nastavení asociací PŘED voláním service
            existingProduct = productService.getProductById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Produkt s ID " + id + " nenalezen pro aktualizaci."));

            // Aktualizujeme asociace na načteném existujícím produktu
            updateAssociationsFromIds(existingProduct, designIds, glazeIds, roofColorIds, addonIds);
            // Aktualizujeme daňové sazby - vyhodí výjimku, pokud taxRateIds je prázdné/neplatné
            updateTaxRateAssociationsFromIds(existingProduct, taxRateIds);

        } catch (IllegalArgumentException | EntityNotFoundException e) {
            // Chyba při načítání produktu nebo nastavování asociací
            bindingResult.reject("global.error", e.getMessage()); // Přidáme jako globální chybu
            log.warn("Error setting associations during update for product ID {}: {}", id, e.getMessage());
            hasAssociationErrors = true;
        }

        // Zkontrolujeme chyby z asociací
        if (hasAssociationErrors) {
            // Vrátíme formulář s chybami asociací
            log.warn("Returning form due to association errors: {}", bindingResult.getAllErrors());
            ensureCollectionsInitialized(productData); // Pro zobrazení formuláře použijeme data z formuláře
            addCommonFormAttributes(model);
            addAssociationAttributesToModel(model);
            model.addAttribute("selectedDesignIds", designIds != null ? designIds : Collections.emptyList());
            model.addAttribute("selectedGlazeIds", glazeIds != null ? glazeIds : Collections.emptyList());
            model.addAttribute("selectedRoofColorIds", roofColorIds != null ? roofColorIds : Collections.emptyList());
            model.addAttribute("selectedAddonIds", addonIds != null ? addonIds : Collections.emptyList());
            model.addAttribute("selectedTaxRateIds", taxRateIds != null ? taxRateIds : Collections.emptySet());
            model.addAttribute("pageTitle", "Upravit produkt (Chyba asociací)");
            // Přidáme globální chybu do modelu, pokud ještě není
            if (!model.containsAttribute("errorMessage") && bindingResult.hasGlobalErrors()){
                model.addAttribute("errorMessage", bindingResult.getGlobalError().getDefaultMessage());
            }
            // Vrátíme productData, aby se zobrazila data z formuláře
            model.addAttribute("product", productData);
            return "admin/product-form";
        }

        // Pokud nejsou chyby validace ani asociací, voláme service
        try {
            // Voláme service s daty z formuláře (productData) a s načtenou/upravenou entitou (existingProduct)
            // ProductService si z productData vezme základní pole a z existingProduct asociace
            Product updatedProduct = productService.updateProduct(id, productData, existingProduct)
                    .orElseThrow(() -> new EntityNotFoundException("Produkt s ID " + id + " nenalezen pro aktualizaci (po pokusu o update)."));

            redirectAttributes.addFlashAttribute("successMessage", "Produkt '" + updatedProduct.getName() + "' byl úspěšně aktualizován.");
            log.info("Product ID {} updated successfully.", id);
            return "redirect:/admin/products";

        } catch (EntityNotFoundException e) { // Chyba z productService.updateProduct
            log.warn("Cannot update product. Product not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/products";
        } catch (IllegalArgumentException e) { // Chyba z productService.updateProduct (např. slug)
            log.warn("Error updating product ID {}: {}", id, e.getMessage());
            if (e.getMessage().contains("slug")) {
                bindingResult.rejectValue("slug", "error.product.duplicateSlug", e.getMessage());
            } else {
                bindingResult.reject("global.error", e.getMessage()); // Obecná chyba
            }
            // Návrat formuláře s chybou
            ensureCollectionsInitialized(productData);
            addCommonFormAttributes(model);
            addAssociationAttributesToModel(model);
            model.addAttribute("selectedDesignIds", designIds != null ? designIds : Collections.emptyList());
            model.addAttribute("selectedGlazeIds", glazeIds != null ? glazeIds : Collections.emptyList());
            model.addAttribute("selectedRoofColorIds", roofColorIds != null ? roofColorIds : Collections.emptyList());
            model.addAttribute("selectedAddonIds", addonIds != null ? addonIds : Collections.emptyList());
            model.addAttribute("selectedTaxRateIds", taxRateIds != null ? taxRateIds : Collections.emptySet());
            model.addAttribute("pageTitle", "Upravit produkt (chyba)");
            // Vrátíme productData, aby se zobrazila data z formuláře a chyby z bindingResult
            model.addAttribute("product", productData);
            return "admin/product-form";
        }
        catch (Exception e) { // Neočekávané chyby
            log.error("Unexpected error updating product ID {}: {}", id, e.getMessage(), e);
            ensureCollectionsInitialized(productData);
            addCommonFormAttributes(model);
            addAssociationAttributesToModel(model);
            model.addAttribute("errorMessage", "Při aktualizaci produktu nastala neočekávaná chyba: " + e.getMessage());
            model.addAttribute("selectedDesignIds", designIds != null ? designIds : Collections.emptyList());
            model.addAttribute("selectedGlazeIds", glazeIds != null ? glazeIds : Collections.emptyList());
            model.addAttribute("selectedRoofColorIds", roofColorIds != null ? roofColorIds : Collections.emptyList());
            model.addAttribute("selectedAddonIds", addonIds != null ? addonIds : Collections.emptyList());
            model.addAttribute("selectedTaxRateIds", taxRateIds != null ? taxRateIds : Collections.emptySet());
            model.addAttribute("pageTitle", "Upravit produkt (chyba)");
            model.addAttribute("product", productData); // Vracíme data z formuláře
            return "admin/product-form";
        }
    }

    // Pomocná metoda pro aktualizaci asociací na Product entitě (očekává List)
    private void updateAssociationsFromIds(Product product, List<Long> designIds, List<Long> glazeIds, List<Long> roofColorIds, List<Long> addonIds) {
        // Získáme množiny entit podle předaných ID
        Set<Design> designs = CollectionUtils.isEmpty(designIds) ? Collections.emptySet() : new HashSet<>(designRepository.findAllById(designIds));
        Set<Glaze> glazes = CollectionUtils.isEmpty(glazeIds) ? Collections.emptySet() : new HashSet<>(glazeRepository.findAllById(glazeIds));
        Set<RoofColor> roofColors = CollectionUtils.isEmpty(roofColorIds) ? Collections.emptySet() : new HashSet<>(roofColorRepository.findAllById(roofColorIds));
        Set<Addon> addons = CollectionUtils.isEmpty(addonIds) ? Collections.emptySet() : new HashSet<>(addonsRepository.findAllById(addonIds));

        // Zde můžeš přidat kontrolu, zda velikost nalezených entit odpovídá velikosti seznamů ID, pokud chceš být přísnější

        // Nastavíme načtené entity na produkt
        product.setAvailableDesigns(designs);
        product.setAvailableGlazes(glazes);
        product.setAvailableRoofColors(roofColors);
        product.setAvailableAddons(addons); // Toto se aplikuje jen pokud je produkt customisable (řeší ProductService)
        log.debug("Updated associations for product {}: designs={}, glazes={}, roofColors={}, addons={}",
                product.getId(), designIds, glazeIds, roofColorIds, addonIds);
    }

    // *** PŘIDÁNA CHYBĚJÍCÍ METODA ***
    // Pomocná metoda pro aktualizaci asociací TaxRates (očekává Set)
    private void updateTaxRateAssociationsFromIds(Product product, Set<Long> taxRateIds) {
        if (CollectionUtils.isEmpty(taxRateIds)) {
            // Pokud se vyžaduje alespoň jedna sazba, vyhodíme zde výjimku
            throw new IllegalArgumentException("Produkt musí mít vybránu alespoň jednu daňovou sazbu.");
        }
        Set<TaxRate> taxRates = new HashSet<>(taxRateRepository.findAllById(taxRateIds));
        // Kontrola, zda byly nalezeny všechny požadované sazby
        if (taxRates.size() != taxRateIds.size()) {
            Set<Long> foundIds = taxRates.stream().map(TaxRate::getId).collect(Collectors.toSet());
            Set<Long> missingIds = new HashSet<>(taxRateIds);
            missingIds.removeAll(foundIds);
            log.warn("Could not find all tax rates for IDs: {}", missingIds);
            throw new EntityNotFoundException("Některé vybrané daňové sazby nebyly nalezeny: " + missingIds);
        }
        product.setAvailableTaxRates(taxRates);
        log.debug("Updated available tax rates for product {}: {}", product.getId(), taxRateIds);
    }

    // Pomocná metoda pro inicializaci kolekcí
    private void ensureCollectionsInitialized(Product product) {
        if (product == null) return;
        if (product.getAvailableDesigns() == null) product.setAvailableDesigns(new HashSet<>());
        if (product.getAvailableGlazes() == null) product.setAvailableGlazes(new HashSet<>());
        if (product.getAvailableRoofColors() == null) product.setAvailableRoofColors(new HashSet<>());
        if (product.getAvailableAddons() == null) product.setAvailableAddons(new HashSet<>());
        if (product.getAvailableTaxRates() == null) product.setAvailableTaxRates(new HashSet<>()); // Přidáno
        if (product.getImages() == null) product.setImages(new HashSet<>());
        if (product.getDiscounts() == null) product.setDiscounts(new HashSet<>());
    }

    // --- Delete Product (zůstává stejné) ---
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