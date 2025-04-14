package org.example.eshop.admin.controller; // Přesun do správného balíčku

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.config.SecurityTestConfig; // Import sdílené testovací konfigurace
import org.example.eshop.model.TaxRate;
import org.example.eshop.service.CurrencyService; // Mock pro ControllerAdvice
import org.example.eshop.service.TaxRateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

// Importy pro Hamcrest a Mockito
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminTaxRateController.class) // Testujeme tento controller
@WithMockUser(roles = "ADMIN") // Simulujeme přihlášeného admina
@Import(SecurityTestConfig.class) // Použijeme naši testovací security konfiguraci
class AdminTaxRateControllerTest {

    @Autowired
    private MockMvc mockMvc; // Pro simulaci HTTP requestů

    @MockBean // Mockujeme service vrstvu
    private TaxRateService taxRateService;

    @MockBean // Mockujeme i CurrencyService kvůli GlobalModelAttributeAdvice
    private CurrencyService currencyService;

    private TaxRate rate21;
    private TaxRate rate12;

    @BeforeEach
    void setUp() {
        rate21 = new TaxRate(1L, "Základní 21%", new BigDecimal("0.21"), false, null);
        rate12 = new TaxRate(2L, "Snížená 12%", new BigDecimal("0.12"), false, null);

        // Mock pro GlobalModelAttributeAdvice (pokud je potřeba)
        lenient().when(currencyService.getSelectedCurrency()).thenReturn("CZK");
    }

