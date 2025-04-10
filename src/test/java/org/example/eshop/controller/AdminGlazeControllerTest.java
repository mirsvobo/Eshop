package org.example.eshop.controller;

import org.example.eshop.admin.controller.AdminGlazeController;
import org.example.eshop.config.SecurityTestConfig;
import org.example.eshop.model.Glaze;
import org.example.eshop.service.CurrencyService;
import org.example.eshop.admin.service.GlazeService;
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

@WebMvcTest(AdminGlazeController.class)
@WithMockUser(roles = "ADMIN")
@Import(SecurityTestConfig.class)
class AdminGlazeControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private GlazeService glazeService;
    @MockBean private CurrencyService currencyService;

    private Glaze glaze1;
    private Glaze glaze2;

    @BeforeEach
    void setUp() {
        glaze1 = new Glaze(); glaze1.setId(1L); glaze1.setName("Ořech"); glaze1.setActive(true); glaze1.setPriceSurchargeCZK(null); glaze1.setPriceSurchargeEUR(null);
        glaze2 = new Glaze(); glaze2.setId(2L); glaze2.setName("Teak Premium"); glaze2.setActive(true); glaze2.setPriceSurchargeCZK(new BigDecimal("200.00")); glaze2.setPriceSurchargeEUR(new BigDecimal("8.00"));
        lenient().when(currencyService.getSelectedCurrency()).thenReturn("CZK");
    }

    @Test
    @DisplayName("GET /admin/glazes - Zobrazí seznam lazur")
    void listGlazes_Success() throws Exception {
        List<Glaze> glazeList = Arrays.asList(glaze1, glaze2);
        when(glazeService.getAllGlazesSortedByName()).thenReturn(glazeList);

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/glazes"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/glazes-list"))
                .andExpect(model().attributeExists("glazes"))
                .andExpect(model().attribute("glazes", hasSize(2)));

        verify(glazeService).getAllGlazesSortedByName();
    }

    @Test
    @DisplayName("GET /admin/glazes/new - Zobrazí formulář pro novou lazuru")
    void showCreateForm_Success() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/glazes/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/glaze-form"))
                .andExpect(model().attributeExists("glaze", "pageTitle"))
                .andExpect(model().attribute("glaze", hasProperty("id", nullValue())))
                .andExpect(model().attribute("pageTitle", containsString("Vytvořit")));
    }

    @Test
    @DisplayName("POST /admin/glazes - Úspěšné vytvoření")
    void createGlaze_Success() throws Exception {
        Glaze createdGlaze = new Glaze(); createdGlaze.setId(3L); createdGlaze.setName("Nová Lazura");
        when(glazeService.createGlaze(any(Glaze.class))).thenReturn(createdGlaze);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/glazes")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Nová Lazura")
                        .param("priceSurchargeCZK", "150.00")
                        .param("imageUrl", "http://example.com/img.png")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/glazes"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(glazeService).createGlaze(any(Glaze.class));
    }

    @Test
    @DisplayName("POST /admin/glazes - Chyba validace vrátí formulář")
    void createGlaze_ValidationError() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/glazes")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "") // Prázdný název
                )
                .andExpect(status().isOk())
                .andExpect(view().name("admin/glaze-form"))
                .andExpect(model().attributeExists("glaze", "pageTitle"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("glaze", "name"));

        verify(glazeService, never()).createGlaze(any());
    }

    @Test
    @DisplayName("GET /admin/glazes/{id}/edit - Zobrazí formulář pro úpravu")
    void showEditForm_Success() throws Exception {
        when(glazeService.findById(1L)).thenReturn(Optional.of(glaze1));

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/glazes/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/glaze-form"))
                .andExpect(model().attributeExists("glaze", "pageTitle"))
                .andExpect(model().attribute("glaze", hasProperty("id", is(1L))))
                .andExpect(model().attribute("pageTitle", containsString("Upravit lazuru: Ořech")));

        verify(glazeService).findById(1L);
    }

    @Test
    @DisplayName("POST /admin/glazes/{id} - Úspěšná aktualizace")
    void updateGlaze_Success() throws Exception {
        long id = 1L;
        Glaze updatedGlaze = new Glaze(); updatedGlaze.setId(id); updatedGlaze.setName("Ořech Upravený");
        when(glazeService.updateGlaze(eq(id), any(Glaze.class))).thenReturn(updatedGlaze);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/glazes/{id}", id)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Ořech Upravený")
                        .param("priceSurchargeCZK", "50")
                        .param("active", "true")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/glazes"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(glazeService).updateGlaze(eq(id), any(Glaze.class));
    }

    @Test
    @DisplayName("POST /admin/glazes/{id} - Chyba validace vrátí formulář")
    void updateGlaze_ValidationError() throws Exception {
        long id = 1L;
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/glazes/{id}", id)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "") // Nevalidní
                        .param("active", "true")
                )
                .andExpect(status().isOk())
                .andExpect(view().name("admin/glaze-form"))
                .andExpect(model().attributeExists("glaze", "pageTitle"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("glaze", "name"));

        verify(glazeService, never()).updateGlaze(anyLong(), any());
    }

    @Test
    @DisplayName("POST /admin/glazes/{id}/delete - Úspěšná deaktivace")
    void deleteGlaze_Success() throws Exception {
        long id = 1L;
        doNothing().when(glazeService).deleteGlaze(id);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/glazes/{id}/delete", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/glazes"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(glazeService).deleteGlaze(id);
    }

    @Test
    @DisplayName("POST /admin/glazes/{id}/delete - Chyba (použitá lazura)")
    void deleteGlaze_InUseError() throws Exception {
        long id = 1L;
        String errorMsg = "Lazuru 'Ořech' nelze deaktivovat, je přiřazena k produktům.";
        doThrow(new IllegalStateException(errorMsg)).when(glazeService).deleteGlaze(id);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/glazes/{id}/delete", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/glazes"))
                .andExpect(flash().attribute("errorMessage", is(errorMsg)));

        verify(glazeService).deleteGlaze(id);
    }
}