// src/main/java/org/example/eshop/admin/controller/AdminProductController.java

package org.example.eshop.admin.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.eshop.model.*; // Model importy
import org.example.eshop.repository.*;
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
    private final DesignRepository designRepository;
    private final GlazeRepository glazeRepository;
    private final RoofColorRepository roofColorRepository;
    private final AddonsRepository addonsRepository;
    private final ImageRepository imageRepository;

    @Autowired
    public AdminProductController(ProductService productService,
                                  TaxRateService taxRateService,
                                  DesignRepository designRepository,
                                  GlazeRepository glazeRepository,
                                  RoofColorRepository roofColorRepository,
                                  AddonsRepository addonsRepository, ImageRepository imageRepository) {
        this.productService = productService;
        this.taxRateService = taxRateService;
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

    @GetMapping("/{id}/edit")
    @Transactional(readOnly = true) // Ponecháme, pokud ProductService potřebuje
    public String showEditProductForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Requesting edit form for product ID: {}", id);
        try {
            // Voláme optimalizovanou metodu service, která používá findByIdWithDetails
            Product product = productService.getProductById(id) // Předpokládáme, že getProductById bylo upraveno
                    .orElseThrow(() -> new EntityNotFoundException("Produkt s ID " + id + " nenalezen."));

            // DEBUG LOG:
            log.debug("Product ID: {}, Images loaded: {}", product.getId(), product.getImages() != null ? product.getImages().size() : "null");
            if (product.getImages() != null) {
                for (Image img : product.getImages()) {
                    log.debug("  Image ID: {}, URL: {}", img.getId(), img.getUrl());
                    // Pokud chceš vidět hash kód objektu pro ověření identity:
                    // log.debug("    Image Object HashCode: {}", System.identityHashCode(img));
                }
            }
            model.addAttribute("product", product); // Data jsou již načtena
            model.addAttribute("newImage", new Image());
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
            redirectAttributes.addFlashAttribute("errorMessage", "Produkt nenalezen."); // Obecná chyba, přesměrujeme na seznam
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

    /**
     * Smaže existující obrázek produktu.
     */
    @PostMapping("/images/{imageId}/delete")
    public String deleteProductImage(@PathVariable Long imageId, RedirectAttributes redirectAttributes) {
        log.warn("Attempting to DELETE image ID: {}", imageId);
        Long productId = null;
        try {
            // Získáme ID produktu PŘED smazáním obrázku pro správné přesměrování
            Optional<Image> imageOpt = imageRepository.findById(imageId); // Použijeme ImageRepository
            if (imageOpt.isPresent() && imageOpt.get().getProduct() != null) {
                productId = imageOpt.get().getProduct().getId();
            }

            productService.deleteImage(imageId);
            redirectAttributes.addFlashAttribute("imageSuccess", "Obrázek byl úspěšně smazán.");
            log.info("Image ID {} deleted successfully.", imageId);

        } catch (EntityNotFoundException e) {
            log.warn("Cannot delete image. Image not found: ID={}", imageId, e);
            redirectAttributes.addFlashAttribute("imageError", e.getMessage());
            // Pokud obrázek nebyl nalezen, nevíme ID produktu, přesměrujeme obecně
            return "redirect:/admin/products";
        } catch (Exception e) {
            log.error("Error deleting image ID {}: {}", imageId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("imageError", "Při mazání obrázku nastala chyba: " + e.getMessage());
        }
        // Přesměrujeme zpět na editaci produktu, ke kterému obrázek patřil (pokud známe ID)
        return productId != null ? "redirect:/admin/products/" + productId + "/edit" : "redirect:/admin/products";
    }

    // Metoda pro aktualizaci pořadí obrázků (pokud bude potřeba)
    @PostMapping("/images/update-order")
    @Transactional
    public String updateImageOrder(@RequestParam Map<String, String> params, RedirectAttributes redirectAttributes) {
        Long productId = null; // Budeme potřebovat ID produktu pro přesměrování
        log.info("Attempting to update image display order. Params: {}", params);
        try {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (entry.getKey().startsWith("displayOrder_")) {
                    Long imageId = Long.parseLong(entry.getKey().substring("displayOrder_".length()));
                    Integer order = null;
                    try {
                        order = Integer.parseInt(entry.getValue());
                        if (order < 0) order = 0; // Zajistit nezáporné pořadí
                    } catch (NumberFormatException e) {
                        log.warn("Invalid display order value '{}' for image ID {}. Skipping.", entry.getValue(), imageId);
                        continue; // Přeskočit neplatné hodnoty
                    }

                    Image image = imageRepository.findById(imageId)
                            .orElseThrow(() -> new EntityNotFoundException("Image not found: " + imageId));
                    if (productId == null && image.getProduct() != null) {
                        productId = image.getProduct().getId(); // Získáme ID produktu z prvního obrázku
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