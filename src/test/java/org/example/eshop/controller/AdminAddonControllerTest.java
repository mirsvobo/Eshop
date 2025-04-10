package org.example.eshop.controller;

import org.example.eshop.admin.controller.AdminAddonsController;
import org.example.eshop.admin.service.AddonsService;
import org.example.eshop.config.SecurityTestConfig;
import org.example.eshop.model.Addon;
import org.example.eshop.service.CurrencyService;
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
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminAddonsController.class)
@WithMockUser(roles = "ADMIN")
@Import(SecurityTestConfig.class)
class AdminAddonControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AddonsService addonsService;
    @MockBean private CurrencyService currencyService;

    private Addon addon1;
    private Addon addon2;

    @BeforeEach
    void setUp() {
        addon1 = new Addon(); addon1.setId(1L); addon1.setName("Polička"); addon1.setSku("POL-01"); addon1.setActive(true); addon1.setPriceCZK(new BigDecimal("350.00")); addon1.setPriceEUR(new BigDecimal("15.00"));
        addon2 = new Addon(); addon2.setId(2L); addon2.setName("Držák"); addon2.setSku("DRZ-01"); addon2.setActive(true); addon2.setPriceCZK(new BigDecimal("500.00")); addon2.setPriceEUR(new BigDecimal("20.00"));
        lenient().when(currencyService.getSelectedCurrency()).thenReturn("CZK");
    }

    @Test
    @DisplayName("GET /admin/addons - Zobrazí seznam doplňků")
    void listAddons_Success() throws Exception {
        List<Addon> addonList = Arrays.asList(addon1, addon2);
        when(addonsService.getAllAddons()).thenReturn(addonList); // Předpokládáme getAllAddons pro seznam

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/addons"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/addons-list"))
                .andExpect(model().attributeExists("addons"))
                .andExpect(model().attribute("addons", hasSize(2)));

        verify(addonsService).getAllAddons();
    }

    @Test
    @DisplayName("GET /admin/addons/new - Zobrazí formulář pro nový doplněk")
    void showCreateForm_Success() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/addons/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/addon-form"))
                .andExpect(model().attributeExists("addon", "pageTitle"))
                .andExpect(model().attribute("addon", hasProperty("id", nullValue())));
    }

    @Test
    @DisplayName("POST /admin/addons - Úspěšné vytvoření")
    void createAddon_Success() throws Exception {
        Addon createdAddon = new Addon(); createdAddon.setId(3L); createdAddon.setName("Nový Doplněk");
        when(addonsService.createAddon(any(Addon.class))).thenReturn(createdAddon);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/addons")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Nový Doplněk")
                        .param("sku", "NEW-ADD")
                        .param("priceCZK", "100")
                        .param("priceEUR", "4")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/addons"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(addonsService).createAddon(any(Addon.class));
    }

    @Test
    @DisplayName("POST /admin/addons - Chyba validace (chybí cena) vrátí formulář")
    void createAddon_ValidationError_MissingPrice() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/addons")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("name", "Chybí Cena")
                        // Chybí priceCZK a priceEUR
                )
                .andExpect(status().isOk())
                .andExpect(view().name("admin/addon-form"))
                .andExpect(model().attributeExists("addon", "pageTitle"))
                .andExpect(model().hasErrors())
                // Controller přidává chyby pro obě ceny, pokud nejsou kladné
                .andExpect(model().attributeHasFieldErrors("addon", "priceCZK", "priceEUR"));

        verify(addonsService, never()).createAddon(any());
    }

    @Test
    @DisplayName("GET /admin/addons/{id}/edit - Zobrazí formulář pro úpravu")
    void showEditForm_Success() throws Exception {
        when(addonsService.getAddonById(1L)).thenReturn(Optional.of(addon1));

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/addons/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/addon-form"))
                .andExpect(model().attributeExists("addon", "pageTitle"))
                .andExpect(model().attribute("addon", hasProperty("id", is(1L))));

        verify(addonsService).getAddonById(1L);
    }

    @Test
    @DisplayName("POST /admin/addons/{id} - Úspěšná aktualizace")
    void updateAddon_Success() throws Exception {
        long id = 1L;
        Addon updatedAddon = new Addon(); updatedAddon.setId(id); updatedAddon.setName("Polička Upravená");
        when(addonsService.updateAddon(eq(id), any(Addon.class))).thenReturn(updatedAddon);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/addons/{id}", id)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Polička Upravená")
                        .param("priceCZK", "360")
                        .param("priceEUR", "16")
                        .param("active", "false") // Deaktivujeme
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/addons"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(addonsService).updateAddon(eq(id), any(Addon.class));
    }

    @Test
    @DisplayName("POST /admin/addons/{id} - Chyba validace vrátí formulář")
    void updateAddon_ValidationError() throws Exception {
        long id = 1L;
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/addons/{id}", id)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Valid Name")
                        .param("priceCZK", "-10") // Nevalidní cena
                        .param("priceEUR", "5")
                        .param("active", "true")
                )
                .andExpect(status().isOk())
                .andExpect(view().name("admin/addon-form"))
                .andExpect(model().attributeExists("addon", "pageTitle"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("addon", "priceCZK"));

        verify(addonsService, never()).updateAddon(anyLong(), any());
    }

    @Test
    @DisplayName("POST /admin/addons/{id}/delete - Úspěšná deaktivace")
    void deleteAddon_Success() throws Exception {
        long id = 1L;
        doNothing().when(addonsService).deleteAddon(id);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/addons/{id}/delete", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/addons"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(addonsService).deleteAddon(id);
    }
}