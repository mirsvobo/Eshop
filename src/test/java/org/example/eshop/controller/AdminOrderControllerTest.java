package org.example.eshop.controller; // Ověřte správnost balíčku

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.admin.controller.AdminOrderController;
import org.example.eshop.config.SecurityTestConfig; // Import sdílené konfigurace
import org.example.eshop.model.Customer;
import org.example.eshop.model.Order;
import org.example.eshop.model.OrderItem;
import org.example.eshop.model.OrderState;
import org.example.eshop.service.CurrencyService; // Ponecháno pro @ControllerAdvice
import org.example.eshop.service.OrderService;
import org.example.eshop.service.OrderStateService;
import org.example.eshop.service.SuperFakturaInvoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

// Import Hamcrest matchers
import static org.hamcrest.Matchers.*;
// Import JUnit assertions
import static org.junit.jupiter.api.Assertions.assertEquals; // Příklad
// Import Mockito methods and ArgumentMatchers
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
// Import Spring MVC ResultMatchers
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminOrderController.class)
@WithMockUser(roles = "ADMIN") // Přístup pouze pro admina
@Import(SecurityTestConfig.class) // Použití sdílené konfigurace (vypíná např. CSRF)
class AdminOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private OrderService orderService;
    @MockBean private OrderStateService orderStateService;
    @MockBean private SuperFakturaInvoiceService superFakturaInvoiceService;
    @MockBean private CurrencyService currencyService; // Mock pro @ControllerAdvice/layout

    @Value("${superfaktura.api.url:https://default.sf.url}")
    private String superFakturaBaseUrl;

    private Order testOrder1; // Pending
    private Order testOrder2_DepositRequired; // Awaiting deposit
    private Order testOrder3_DepositPaid; // Deposit paid
    private Order testOrder4_FinalGenerated; // Final invoice already generated
    private OrderState stateNew;
    private OrderState stateProcessing;
    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        testCustomer = new Customer();
        testCustomer.setId(1L);
        testCustomer.setEmail("customer@test.com");

        stateNew = new OrderState(); stateNew.setId(1L); stateNew.setCode("NEW"); stateNew.setName("Nová");
        stateProcessing = new OrderState(); stateProcessing.setId(2L); stateProcessing.setCode("PROCESSING"); stateProcessing.setName("Zpracovává se");

        // --- Vytvoření položky pro testOrder1 ---
        OrderItem itemForOrder1 = new OrderItem();
        itemForOrder1.setId(101L); // Příklad ID
        itemForOrder1.setProductName("Test Item 1");
        itemForOrder1.setCount(1);
        itemForOrder1.setUnitPriceWithoutTax(new BigDecimal("1000.00"));
        itemForOrder1.setTaxRate(new BigDecimal("0.21")); // <-- Nastavení nenulové sazby DPH
        itemForOrder1.setTotalPriceWithoutTax(new BigDecimal("1000.00"));
        itemForOrder1.setTotalTaxAmount(new BigDecimal("210.00"));
        itemForOrder1.setTotalPriceWithTax(new BigDecimal("1210.00"));
        // --- Konec vytvoření položky ---

        testOrder1 = new Order();
        testOrder1.setId(1L);
        testOrder1.setOrderCode("ORD-001");
        testOrder1.setCustomer(testCustomer);
        testOrder1.setOrderDate(LocalDateTime.now().minusDays(1));
        testOrder1.setStateOfOrder(stateNew);
        testOrder1.setPaymentStatus("PENDING");
        testOrder1.setTotalPrice(new BigDecimal("1210.00"));
        testOrder1.setCurrency("CZK");
        testOrder1.setOrderItems(List.of(itemForOrder1)); // <-- Přiřazení inicializované položky

        // --- Inicializace položek pro ostatní objednávky (pro jistotu) ---
        OrderItem itemForOrder2 = new OrderItem();
        itemForOrder2.setId(102L); itemForOrder2.setProductName("Test Item 2"); itemForOrder2.setCount(1);
        itemForOrder2.setUnitPriceWithoutTax(new BigDecimal("826.45")); itemForOrder2.setTaxRate(new BigDecimal("0.21")); // Nastavení sazby
        itemForOrder2.setTotalPriceWithoutTax(new BigDecimal("826.45")); itemForOrder2.setTotalTaxAmount(new BigDecimal("173.55")); itemForOrder2.setTotalPriceWithTax(new BigDecimal("1000.00"));
        testOrder2_DepositRequired = new Order();
        testOrder2_DepositRequired.setId(2L); testOrder2_DepositRequired.setOrderCode("ORD-002-DEP"); testOrder2_DepositRequired.setCustomer(testCustomer); testOrder2_DepositRequired.setOrderDate(LocalDateTime.now());
        testOrder2_DepositRequired.setStateOfOrder(stateNew); testOrder2_DepositRequired.setPaymentStatus("AWAITING_DEPOSIT"); testOrder2_DepositRequired.setDepositAmount(new BigDecimal("500.00"));
        testOrder2_DepositRequired.setTotalPrice(new BigDecimal("1000.00")); testOrder2_DepositRequired.setCurrency("CZK");
        testOrder2_DepositRequired.setOrderItems(List.of(itemForOrder2));

        OrderItem itemForOrder3 = new OrderItem();
        itemForOrder3.setId(103L); itemForOrder3.setProductName("Test Item 3"); itemForOrder3.setCount(1);
        itemForOrder3.setUnitPriceWithoutTax(new BigDecimal("991.74")); itemForOrder3.setTaxRate(new BigDecimal("0.21")); // Nastavení sazby
        itemForOrder3.setTotalPriceWithoutTax(new BigDecimal("991.74")); itemForOrder3.setTotalTaxAmount(new BigDecimal("208.26")); itemForOrder3.setTotalPriceWithTax(new BigDecimal("1200.00"));
        testOrder3_DepositPaid = new Order();
        testOrder3_DepositPaid.setId(3L); testOrder3_DepositPaid.setOrderCode("ORD-003-DEPPAID"); testOrder3_DepositPaid.setCustomer(testCustomer); testOrder3_DepositPaid.setOrderDate(LocalDateTime.now().minusHours(5));
        testOrder3_DepositPaid.setStateOfOrder(stateProcessing); testOrder3_DepositPaid.setPaymentStatus("DEPOSIT_PAID"); testOrder3_DepositPaid.setDepositAmount(new BigDecimal("600.00"));
        testOrder3_DepositPaid.setDepositPaidDate(LocalDateTime.now().minusDays(1)); testOrder3_DepositPaid.setTotalPrice(new BigDecimal("1200.00")); testOrder3_DepositPaid.setCurrency("EUR");
        testOrder3_DepositPaid.setSfProformaInvoiceId(54321L); testOrder3_DepositPaid.setProformaInvoiceNumber("PF-EUR-001");
        testOrder3_DepositPaid.setOrderItems(List.of(itemForOrder3));

        OrderItem itemForOrder4 = new OrderItem();
        itemForOrder4.setId(104L); itemForOrder4.setProductName("Test Item 4"); itemForOrder4.setCount(1);
        itemForOrder4.setUnitPriceWithoutTax(new BigDecimal("661.16")); itemForOrder4.setTaxRate(new BigDecimal("0.21")); // Nastavení sazby
        itemForOrder4.setTotalPriceWithoutTax(new BigDecimal("661.16")); itemForOrder4.setTotalTaxAmount(new BigDecimal("138.84")); itemForOrder4.setTotalPriceWithTax(new BigDecimal("800.00"));
        testOrder4_FinalGenerated = new Order();
        testOrder4_FinalGenerated.setId(4L); testOrder4_FinalGenerated.setOrderCode("ORD-004-FINAL"); testOrder4_FinalGenerated.setCustomer(testCustomer); testOrder4_FinalGenerated.setOrderDate(LocalDateTime.now().minusDays(2));
        testOrder4_FinalGenerated.setStateOfOrder(stateProcessing); testOrder4_FinalGenerated.setPaymentStatus("PAID"); testOrder4_FinalGenerated.setTotalPrice(new BigDecimal("800.00")); testOrder4_FinalGenerated.setCurrency("CZK");
        testOrder4_FinalGenerated.setFinalInvoiceGenerated(true); testOrder4_FinalGenerated.setSfFinalInvoiceId(98765L);
        testOrder4_FinalGenerated.setOrderItems(List.of(itemForOrder4));

        // Lenient mockování (zůstává stejné)
        lenient().when(currencyService.getSelectedCurrency()).thenReturn("CZK");
        lenient().when(orderStateService.getAllOrderStatesSorted()).thenReturn(Arrays.asList(stateNew, stateProcessing));
        lenient().when(orderService.findOrderById(1L)).thenReturn(Optional.of(testOrder1));
        lenient().when(orderService.findOrderById(2L)).thenReturn(Optional.of(testOrder2_DepositRequired));
        lenient().when(orderService.findOrderById(3L)).thenReturn(Optional.of(testOrder3_DepositPaid));
        lenient().when(orderService.findOrderById(4L)).thenReturn(Optional.of(testOrder4_FinalGenerated));
        lenient().when(orderService.findOrderById(99L)).thenReturn(Optional.empty());
    }

    // --- Testy GET Seznamu (zůstává stejný) ---

    @Test
    @DisplayName("GET /admin/orders - Zobrazí seznam objednávek")
    void listOrders_ShouldReturnListView() throws Exception {
        Page<Order> orderPage = new PageImpl<>(Arrays.asList(testOrder1, testOrder2_DepositRequired), PageRequest.of(0, 20), 2);
        when(orderService.findOrders(any(Pageable.class), eq(Optional.empty()), eq(Optional.empty()), eq(Optional.empty()), eq(Optional.empty()), eq(Optional.empty())))
                .thenReturn(orderPage);

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/orders"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/orders-list"))
                .andExpect(model().attributeExists("orderPage", "allOrderStates", "currentSort"))
                .andExpect(model().attribute("orderPage", hasProperty("content", hasSize(2))));

        verify(orderService).findOrders(any(Pageable.class), eq(Optional.empty()), eq(Optional.empty()), eq(Optional.empty()), eq(Optional.empty()), eq(Optional.empty()));
        verify(orderStateService).getAllOrderStatesSorted();
    }

    // --- Testy GET Detailu (opraven isAddressesMatchInOrder) ---

    @Test
    @DisplayName("GET /admin/orders/{id} - Zobrazí detail objednávky")
    void viewOrderDetail_Success() throws Exception {
        // findOrderById je mockováno v setUp
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/orders/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/order-detail"))
                .andExpect(model().attributeExists("order", "allOrderStates", "superFakturaBaseUrl", "currentUri"))
                .andExpect(model().attribute("order", hasProperty("id", is(1L))))
                .andExpect(model().attribute("superFakturaBaseUrl", is(superFakturaBaseUrl))); // Ověříme předání URL z @Value

        verify(orderService).findOrderById(1L);
        verify(orderStateService).getAllOrderStatesSorted(); // Musí se volat i pro detail
    }

    @Test
    @DisplayName("GET /admin/orders/{id} - Nenalezeno - Přesměruje na seznam")
    void viewOrderDetail_NotFound() throws Exception {
        // findOrderById je mockováno v setUp
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/orders/99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders"))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(orderService).findOrderById(99L);
        verify(orderStateService, never()).getAllOrderStatesSorted();
    }


    // --- Testy POST Akcí (zůstávají stejné) ---

    @Test
    @DisplayName("POST /admin/orders/{id}/update-state - Úspěšná změna stavu")
    void updateOrderState_Success() throws Exception {
        long orderId = 1L;
        long newStateId = 2L;
        when(orderService.updateOrderState(orderId, newStateId)).thenReturn(testOrder1); // Vrátí upravenou objednávku

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/orders/{id}/update-state", orderId)
                        .param("newStateId", String.valueOf(newStateId))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attributeExists("successMessage"));

        verify(orderService).updateOrderState(orderId, newStateId);
    }

    @Test
    @DisplayName("POST /admin/orders/{id}/update-state - Chyba (objednávka nenalezena)")
    void updateOrderState_OrderNotFound() throws Exception {
        long orderId = 99L;
        long newStateId = 2L;
        String errorMsg = "Order not found: " + orderId;
        when(orderService.updateOrderState(orderId, newStateId)).thenThrow(new EntityNotFoundException(errorMsg));

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/orders/{id}/update-state", orderId)
                        .param("newStateId", String.valueOf(newStateId))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders")) // Při nenalezení objednávky by mělo jít na seznam
                .andExpect(flash().attribute("errorMessage", containsString(errorMsg)));

        verify(orderService).updateOrderState(orderId, newStateId);
    }

    @Test
    @DisplayName("POST /admin/orders/{id}/mark-deposit-paid - Úspěch")
    void markDepositAsPaid_Success() throws Exception {
        long orderId = 2L; // testOrder2_DepositRequired
        // Mockujeme orderService.markDepositAsPaid tak, aby vracel upravený order
        when(orderService.markDepositAsPaid(eq(orderId), any(LocalDate.class))).thenAnswer(invocation -> {
            testOrder2_DepositRequired.setPaymentStatus("DEPOSIT_PAID");
            testOrder2_DepositRequired.setDepositPaidDate(invocation.getArgument(1, LocalDate.class).atStartOfDay());
            return testOrder2_DepositRequired;
        });


        mockMvc.perform(MockMvcRequestBuilders.post("/admin/orders/{id}/mark-deposit-paid", orderId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attributeExists("successMessage")); // Očekáváme success

        verify(orderService).markDepositAsPaid(eq(orderId), any(LocalDate.class));
    }

    @Test
    @DisplayName("POST /admin/orders/{id}/mark-deposit-paid - Chyba (záloha není potřeba)")
    void markDepositAsPaid_DepositNotRequired() throws Exception {
        long orderId = 1L; // testOrder1 nemá depositAmount
        testOrder1.setDepositAmount(null); // Ujistíme se
        // Mockujeme, že orderService vyhodí výjimku
        when(orderService.markDepositAsPaid(eq(orderId), any(LocalDate.class)))
                .thenThrow(new IllegalStateException("Objednávka nevyžaduje zálohu."));

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/orders/{id}/mark-deposit-paid", orderId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId)) // Chyba -> zpět na detail
                .andExpect(flash().attributeExists("errorMessage"))
                .andExpect(flash().attribute("errorMessage", containsString("nevyžaduje zálohu")));

        verify(orderService).markDepositAsPaid(eq(orderId), any(LocalDate.class));
    }

    @Test
    @DisplayName("POST /admin/orders/{id}/mark-fully-paid - Úspěch")
    void markFullyPaid_Success() throws Exception {
        long orderId = 3L; // testOrder3_DepositPaid
        // Mockujeme orderService.markOrderAsFullyPaid
        when(orderService.markOrderAsFullyPaid(eq(orderId), any(LocalDate.class))).thenAnswer(invocation -> {
            testOrder3_DepositPaid.setPaymentStatus("PAID");
            testOrder3_DepositPaid.setPaymentDate(invocation.getArgument(1, LocalDate.class).atStartOfDay());
            return testOrder3_DepositPaid;
        });


        mockMvc.perform(MockMvcRequestBuilders.post("/admin/orders/{id}/mark-fully-paid", orderId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attributeExists("successMessage"));

        verify(orderService).markOrderAsFullyPaid(eq(orderId), any(LocalDate.class));
    }

    // --- Testy POST SuperFaktura (s opravenými očekáváními) ---

    @Test
    @DisplayName("POST /admin/orders/{id}/generate-proforma - Úspěch")
    void generateProforma_Success() throws Exception {
        long orderId = 2L; // testOrder2_DepositRequired
        // findOrderById je mockováno v setUp
        doNothing().when(superFakturaInvoiceService).generateProformaInvoice(testOrder2_DepositRequired);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/orders/{id}/generate-proforma", orderId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attributeExists("successMessage")); // Úspěch

        verify(orderService).findOrderById(orderId);
        verify(superFakturaInvoiceService).generateProformaInvoice(testOrder2_DepositRequired);
    }

    @Test
    @DisplayName("POST /admin/orders/{id}/generate-proforma - Chyba (již vygenerováno)")
    void generateProforma_AlreadyGenerated_Warning() throws Exception {
        long orderId = 3L; // testOrder3_DepositPaid již má sfProformaInvoiceId
        // findOrderById je mockováno v setUp

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/orders/{id}/generate-proforma", orderId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attributeExists("warningMessage")) // Varování
                .andExpect(flash().attribute("warningMessage", containsString("již byla vygenerována")));

        verify(orderService).findOrderById(orderId);
        verify(superFakturaInvoiceService, never()).generateProformaInvoice(any()); // Nemělo by se volat
    }

    @Test
    @DisplayName("POST /admin/orders/{id}/generate-tax-doc - Úspěch")
    void generateTaxDoc_Success() throws Exception {
        long orderId = 3L; // testOrder3_DepositPaid má zaplacenou zálohu
        // findOrderById je mockováno v setUp
        doNothing().when(superFakturaInvoiceService).generateTaxDocumentForDeposit(testOrder3_DepositPaid);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/orders/{id}/generate-tax-doc", orderId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attributeExists("successMessage")); // Úspěch

        verify(orderService).findOrderById(orderId);
        verify(superFakturaInvoiceService).generateTaxDocumentForDeposit(testOrder3_DepositPaid);
    }

    @Test
    @DisplayName("POST /admin/orders/{id}/generate-tax-doc - Chyba (záloha nezaplacena)")
    void generateTaxDoc_DepositNotPaid_Error() throws Exception {
        long orderId = 2L; // testOrder2_DepositRequired čeká na zálohu
        // findOrderById je mockováno v setUp

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/orders/{id}/generate-tax-doc", orderId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attributeExists("errorMessage")) // Očekáváme chybu
                .andExpect(flash().attribute("errorMessage", containsString("nebyla označena jako zaplacená")));

        verify(orderService).findOrderById(orderId);
        verify(superFakturaInvoiceService, never()).generateTaxDocumentForDeposit(any()); // Nemělo by se volat
    }

    @Test
    @DisplayName("POST /admin/orders/{id}/generate-final - Úspěch")
    void generateFinal_Success() throws Exception {
        long orderId = 3L; // testOrder3_DepositPaid má zaplacenou zálohu
        // findOrderById je mockováno v setUp
        doNothing().when(superFakturaInvoiceService).generateFinalInvoice(testOrder3_DepositPaid);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/orders/{id}/generate-final", orderId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attributeExists("successMessage")); // Úspěch

        verify(orderService).findOrderById(orderId);
        verify(superFakturaInvoiceService).generateFinalInvoice(testOrder3_DepositPaid);
    }

    @Test
    @DisplayName("POST /admin/orders/{id}/generate-final - Chyba (záloha nezaplacena)")
    void generateFinal_DepositNotPaid_Error() throws Exception {
        long orderId = 2L; // testOrder2_DepositRequired čeká na zálohu
        // findOrderById je mockováno v setUp

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/orders/{id}/generate-final", orderId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attributeExists("errorMessage")) // Chyba
                .andExpect(flash().attribute("errorMessage", containsString("není zaplacena záloha")));

        verify(orderService).findOrderById(orderId);
        verify(superFakturaInvoiceService, never()).generateFinalInvoice(any()); // Nemělo by se volat
    }

    @Test
    @DisplayName("POST /admin/orders/{id}/generate-final - Chyba (již vygenerováno)")
    void generateFinal_AlreadyGenerated_Warning() throws Exception {
        long orderId = 4L; // testOrder4_FinalGenerated má finalInvoiceGenerated=true
        // findOrderById je mockováno v setUp

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/orders/{id}/generate-final", orderId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attributeExists("warningMessage")) // Varování
                .andExpect(flash().attribute("warningMessage", containsString("již byla vygenerována")));

        verify(orderService).findOrderById(orderId);
        verify(superFakturaInvoiceService, never()).generateFinalInvoice(any()); // Nemělo by se volat
    }


    // --- Testy POST Email/Označení ---

    @Test
    @DisplayName("POST /admin/orders/{id}/send-invoice-email - Úspěch")
    void sendInvoiceEmail_Success() throws Exception {
        long orderId = 3L; // testOrder3_DepositPaid
        long sfInvoiceId = 54321L; // ID proformy z tohoto orderu
        String invoiceType = "proforma";
        // findOrderById je mockováno v setUp
        doNothing().when(superFakturaInvoiceService).sendInvoiceByEmail(sfInvoiceId, testCustomer.getEmail(), invoiceType, testOrder3_DepositPaid.getOrderCode());

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/orders/{id}/send-invoice-email", orderId)
                        .param("sfInvoiceId", String.valueOf(sfInvoiceId))
                        .param("invoiceType", invoiceType)
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attributeExists("successMessage"));

        verify(orderService).findOrderById(orderId);
        verify(superFakturaInvoiceService).sendInvoiceByEmail(sfInvoiceId, testCustomer.getEmail(), invoiceType, testOrder3_DepositPaid.getOrderCode());
    }

    @Test
    @DisplayName("POST /admin/orders/{id}/mark-invoice-sent - Úspěch")
    void markInvoiceSent_Success() throws Exception {
        long orderId = 3L;
        long sfInvoiceId = 54321L;
        String invoiceType = "proforma";
        // findOrderById je mockováno v setUp
        doNothing().when(superFakturaInvoiceService).markInvoiceAsSent(eq(sfInvoiceId), eq(testCustomer.getEmail()), anyString());

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/orders/{id}/mark-invoice-sent", orderId)
                        .param("sfInvoiceId", String.valueOf(sfInvoiceId))
                        .param("invoiceType", invoiceType)
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attributeExists("successMessage"));

        verify(orderService).findOrderById(orderId);
        // Ověříme, že se volá s ID, emailem a nějakým subjectem (obsahuje číslo faktury)
        verify(superFakturaInvoiceService).markInvoiceAsSent(eq(sfInvoiceId), eq(testCustomer.getEmail()), contains("Faktura PF-EUR-001"));
    }
}