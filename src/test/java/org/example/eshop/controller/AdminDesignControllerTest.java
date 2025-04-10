package org.example.eshop.controller;

import org.example.eshop.admin.controller.AdminDesignController;
import org.example.eshop.config.SecurityTestConfig;
import org.example.eshop.model.Design;
import org.example.eshop.service.CurrencyService;
import org.example.eshop.admin.service.DesignService;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminDesignController.class)
@WithMockUser(roles = "ADMIN")
@Import(SecurityTestConfig.class)
class AdminDesignControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private DesignService designService;
    @MockBean private CurrencyService currencyService; // Pro layout/advice

    private Design design1;
    private Design design2;

    @BeforeEach
    void setUp() {
        design1 = new Design(); design1.setId(1L); design1.setName("Klasik"); design1.setActive(true);
        design2 = new Design(); design2.setId(2L); design2.setName("Modern"); design2.setActive(true);
        lenient().when(currencyService.getSelectedCurrency()).thenReturn("CZK"); // Mock pro layout
    }

    @Test
    @DisplayName("GET /admin/designs - Zobrazí seznam designů")
    void listDesigns_Success() throws Exception {
        List<Design> designList = Arrays.asList(design1, design2);
        when(designService.getAllDesignsSortedByName()).thenReturn(designList);

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/designs"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/designs-list"))
                .andExpect(model().attributeExists("designs"))
                .andExpect(model().attribute("designs", hasSize(2)));

        verify(designService).getAllDesignsSortedByName();
    }

    @Test
    @DisplayName("GET /admin/designs/new - Zobrazí formulář pro nový design")
    void showCreateForm_Success() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/designs/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/design-form"))
                .andExpect(model().attributeExists("design"))
                .andExpect(model().attribute("design", hasProperty("id", nullValue())))
                .andExpect(model().attribute("pageTitle", containsString("Vytvořit")));
    }

    @Test
    @DisplayName("POST /admin/designs - Úspěšné vytvoření")
    void createDesign_Success() throws Exception {
        Design createdDesign = new Design(); createdDesign.setId(3L); createdDesign.setName("Nový Design");
        when(designService.createDesign(any(Design.class))).thenReturn(createdDesign);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/designs")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Nový Design")
                        .param("description", "Popisek")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/designs"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(designService).createDesign(any(Design.class));
    }

    @Test
    @DisplayName("POST /admin/designs - Chyba validace vrátí formulář")
    void createDesign_ValidationError() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/designs")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "") // Prázdný název
                )
                .andExpect(status().isOk())
                .andExpect(view().name("admin/design-form"))
                .andExpect(model().attributeExists("design", "pageTitle"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("design", "name"));

        verify(designService, never()).createDesign(any());
    }

    @Test
    @DisplayName("GET /admin/designs/{id}/edit - Zobrazí formulář pro úpravu")
    void showEditForm_Success() throws Exception {
        when(designService.findById(1L)).thenReturn(Optional.of(design1));

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/designs/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/design-form"))
                .andExpect(model().attributeExists("design", "pageTitle"))
                .andExpect(model().attribute("design", hasProperty("id", is(1L))))
                .andExpect(model().attribute("pageTitle", containsString("Upravit design: Klasik")));

        verify(designService).findById(1L);
    }

    @Test
    @DisplayName("POST /admin/designs/{id} - Úspěšná aktualizace")
    void updateDesign_Success() throws Exception {
        long id = 1L;
        Design updatedDesign = new Design(); updatedDesign.setId(id); updatedDesign.setName("Klasik Upravený");
        when(designService.updateDesign(eq(id), any(Design.class))).thenReturn(updatedDesign);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/designs/{id}", id)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Klasik Upravený")
                        .param("description", "Nový popisek")
                        .param("active", "true")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/designs"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(designService).updateDesign(eq(id), any(Design.class));
    }

    @Test
    @DisplayName("POST /admin/designs/{id} - Chyba validace vrátí formulář")
    void updateDesign_ValidationError() throws Exception {
        long id = 1L;
        // Mock pro zobrazení formuláře s chybou
        // Není potřeba mockovat findById, protože se controller pokusí uložit
        // a až poté by mohl narazit na chybu (pokud by service nevalidoval)
        // Ale náš controller kontroluje BindingResult jako první

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/designs/{id}", id)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "") // Nevalidní prázdný název
                        .param("active", "true")
                )
                .andExpect(status().isOk())
                .andExpect(view().name("admin/design-form"))
                .andExpect(model().attributeExists("design", "pageTitle"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("design", "name"));

        verify(designService, never()).updateDesign(anyLong(), any());
    }

    @Test
    @DisplayName("POST /admin/designs/{id}/delete - Úspěšná deaktivace")
    void deleteDesign_Success() throws Exception {
        long id = 1L;
        doNothing().when(designService).deleteDesign(id);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/designs/{id}/delete", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/designs"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(designService).deleteDesign(id);
    }

    @Test
    @DisplayName("POST /admin/designs/{id}/delete - Chyba (použitý design)")
    void deleteDesign_InUseError() throws Exception {
        long id = 1L;
        String errorMsg = "Design 'Klasik' nelze deaktivovat, je přiřazen k produktům.";
        doThrow(new IllegalStateException(errorMsg)).when(designService).deleteDesign(id);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/designs/{id}/delete", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/designs"))
                .andExpect(flash().attribute("errorMessage", is(errorMsg)));

        verify(designService).deleteDesign(id);
    }
}