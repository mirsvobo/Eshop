package org.example.eshop.admin.controller;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.config.SecurityTestConfig;
import org.example.eshop.model.OrderState;
import org.example.eshop.service.CurrencyService; // Pro advice
import org.example.eshop.service.OrderStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

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

@WebMvcTest(AdminOrderStateController.class)
@WithMockUser(roles = "ADMIN")
@Import(SecurityTestConfig.class)
class AdminOrderStateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderStateService orderStateService;

    @MockBean
    private CurrencyService currencyService; // Pro advice

    private OrderState stateNew;
    private OrderState stateProcessing;

    @BeforeEach
    void setUp() {
        stateNew = new OrderState(); stateNew.setId(1L); stateNew.setCode("NEW"); stateNew.setName("Nová"); stateNew.setDisplayOrder(10);
        stateProcessing = new OrderState(); stateProcessing.setId(2L); stateProcessing.setCode("PROCESSING"); stateProcessing.setName("Zpracovává se"); stateProcessing.setDisplayOrder(20);

        lenient().when(currencyService.getSelectedCurrency()).thenReturn("CZK");
    }

    @Test
    @DisplayName("GET /admin/order-states - Zobrazí seřazený seznam stavů")
    void listOrderStates_Success() throws Exception {
        List<OrderState> stateList = Arrays.asList(stateNew, stateProcessing);
        when(orderStateService.getAllOrderStatesSorted()).thenReturn(stateList);

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/order-states"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/order-states-list"))
                .andExpect(model().attributeExists("orderStates"))
                .andExpect(model().attribute("orderStates", hasSize(2)))
                .andExpect(model().attribute("orderStates", contains( // Ověření pořadí
                        hasProperty("code", is("NEW")),
                        hasProperty("code", is("PROCESSING"))
                )));

        verify(orderStateService).getAllOrderStatesSorted();
    }

    @Test
    @DisplayName("GET /admin/order-states/new - Zobrazí formulář")
    void showCreateOrderStateForm_Success() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/order-states/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/order-state-form"))
                .andExpect(model().attributeExists("orderState", "pageTitle"))
                .andExpect(model().attribute("orderState", instanceOf(OrderState.class)))
                .andExpect(model().attribute("orderState", hasProperty("id", nullValue())));
    }

    @Test
    @DisplayName("POST /admin/order-states - Úspěšné vytvoření")
    void createOrderState_Success() throws Exception {
        OrderState createdState = new OrderState(); createdState.setId(3L); createdState.setCode("SHIPPED"); createdState.setName("Odesláno");
        when(orderStateService.createOrderState(any(OrderState.class))).thenReturn(createdState);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/order-states")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("code", "SHIPPED")
                        .param("name", "Odesláno")
                        .param("displayOrder", "30")
                        .param("isFinalState", "false")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/order-states"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(orderStateService).createOrderState(any(OrderState.class));
    }

    @Test
    @DisplayName("POST /admin/order-states - Chyba validace (chybí kód)")
    void createOrderState_ValidationError_MissingCode() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/order-states")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("code", "") // Chybějící kód
                        .param("name", "Platný Název")
                )
                .andExpect(status().isOk())
                .andExpect(view().name("admin/order-state-form"))
                .andExpect(model().attributeExists("orderState", "pageTitle"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("orderState", "code"));

        verify(orderStateService, never()).createOrderState(any());
    }

    @Test
    @DisplayName("POST /admin/order-states - Chyba (duplicitní kód)")
    void createOrderState_DuplicateCodeError() throws Exception {
        String duplicateCode = "NEW";
        when(orderStateService.createOrderState(any(OrderState.class)))
                .thenThrow(new IllegalArgumentException("OrderState with code '" + duplicateCode + "' already exists."));

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/order-states")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("code", duplicateCode)
                        .param("name", "Duplicitní Název")
                )
                .andExpect(status().isOk())
                .andExpect(view().name("admin/order-state-form"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrorCode("orderState", "code", "error.orderState.duplicate"));

        verify(orderStateService).createOrderState(any(OrderState.class));
    }

    @Test
    @DisplayName("GET /admin/order-states/{id}/edit - Zobrazí formulář")
    void showEditOrderStateForm_Success() throws Exception {
        when(orderStateService.getOrderStateById(1L)).thenReturn(Optional.of(stateNew));

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/order-states/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/order-state-form"))
                .andExpect(model().attributeExists("orderState", "pageTitle"))
                .andExpect(model().attribute("orderState", hasProperty("id", is(1L))));

        verify(orderStateService).getOrderStateById(1L);
    }

    @Test
    @DisplayName("GET /admin/order-states/{id}/edit - Nenalezeno")
    void showEditOrderStateForm_NotFound() throws Exception {
        when(orderStateService.getOrderStateById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/order-states/99/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/order-states"))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(orderStateService).getOrderStateById(99L);
    }

    @Test
    @DisplayName("POST /admin/order-states/{id} - Úspěšná aktualizace")
    void updateOrderState_Success() throws Exception {
        long id = 1L;
        // Mockujeme návratovou hodnotu (Optional s Optional uvnitř kvůli Object)
        Optional<OrderState> innerOptional = Optional.of(stateNew);
        Optional<Object> outerOptional = Optional.of(innerOptional);
        when(orderStateService.updateOrderState(eq(id), any(OrderState.class))).thenReturn(outerOptional);


        mockMvc.perform(MockMvcRequestBuilders.post("/admin/order-states/{id}", id)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("code", "NEW_UPDATED")
                        .param("name", "Nová Upravená")
                        .param("displayOrder", "5")
                        .param("isFinalState", "true")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/order-states"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(orderStateService).updateOrderState(eq(id), any(OrderState.class));
    }


    @Test
    @DisplayName("POST /admin/order-states/{id}/delete - Úspěšné smazání")
    void deleteOrderState_Success() throws Exception {
        long id = 1L;
        doNothing().when(orderStateService).deleteOrderStateById(id);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/order-states/{id}/delete", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/order-states"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(orderStateService).deleteOrderStateById(id);
    }

    @Test
    @DisplayName("POST /admin/order-states/{id}/delete - Chyba (stav použit)")
    void deleteOrderState_InUseError() throws Exception {
        long id = 1L;
        String errorMsg = "Cannot delete order state 'Nová' as it is currently in use by 5 orders.";
        doThrow(new IllegalStateException(errorMsg)).when(orderStateService).deleteOrderStateById(id);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/order-states/{id}/delete", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/order-states"))
                .andExpect(flash().attribute("errorMessage", is(errorMsg)));

        verify(orderStateService).deleteOrderStateById(id);
    }
}