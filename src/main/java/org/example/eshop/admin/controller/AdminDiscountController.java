package org.example.eshop.admin.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.eshop.model.Discount;
import org.example.eshop.model.Product;
import org.example.eshop.service.DiscountService;
import org.example.eshop.service.ProductService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/discounts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDiscountController {

    private static final Logger log = LoggerFactory.getLogger(AdminDiscountController.class);

    @Autowired
    private DiscountService discountService;
    @Autowired
    private ProductService productService; // Pro načtení seznamu produktů

    @NotNull
    private static Optional<Discount> getDiscount(Object resultObject) {
        Optional<Discount> updatedDiscountOpt = Optional.empty();

        // Bezpečné rozbalení a přetypování
        if (resultObject instanceof Optional<?> outerOptional && outerOptional.isPresent()) {
            Object innerObject = outerOptional.get();
            if (innerObject instanceof Optional<?> innerOptional && innerOptional.isPresent()) {
                if (innerOptional.get() instanceof Discount discount) { // Použití pattern matching pro cast
                    updatedDiscountOpt = Optional.of(discount);
                }
            } else if (innerObject instanceof Discount discount) { // Pro případ, že by updateDiscount vracel jen Optional<Discount>
                updatedDiscountOpt = Optional.of(discount);
            }
        }
        return updatedDiscountOpt;
    }

    @ModelAttribute("currentUri")
    public String getCurrentUri(HttpServletRequest request) {
        return request.getRequestURI().replaceFirst("/(new|\\d+/edit)$", "");
    }

    // Pomocná metoda pro načtení všech produktů do modelu
    private void loadAllProducts(Model model) {
        try {
            // --- ZAČÁTEK OPRAVY ---
            // Voláme novou metodu, která vrací List<Product>
            log.debug("Loading all products for discount form dropdown.");
            List<Product> products = productService.getAllProductsList();
            // --- KONEC OPRAVY ---
            model.addAttribute("allProducts", products);
        } catch (Exception e) {
            log.error("Failed to load products for discount form", e);
            model.addAttribute("allProducts", Collections.emptyList());
            model.addAttribute("productsLoadError", "Nepodařilo se načíst seznam produktů.");
        }
    }

    @GetMapping
    public String listDiscounts(Model model) {
        log.info("Requesting discount list view.");
        try {
            List<Discount> discounts = discountService.getAllDiscounts();
            model.addAttribute("discounts", discounts);
        } catch (Exception e) {
            log.error("Error fetching discounts: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Nepodařilo se načíst slevy.");
            model.addAttribute("discounts", Collections.emptyList());
        }
        return "admin/discounts-list";
    }

    @GetMapping("/new")
    public String showCreateDiscountForm(Model model) {
        log.info("Requesting new discount form.");
        Discount newDiscount = new Discount();
        newDiscount.setActive(true); // Defaultně aktivní
        newDiscount.setPercentage(false); // Defaultně fixní
        model.addAttribute("discount", newDiscount);
        loadAllProducts(model); // Načteme produkty pro výběr
        model.addAttribute("pageTitle", "Vytvořit novou slevu");
        return "admin/discount-form";
    }

    @PostMapping
    public String createDiscount(@Valid @ModelAttribute("discount") Discount discount,
                                 BindingResult bindingResult,
                                 @RequestParam(required = false) Set<Long> productIds, // Získáme ID produktů
                                 RedirectAttributes redirectAttributes,
                                 Model model) {
        log.info("Attempting to create new discount: Name='{}', isPercentage={}", discount.getName(), discount.isPercentage());
        // Převedeme data z polí pro datum (pokud používáme String inputy) - ZATÍM POUŽIJEME @DateTimeFormat
        // parseAndSetDates(discount, validFromString, validToString, bindingResult);

        if (bindingResult.hasErrors()) {
            log.warn("Initial validation errors creating discount: {}", bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Vytvořit novou slevu (Chyba)");
            loadAllProducts(model);
            // Musíme znovu nastavit vybraná ID pro formulář, pokud nejsou součástí objektu discount
            // model.addAttribute("selectedProductIds", productIds != null ? productIds : Collections.emptySet());
            return "admin/discount-form";
        }
        try {
            Discount savedDiscount = discountService.createDiscount(discount, productIds != null ? productIds : Collections.emptySet());
            redirectAttributes.addFlashAttribute("successMessage", "Sleva '" + savedDiscount.getName() + "' byla úspěšně vytvořena.");
            log.info("Discount '{}' created successfully with ID: {}", savedDiscount.getName(), savedDiscount.getId());
            return "redirect:/admin/discounts";
        } catch (IllegalArgumentException e) {
            log.warn("Error creating discount '{}': {}", discount.getName(), e.getMessage());
            model.addAttribute("errorMessage", e.getMessage()); // Zobrazit obecnou chybu
            model.addAttribute("pageTitle", "Vytvořit novou slevu (Chyba)");
            loadAllProducts(model);
            // model.addAttribute("selectedProductIds", productIds != null ? productIds : Collections.emptySet());
            return "admin/discount-form";
        } catch (DateTimeParseException e) {
            log.warn("Error parsing dates for discount '{}': {}", discount.getName(), e.getMessage());
            bindingResult.reject("global", "Neplatný formát data."); // Přidáme globální chybu
            model.addAttribute("pageTitle", "Vytvořit novou slevu (Chyba)");
            loadAllProducts(model);
            return "admin/discount-form";
        } catch (Exception e) {
            log.error("Unexpected error creating discount '{}': {}", discount.getName(), e.getMessage(), e);
            model.addAttribute("errorMessage", "Při vytváření slevy nastala neočekávaná chyba: " + e.getMessage());
            model.addAttribute("pageTitle", "Vytvořit novou slevu (Chyba)");
            loadAllProducts(model);
            // model.addAttribute("selectedProductIds", productIds != null ? productIds : Collections.emptySet());
            return "admin/discount-form";
        }
    }

    @GetMapping("/{id}/edit")
    // Odstraněno: @Transactional(readOnly = true)
    public String showEditDiscountForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Requesting edit form for discount ID: {}", id);
        try {
            // Voláme optimalizovanou metodu service, která používá findWithProductsById
            Discount discount = discountService.getDiscountById(id) // Předpokládáme, že getDiscountById bylo upraveno
                    .orElseThrow(() -> new EntityNotFoundException("Sleva s ID " + id + " nenalezena."));

            model.addAttribute("discount", discount);
            loadAllProducts(model);
            model.addAttribute("pageTitle", "Upravit slevu: " + discount.getName());
            // Produkty jsou již načteny v objektu discount
            model.addAttribute("selectedProductIds", discount.getProducts().stream().map(Product::getId).collect(Collectors.toSet()));
            return "admin/discount-form";
        } catch (EntityNotFoundException e) {
            log.warn("Discount with ID {} not found for editing.", id);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/discounts";
        } catch (Exception e) {
            log.error("Error loading discount ID {} for edit: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Nepodařilo se načíst slevu k úpravě.");
            return "redirect:/admin/discounts";
        }
    }

    @PostMapping("/{id}")
    public String updateDiscount(@PathVariable Long id,
                                 @Valid @ModelAttribute("discount") Discount discountData,
                                 BindingResult bindingResult,
                                 @RequestParam(required = false) Set<Long> productIds,
                                 RedirectAttributes redirectAttributes,
                                 Model model) {
        log.info("Attempting to update discount ID: {}", id);
        // parseAndSetDates(discountData, validFromString, validToString, bindingResult); // Odstraněno, spoléháme na binding

        if (bindingResult.hasErrors()) {
            log.warn("Initial validation errors updating discount {}: {}", id, bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Upravit slevu (Chyba)");
            discountData.setId(id); // Zachovat ID
            loadAllProducts(model);
            model.addAttribute("selectedProductIds", productIds != null ? productIds : Collections.emptySet());
            return "admin/discount-form";
        }
        try {
            // --- ZAČÁTEK OPRAVY ---
            // updateDiscount v service pravděpodobně vrací Optional<Object> obsahující Optional<Discount>
            // (Nebo jen Object - kód níže zvládne obojí bezpečněji než přímé přetypování)
            Object resultObject = discountService.updateDiscount(id, discountData, productIds != null ? productIds : Collections.emptySet());
            Optional<Discount> updatedDiscountOpt = getDiscount(resultObject);

            if (updatedDiscountOpt.isPresent()) {
                redirectAttributes.addFlashAttribute("successMessage", "Sleva '" + updatedDiscountOpt.get().getName() + "' byla úspěšně aktualizována.");
                log.info("Discount ID {} updated successfully.", id);
                return "redirect:/admin/discounts";
            } else {
                // Pokud Optional nebo vnitřní Optional je prázdný, znamená to, že entita nebyla nalezena
                // nebo služba vrátila neočekávaný typ.
                throw new EntityNotFoundException("Sleva s ID " + id + " nenalezena při pokusu o update nebo služba vrátila neočekávaný výsledek.");
            }
            // --- KONEC OPRAVY ---

        } catch (EntityNotFoundException e) {
            log.warn("Cannot update discount. Discount not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/discounts";
        } catch (IllegalArgumentException e) {
            log.warn("Error updating discount ID {}: {}", id, e.getMessage());
            model.addAttribute("errorMessage", e.getMessage()); // Zobrazit obecnou chybu
            model.addAttribute("pageTitle", "Upravit slevu (Chyba)");
            discountData.setId(id); // Zachovat ID
            loadAllProducts(model);
            model.addAttribute("selectedProductIds", productIds != null ? productIds : Collections.emptySet());
            return "admin/discount-form";
        } catch (DateTimeParseException e) { // Odchycení chyby parsování data
            log.warn("Error parsing dates for discount ID {}: {}", id, e.getMessage());
            bindingResult.reject("global", "Neplatný formát data."); // Přidáme globální chybu
            model.addAttribute("pageTitle", "Upravit slevu (Chyba)");
            discountData.setId(id);
            loadAllProducts(model);
            model.addAttribute("selectedProductIds", productIds != null ? productIds : Collections.emptySet());
            return "admin/discount-form";
        } catch (Exception e) {
            log.error("Unexpected error updating discount ID {}: {}", id, e.getMessage(), e);
            model.addAttribute("pageTitle", "Upravit slevu (Chyba)");
            discountData.setId(id); // Zachovat ID
            loadAllProducts(model);
            model.addAttribute("selectedProductIds", productIds != null ? productIds : Collections.emptySet());
            model.addAttribute("errorMessage", "Při aktualizaci slevy nastala neočekávaná chyba: " + e.getMessage());
            return "admin/discount-form";
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteDiscount(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.warn("Attempting to deactivate (soft delete) discount ID: {}", id);
        try {
            discountService.deleteDiscount(id); // Metoda provádí soft delete
            redirectAttributes.addFlashAttribute("successMessage", "Sleva byla úspěšně deaktivována.");
            log.info("Discount ID {} successfully deactivated.", id);
        } catch (EntityNotFoundException e) {
            log.warn("Cannot deactivate discount. Discount not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error deactivating discount ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Při deaktivaci slevy nastala chyba: " + e.getMessage());
        }
        return "redirect:/admin/discounts";
    }

    // Odstraněna metoda parseAndSetDates, použijeme @DateTimeFormat v modelu Discount nebo přímo binding
}