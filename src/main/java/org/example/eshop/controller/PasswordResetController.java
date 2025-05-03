package org.example.eshop.controller; // Uprav dle své struktury

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.example.eshop.model.PasswordResetToken;
import org.example.eshop.service.CustomerService; // Ujisti se, že importuješ správnou service
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.persistence.EntityNotFoundException; // Přidán import

import java.util.Optional;

@Controller
public class PasswordResetController {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetController.class);

    // --- OPRAVA: Přidána @Autowired závislost ---
    @Autowired
    private CustomerService customerService; // Nebo PasswordResetService, pokud jsi ji vytvořil

    // DTO pro formulář žádosti o reset
    @Data
    public static class ForgotPasswordDto {
        @NotBlank(message = "Email nesmí být prázdný.")
        @Email(message = "Zadejte platný email.")
        private String email;
    }

    // DTO pro formulář změny hesla
    @Data
    public static class ResetPasswordDto {
        @NotBlank
        private String token;

        @NotBlank(message = "Nové heslo nesmí být prázdné.")
        @Size(min = 6, message = "Heslo musí mít alespoň 6 znaků.") // Změněno min na 6 dle tvého registračního DTO
        private String newPassword;

        @NotBlank(message = "Potvrzení hesla nesmí být prázdné.")
        private String confirmPassword;
    }

    @GetMapping("/zapomenute-heslo")
    public String showForgotPasswordForm(Model model) {
        model.addAttribute("forgotPasswordDto", new ForgotPasswordDto());
        return "zapomenute-heslo";
    }

    @PostMapping("/zapomenute-heslo")
    public String processForgotPassword(@Valid @ModelAttribute("forgotPasswordDto") ForgotPasswordDto dto,
                                        BindingResult bindingResult,
                                        RedirectAttributes redirectAttributes,
                                        Model model) {
        if (bindingResult.hasErrors()) {
            return "zapomenute-heslo";
        }
        try {
            // --- OPRAVA: Volání metody přes customerService ---
            customerService.createPasswordResetTokenForUser(dto.getEmail());
            redirectAttributes.addFlashAttribute("successMessage", "Pokud váš email existuje v našem systému, byl na něj odeslán odkaz pro reset hesla.");
            return "redirect:/prihlaseni";
        } catch (EntityNotFoundException e) {
            log.warn("Password reset requested for non-existent email: {}", dto.getEmail());
            // Můžeme zobrazit obecnou zprávu, aby se neodhalilo, zda email existuje
            redirectAttributes.addFlashAttribute("successMessage", "Pokud váš email existuje v našem systému, byl na něj odeslán odkaz pro reset hesla.");
            return "redirect:/prihlaseni"; // Přesměrování i při nenalezení pro bezpečnost
        } catch (IllegalArgumentException e) { // Např. pro účet hosta
            log.warn("Error processing forgot password request for {}: {}", dto.getEmail(), e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            return "zapomenute-heslo";
        } catch (Exception e) {
            log.error("Error processing forgot password request for {}: {}", dto.getEmail(), e.getMessage());
            model.addAttribute("errorMessage", "Žádost o reset hesla se nezdařila. Zkuste to prosím znovu.");
            return "zapomenute-heslo";
        }
    }

    @GetMapping("/resetovat-heslo")
    public String showResetPasswordForm(@RequestParam("token") String token, Model model, RedirectAttributes redirectAttributes) {
        // --- OPRAVA: Volání metody přes customerService ---
        Optional<PasswordResetToken> tokenOpt = customerService.validatePasswordResetToken(token);
        if (tokenOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Odkaz pro reset hesla je neplatný nebo vypršel.");
            return "redirect:/zapomenute-heslo";
        }
        ResetPasswordDto dto = new ResetPasswordDto();
        dto.setToken(token);
        model.addAttribute("resetPasswordDto", dto);
        return "resetovat-heslo";
    }

    @PostMapping("/resetovat-heslo")
    public String processResetPassword(@Valid @ModelAttribute("resetPasswordDto") ResetPasswordDto dto,
                                       BindingResult bindingResult,
                                       RedirectAttributes redirectAttributes,
                                       Model model) {

        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "error.resetPasswordDto", "Hesla se neshodují.");
        }
        if (bindingResult.hasErrors()) {
            // Token je v DTO, takže ho není třeba znovu přidávat do modelu
            return "resetovat-heslo";
        }

        // --- OPRAVA: Volání metody přes customerService ---
        Optional<PasswordResetToken> tokenOpt = customerService.validatePasswordResetToken(dto.getToken());
        if (tokenOpt.isEmpty()) {
            // Zde je lepší zobrazit chybu přímo na stránce, ne přesměrovávat
            model.addAttribute("errorMessage", "Odkaz pro reset hesla je neplatný nebo vypršel. Požádejte o nový.");
            // Token je v DTO, takže se formulář zobrazí správně
            return "resetovat-heslo";
        }

        try {
            // --- OPRAVA: Volání metody přes customerService ---
            customerService.changeUserPassword(tokenOpt.get(), dto.getNewPassword());
            redirectAttributes.addFlashAttribute("successMessage", "Vaše heslo bylo úspěšně změněno. Nyní se můžete přihlásit."); // Změněno na successMessage pro konzistenci
            return "redirect:/prihlaseni";
        } catch (Exception e) {
            log.error("Error resetting password for token {}:", dto.getToken(), e);
            model.addAttribute("errorMessage", "Změna hesla selhala. Zkuste to prosím znovu.");
            // Token je v DTO, není třeba ho znovu přidávat
            return "resetovat-heslo";
        }
    }
}
