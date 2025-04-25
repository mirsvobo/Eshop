package org.example.eshop.admin.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.eshop.admin.service.DesignService;
import org.example.eshop.model.Design;
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
@RequestMapping("/admin/designs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDesignController {

    private static final Logger log = LoggerFactory.getLogger(AdminDesignController.class);

    @Autowired
    private DesignService designService;

    @ModelAttribute("currentUri")
    public String getCurrentUri(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @GetMapping
    public String listDesigns(Model model) {
        log.info("Requesting design list view.");
        try {
            List<Design> designs = designService.getAllDesignsSortedByName();
            model.addAttribute("designs", designs);
        } catch (Exception e) {
            log.error("Error fetching designs: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Nepodařilo se načíst seznam designů.");
            model.addAttribute("designs", Collections.emptyList());
        }
        return "admin/designs-list"; // Název Thymeleaf šablony
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        log.info("Requesting new design form.");
        model.addAttribute("design", new Design());
        model.addAttribute("pageTitle", "Vytvořit nový design");
        return "admin/design-form"; // Název Thymeleaf šablony
    }

    @PostMapping
    public String createDesign(@Valid @ModelAttribute("design") Design design,
                               BindingResult bindingResult,
                               RedirectAttributes redirectAttributes,
                               Model model) {
        log.info("Attempting to create new design: {}", design.getName());
        if (bindingResult.hasErrors()) {
            log.warn("Validation errors creating design: {}", bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Vytvořit nový design (Chyba)");
            // Není třeba přidávat 'design' zpět, @ModelAttribute to zajistí
            return "admin/design-form"; // Zobrazit formulář znovu s chybami
        }
        try {
            Design savedDesign = designService.createDesign(design);
            redirectAttributes.addFlashAttribute("successMessage", "Design '" + savedDesign.getName() + "' byl úspěšně vytvořen.");
            log.info("Design '{}' created successfully with ID: {}", savedDesign.getName(), savedDesign.getId());
            return "redirect:/admin/designs";
        } catch (IllegalArgumentException e) {
            log.warn("Error creating design '{}': {}", design.getName(), e.getMessage());
            bindingResult.rejectValue("name", "error.design.duplicate", e.getMessage()); // Přidáme chybu k poli 'name'
            model.addAttribute("pageTitle", "Vytvořit nový design (Chyba)");
            model.addAttribute("errorMessage", e.getMessage()); // Můžeme přidat i obecnou zprávu
            return "admin/design-form";
        } catch (Exception e) {
            log.error("Unexpected error creating design '{}': {}", design.getName(), e.getMessage(), e);
            model.addAttribute("pageTitle", "Vytvořit nový design (Chyba)");
            model.addAttribute("errorMessage", "Při vytváření designu nastala neočekávaná chyba: " + e.getMessage());
            return "admin/design-form";
        }
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Requesting edit form for design ID: {}", id);
        try {
            Design design = designService.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Design s ID " + id + " nenalezen."));
            model.addAttribute("design", design);
            model.addAttribute("pageTitle", "Upravit design: " + design.getName());
            return "admin/design-form";
        } catch (EntityNotFoundException e) {
            log.warn("Design with ID {} not found for editing.", id);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/designs";
        } catch (Exception e) {
            log.error("Error loading design ID {} for edit: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Nepodařilo se načíst design k úpravě.");
            return "redirect:/admin/designs";
        }
    }

    @PostMapping("/{id}")
    public String updateDesign(@PathVariable Long id,
                               @Valid @ModelAttribute("design") Design designData,
                               BindingResult bindingResult,
                               RedirectAttributes redirectAttributes,
                               Model model) {
        log.info("Attempting to update design ID: {}", id);
        if (bindingResult.hasErrors()) {
            log.warn("Validation errors updating design {}: {}", id, bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Upravit design (Chyba)");
            // Zajistíme, že ID zůstane v DTO pro POST action v šabloně
            designData.setId(id);
            model.addAttribute("design", designData); // Vrátíme data s chybami
            return "admin/design-form";
        }
        try {
            Design updatedDesign = designService.updateDesign(id, designData);
            redirectAttributes.addFlashAttribute("successMessage", "Design '" + updatedDesign.getName() + "' byl úspěšně aktualizován.");
            log.info("Design ID {} updated successfully.", id);
            return "redirect:/admin/designs";
        } catch (EntityNotFoundException e) {
            log.warn("Cannot update design. Design not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/designs";
        } catch (IllegalArgumentException e) {
            log.warn("Error updating design ID {}: {}", id, e.getMessage());
            bindingResult.rejectValue("name", "error.design.duplicate", e.getMessage());
            model.addAttribute("pageTitle", "Upravit design (Chyba)");
            designData.setId(id); // Znovu nastavit ID
            model.addAttribute("design", designData);
            model.addAttribute("errorMessage", e.getMessage());
            return "admin/design-form";
        } catch (Exception e) {
            log.error("Unexpected error updating design ID {}: {}", id, e.getMessage(), e);
            model.addAttribute("pageTitle", "Upravit design (Chyba)");
            designData.setId(id); // Znovu nastavit ID
            model.addAttribute("design", designData);
            model.addAttribute("errorMessage", "Při aktualizaci designu nastala neočekávaná chyba: " + e.getMessage());
            return "admin/design-form";
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteDesign(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.warn("Attempting to SOFT DELETE design ID: {}", id);
        try {
            designService.deleteDesign(id); // Metoda provádí soft delete (deaktivaci)
            redirectAttributes.addFlashAttribute("successMessage", "Design byl úspěšně deaktivován.");
            log.info("Design ID {} successfully deactivated.", id);
        } catch (EntityNotFoundException e) {
            log.warn("Cannot deactivate design. Design not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (IllegalStateException e) {
            // Chyba, pokud je design používán
            log.error("Error deactivating design ID {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error deactivating design ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Při deaktivaci designu nastala chyba: " + e.getMessage());
        }
        return "redirect:/admin/designs";
    }
}