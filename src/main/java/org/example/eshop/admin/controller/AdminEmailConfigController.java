package org.example.eshop.admin.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.eshop.model.EmailTemplateConfig;
import org.example.eshop.model.OrderState;
import org.example.eshop.repository.EmailTemplateConfigRepository; // Přidán přímý import repozitáře
import org.example.eshop.service.EmailService; // Pro čištění cache
import org.example.eshop.service.OrderStateService; // Pro načtení stavů
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/email-configs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEmailConfigController {

    private static final Logger log = LoggerFactory.getLogger(AdminEmailConfigController.class);

    // TODO: V budoucnu nahradit dedikovanou EmailTemplateConfigService
    @Autowired private EmailTemplateConfigRepository emailTemplateConfigRepository;
    @Autowired private OrderStateService orderStateService;
    @Autowired private EmailService emailService; // Pro čištění cache

    @ModelAttribute("currentUri")
    public String getCurrentUri(HttpServletRequest request) {
        return request.getRequestURI().replaceFirst("/(new|\\d+/edit)$", "");
    }

    // Pomocná metoda pro načtení všech stavů do modelu (pro select box)
    private void loadAllOrderStates(Model model) {
        List<OrderState> states = orderStateService.getAllOrderStatesSorted();
        model.addAttribute("allOrderStates", states);
    }

    @GetMapping
    public String listEmailConfigs(Model model) {
        log.info("Requesting email config list view.");
        try {
            List<EmailTemplateConfig> configs = emailTemplateConfigRepository.findAll();
            // Seřadíme podle kódu stavu pro konzistenci
            configs.sort(Comparator.comparing(EmailTemplateConfig::getStateCode, String.CASE_INSENSITIVE_ORDER));
            model.addAttribute("emailConfigs", configs);
            // Přidáme mapu stavů pro zobrazení názvu stavu v seznamu
            Map<String, String> stateNames = orderStateService.getAllOrderStatesSorted().stream()
                    .collect(Collectors.toMap(OrderState::getCode, OrderState::getName));
            model.addAttribute("orderStateNames", stateNames);

        } catch (Exception e) {
            log.error("Error fetching email configs: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Nepodařilo se načíst konfigurace emailů.");
        }
        return "admin/email-configs-list";
    }

    @GetMapping("/new")
    public String showCreateEmailConfigForm(Model model) {
        log.info("Requesting new email config form.");
        model.addAttribute("emailTemplateConfig", new EmailTemplateConfig());
        loadAllOrderStates(model); // Načteme stavy pro dropdown
        model.addAttribute("pageTitle", "Vytvořit konfiguraci emailu");
        return "admin/email-config-form";
    }

    @PostMapping
    public String createEmailConfig(@Valid @ModelAttribute("emailTemplateConfig") EmailTemplateConfig config,
                                    BindingResult bindingResult,
                                    RedirectAttributes redirectAttributes,
                                    Model model) {
        log.info("Attempting to create new email config for state code: {}", config.getStateCode());

        // Validace unikátnosti stateCode (musí být case-insensitive)
        if (config.getStateCode() != null) {
            config.setStateCode(config.getStateCode().trim().toUpperCase());
            if (emailTemplateConfigRepository.findByStateCodeIgnoreCase(config.getStateCode()).isPresent()) {
                bindingResult.rejectValue("stateCode", "error.emailConfig.duplicate", "Konfigurace pro tento stav již existuje.");
            }
        } else {
            bindingResult.rejectValue("stateCode", "NotNull", "Kód stavu nesmí být prázdný.");
        }

        if (bindingResult.hasErrors()) {
            log.warn("Validation errors creating email config: {}", bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Vytvořit konfiguraci (Chyba)");
            loadAllOrderStates(model); // Znovu načíst stavy
            return "admin/email-config-form";
        }
        try {
            EmailTemplateConfig savedConfig = emailTemplateConfigRepository.save(config);
            emailService.clearConfigCache(); // Vyčistit cache
            redirectAttributes.addFlashAttribute("successMessage", "Konfigurace emailu pro stav '" + savedConfig.getStateCode() + "' byla úspěšně vytvořena.");
            log.info("Email config for state '{}' created successfully with ID: {}", savedConfig.getStateCode(), savedConfig.getId());
            return "redirect:/admin/email-configs";
        } catch (Exception e) {
            log.error("Unexpected error creating email config for state '{}': {}", config.getStateCode(), e.getMessage(), e);
            model.addAttribute("errorMessage", "Při vytváření konfigurace nastala neočekávaná chyba: " + e.getMessage());
            model.addAttribute("pageTitle", "Vytvořit konfiguraci (Chyba)");
            loadAllOrderStates(model);
            return "admin/email-config-form";
        }
    }

    @GetMapping("/{id}/edit")
    public String showEditEmailConfigForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Requesting edit form for email config ID: {}", id);
        try {
            EmailTemplateConfig config = emailTemplateConfigRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Konfigurace emailu s ID " + id + " nenalezena."));
            model.addAttribute("emailTemplateConfig", config);
            loadAllOrderStates(model); // Načteme stavy pro dropdown
            model.addAttribute("pageTitle", "Upravit konfiguraci emailu: " + config.getStateCode());
            return "admin/email-config-form";
        } catch (EntityNotFoundException e) {
            log.warn("Email config with ID {} not found for editing.", id);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/email-configs";
        } catch (Exception e) {
            log.error("Error loading email config ID {} for edit: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Nepodařilo se načíst konfiguraci k úpravě.");
            return "redirect:/admin/email-configs";
        }
    }

    @PostMapping("/{id}")
    public String updateEmailConfig(@PathVariable Long id,
                                    @Valid @ModelAttribute("emailTemplateConfig") EmailTemplateConfig configData,
                                    BindingResult bindingResult,
                                    RedirectAttributes redirectAttributes,
                                    Model model) {
        log.info("Attempting to update email config ID: {}", id);

        // Ruční validace unikátnosti stateCode při změně
        if (configData.getStateCode() != null) {
            configData.setStateCode(configData.getStateCode().trim().toUpperCase());
            Optional<EmailTemplateConfig> existingWithCode = emailTemplateConfigRepository.findByStateCodeIgnoreCase(configData.getStateCode());
            if (existingWithCode.isPresent() && !existingWithCode.get().getId().equals(id)) {
                bindingResult.rejectValue("stateCode", "error.emailConfig.duplicate", "Konfigurace pro tento stav již existuje.");
            }
        } else {
            bindingResult.rejectValue("stateCode", "NotNull", "Kód stavu nesmí být prázdný.");
        }


        if (bindingResult.hasErrors()) {
            log.warn("Validation errors updating email config {}: {}", id, bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Upravit konfiguraci (Chyba)");
            configData.setId(id); // Zachovat ID
            loadAllOrderStates(model); // Znovu načíst stavy
            return "admin/email-config-form";
        }
        try {
            EmailTemplateConfig existingConfig = emailTemplateConfigRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Konfigurace emailu s ID " + id + " nenalezena pro update."));

            // Ruční přenesení hodnot (bezpečnější než save(configData))
            existingConfig.setStateCode(configData.getStateCode());
            existingConfig.setSendEmail(configData.isSendEmail());
            existingConfig.setTemplateName(configData.getTemplateName());
            existingConfig.setSubjectTemplate(configData.getSubjectTemplate());
            existingConfig.setDescription(configData.getDescription());

            EmailTemplateConfig updatedConfig = emailTemplateConfigRepository.save(existingConfig);
            emailService.clearConfigCache(); // Vyčistit cache
            redirectAttributes.addFlashAttribute("successMessage", "Konfigurace emailu pro stav '" + updatedConfig.getStateCode() + "' byla úspěšně aktualizována.");
            log.info("Email config ID {} updated successfully.", id);
            return "redirect:/admin/email-configs";
        } catch (EntityNotFoundException e) {
            log.warn("Cannot update email config. Config not found: ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/email-configs";
        } catch (Exception e) {
            log.error("Unexpected error updating email config ID {}: {}", id, e.getMessage(), e);
            model.addAttribute("pageTitle", "Upravit konfiguraci (Chyba)");
            configData.setId(id); // Zachovat ID
            model.addAttribute("emailTemplateConfig", configData); // Vrátit data s chybami
            loadAllOrderStates(model); // Načíst stavy
            model.addAttribute("errorMessage", "Při aktualizaci konfigurace nastala neočekávaná chyba: " + e.getMessage());
            return "admin/email-config-form";
        }
    }

    // Smazání konfigurace nedává smysl, raději jen editovat a vypnout odesílání.
    // @PostMapping("/{id}/delete") ...
}