    @Test
    @DisplayName("GET /admin/tax-rates - Zobrazí seznam sazeb")
    void listTaxRates_Success() throws Exception {
        List<TaxRate> rateList = Arrays.asList(rate12, rate21); // Předpokládejme řazení podle názvu
        when(taxRateService.getAllTaxRates()).thenReturn(rateList);

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/tax-rates"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/tax-rates-list"))
                .andExpect(model().attributeExists("taxRates"))
                .andExpect(model().attribute("taxRates", hasSize(2)))
                .andExpect(model().attribute("taxRates", contains(
                        hasProperty("name", is("Snížená 12%")),
                        hasProperty("name", is("Základní 21%"))
                ))); // Zkontrolujeme pořadí

        verify(taxRateService).getAllTaxRates();
    }

    @Test
    @DisplayName("GET /admin/tax-rates - Zobrazí info, pokud nejsou sazby")
    void listTaxRates_Empty() throws Exception {
        when(taxRateService.getAllTaxRates()).thenReturn(Collections.emptyList());

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/tax-rates"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/tax-rates-list"))
                .andExpect(model().attribute("taxRates", empty()));

        verify(taxRateService).getAllTaxRates();
    }

    @Test
    @DisplayName("GET /admin/tax-rates/new - Zobrazí formulář")
    void showCreateTaxRateForm_Success() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/tax-rates/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/tax-rate-form"))
                .andExpect(model().attributeExists("taxRate", "pageTitle"))
                .andExpect(model().attribute("taxRate", instanceOf(TaxRate.class)))
                .andExpect(model().attribute("taxRate", hasProperty("id", nullValue())));
    }

    @Test
    @DisplayName("POST /admin/tax-rates - Úspěšné vytvoření")
    void createTaxRate_Success() throws Exception {
        TaxRate createdRate = new TaxRate(3L, "Nová Sazba 15%", new BigDecimal("0.15"), false, null);
        when(taxRateService.createTaxRate(any(TaxRate.class))).thenReturn(createdRate);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/tax-rates")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Nová Sazba 15%")
                        .param("rate", "0.15")
                        .param("reverseCharge", "false")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tax-rates"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(taxRateService).createTaxRate(any(TaxRate.class));
    }

    @Test
    @DisplayName("POST /admin/tax-rates - Chyba validace (chybí název)")
    void createTaxRate_ValidationError_MissingName() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/tax-rates")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "") // Chybějící název
                        .param("rate", "0.10")
                )
                .andExpect(status().isOk()) // Zůstane na formuláři
                .andExpect(view().name("admin/tax-rate-form"))
                .andExpect(model().attributeExists("taxRate", "pageTitle"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("taxRate", "name")); // Chyba u pole 'name'

        verify(taxRateService, never()).createTaxRate(any()); // Service metoda se nevolala
    }

    @Test
    @DisplayName("POST /admin/tax-rates - Chyba validace (neplatná sazba)")
    void createTaxRate_ValidationError_InvalidRate() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/tax-rates")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Platný Název")
                        .param("rate", "1.5") // Neplatná sazba (> 1)
                )
                .andExpect(status().isOk())
                .andExpect(view().name("admin/tax-rate-form"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("taxRate", "rate")); // Chyba u pole 'rate'

        verify(taxRateService, never()).createTaxRate(any());
    }

    @Test
    @DisplayName("POST /admin/tax-rates - Chyba (duplicitní název)")
    void createTaxRate_DuplicateNameError() throws Exception {
        String duplicateName = "Základní 21%";
        when(taxRateService.createTaxRate(any(TaxRate.class)))
                .thenThrow(new IllegalArgumentException("TaxRate with name '" + duplicateName + "' already exists."));

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/tax-rates")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", duplicateName)
                        .param("rate", "0.21")
                )
                .andExpect(status().isOk())
                .andExpect(view().name("admin/tax-rate-form"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrorCode("taxRate", "name", "error.taxRate.duplicate"));

        verify(taxRateService).createTaxRate(any(TaxRate.class));
    }


    @Test
    @DisplayName("GET /admin/tax-rates/{id}/edit - Zobrazí formulář pro úpravu")
    void showEditTaxRateForm_Success() throws Exception {
        when(taxRateService.getTaxRateById(1L)).thenReturn(Optional.of(rate21));

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/tax-rates/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/tax-rate-form"))
                .andExpect(model().attributeExists("taxRate", "pageTitle"))
                .andExpect(model().attribute("taxRate", hasProperty("id", is(1L))))
                .andExpect(model().attribute("taxRate", hasProperty("name", is("Základní 21%"))));

        verify(taxRateService).getTaxRateById(1L);
    }

    @Test
    @DisplayName("GET /admin/tax-rates/{id}/edit - Sazba nenalezena")
    void showEditTaxRateForm_NotFound() throws Exception {
        when(taxRateService.getTaxRateById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/tax-rates/99/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tax-rates"))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(taxRateService).getTaxRateById(99L);
    }

    @Test
    @DisplayName("POST /admin/tax-rates/{id} - Úspěšná aktualizace")
    void updateTaxRate_Success() throws Exception {
        long id = 1L;
        // Service metoda updateTaxRate vrací Optional<Object>, mockujeme prázdný pro úspěch
        when(taxRateService.updateTaxRate(eq(id), any(TaxRate.class))).thenReturn(Optional.of(rate21));

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/tax-rates/{id}", id)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Upravená 21%")
                        .param("rate", "0.215") // Měníme sazbu
                        .param("reverseCharge", "true")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tax-rates"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(taxRateService).updateTaxRate(eq(id), any(TaxRate.class));
    }

    @Test
    @DisplayName("POST /admin/tax-rates/{id} - Chyba validace vrátí formulář")
    void updateTaxRate_ValidationError() throws Exception {
        long id = 1L;
        // Předpokládáme, že getTaxRateById se volá v error path controlleru
        when(taxRateService.getTaxRateById(id)).thenReturn(Optional.of(rate21));

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/tax-rates/{id}", id)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Valid Name")
                        .param("rate", "") // Prázdná sazba
                )
                .andExpect(status().isOk())
                .andExpect(view().name("admin/tax-rate-form"))
                .andExpect(model().attributeExists("taxRate", "pageTitle"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("taxRate", "rate"));

        verify(taxRateService, never()).updateTaxRate(anyLong(), any());
    }

    @Test
    @DisplayName("POST /admin/tax-rates/{id}/delete - Úspěšné smazání")
    void deleteTaxRate_Success() throws Exception {
        long id = 1L;
        doNothing().when(taxRateService).deleteTaxRate(id);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/tax-rates/{id}/delete", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tax-rates"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(taxRateService).deleteTaxRate(id);
    }

    @Test
    @DisplayName("POST /admin/tax-rates/{id}/delete - Chyba (sazba použita)")
    void deleteTaxRate_InUseError() throws Exception {
        long id = 1L;
        String errorMsg = "Cannot delete tax rate '...' as it is currently assigned to products.";
        doThrow(new IllegalStateException(errorMsg)).when(taxRateService).deleteTaxRate(id);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/tax-rates/{id}/delete", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tax-rates"))
                .andExpect(flash().attribute("errorMessage", is(errorMsg)));

        verify(taxRateService).deleteTaxRate(id);
    }

    @Test
    @DisplayName("POST /admin/tax-rates/{id}/delete - Chyba (sazba nenalezena)")
    void deleteTaxRate_NotFoundError() throws Exception {
        long id = 99L;
        String errorMsg = "TaxRate with id 99 not found for deletion.";
        doThrow(new EntityNotFoundException(errorMsg)).when(taxRateService).deleteTaxRate(id);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/tax-rates/{id}/delete", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tax-rates"))
                .andExpect(flash().attribute("errorMessage", is(errorMsg)));

        verify(taxRateService).deleteTaxRate(id);
    }
}