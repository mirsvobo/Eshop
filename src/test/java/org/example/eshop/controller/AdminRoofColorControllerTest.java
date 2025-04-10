package org.example.eshop.controller;

import org.example.eshop.admin.controller.AdminRoofColorController;
import org.example.eshop.config.SecurityTestConfig;
import org.example.eshop.model.RoofColor;
import org.example.eshop.service.CurrencyService;
import org.example.eshop.admin.service.RoofColorService;
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

@WebMvcTest(AdminRoofColorController.class)
@WithMockUser(roles = "ADMIN")
@Import(SecurityTestConfig.class)
class AdminRoofColorControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private RoofColorService roofColorService;
    @MockBean private CurrencyService currencyService;

    private RoofColor color1;
    private RoofColor color2;

    @BeforeEach
    void setUp() {
        color1 = new RoofColor(); color1.setId(1L); color1.setName("Antracit"); color1.setActive(true);
        color2 = new RoofColor(); color2.setId(2L); color2.setName("Červená"); color2.setActive(true);
        lenient().when(currencyService.getSelectedCurrency()).thenReturn("CZK");
    }

    @Test
    @DisplayName("GET /admin/roof-colors - Zobrazí seznam barev")
    void listRoofColors_Success() throws Exception {
        List<RoofColor> colorList = Arrays.asList(color1, color2);
        when(roofColorService.getAllRoofColorsSortedByName()).thenReturn(colorList);

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/roof-colors"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/roof-colors-list"))
                .andExpect(model().attributeExists("roofColors")) // Ověření klíče v modelu
                .andExpect(model().attribute("roofColors", hasSize(2)));

        verify(roofColorService).getAllRoofColorsSortedByName();
    }

    @Test
    @DisplayName("GET /admin/roof-colors/new - Zobrazí formulář pro novou barvu")
    void showCreateForm_Success() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/roof-colors/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/roof-color-form"))
                .andExpect(model().attributeExists("roofColor", "pageTitle"))
                .andExpect(model().attribute("roofColor", hasProperty("id", nullValue())));
    }

    @Test
    @DisplayName("POST /admin/roof-colors - Úspěšné vytvoření")
    void createRoofColor_Success() throws Exception {
        RoofColor createdColor = new RoofColor(); createdColor.setId(3L); createdColor.setName("Nová Barva");
        when(roofColorService.createRoofColor(any(RoofColor.class))).thenReturn(createdColor);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/roof-colors")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Nová Barva")
                        .param("priceSurchargeCZK", "100")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/roof-colors"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(roofColorService).createRoofColor(any(RoofColor.class));
    }

    @Test
    @DisplayName("POST /admin/roof-colors - Chyba validace vrátí formulář")
    void createRoofColor_ValidationError() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/roof-colors")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "") // Prázdný název
                )
                .andExpect(status().isOk())
                .andExpect(view().name("admin/roof-color-form"))
                .andExpect(model().attributeExists("roofColor", "pageTitle"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("roofColor", "name"));

        verify(roofColorService, never()).createRoofColor(any());
    }

    @Test
    @DisplayName("GET /admin/roof-colors/{id}/edit - Zobrazí formulář pro úpravu")
    void showEditForm_Success() throws Exception {
        when(roofColorService.findById(1L)).thenReturn(Optional.of(color1));

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/roof-colors/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/roof-color-form"))
                .andExpect(model().attributeExists("roofColor", "pageTitle"))
                .andExpect(model().attribute("roofColor", hasProperty("id", is(1L))));

        verify(roofColorService).findById(1L);
    }

    @Test
    @DisplayName("POST /admin/roof-colors/{id} - Úspěšná aktualizace")
    void updateRoofColor_Success() throws Exception {
        long id = 1L;
        RoofColor updatedColor = new RoofColor(); updatedColor.setId(id); updatedColor.setName("Antracit Upravený");
        when(roofColorService.updateRoofColor(eq(id), any(RoofColor.class))).thenReturn(updatedColor);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/roof-colors/{id}", id)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Antracit Upravený")
                        .param("active", "true")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/roof-colors"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(roofColorService).updateRoofColor(eq(id), any(RoofColor.class));
    }

    @Test
    @DisplayName("POST /admin/roof-colors/{id} - Chyba validace vrátí formulář")
    void updateRoofColor_ValidationError() throws Exception {
        long id = 1L;
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/roof-colors/{id}", id)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "") // Nevalidní
                        .param("active", "true")
                )
                .andExpect(status().isOk())
                .andExpect(view().name("admin/roof-color-form"))
                .andExpect(model().attributeExists("roofColor", "pageTitle"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("roofColor", "name"));

        verify(roofColorService, never()).updateRoofColor(anyLong(), any());
    }

    @Test
    @DisplayName("POST /admin/roof-colors/{id}/delete - Úspěšná deaktivace")
    void deleteRoofColor_Success() throws Exception {
        long id = 1L;
        doNothing().when(roofColorService).deleteRoofColor(id);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/roof-colors/{id}/delete", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/roof-colors"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(roofColorService).deleteRoofColor(id);
    }

    @Test
    @DisplayName("POST /admin/roof-colors/{id}/delete - Chyba (použitá barva)")
    void deleteRoofColor_InUseError() throws Exception {
        long id = 1L;
        String errorMsg = "Barvu střechy 'Antracit' nelze deaktivovat, je přiřazena k produktům.";
        doThrow(new IllegalStateException(errorMsg)).when(roofColorService).deleteRoofColor(id);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/roof-colors/{id}/delete", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/roof-colors"))
                .andExpect(flash().attribute("errorMessage", is(errorMsg)));

        verify(roofColorService).deleteRoofColor(id);
    }
}