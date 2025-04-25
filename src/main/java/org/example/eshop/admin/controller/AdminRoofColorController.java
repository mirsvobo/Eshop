package org.example.eshop.admin.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.eshop.admin.service.RoofColorService;
import org.example.eshop.model.RoofColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/admin/roof-colors")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRoofColorController {

    private static final Logger log = LoggerFactory.getLogger(AdminRoofColorController.class);

    @Autowired
    private RoofColorService roofColorService;

    @ModelAttribute("currentUri")
    public String getCurrentUri(HttpServletRequest request) {
        // Získáme URI bez případných query parametrů
        String uri = request.getRequestURI();
        log.trace("Setting currentUri model attribute to: {}", uri);
        return uri;
        // Nebo pokud potřebujeme identifikovat podsekce:
        // if (uri.contains("/new")) return "/admin/roof-colors/new";
        // if (uri.matches(".*/\\d+/edit")) return "/admin/roof-colors/edit"; // Obecnější pro editaci
        // return "/admin/roof-colors"; // Výchozí pro seznam
    }


    @GetMapping
    public String listRoofColors(Model model) {
        log.info("Requesting roof color list view.");
        try {
            List<RoofColor> colors = roofColorService.getAllRoofColorsSortedByName();
            model.addAttribute("roofColors", colors); // Přidat do modelu pod klíčem "roofColors"
        } catch (Exception e) {
            log.error("Error fetching roof colors: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Nepodařilo se načíst seznam barev střech.");
            model.addAttribute("roofColors", Collections.emptyList());
        }
        return "admin/roof-colors-list"; // Název Thymeleaf šablony
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        log.info("Requesting new roof color form.");
        model.addAttribute("roofColor", new RoofColor());
        model.addAttribute("pageTitle", "Vytvořit novou barvu střechy");
        return "admin/roof-color-form"; // Název Thymeleaf šablony
    }

    @PostMapping
    public String createRoofColor(@Valid @ModelAttribute("roofColor") RoofColor roofColor,
                                  BindingResult bindingResult,
                                  RedirectAttributes redirectAttributes,
                                  Model model) {
        log.info("Attempting to create new roof color: {}", roofColor.getName());
        if (bindingResult.hasErrors()) {
            log.warn("Validation errors creating roof color: {}", bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Vytvořit novou barvu střechy (Chyba)");
            return "admin/roof-color-form";
        }
        try {
            RoofColor savedColor = roofColorService.createRoofColor(roofColor);
            redirectAttributes.addFlashAttribute("successMessage", "Barva střechy '" + savedColor.getName() + "' byla úspěšně vytvořena.");
            log.info("RoofColor '{}' created successfully with ID: {}", savedColor.getName(), savedColor.getId());
            return "redirect:/admin/roof-colors";
        } catch (IllegalArgumentException e) {
            log.warn("Error creating roof color '{}': {}", roofColor.getName(), e.getMessage());
            bindingResult.rejectValue("name", "error.roofColor.duplicate", e.getMessage());
            model.addAttribute("pageTitle", "Vytvořit novou barvu střechy (Chyba)");
            model.addAttribute("errorMessage", e.getMessage());
            return "admin/roof-color-form";
        } catch (Exception e) {
            log.error("Unexpected error creating roof color '{}': {}", roofColor.getName(), e.getMessage(), e);
            model.addAttribute("pageTitle", "Vytvořit novou barvu střechy (Chyba)");
            model.addAttribute("errorMessage", "Při vytváření barvy střechy nastala neočekávaná chyba: " + e.getMessage());
            return "admin/roof-color-form";
        }
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Requesting edit form for roof color ID: {}", id);
        try {
            RoofColor color = roofColorService.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Barva střechy s ID " + id + " nenalezena."));
            model.addAttribute("roofColor", color);
            model.addAttribute("pageTitle", "Upravit barvu střechy: " + color.getName());
            return "admin/roof-color-form";
        } catch (EntityNotFoundException e) {
            log.warn("RoofColor with ID {} not found for editing.", id);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/roof-colors";
        } catch (Exception e) {
            log.error("Error loading roof color ID {} for edit: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Nepodařilo se načíst barvu střechy k úpravě.");
            return "redirect:/admin/roof-colors";
        }
    }

    @PostMapping("/{id}")
    public String updateRoofColor(@PathVariable Long id,
                                  @Valid @ModelAttribute("roofColor") RoofColor roofColorData,
                                  BindingResult bindingResult,
                                  RedirectAttributes redirectAttributes,
                                  Model model) {
        log.info("Attempting to update roof color ID: {}", id);
        if (bindingResult.hasErrors()) {
            log.warn("Validation errors updating roof color {}: {}", id, bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Upravit barvu střechy (Chyba)");
            roofColorData.setId(id); // Ensure ID is preserved
            model.addAttribute("roofColor", roofColorData);
            return "admin/roof-color-form";
        }
        try {
            RoofColor updatedColor = roofColorService.updateRoofColor(id, roofColorData);
            redirectAttributes.addFlashAttribute("successMessage", "Barva střechy '" + updatedColor.getName() + "' byla úspěšně aktualizována.");
            log.info("RoofColor ID {} updated successfully.", id);
            return "redirect:/admin/roof-colors";
        } catch (EntityNotFoundException e) {
            log.warn("Cannot update roof color. RoofColor not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/roof-colors";
        } catch (IllegalArgumentException e) {
            log.warn("Error updating roof color ID {}: {}", id, e.getMessage());
            bindingResult.rejectValue("name", "error.roofColor.duplicate", e.getMessage());
            model.addAttribute("pageTitle", "Upravit barvu střechy (Chyba)");
            roofColorData.setId(id);
            model.addAttribute("roofColor", roofColorData);
            model.addAttribute("errorMessage", e.getMessage());
            return "admin/roof-color-form";
        } catch (Exception e) {
            log.error("Unexpected error updating roof color ID {}: {}", id, e.getMessage(), e);
            model.addAttribute("pageTitle", "Upravit barvu střechy (Chyba)");
            roofColorData.setId(id);
            model.addAttribute("roofColor", roofColorData);
            model.addAttribute("errorMessage", "Při aktualizaci barvy střechy nastala neočekávaná chyba: " + e.getMessage());
            return "admin/roof-color-form";
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteRoofColor(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.warn("Attempting to SOFT DELETE roof color ID: {}", id);
        try {
            roofColorService.deleteRoofColor(id);
            redirectAttributes.addFlashAttribute("successMessage", "Barva střechy byla úspěšně deaktivována.");
            log.info("RoofColor ID {} successfully deactivated.", id);
        } catch (EntityNotFoundException e) {
            log.warn("Cannot deactivate roof color. RoofColor not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (IllegalStateException e) {
            log.error("Error deactivating roof color ID {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error deactivating roof color ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Při deaktivaci barvy střechy nastala chyba: " + e.getMessage());
        }
        return "redirect:/admin/roof-colors";
    }
}