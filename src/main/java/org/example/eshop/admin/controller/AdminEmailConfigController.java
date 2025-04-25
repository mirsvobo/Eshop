package org.example.eshop.admin.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.eshop.model.EmailTemplateConfig;
import org.example.eshop.model.OrderState;
import org.example.eshop.repository.EmailTemplateConfigRepository;
import org.example.eshop.service.EmailService;
import org.example.eshop.service.OrderStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/email-configs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEmailConfigController {

    private static final Logger log = LoggerFactory.getLogger(AdminEmailConfigController.class);

    // Poznámka: Zvažte použití dedikované Service vrstvy místo přímého repozitáře
    @Autowired
    private EmailTemplateConfigRepository emailTemplateConfigRepository;
    @Autowired
    private OrderStateService orderStateService;
    @Autowired
    private EmailService emailService; // Pro čištění cache

    @ModelAttribute("currentUri")
    public String getCurrentUri(HttpServletRequest request) {
        // Odstraní /new nebo /<id>/edit pro správné zvýraznění v menu
        return request.getRequestURI().replaceFirst("/(new|\\d+/edit)$", "");
    }

    // Pomocná metoda pro načtení všech stavů do modelu (pro select box)
    private void loadAllOrderStates(Model model) {
        try {
            List<OrderState> states = orderStateService.getAllOrderStatesSorted();
            model.addAttribute("allOrderStates", states);
        } catch (Exception e) {
            log.error("Failed to load order states: {}", e.getMessage(), e);
            // Případně přidat chybovou hlášku do modelu, pokud je to kritické
            model.addAttribute("allOrderStates", List.of()); // Poskytnout prázdný seznam
        }
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
            String normalizedCode = config.getStateCode().trim().toUpperCase();
            config.setStateCode(normalizedCode); // Normalizujeme kód před další kontrolou
            if (emailTemplateConfigRepository.findByStateCodeIgnoreCase(normalizedCode).isPresent()) {
                bindingResult.rejectValue("stateCode", "error.emailConfig.duplicate", "Konfigurace pro tento stav již existuje.");
            }
        } else {
            // Tato kontrola by měla být pokryta @Valid (@NotEmpty nebo @NotBlank na entitě)
            if (!bindingResult.hasFieldErrors("stateCode")) { // Jen pokud ještě není chyba z @Valid
                bindingResult.rejectValue("stateCode", "NotNull", "Kód stavu nesmí být prázdný.");
            }
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
            loadAllOrderStates(model); // Načteme všechny stavy

            // --- Opravená část: Příprava zobrazovaného názvu stavu ---
            String currentStateDisplayName = "N/A"; // Výchozí hodnota
            List<OrderState> allStates = orderStateService.getAllOrderStatesSorted(); // Získáme stavy (již by měly být v modelu, ale pro jistotu)

            if (config.getStateCode() != null && allStates != null) {
                currentStateDisplayName = allStates.stream()
                        // Bezpečné porovnání kódů (case-insensitive, pokud je potřeba, zde je sensitive)
                        .filter(state -> Objects.equals(state.getCode(), config.getStateCode()))
                        .map(state -> state.getName() + " (" + state.getCode() + ")") // Vytvořit řetězec Název (KOD)
                        .findFirst() // Najít první shodu
                        .orElse(config.getStateCode()); // Pokud nenalezeno, zobrazit jen kód
            }
            model.addAttribute("currentStateDisplayName", currentStateDisplayName); // Přidáme do modelu
            // ----------------------------------------------------------

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

    // Poznámka: Metoda update používá POST, což je v šabloně nastaveno pomocí hidden inputu.
    // Pokud byste chtěli použít PUT, změňte value v hidden inputu na "PUT"
    // a zde změňte @PostMapping na @PutMapping.
    @PostMapping("/{id}")
    public String updateEmailConfig(@PathVariable Long id,
                                    @Valid @ModelAttribute("emailTemplateConfig") EmailTemplateConfig configData,
                                    BindingResult bindingResult,
                                    RedirectAttributes redirectAttributes,
                                    Model model) {
        log.info("Attempting to update email config ID: {}", id);

        // Najdeme existující konfiguraci NEBO vyhodíme výjimku HNED na začátku
        Optional<EmailTemplateConfig> existingConfigOpt = emailTemplateConfigRepository.findById(id);
        if (existingConfigOpt.isEmpty()) {
            log.warn("Cannot update email config. Config not found: ID={}", id);
            redirectAttributes.addFlashAttribute("errorMessage", "Konfigurace emailu s ID " + id + " nenalezena pro update.");
            return "redirect:/admin/email-configs";
        }
        EmailTemplateConfig existingConfig = existingConfigOpt.get();

        // Získání kódu stavu z databáze (ten se nemění a musí být odeslán)
        String originalStateCode = existingConfig.getStateCode();
        // Nastavíme ho do příchozích dat, aby prošel validací a byl dostupný v modelu v případě chyby
        configData.setStateCode(originalStateCode);


        // Pokud jsou chyby z @Valid anotací (kromě stateCode, který jsme právě nastavili)
        if (bindingResult.hasErrors()) {
            // Odstraníme případnou chybu u stateCode, protože ten jsme nastavili ručně
            if (bindingResult.hasFieldErrors("stateCode")) {
                // Toto je složitější, ideálně by validace @Valid neměla běžet na stateCode při update
                // Nebo by se musela validace přepsat. Pro jednoduchost chybu necháme,
                // ale v šabloně se zobrazí správná hodnota.
                log.warn("Validation errors on fields other than potentially stateCode during update of {}: {}", id, bindingResult.getAllErrors());
            } else {
                log.warn("Validation errors updating email config {}: {}", id, bindingResult.getAllErrors());
            }

            model.addAttribute("pageTitle", "Upravit konfiguraci (Chyba)");
            configData.setId(id); // Zachovat ID
            loadAllOrderStates(model); // Znovu načíst stavy
            // Znovu připravit zobrazovaný název pro případ návratu na formulář
            model.addAttribute("currentStateDisplayName", existingConfig.getStateCode()); // Zobrazíme jen kód pro jednoduchost při chybě
            if (orderStateService != null) { // Zkontrolujeme null pro jistotu
                List<OrderState> allStates = orderStateService.getAllOrderStatesSorted();
                String displayName = allStates.stream()
                        .filter(state -> Objects.equals(state.getCode(), originalStateCode))
                        .map(state -> state.getName() + " (" + state.getCode() + ")")
                        .findFirst()
                        .orElse(originalStateCode);
                model.addAttribute("currentStateDisplayName", displayName);
            }

            return "admin/email-config-form";
        }

        // Pokud validace prošla, aktualizujeme entitu
        try {
            // Ruční přenesení hodnot (bezpečnější než save(configData))
            // stateCode se nemění!
            existingConfig.setSendEmail(configData.isSendEmail());
            existingConfig.setTemplateName(configData.getTemplateName());
            existingConfig.setSubjectTemplate(configData.getSubjectTemplate());
            existingConfig.setDescription(configData.getDescription());

            EmailTemplateConfig updatedConfig = emailTemplateConfigRepository.save(existingConfig);
            emailService.clearConfigCache(); // Vyčistit cache
            redirectAttributes.addFlashAttribute("successMessage", "Konfigurace emailu pro stav '" + updatedConfig.getStateCode() + "' byla úspěšně aktualizována.");
            log.info("Email config ID {} updated successfully.", id);
            return "redirect:/admin/email-configs";
        } catch (Exception e) { // Zachytáváme obecnější výjimku pro případ selhání save atd.
            log.error("Unexpected error updating email config ID {}: {}", id, e.getMessage(), e);
            model.addAttribute("pageTitle", "Upravit konfiguraci (Chyba)");
            configData.setId(id); // Zachovat ID
            configData.setStateCode(originalStateCode); // Ujistit se, že stateCode je správný
            model.addAttribute("emailTemplateConfig", configData); // Vrátit data s chybami
            loadAllOrderStates(model); // Načíst stavy
            // Znovu připravit zobrazovaný název
            model.addAttribute("currentStateDisplayName", originalStateCode); // Zobrazíme jen kód pro jednoduchost při chybě
            if (orderStateService != null) {
                List<OrderState> allStates = orderStateService.getAllOrderStatesSorted();
                String displayName = allStates.stream()
                        .filter(state -> Objects.equals(state.getCode(), originalStateCode))
                        .map(state -> state.getName() + " (" + state.getCode() + ")")
                        .findFirst()
                        .orElse(originalStateCode);
                model.addAttribute("currentStateDisplayName", displayName);
            }
            model.addAttribute("errorMessage", "Při aktualizaci konfigurace nastala neočekávaná chyba: " + e.getMessage());
            return "admin/email-config-form";
        }
    }

    // Smazání konfigurace nedává smysl, raději jen editovat a vypnout odesílání.
    // @PostMapping("/{id}/delete") ...
}