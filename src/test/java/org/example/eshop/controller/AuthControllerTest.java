// Soubor: src/test/java/org/example/eshop/controller/AuthControllerTest.java

package org.example.eshop.controller;

// Importy ...
import org.example.eshop.config.SecurityTestConfig; // Import sdílené konfigurace
import org.example.eshop.dto.RegistrationDto;
import org.example.eshop.model.Customer;
import org.example.eshop.service.CurrencyService;
import org.example.eshop.service.CustomerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import; // Import pro @Import
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
// import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf; // Odstraněno
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityTestConfig.class) // Aplikace sdílené konfigurace
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private CustomerService customerService;
    @MockBean private CurrencyService currencyService;
    @MockBean private UserDetailsService userDetailsService; // Ponecháno pro Security

    @Test
    @DisplayName("GET /prihlaseni - Zobrazí přihlašovací stránku")
    void showLoginPage_ShouldReturnLoginView() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/prihlaseni"))
                .andExpect(status().isOk())
                .andExpect(view().name("prihlaseni"));
    }

    @Test
    @DisplayName("GET /registrace - Zobrazí registrační stránku s prázdným DTO")
    void showRegistrationPage_ShouldReturnRegistrationViewWithDto() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/registrace"))
                .andExpect(status().isOk())
                .andExpect(view().name("registrace"))
                .andExpect(model().attributeExists("registrationDto"))
                // TOTO JE KLÍČOVÁ OPRAVA: Ověřujeme typ, ne null
                .andExpect(model().attribute("registrationDto", isA(RegistrationDto.class)));
    }

    @Test
    @DisplayName("POST /registrace - Úspěšná registrace přesměruje na přihlášení")
    void processRegistration_Success_ShouldRedirectToLogin() throws Exception {
        // Mockujeme úspěšnou registraci
        when(customerService.registerCustomer(any(RegistrationDto.class))).thenReturn(new Customer());

        mockMvc.perform(MockMvcRequestBuilders.post("/registrace")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("firstName", "Test")
                                .param("lastName", "User")
                                .param("email", "test.reg@example.com")
                                .param("password", "password123")
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().is3xxRedirection()) // Očekáváme přesměrování
                .andExpect(redirectedUrl("/prihlaseni"))
                .andExpect(flash().attributeExists("registraceSuccess")); // Očekáváme zprávu o úspěchu

        // Ověříme, že metoda v service byla volána
        verify(customerService).registerCustomer(any(RegistrationDto.class));
    }

    @Test
    @DisplayName("POST /registrace - Chyba validace (chybí email) vrátí formulář")
    void processRegistration_ValidationError_ShouldReturnForm() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/registrace")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("firstName", "Test")
                                .param("lastName", "User")
                                .param("email", "") // Prázdný email - vyvolá chybu validace @NotBlank/@Email
                                .param("password", "password123")
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().isOk()) // Zůstane na stránce
                .andExpect(view().name("registrace"))
                .andExpect(model().attributeExists("registrationDto")) // Formulářový objekt zůstává
                .andExpect(model().hasErrors()) // Očekáváme chyby v modelu
                .andExpect(model().attributeHasFieldErrors("registrationDto", "email")); // Specifická chyba u emailu

        // Ověříme, že se služba pro registraci nezavolala
        verify(customerService, never()).registerCustomer(any(RegistrationDto.class));
    }

    @Test
    @DisplayName("POST /registrace - Chyba (email již existuje) vrátí formulář s chybou")
    void processRegistration_EmailExistsError_ShouldReturnFormWithError() throws Exception {
        String existingEmail = "existing@example.com";
        String errorMessage = "Zákazník s emailem " + existingEmail + " již existuje.";
        // Mockujeme, že služba vyhodí výjimku
        doThrow(new IllegalArgumentException(errorMessage))
                .when(customerService).registerCustomer(any(RegistrationDto.class));

        mockMvc.perform(MockMvcRequestBuilders.post("/registrace")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("firstName", "Test")
                                .param("lastName", "User")
                                .param("email", existingEmail)
                                .param("password", "password123")
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().isOk()) // Zůstane na stránce
                .andExpect(view().name("registrace"))
                .andExpect(model().attributeExists("registrationDto"))
                .andExpect(model().attributeExists("registrationError")) // Očekáváme atribut s chybou
                .andExpect(model().attribute("registrationError", is(errorMessage))); // Ověříme text chyby

        // Ověříme, že služba byla volána (a selhala)
        verify(customerService).registerCustomer(any(RegistrationDto.class));
    }
}