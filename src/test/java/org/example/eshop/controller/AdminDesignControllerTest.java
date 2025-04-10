package org.example.eshop.controller;

import org.example.eshop.admin.controller.AdminDesignController;
import org.example.eshop.config.SecurityTestConfig;
import org.example.eshop.model.Design;
import org.example.eshop.service.CurrencyService;
import org.example.eshop.admin.service.DesignService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminDesignController.class)
@WithMockUser(roles = "ADMIN")
@Import(SecurityTestConfig.class)
class AdminDesignControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private DesignService designService;
    @MockBean private CurrencyService currencyService;

    private Design design1;
    private Design design2_withPrice;

    @BeforeEach
    void setUp() {
        design1 = new Design(); design1.setId(1L); design1.setName("Klasik"); design1.setActive(true);
        design2_withPrice = new Design(); design2_withPrice.setId(2L); design2_withPrice.setName("Modern Premium"); design2_withPrice.setActive(true);
        design2_withPrice.setImageUrl("/img/modern.jpg");
        design2_withPrice.setPriceSurchargeCZK(new BigDecimal("500.00"));
        design2_withPrice.setPriceSurchargeEUR(new BigDecimal("20.00"));
        lenient().when(currencyService.getSelectedCurrency()).thenReturn("CZK");
    }

    @Test
    @DisplayName("GET /admin/designs - Zobrazí seznam designů")
    void listDesigns_Success() throws Exception {
        List<Design> designList = Arrays.asList(design1, design2_withPrice);
        when(designService.getAllDesignsSortedByName()).thenReturn(designList);

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/designs"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/designs-list"))
                .andExpect(model().attributeExists("designs"))
                .andExpect(model().attribute("designs", hasSize(2)))
                // Namátkově zkontrolujeme, že data v modelu odpovídají (včetně nové ceny)
                .andExpect(model().attribute("designs", hasItem(
                        allOf(
                                hasProperty("id", is(2L)),
                                hasProperty("name", is("Modern Premium")),
                                hasProperty("priceSurchargeCZK", comparesEqualTo(new BigDecimal("500.00")))
                        )
                )));

        verify(designService).getAllDesignsSortedByName();
    }

    @Test
    @DisplayName("GET /admin/designs/new - Zobrazí formulář pro nový design")
    void showCreateForm_Success() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/designs/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/design-form"))
                .andExpect(model().attributeExists("design", "pageTitle"))
                .andExpect(model().attribute("design", hasProperty("id", nullValue())));
    }

    @Test
    @DisplayName("POST /admin/designs - Úspěšné vytvoření designu s cenou a obrázkem")
    void createDesign_Success_WithExtras() throws Exception {
        Design createdDesign = new Design(); createdDesign.setId(3L); createdDesign.setName("Nová Barva");
        when(designService.createDesign(any(Design.class))).thenReturn(createdDesign);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/designs")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Nová Barva")
                        .param("description", "Popisek barvy")
                        .param("priceSurchargeCZK", "100.50")
                        .param("priceSurchargeEUR", "4.50")
                        .param("imageUrl", "http://images.com/nova.png")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/designs"))
                .andExpect(flash().attributeExists("successMessage"));

        // Ověření, že service metoda byla volána s objektem, který má nastavené hodnoty
        ArgumentCaptor<Design> designCaptor = ArgumentCaptor.forClass(Design.class);
        verify(designService).createDesign(designCaptor.capture());
        Design designPassed = designCaptor.getValue();
        assertEquals("Nová Barva", designPassed.getName());
        assertEquals("http://images.com/nova.png", designPassed.getImageUrl());
        assertEquals(0, new BigDecimal("100.50").compareTo(designPassed.getPriceSurchargeCZK()));
        assertEquals(0, new BigDecimal("4.50").compareTo(designPassed.getPriceSurchargeEUR()));
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
        when(designService.findById(2L)).thenReturn(Optional.of(design2_withPrice)); // Použijeme design s cenou

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/designs/2/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/design-form"))
                .andExpect(model().attributeExists("design", "pageTitle"))
                .andExpect(model().attribute("design", hasProperty("id", is(2L))))
                .andExpect(model().attribute("design", hasProperty("imageUrl", is("/img/modern.jpg"))))
                .andExpect(model().attribute("design", hasProperty("priceSurchargeCZK", comparesEqualTo(new BigDecimal("500.00")))));

        verify(designService).findById(2L);
    }

    @Test
    @DisplayName("POST /admin/designs/{id} - Úspěšná aktualizace s novými poli")
    void updateDesign_Success_WithExtras() throws Exception {
        long id = 1L;
        Design updatedDesign = new Design(); updatedDesign.setId(id); updatedDesign.setName("Klasik Nový");
        when(designService.updateDesign(eq(id), any(Design.class))).thenReturn(updatedDesign);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/designs/{id}", id)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Klasik Nový")
                        .param("description", "Aktualizovaný popis")
                        .param("priceSurchargeCZK", "50") // Nová cena
                        .param("priceSurchargeEUR", "") // Smazání EUR ceny
                        .param("imageUrl", "/img/klasik-novy.png") // Nový obrázek
                        .param("active", "true")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/designs"))
                .andExpect(flash().attributeExists("successMessage"));

        // Ověření dat předaných do service
        ArgumentCaptor<Design> designCaptor = ArgumentCaptor.forClass(Design.class);
        verify(designService).updateDesign(eq(id), designCaptor.capture());
        Design designPassed = designCaptor.getValue();
        assertEquals("Klasik Nový", designPassed.getName());
        assertEquals("/img/klasik-novy.png", designPassed.getImageUrl());
        assertEquals(0, new BigDecimal("50").compareTo(designPassed.getPriceSurchargeCZK()));
        // PriceSurchargeEUR by měl být null nebo "" v DTO, service by ho měl normalizovat na null
        assertTrue(designPassed.getPriceSurchargeEUR() == null || "".equals(designPassed.getPriceSurchargeEUR().toString()));
    }

    @Test
    @DisplayName("POST /admin/designs/{id} - Chyba validace vrátí formulář")
    void updateDesign_ValidationError() throws Exception {
        long id = 1L;
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/designs/{id}", id)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "") // Nevalidní
                        .param("active", "true")
                )
                .andExpect(status().isOk())
                .andExpect(view().name("admin/design-form"))
                .andExpect(model().attributeExists("design", "pageTitle"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("design", "name"));

        verify(designService, never()).updateDesign(anyLong(), any());
    }

    // Testy pro delete zůstávají stejné jako v předchozí verzi controller testu
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