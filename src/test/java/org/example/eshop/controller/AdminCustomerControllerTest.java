// Soubor: src/test/java/org/example/eshop/controller/AdminCustomerControllerTest.java

package org.example.eshop.controller; // Balíček možná potřeba upravit na org.example.eshop.admin.controller

// Importy ...
import org.example.eshop.admin.controller.AdminCustomerController;
import org.example.eshop.config.SecurityTestConfig; // Import sdílené konfigurace
import org.example.eshop.dto.AddressDto;
import org.example.eshop.dto.ProfileUpdateDto;
import org.example.eshop.model.Customer;
import org.example.eshop.service.CurrencyService; // Ponecháno pro případné @ControllerAdvice
import org.example.eshop.service.CustomerService;
import org.example.eshop.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import; // Import pro @Import
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminCustomerController.class)
@WithMockUser(roles = "ADMIN") // Globální admin role pro testy
@Import(SecurityTestConfig.class) // Aplikace sdílené konfigurace
class AdminCustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private CustomerService customerService;
    @MockBean private OrderService orderService;
    @MockBean private CurrencyService currencyService;

    private Customer testCustomer;
    private AddressDto testInvoiceAddressDto;
    private AddressDto testDeliveryAddressDto;
    private ProfileUpdateDto testProfileUpdateDto;

    @BeforeEach
    void setUp() {
        // Inicializace testovacích dat
        testCustomer = new Customer();
        testCustomer.setId(1L);
        testCustomer.setFirstName("Test");
        testCustomer.setLastName("Customer");
        testCustomer.setEmail("test@example.com");
        testCustomer.setEnabled(true);
        testCustomer.setInvoiceStreet("Invoice St 1");
        testCustomer.setInvoiceCity("Invoice City");
        testCustomer.setInvoiceZipCode("11111");
        testCustomer.setInvoiceCountry("ČR");
        testCustomer.setUseInvoiceAddressAsDelivery(false); // Testujeme i rozdílné adresy
        testCustomer.setDeliveryStreet("Delivery St 2");
        testCustomer.setDeliveryCity("Delivery City");
        testCustomer.setDeliveryZipCode("22222");
        testCustomer.setDeliveryCountry("ČR");
        testCustomer.setPhone("123456789");

        testInvoiceAddressDto = new AddressDto();
        testInvoiceAddressDto.setStreet("Invoice St 1");
        testInvoiceAddressDto.setCity("Invoice City");
        testInvoiceAddressDto.setZipCode("11111");
        testInvoiceAddressDto.setCountry("ČR");
        testInvoiceAddressDto.setFirstName("Test");
        testInvoiceAddressDto.setLastName("Customer");

        testDeliveryAddressDto = new AddressDto();
        testDeliveryAddressDto.setStreet("New Delivery St 3");
        testDeliveryAddressDto.setCity("New Delivery City");
        testDeliveryAddressDto.setZipCode("33333");
        testDeliveryAddressDto.setCountry("ČR");
        testDeliveryAddressDto.setFirstName("Delivery First"); // Jméno je povinné, pokud není firma
        testDeliveryAddressDto.setLastName("Delivery Last");

        testProfileUpdateDto = new ProfileUpdateDto();
        testProfileUpdateDto.setFirstName("UpdatedFirst");
        testProfileUpdateDto.setLastName("UpdatedLast");
        testProfileUpdateDto.setPhone("111222333");
    }

    @Test
    @DisplayName("GET /admin/customers - Zobrazí seznam zákazníků")
    void listCustomers_ShouldReturnListView() throws Exception {
        Page<Customer> customerPage = new PageImpl<>(Collections.singletonList(testCustomer), PageRequest.of(0, 20), 1);
        when(customerService.findCustomers(any(Pageable.class), any(), any(), any())).thenReturn(customerPage);

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/customers"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/customers-list"))
                .andExpect(model().attributeExists("customerPage"))
                .andExpect(model().attribute("customerPage", hasProperty("content", hasSize(1))))
                .andExpect(model().attribute("customerPage", hasProperty("content", hasItem(
                        hasProperty("email", is(testCustomer.getEmail()))
                ))));

        verify(customerService).findCustomers(any(Pageable.class), eq(null), eq(null), eq(null));
    }

    @Test
    @DisplayName("GET /admin/customers/{id} - Zobrazí detail zákazníka")
    void viewCustomerDetail_ShouldReturnDetailView() throws Exception {
        when(customerService.getCustomerById(1L)).thenReturn(Optional.of(testCustomer));

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/customers/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/customer-detail"))
                .andExpect(model().attributeExists("customer", "profileUpdateDto", "invoiceAddressDto", "deliveryAddressDto"))
                .andExpect(model().attribute("customer", hasProperty("id", is(1L))))
                .andExpect(model().attribute("invoiceAddressDto", hasProperty("street", is(testCustomer.getInvoiceStreet()))))
                .andExpect(model().attribute("deliveryAddressDto", hasProperty("street", is(testCustomer.getDeliveryStreet()))));

        verify(customerService).getCustomerById(1L);
    }

    @Test
    @DisplayName("GET /admin/customers/{id} - Nenalezen - Přesměruje na seznam")
    void viewCustomerDetail_NotFound_ShouldRedirect() throws Exception {
        when(customerService.getCustomerById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/customers/99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/customers"))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(customerService).getCustomerById(99L);
    }

    @Test
    @DisplayName("POST /admin/customers/{id}/update-basic - Úspěšná aktualizace")
    void updateCustomerBasicInfo_Success() throws Exception {
        when(customerService.getCustomerById(1L)).thenReturn(Optional.of(testCustomer));
        when(customerService.saveCustomer(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/customers/1/update-basic")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("firstName", testProfileUpdateDto.getFirstName())
                                .param("lastName", testProfileUpdateDto.getLastName())
                                .param("phone", testProfileUpdateDto.getPhone())
                        // CSRF vypnuto
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/customers/1"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(customerService).getCustomerById(1L);
        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerService).saveCustomer(customerCaptor.capture());
        assertEquals(testProfileUpdateDto.getFirstName(), customerCaptor.getValue().getFirstName());
        assertEquals(testProfileUpdateDto.getLastName(), customerCaptor.getValue().getLastName());
        assertEquals(testProfileUpdateDto.getPhone(), customerCaptor.getValue().getPhone());
    }

    @Test
    @DisplayName("POST /admin/customers/{id}/update-basic - Chyba validace")
    void updateCustomerBasicInfo_ValidationError() throws Exception {
        when(customerService.getCustomerById(1L)).thenReturn(Optional.of(testCustomer));

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/customers/1/update-basic")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("firstName", "") // Prázdné jméno -> chyba validace
                                .param("lastName", testProfileUpdateDto.getLastName())
                                .param("phone", testProfileUpdateDto.getPhone())
                        // CSRF vypnuto
                )
                .andExpect(status().isOk())
                .andExpect(view().name("admin/customer-detail"))
                // --- OPRAVA: Ověření, že všechny potřebné atributy jsou v modelu ---
                .andExpect(model().attributeExists("customer", "profileUpdateDto", "invoiceAddressDto", "deliveryAddressDto"))
                // --- KONEC OPRAVY ---
                .andExpect(model().hasErrors()) // BindingResult pro profileUpdateDto má chyby
                .andExpect(model().attributeHasFieldErrors("profileUpdateDto", "firstName")) // Specifická chyba
                .andExpect(model().attributeExists("errorMessage")); // Obecná chybová zpráva

        verify(customerService, never()).saveCustomer(any()); // Save se nesmí volat
        // --- OPRAVA: Ověření volání getCustomerById v error path ---
        verify(customerService).getCustomerById(1L);
        // --- KONEC OPRAVY ---
    }


    @Test
    @DisplayName("POST /admin/customers/{id}/update-invoice-address - Úspěch")
    void updateInvoiceAddress_Success() throws Exception {
        when(customerService.updateAddress(eq(1L), eq(CustomerService.AddressType.INVOICE), any(AddressDto.class)))
                .thenReturn(testCustomer);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/customers/1/update-invoice-address")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("firstName", "InvoiceFName")
                                .param("lastName", "InvoiceLName")
                                .param("street", "Updated Invoice St 10")
                                .param("city", "Updated Invoice City")
                                .param("zipCode", "11199")
                                .param("country", "Česká republika")
                        // CSRF vypnuto
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/customers/1"))
                .andExpect(flash().attributeExists("successMessage"));

        ArgumentCaptor<AddressDto> addressCaptor = ArgumentCaptor.forClass(AddressDto.class);
        verify(customerService).updateAddress(eq(1L), eq(CustomerService.AddressType.INVOICE), addressCaptor.capture());
        assertEquals("Updated Invoice St 10", addressCaptor.getValue().getStreet());
    }

    @Test
    @DisplayName("POST /admin/customers/{id}/update-delivery-address - Úspěch")
    void updateDeliveryAddress_Success() throws Exception {
        when(customerService.updateAddress(eq(1L), eq(CustomerService.AddressType.DELIVERY), any(AddressDto.class)))
                .thenReturn(testCustomer);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/customers/1/update-delivery-address")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("firstName", "DeliveryFName")
                                .param("lastName", "DeliveryLName")
                                .param("street", "Updated Delivery St 20")
                                .param("city", "Updated Delivery City")
                                .param("zipCode", "22299")
                                .param("country", "Česká republika")
                                .param("phone", "444555666")
                        // CSRF vypnuto
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/customers/1"))
                .andExpect(flash().attributeExists("successMessage"));

        ArgumentCaptor<AddressDto> addressCaptor = ArgumentCaptor.forClass(AddressDto.class);
        verify(customerService).updateAddress(eq(1L), eq(CustomerService.AddressType.DELIVERY), addressCaptor.capture());
        assertEquals("Updated Delivery City", addressCaptor.getValue().getCity());
    }

    @Test
    @DisplayName("POST /admin/customers/{id}/toggle-delivery-address - Nastaví na true")
    void toggleDeliveryAddressUsage_SetTrue() throws Exception {
        when(customerService.setUseInvoiceAddressAsDelivery(eq(1L), eq(true)))
                .thenReturn(testCustomer);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/customers/1/toggle-delivery-address")
                                .param("useInvoiceAddress", "true")
                        // CSRF vypnuto
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/customers/1"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(customerService).setUseInvoiceAddressAsDelivery(1L, true);
    }

    @Test
    @DisplayName("POST /admin/customers/{id}/toggle-delivery-address - Nastaví na false")
    void toggleDeliveryAddressUsage_SetFalse() throws Exception {
        when(customerService.setUseInvoiceAddressAsDelivery(eq(1L), eq(false)))
                .thenReturn(testCustomer);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/customers/1/toggle-delivery-address")
                        // Parametr není poslán
                        // CSRF vypnuto
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/customers/1"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(customerService).setUseInvoiceAddressAsDelivery(1L, false);
    }

    @Test
    @DisplayName("POST /admin/customers/{id}/toggle-enabled - Deaktivace")
    void toggleCustomerEnabled_Disable() throws Exception {
        when(customerService.getCustomerById(1L)).thenReturn(Optional.of(testCustomer));
        when(customerService.saveCustomer(any(Customer.class))).thenReturn(testCustomer);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/customers/1/toggle-enabled")
                                .param("enable", "false")
                        // CSRF vypnuto
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/customers/1"))
                .andExpect(flash().attribute("successMessage", containsString("deaktivován")));

        verify(customerService).getCustomerById(1L);
        verify(customerService).saveCustomer(argThat(customer -> !customer.isEnabled()));
    }

    @Test
    @DisplayName("POST /admin/customers/{id}/toggle-enabled - Aktivace")
    void toggleCustomerEnabled_Enable() throws Exception {
        testCustomer.setEnabled(false); // Počáteční stav je neaktivní
        when(customerService.getCustomerById(1L)).thenReturn(Optional.of(testCustomer));
        when(customerService.saveCustomer(any(Customer.class))).thenReturn(testCustomer);

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/customers/1/toggle-enabled")
                                .param("enable", "true")
                        // CSRF vypnuto
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/customers/1"))
                .andExpect(flash().attribute("successMessage", containsString("aktivován")));

        verify(customerService).getCustomerById(1L);
        verify(customerService).saveCustomer(argThat(Customer::isEnabled));
    }
}