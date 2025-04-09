// src/test/java/org/example/eshop/controller/CurrencyControllerTest.java

package org.example.eshop.controller;

import org.example.eshop.config.SecurityTestConfig; // Import sdílené konfigurace
import org.example.eshop.service.CurrencyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import; // Import pro @Import
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.mockito.Mockito.*;
// import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf; // Odstranit import csrf
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CurrencyController.class)
@Import(SecurityTestConfig.class) // Aplikace sdílené konfigurace
class CurrencyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CurrencyService currencyService; // Mockujeme závislost

    @Test
    @DisplayName("POST /nastavit-menu - Nastaví měnu a přesměruje zpět")
    void setCurrency_ShouldSetCurrencyAndRedirectBack() throws Exception {
        String selectedCurrency = "EUR";
        String referrerUrl = "http://localhost:8080/produkty";

        // Mockujeme volání metody v service (nemá návratovou hodnotu)
        doNothing().when(currencyService).setSelectedCurrency(selectedCurrency);

        mockMvc.perform(MockMvcRequestBuilders.post("/nastavit-menu")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("currency", selectedCurrency)
                                .header("Referer", referrerUrl) // Simulujeme hlavičku Referer
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().is3xxRedirection()) // Očekáváme přesměrování
                .andExpect(redirectedUrl(referrerUrl)); // Očekáváme přesměrování na referer URL

        // Ověříme, že metoda setSelectedCurrency byla volána s hodnotou "EUR"
        verify(currencyService, times(1)).setSelectedCurrency(selectedCurrency);
    }

    @Test
    @DisplayName("POST /nastavit-menu - Přesměruje na root, pokud chybí Referer")
    void setCurrency_NoReferer_ShouldRedirectToRoot() throws Exception {
        String selectedCurrency = "CZK";
        doNothing().when(currencyService).setSelectedCurrency(selectedCurrency);

        mockMvc.perform(MockMvcRequestBuilders.post("/nastavit-menu")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("currency", selectedCurrency)
                        // Bez hlavičky Referer
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/")); // Očekáváme přesměrování na "/"

        verify(currencyService, times(1)).setSelectedCurrency(selectedCurrency);
    }

    @Test
    @DisplayName("POST /nastavit-menu - Ignoruje neplatnou měnu")
    void setCurrency_InvalidCurrency_ShouldNotChange() throws Exception {
        String invalidCurrency = "USD";
        String referrerUrl = "http://localhost:8080/";

        // Mockujeme, že se metoda zavolá, ale NECHCEme, aby se hodnota změnila (logika je v service)
        // V tomto případě jen ověříme, že se service metoda zavolala
        doNothing().when(currencyService).setSelectedCurrency(invalidCurrency);

        mockMvc.perform(MockMvcRequestBuilders.post("/nastavit-menu")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("currency", invalidCurrency)
                                .header("Referer", referrerUrl)
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(referrerUrl));

        // Ověříme, že se metoda service zavolala (i když vnitřní logika service by měla změnu ignorovat)
        verify(currencyService, times(1)).setSelectedCurrency(invalidCurrency);
    }
}