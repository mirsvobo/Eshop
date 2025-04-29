package org.example.eshop.admin.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.eshop.admin.service.GlazeService;
import org.example.eshop.model.Glaze;
import org.example.eshop.repository.GlazeRepository; // Přidáno
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
@RequestMapping("/admin/glazes")
@PreAuthorize("hasRole('ADMIN')")
public class AdminGlazeController {

    private static final Logger log = LoggerFactory.getLogger(AdminGlazeController.class);

    @Autowired
    private GlazeService glazeService;

    // --- Přidané závislosti ---
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private GlazeRepository glazeRepository; // Pro přímé uložení po změně URL
    // --------------------------

    @ModelAttribute("currentUri")
    public String getCurrentUri(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @GetMapping
    public String listGlazes(Model model) {
        log.info("Requesting glaze list view.");
        try {
            List<Glaze> glazes = glazeService.getAllGlazesSortedByName();
            model.addAttribute("glazes", glazes);
        } catch (Exception e) {
            log.error("Error fetching glazes: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Nepodařilo se načíst seznam lazur.");
            model.addAttribute("glazes", Collections.emptyList());
        }
        return "admin/glazes-list"; // Název Thymeleaf šablony
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        log.info("Requesting new glaze form.");
        model.addAttribute("glaze", new Glaze());
        model.addAttribute("pageTitle", "Vytvořit novou lazuru");
        return "admin/glaze-form"; // Název Thymeleaf šablony
    }

    @PostMapping
    public String createGlaze(@Valid @ModelAttribute("glaze") Glaze glaze,
                              BindingResult bindingResult,
                              RedirectAttributes redirectAttributes,
                              Model model) {
        log.info("Attempting to create new glaze: {}", glaze.getName());
        if (bindingResult.hasErrors()) {
            log.warn("Validation errors creating glaze: {}", bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Vytvořit novou lazuru (Chyba)");
            return "admin/glaze-form";
        }
        try {
            Glaze savedGlaze = glazeService.createGlaze(glaze);
            redirectAttributes.addFlashAttribute("successMessage", "Lazura '" + savedGlaze.getName() + "' byla úspěšně vytvořena.");
            log.info("Glaze '{}' created successfully with ID: {}", savedGlaze.getName(), savedGlaze.getId());
            return "redirect:/admin/glazes";
        } catch (IllegalArgumentException e) {
            log.warn("Error creating glaze '{}': {}", glaze.getName(), e.getMessage());
            bindingResult.rejectValue("name", "error.glaze.duplicate", e.getMessage());
            model.addAttribute("pageTitle", "Vytvořit novou lazuru (Chyba)");
            model.addAttribute("errorMessage", e.getMessage());
            return "admin/glaze-form";
        } catch (Exception e) {
            log.error("Unexpected error creating glaze '{}': {}", glaze.getName(), e.getMessage(), e);
            model.addAttribute("pageTitle", "Vytvořit novou lazuru (Chyba)");
            model.addAttribute("errorMessage", "Při vytváření lazury nastala neočekávaná chyba: " + e.getMessage());
            return "admin/glaze-form";
        }
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Requesting edit form for glaze ID: {}", id);
        try {
            Glaze glaze = glazeService.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Lazura s ID " + id + " nenalezena."));
            model.addAttribute("glaze", glaze);
            model.addAttribute("pageTitle", "Upravit lazuru: " + glaze.getName());
            return "admin/glaze-form";
        } catch (EntityNotFoundException e) {
            log.warn("Glaze with ID {} not found for editing.", id);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/glazes";
        } catch (Exception e) {
            log.error("Error loading glaze ID {} for edit: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Nepodařilo se načíst lazuru k úpravě.");
            return "redirect:/admin/glazes";
        }
    }

    @PostMapping("/{id}")
    public String updateGlaze(@PathVariable Long id,
                              @Valid @ModelAttribute("glaze") Glaze glazeData,
                              BindingResult bindingResult,
                              RedirectAttributes redirectAttributes,
                              Model model) {
        log.info("Attempting to update glaze ID: {}", id);
        if (bindingResult.hasErrors()) {
            log.warn("Validation errors updating glaze {}: {}", id, bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Upravit lazuru (Chyba)");
            glazeData.setId(id); // Ensure ID is preserved for the form action
            model.addAttribute("glaze", glazeData);
            return "admin/glaze-form";
        }
        try {
            Glaze updatedGlaze = glazeService.updateGlaze(id, glazeData);
            redirectAttributes.addFlashAttribute("successMessage", "Lazura '" + updatedGlaze.getName() + "' byla úspěšně aktualizována.");
            log.info("Glaze ID {} updated successfully.", id);
            return "redirect:/admin/glazes";
        } catch (EntityNotFoundException e) {
            log.warn("Cannot update glaze. Glaze not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/glazes";
        } catch (IllegalArgumentException e) {
            log.warn("Error updating glaze ID {}: {}", id, e.getMessage());
            bindingResult.rejectValue("name", "error.glaze.duplicate", e.getMessage()); // Příklad chyby
            model.addAttribute("pageTitle", "Upravit lazuru (Chyba)");
            glazeData.setId(id);
            model.addAttribute("glaze", glazeData);
            model.addAttribute("errorMessage", e.getMessage());
            return "admin/glaze-form";
        } catch (Exception e) {
            log.error("Unexpected error updating glaze ID {}: {}", id, e.getMessage(), e);
            model.addAttribute("pageTitle", "Upravit lazuru (Chyba)");
            glazeData.setId(id);
            model.addAttribute("glaze", glazeData);
            model.addAttribute("errorMessage", "Při aktualizaci lazury nastala neočekávaná chyba: " + e.getMessage());
            return "admin/glaze-form";
        }
    }

    // V AdminGlazeController.java

    // --- Změna v metodě pro AJAX upload ---
    @PostMapping("/{glazeId}/upload-image")
    @ResponseBody
    public ResponseEntity<?> uploadGlazeImage(@PathVariable Long glazeId,
                                              @RequestParam("attributeImageFile") MultipartFile imageFile) {
        log.info("Attempting to upload image for Glaze ID: {}", glazeId);
        if (imageFile.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vyberte prosím soubor k nahrání."));
        }
        if (glazeId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Chybí ID lazury."));
        }

        try {
            Glaze glaze = glazeRepository.findById(glazeId)
                    .orElseThrow(() -> new EntityNotFoundException("Lazura s ID " + glazeId + " nenalezena."));

            // --- ZAČÁTEK ZMĚNY: Mazání starého souboru z GCS ---
            String oldFileUrl = glaze.getImageUrl();
            if (oldFileUrl != null && !oldFileUrl.isEmpty()) {
                try {
                    log.debug("Attempting to delete old image file from GCS for Glaze ID {}: {}", glazeId, oldFileUrl);
                    fileStorageService.deleteFile(oldFileUrl); // Použije novou implementaci pro GCS
                } catch (Exception e) {
                    log.warn("Could not delete old GCS file {} for Glaze ID {}: {}", oldFileUrl, glazeId, e.getMessage());
                }
            }
            // --- KONEC ZMĚNY ---

            // Uložit nový soubor do GCS a získat URL
            String newFileUrl = fileStorageService.storeFile(imageFile, "glazes"); // storeFile nyní ukládá do GCS
            log.info("New image stored in GCS for Glaze ID {}. URL: {}", glazeId, newFileUrl);

            // Aktualizovat URL v entitě Glaze
            glaze.setImageUrl(newFileUrl); // Uložíme GCS URL

            glazeRepository.save(glaze);
            log.info("GCS Image URL updated in database for Glaze ID {}", glazeId);

            // Vrátit úspěšnou odpověď s GCS URL
            return ResponseEntity.ok(Map.of(
                    "message", "Obrázek úspěšně nahrán.",
                    "imageUrl", newFileUrl, // Vracíme GCS URL
                    "glazeId", glazeId
            ));

        } catch (EntityNotFoundException e) {
            log.warn("Cannot upload image. Glaze not found: ID={}", glazeId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IOException | IllegalArgumentException e) {
            log.error("Failed to store image file for Glaze ID {}: {}", glazeId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Nahrání obrázku selhalo: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error uploading image for Glaze ID {}: {}", glazeId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Neočekávaná chyba při nahrávání obrázku."));
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteGlaze(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.warn("Attempting to SOFT DELETE glaze ID: {}", id);
        try {
            glazeService.deleteGlaze(id);
            redirectAttributes.addFlashAttribute("successMessage", "Lazura byla úspěšně deaktivována.");
            log.info("Glaze ID {} successfully deactivated.", id);
        } catch (EntityNotFoundException e) {
            log.warn("Cannot deactivate glaze. Glaze not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (IllegalStateException e) {
            log.error("Error deactivating glaze ID {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error deactivating glaze ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Při deaktivaci lazury nastala chyba: " + e.getMessage());
        }
        return "redirect:/admin/glazes";
    }
}