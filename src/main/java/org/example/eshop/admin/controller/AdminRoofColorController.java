package org.example.eshop.admin.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.eshop.admin.service.RoofColorService;
import org.example.eshop.model.RoofColor;
import org.example.eshop.repository.RoofColorRepository; // Přidáno
import org.example.eshop.service.FileStorageService; // Přidáno
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired; // Přidáno
import org.springframework.http.HttpStatus; // Přidáno
import org.springframework.http.ResponseEntity; // Přidáno
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile; // Přidáno
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException; // Přidáno
import java.util.Collections;
import java.util.List;
import java.util.Map; // Přidáno

@Controller
@RequestMapping("/admin/roof-colors")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRoofColorController {

    private static final Logger log = LoggerFactory.getLogger(AdminRoofColorController.class);

    @Autowired
    private RoofColorService roofColorService;

    // --- Přidané závislosti ---
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private RoofColorRepository roofColorRepository; // Pro přímé uložení po změně URL
    // --------------------------


    @ModelAttribute("currentUri")
    public String getCurrentUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        log.trace("Setting currentUri model attribute to: {}", uri);
        return uri;
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

    // V AdminRoofColorController.java

    // --- Změna v metodě pro AJAX upload ---
    @PostMapping("/{roofColorId}/upload-image")
    @ResponseBody
    public ResponseEntity<?> uploadRoofColorImage(@PathVariable Long roofColorId,
                                                  @RequestParam("attributeImageFile") MultipartFile imageFile) {
        log.info("Attempting to upload image for RoofColor ID: {}", roofColorId);
        if (imageFile.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vyberte prosím soubor k nahrání."));
        }
        if (roofColorId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Chybí ID barvy střechy."));
        }

        try {
            RoofColor roofColor = roofColorRepository.findById(roofColorId)
                    .orElseThrow(() -> new EntityNotFoundException("Barva střechy s ID " + roofColorId + " nenalezena."));

            // --- ZAČÁTEK ZMĚNY: Mazání starého souboru z GCS ---
            String oldFileUrl = roofColor.getImageUrl();
            if (oldFileUrl != null && !oldFileUrl.isEmpty()) {
                try {
                    log.debug("Attempting to delete old image file from GCS for RoofColor ID {}: {}", roofColorId, oldFileUrl);
                    fileStorageService.deleteFile(oldFileUrl); // Použije novou implementaci pro GCS
                } catch (Exception e) {
                    log.warn("Could not delete old GCS file {} for RoofColor ID {}: {}", oldFileUrl, roofColorId, e.getMessage());
                }
            }
            // --- KONEC ZMĚNY ---

            // Uložit nový soubor do GCS a získat URL
            String newFileUrl = fileStorageService.storeFile(imageFile, "roof-colors"); // storeFile nyní ukládá do GCS
            log.info("New image stored in GCS for RoofColor ID {}. URL: {}", roofColorId, newFileUrl);

            // Aktualizovat URL v entitě RoofColor
            roofColor.setImageUrl(newFileUrl); // Uložíme GCS URL

            roofColorRepository.save(roofColor);
            log.info("GCS Image URL updated in database for RoofColor ID {}", roofColorId);

            // Vrátit úspěšnou odpověď s GCS URL
            return ResponseEntity.ok(Map.of(
                    "message", "Obrázek úspěšně nahrán.",
                    "imageUrl", newFileUrl, // Vracíme GCS URL
                    "roofColorId", roofColorId
            ));

        } catch (EntityNotFoundException e) {
            log.warn("Cannot upload image. RoofColor not found: ID={}", roofColorId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IOException | IllegalArgumentException e) {
            log.error("Failed to store image file for RoofColor ID {}: {}", roofColorId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Nahrání obrázku selhalo: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error uploading image for RoofColor ID {}: {}", roofColorId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Neočekávaná chyba při nahrávání obrázku."));
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