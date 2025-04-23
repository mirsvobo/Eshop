package org.example.eshop.admin.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.eshop.model.Addon;
import org.example.eshop.admin.service.AddonsService; // <-- SPRÁVNÝ IMPORT
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal; // Import pro BigDecimal
import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/admin/addons")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAddonsController {

    private static final Logger log = LoggerFactory.getLogger(AdminAddonsController.class);

    // Používáme službu ze správného balíčku
    @Autowired private AddonsService addonsService;

    @ModelAttribute("currentUri")
    public String getCurrentUri(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @GetMapping
    public String listAddons(Model model) {
        log.info("Requesting addon list view.");
        try {
            List<Addon> addons = addonsService.getAllAddons();
            model.addAttribute("addons", addons);
        } catch (Exception e) {
            log.error("Error fetching addons: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Nepodařilo se načíst seznam doplňků.");
            model.addAttribute("addons", Collections.emptyList());
        }
        return "admin/addons-list"; // Název Thymeleaf šablony
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        log.info("Requesting new addon form.");
        model.addAttribute("addon", new Addon());
        model.addAttribute("pageTitle", "Vytvořit nový doplněk");
        return "admin/addon-form"; // Název Thymeleaf šablony
    }

    @PostMapping
    public String createAddon(@Valid @ModelAttribute("addon") Addon addon,
                              BindingResult bindingResult,
                              RedirectAttributes redirectAttributes,
                              Model model) {
        log.info("Attempting to create new addon: {}", addon.getName());

        if (bindingResult.hasErrors()) {
            // Logování zde zachytí chyby z @Valid (@AssertTrue v Addon.java)
            log.warn("Validation errors creating addon (from @Valid): {}", bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Vytvořit nový doplněk (Chyba)");
            return "admin/addon-form";
        }
        try {
            // createAddon v AddonsService provede vlastní validaci (validateAddonCommonFields, validateAndNormalizePrices)
            Addon savedAddon = addonsService.createAddon(addon);
            redirectAttributes.addFlashAttribute("successMessage", "Doplněk '" + savedAddon.getName() + "' byl úspěšně vytvořen.");
            log.info("Addon '{}' created successfully with ID: {}", savedAddon.getName(), savedAddon.getId());
            return "redirect:/admin/addons";
        } catch (IllegalArgumentException e) {
            // Chytáme validační chyby z AddonsService
            log.warn("Error creating addon '{}': {}", addon.getName(), e.getMessage());
            // Zde můžeš stále rozlišovat chyby podle zprávy, pokud chceš
            if (e.getMessage().contains("názvem")) {
                bindingResult.rejectValue("name", "error.addon.duplicate.name", e.getMessage());
            } else if (e.getMessage().contains("SKU")) {
                bindingResult.rejectValue("sku", "error.addon.duplicate.sku", e.getMessage());
            } else if (e.getMessage().contains("musí být kladná")) {
                // Chyby ohledně cen přicházející ze service
                if (e.getMessage().contains("'Cena CZK'")) {
                    bindingResult.rejectValue("priceCZK", "Positive", e.getMessage());
                } else if (e.getMessage().contains("'Cena EUR'")) {
                    bindingResult.rejectValue("priceEUR", "Positive", e.getMessage());
                } else if (e.getMessage().contains("'Cena za jednotku CZK'")) {
                    bindingResult.rejectValue("pricePerUnitCZK", "Positive", e.getMessage());
                } else if (e.getMessage().contains("'Cena za jednotku EUR'")) {
                    bindingResult.rejectValue("pricePerUnitEUR", "Positive", e.getMessage());
                } else {
                    model.addAttribute("errorMessage", e.getMessage()); // Obecná validační chyba ze service
                }
            }
            else {
                model.addAttribute("errorMessage", e.getMessage()); // Jiné IllegalArgumentExceptions
            }
            model.addAttribute("pageTitle", "Vytvořit nový doplněk (Chyba)");
            return "admin/addon-form";
        } catch (Exception e) {
            log.error("Unexpected error creating addon '{}': {}", addon.getName(), e.getMessage(), e);
            model.addAttribute("pageTitle", "Vytvořit nový doplněk (Chyba)");
            model.addAttribute("errorMessage", "Při vytváření doplňku nastala neočekávaná chyba: " + e.getMessage());
            return "admin/addon-form";
        }
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Requesting edit form for addon ID: {}", id);
        try {
            // getAddonById vrací Optional, správně zpracujeme
            Addon addon = addonsService.getAddonById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Doplněk s ID " + id + " nenalezen."));
            model.addAttribute("addon", addon);
            model.addAttribute("pageTitle", "Upravit doplněk: " + addon.getName());
            return "admin/addon-form";
        } catch (EntityNotFoundException e) {
            log.warn("Addon with ID {} not found for editing.", id);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/addons";
        } catch (Exception e) {
            log.error("Error loading addon ID {} for edit: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Nepodařilo se načíst doplněk k úpravě.");
            return "redirect:/admin/addons";
        }
    }

    @PostMapping("/{id}")
    public String updateAddon(@PathVariable Long id,
                              @Valid @ModelAttribute("addon") Addon addonData,
                              BindingResult bindingResult,
                              RedirectAttributes redirectAttributes,
                              Model model) {
        log.info("Attempting to update addon ID: {}", id);
        // Dodatečná validace cen (musí být kladné)
        if (addonData.getPriceCZK() == null || addonData.getPriceCZK().compareTo(BigDecimal.ZERO) <= 0) {
            bindingResult.rejectValue("priceCZK", "Positive", "Cena CZK musí být kladná.");
        }
        if (addonData.getPriceEUR() == null || addonData.getPriceEUR().compareTo(BigDecimal.ZERO) <= 0) {
            bindingResult.rejectValue("priceEUR", "Positive", "Cena EUR musí být kladná.");
        }

        if (bindingResult.hasErrors()) {
            log.warn("Validation errors updating addon {}: {}", id, bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Upravit doplněk (Chyba)");
            addonData.setId(id); // Ensure ID is preserved for the form action
            model.addAttribute("addon", addonData); // Vracíme data formuláře zpět
            return "admin/addon-form";
        }
        try {
            // Voláme metodu vracející Addon a chytáme EntityNotFoundException
            Addon updatedAddon = addonsService.updateAddon(id, addonData);
            redirectAttributes.addFlashAttribute("successMessage", "Doplněk '" + updatedAddon.getName() + "' byl úspěšně aktualizován.");
            log.info("Addon ID {} updated successfully.", id);
            return "redirect:/admin/addons";
        } catch (EntityNotFoundException e) {
            log.warn("Cannot update addon. Addon not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/addons"; // Při nenalezení přesměrujeme na seznam
        } catch (IllegalArgumentException e) {
            log.warn("Error updating addon ID {}: {}", id, e.getMessage());
            if (e.getMessage().contains("názvem")) {
                bindingResult.rejectValue("name", "error.addon.duplicate.name", e.getMessage());
            } else if (e.getMessage().contains("SKU")) {
                bindingResult.rejectValue("sku", "error.addon.duplicate.sku", e.getMessage());
            } else {
                model.addAttribute("errorMessage", e.getMessage());
            }
            model.addAttribute("pageTitle", "Upravit doplněk (Chyba)");
            addonData.setId(id);
            model.addAttribute("addon", addonData);
            return "admin/addon-form"; // Vrátíme formulář s chybou
        } catch (Exception e) {
            log.error("Unexpected error updating addon ID {}: {}", id, e.getMessage(), e);
            model.addAttribute("pageTitle", "Upravit doplněk (Chyba)");
            addonData.setId(id);
            model.addAttribute("addon", addonData);
            model.addAttribute("errorMessage", "Při aktualizaci doplňku nastala neočekávaná chyba: " + e.getMessage());
            return "admin/addon-form"; // Vrátíme formulář s chybou
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteAddon(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.warn("Attempting to SOFT DELETE addon ID: {}", id);
        try {
            addonsService.deleteAddon(id); // Voláme metodu ze správné service
            redirectAttributes.addFlashAttribute("successMessage", "Doplněk byl úspěšně deaktivován.");
            log.info("Addon ID {} successfully deactivated.", id);
        } catch (EntityNotFoundException e) {
            log.warn("Cannot deactivate addon. Addon not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        catch (Exception e) {
            log.error("Error deactivating addon ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Při deaktivaci doplňku nastala chyba: " + e.getMessage());
        }
        return "redirect:/admin/addons";
    }
}