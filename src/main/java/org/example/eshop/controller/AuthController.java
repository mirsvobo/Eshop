package org.example.eshop.controller;

import jakarta.validation.Valid;
import org.example.eshop.dto.RegistrationDto; // Potřebujeme DTO pro registraci
import org.example.eshop.service.CustomerService; // Potřebujeme CustomerService
import org.slf4j.Logger; // Import loggeru
import org.slf4j.LoggerFactory; // Import loggeru
import org.springframework.beans.factory.annotation.Autowired; // Import Autowired
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model; // Import Model
import org.springframework.validation.BindingResult; // Import BindingResult
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute; // Import ModelAttribute
import org.springframework.web.bind.annotation.PostMapping; // Import PostMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // Import RedirectAttributes

@Controller
public class AuthController {

    // Přidání loggeru
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    // Přidání závislosti na CustomerService
    @Autowired
    private CustomerService customerService;

    // --- Přihlášení ---
    @GetMapping("/prihlaseni")
    public String showLoginPage() {
        log.debug("Displaying login page");
        return "prihlaseni";
    }

    // --- Registrace ---
    @GetMapping("/registrace")
    public String showRegistrationPage(Model model) {
        log.debug("Displaying registration page");
        // Přidáme prázdné DTO do modelu, aby se na něj mohl formulář navázat
        model.addAttribute("registrationDto", new RegistrationDto());
        return "registrace"; // Název Thymeleaf šablony pro registraci
    }

    @PostMapping("/registrace")
    public String processRegistration(
            @Valid @ModelAttribute("registrationDto") RegistrationDto registrationDto,
            BindingResult bindingResult, // Výsledek validace
            Model model, // Pro případ zobrazení chyby ve formuláři
            RedirectAttributes redirectAttributes) { // Pro zprávu po přesměrování

        log.info("Processing registration request for email: {}", registrationDto.getEmail());

        // 1. Zkontrolovat validaci DTO (pomocí anotací v RegistrationDto)
        if (bindingResult.hasErrors()) {
            log.warn("Registration form validation failed: {}", bindingResult.getAllErrors());
            // Nepřesměrováváme, zůstáváme na stránce registrace a zobrazíme chyby
            // Model s registrationDto (včetně chyb) se automaticky předá zpět
            return "registrace";
        }

        // 2. Zkusit zaregistrovat uživatele pomocí CustomerService
        try {
            customerService.registerCustomer(registrationDto);
            log.info("Customer registered successfully: {}", registrationDto.getEmail());
            // Přesměrování na přihlašovací stránku s úspěšnou zprávou
            redirectAttributes.addFlashAttribute("registraceSuccess", "Registrace proběhla úspěšně. Nyní se můžete přihlásit.");
            return "redirect:/prihlaseni";

        } catch (IllegalArgumentException e) { // Chytáme specifickou výjimku pro duplicitní email atd.
            log.warn("Registration failed for {}: {}", registrationDto.getEmail(), e.getMessage());
            // Zůstaneme na registrační stránce a zobrazíme chybu
            // registrationDto se opět automaticky předá zpět
            model.addAttribute("registrationError", e.getMessage()); // Předáme chybovou zprávu do modelu
            return "registrace";
        } catch (Exception e) { // Obecná chyba
            log.error("Unexpected error during registration for {}: {}", registrationDto.getEmail(), e.getMessage(), e);
            model.addAttribute("registrationError", "Při registraci nastala neočekávaná chyba. Zkuste to prosím znovu.");
            return "registrace";
        }
    }
}