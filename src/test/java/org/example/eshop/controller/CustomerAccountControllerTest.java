// Soubor: src/test/java/org/example/eshop/controller/CustomerAccountControllerTest.java

package org.example.eshop.controller; // Ujistěte se, že balíček je správný

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.config.SecurityTestConfig; // Import sdílené konfigurace
import org.example.eshop.dto.AddressDto;
import org.example.eshop.dto.ChangePasswordDto;
import org.example.eshop.dto.ProfileUpdateDto;
import org.example.eshop.model.Customer;
import org.example.eshop.model.Order;
import org.example.eshop.model.OrderState;
import org.example.eshop.service.CurrencyService;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

// Import specific Hamcrest matchers
import static org.hamcrest.Matchers.*;
// Import specific Mockito methods and ArgumentMatchers
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
// import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf; // Odstraněno
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerAccountController.class)
@WithMockUser(username = "user@example.com") // Globální uživatel pro testy
@Import(SecurityTestConfig.class) // Aplikace sdílené konfigurace
class CustomerAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private CustomerService customerService;
    @MockBean private OrderService orderService;
    @MockBean private CurrencyService currencyService; // I když není přímo volán, může být potřeba pro @ControllerAdvice

    private Customer loggedInCustomer;
    private Order customerOrder;
    private AddressDto addressDto;
    private ProfileUpdateDto profileDto;
    private ChangePasswordDto passwordDto;

    private final String MOCK_USER_EMAIL = "user@example.com";

    @BeforeEach
    void setUp() {
        loggedInCustomer = new Customer();
        loggedInCustomer.setId(1L);
        loggedInCustomer.setEmail(MOCK_USER_EMAIL);
        loggedInCustomer.setFirstName("Test");
        loggedInCustomer.setLastName("User");
        loggedInCustomer.setPhone("123456789");
        loggedInCustomer.setInvoiceStreet("Fakt 1");
        loggedInCustomer.setInvoiceCity("Fak City");
        loggedInCustomer.setInvoiceZipCode("11111");
        loggedInCustomer.setInvoiceCountry("ČR");
        loggedInCustomer.setUseInvoiceAddressAsDelivery(true); // Defaultně true
        // Není potřeba nastavovat delivery, @PrePersist/@PreUpdate by to měl synchronizovat,
        // ale pro jistotu v testech můžeme
        loggedInCustomer.setDeliveryStreet(loggedInCustomer.getInvoiceStreet());
        loggedInCustomer.setDeliveryCity(loggedInCustomer.getInvoiceCity());
        loggedInCustomer.setDeliveryZipCode(loggedInCustomer.getInvoiceZipCode());
        loggedInCustomer.setDeliveryCountry(loggedInCustomer.getInvoiceCountry());


        OrderState state = new OrderState(); state.setCode("NEW"); state.setName("Nová");
        customerOrder = new Order();
        customerOrder.setId(10L);
        customerOrder.setOrderCode("ORD123");
        customerOrder.setCustomer(loggedInCustomer);
        customerOrder.setOrderDate(LocalDateTime.now().minusDays(2));
        customerOrder.setStateOfOrder(state);
        customerOrder.setTotalPrice(new BigDecimal("150.00"));
        customerOrder.setCurrency("CZK");
        customerOrder.setCouponDiscountAmount(BigDecimal.ZERO); // <-- Inicializace pro Thymeleaf test

        addressDto = new AddressDto();
        addressDto.setStreet("Nova Ulice 10");
        addressDto.setCity("Nove Mesto");
        addressDto.setZipCode("99999");
        addressDto.setCountry("Česká Republika");
        addressDto.setFirstName("Test"); // Musí být pro validaci hasRecipient()
        addressDto.setLastName("User");

        profileDto = new ProfileUpdateDto();
        profileDto.setFirstName("UpdatedFirst");
        profileDto.setLastName("UpdatedLast");
        profileDto.setPhone("987654321");

        passwordDto = new ChangePasswordDto();
        passwordDto.setCurrentPassword("oldPassword");
        passwordDto.setNewPassword("newPassword123");
        passwordDto.setConfirmNewPassword("newPassword123");

        // Základní mockování getCurrentCustomer (voláno na začátku většiny metod)
        // Použijeme lenient(), protože ne každý test musí tuto metodu volat
        lenient().when(customerService.getCustomerByEmail(MOCK_USER_EMAIL)).thenReturn(Optional.of(loggedInCustomer));
    }

    // --- Testy Profilu ---

    @Test
    @DisplayName("GET /muj-ucet/profil - Zobrazí profil přihlášeného uživatele")
    void viewProfile_ShouldReturnProfileView() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/muj-ucet/profil"))
                .andExpect(status().isOk())
                .andExpect(view().name("muj-ucet/profil"))
                .andExpect(model().attributeExists("profile", "customerEmail"))
                .andExpect(model().attribute("customerEmail", is(MOCK_USER_EMAIL)))
                .andExpect(model().attribute("profile", hasProperty("firstName", is(loggedInCustomer.getFirstName()))));

        verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL);
    }

    @Test
    @DisplayName("POST /muj-ucet/profil - Úspěšně aktualizuje profil")
    void updateProfile_Success_ShouldRedirectWithSuccess() throws Exception {
        // Předpoklad: updateProfile v CustomerService je void
        doNothing().when(customerService).updateProfile(eq(MOCK_USER_EMAIL), any(ProfileUpdateDto.class));

        mockMvc.perform(MockMvcRequestBuilders.post("/muj-ucet/profil")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("firstName", profileDto.getFirstName())
                                .param("lastName", profileDto.getLastName())
                                .param("phone", profileDto.getPhone())
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/muj-ucet/profil"))
                .andExpect(flash().attributeExists("profileSuccess"));

        ArgumentCaptor<ProfileUpdateDto> dtoCaptor = ArgumentCaptor.forClass(ProfileUpdateDto.class);
        // Ověření volání metody v CustomerService
        verify(customerService).updateProfile(eq(MOCK_USER_EMAIL), dtoCaptor.capture());
        // Ověření dat předaných do service
        assertEquals(profileDto.getFirstName(), dtoCaptor.getValue().getFirstName());
        assertEquals(profileDto.getPhone(), dtoCaptor.getValue().getPhone());
    }

    @Test
    @DisplayName("POST /muj-ucet/profil - Chyba validace vrátí formulář")
    void updateProfile_ValidationError_ShouldReturnForm() throws Exception {
        // Mock, že getCustomerByEmail vrátí zákazníka (bude voláno v error handlingu controlleru)
        when(customerService.getCustomerByEmail(MOCK_USER_EMAIL)).thenReturn(Optional.of(loggedInCustomer));

        mockMvc.perform(MockMvcRequestBuilders.post("/muj-ucet/profil")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("firstName", "") // Prázdné jméno -> chyba
                                .param("lastName", "User")
                                .param("phone", "invalid-phone-format") // Nevalidní telefon
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().isOk())
                .andExpect(view().name("muj-ucet/profil"))
                .andExpect(model().attributeExists("profile", "customerEmail"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("profile", "firstName", "phone"));

        // Ověříme, že update se NEVOLAL
        verify(customerService, never()).updateProfile(anyString(), any());
        // Ověříme, že se zákazník načetl pro zobrazení formuláře (oprava chyby "Wanted but not invoked")
        verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL);
    }


    // --- Testy Změny Hesla ---

    @Test
    @DisplayName("GET /muj-ucet/zmena-hesla - Zobrazí formulář pro změnu hesla")
    void showChangePasswordForm_ShouldReturnView() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/muj-ucet/zmena-hesla"))
                .andExpect(status().isOk())
                .andExpect(view().name("muj-ucet/zmena-hesla"))
                .andExpect(model().attributeExists("passwordChange")) // <-- Opraveno z expected null
                .andExpect(model().attribute("passwordChange", isA(ChangePasswordDto.class))); // <-- Opraveno
    }

    @Test
    @DisplayName("POST /muj-ucet/zmena-hesla - Úspěšná změna hesla")
    void processChangePassword_Success_ShouldRedirect() throws Exception {
        // Předpoklad: changePassword je void
        doNothing().when(customerService).changePassword(eq(loggedInCustomer.getId()), any(ChangePasswordDto.class));

        mockMvc.perform(MockMvcRequestBuilders.post("/muj-ucet/zmena-hesla")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("currentPassword", passwordDto.getCurrentPassword())
                                .param("newPassword", passwordDto.getNewPassword())
                                .param("confirmNewPassword", passwordDto.getConfirmNewPassword())
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/muj-ucet/profil"))
                .andExpect(flash().attributeExists("passwordSuccess"));

        ArgumentCaptor<ChangePasswordDto> dtoCaptor = ArgumentCaptor.forClass(ChangePasswordDto.class);
        verify(customerService).changePassword(eq(loggedInCustomer.getId()), dtoCaptor.capture());
        assertEquals(passwordDto.getNewPassword(), dtoCaptor.getValue().getNewPassword());
    }

    @Test
    @DisplayName("POST /muj-ucet/zmena-hesla - Chyba (neshodující se hesla)")
    void processChangePassword_PasswordsMismatch_ShouldReturnForm() throws Exception {
        // Mock pro načtení zákazníka v chybové cestě
        when(customerService.getCustomerByEmail(MOCK_USER_EMAIL)).thenReturn(Optional.of(loggedInCustomer));

        mockMvc.perform(MockMvcRequestBuilders.post("/muj-ucet/zmena-hesla")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("currentPassword", "oldPassword")
                                .param("newPassword", "newPassword123")
                                .param("confirmNewPassword", "differentPassword456") // Neshoduje se
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().isOk())
                .andExpect(view().name("muj-ucet/zmena-hesla"))
                .andExpect(model().attributeExists("passwordChange"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("passwordChange", "confirmNewPassword")); // Očekáváme chybu zde

        verify(customerService, never()).changePassword(anyLong(), any());
        // Ověření, že se zákazník načetl pro formulář
        verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL);
    }

    @Test
    @DisplayName("POST /muj-ucet/zmena-hesla - Chyba (nesprávné staré heslo)")
    void processChangePassword_IncorrectOldPassword_ShouldReturnForm() throws Exception {
        String errorMessage = "Nesprávné staré heslo.";
        // Mock, že service metoda hodí výjimku
        doThrow(new IllegalArgumentException(errorMessage))
                .when(customerService).changePassword(eq(loggedInCustomer.getId()), any(ChangePasswordDto.class));
        // Mock pro načtení zákazníka v chybové cestě
        when(customerService.getCustomerByEmail(MOCK_USER_EMAIL)).thenReturn(Optional.of(loggedInCustomer));

        mockMvc.perform(MockMvcRequestBuilders.post("/muj-ucet/zmena-hesla")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("currentPassword", "wrongOldPassword") // Špatné staré heslo
                                .param("newPassword", passwordDto.getNewPassword())
                                .param("confirmNewPassword", passwordDto.getConfirmNewPassword())
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().isOk())
                .andExpect(view().name("muj-ucet/zmena-hesla"))
                .andExpect(model().attributeExists("passwordChange"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("passwordChange", "currentPassword")) // Chyba u starého hesla
                .andExpect(model().attributeHasFieldErrorCode("passwordChange", "currentPassword", "error.passwordChange")); // Ověření kódu chyby

        verify(customerService).changePassword(eq(loggedInCustomer.getId()), any(ChangePasswordDto.class));
        // Ověření, že se zákazník načetl pro formulář
        verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL);
    }


    // --- Testy Objednávek ---

    @Test
    @DisplayName("GET /muj-ucet/objednavky - Zobrazí seznam objednávek")
    void viewOrders_ShouldReturnOrdersView() throws Exception {
        when(orderService.findAllOrdersByCustomerId(loggedInCustomer.getId())).thenReturn(List.of(customerOrder));

        mockMvc.perform(MockMvcRequestBuilders.get("/muj-ucet/objednavky"))
                .andExpect(status().isOk())
                .andExpect(view().name("muj-ucet/objednavky"))
                .andExpect(model().attributeExists("orders"))
                .andExpect(model().attribute("orders", hasSize(1)))
                .andExpect(model().attribute("orders", hasItem(
                        hasProperty("orderCode", is(customerOrder.getOrderCode()))
                )));

        verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL);
        verify(orderService).findAllOrdersByCustomerId(loggedInCustomer.getId());
    }

    @Test
    @DisplayName("GET /muj-ucet/objednavky/{orderId} - Zobrazí detail objednávky")
    void viewOrderDetail_Success_ShouldReturnDetailView() throws Exception {
        Long orderId = customerOrder.getId();
        // Zajistíme, že mockovaná objednávka má couponDiscountAmount (viz setUp)
        when(orderService.findOrderById(orderId)).thenReturn(Optional.of(customerOrder));

        mockMvc.perform(MockMvcRequestBuilders.get("/muj-ucet/objednavky/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(view().name("muj-ucet/objednavka-detail"))
                .andExpect(model().attributeExists("order"))
                .andExpect(model().attribute("order", hasProperty("id", is(orderId))));

        verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL);
        verify(orderService).findOrderById(orderId);
    }

    @Test
    @DisplayName("GET /muj-ucet/objednavky/{orderId} - Přístup zamítnut (objednávka jiného uživatele)")
    void viewOrderDetail_AccessDenied_ShouldRedirect() throws Exception {
        Long orderId = 20L;
        Customer otherCustomer = new Customer(); otherCustomer.setId(99L);
        Order otherOrder = new Order();
        otherOrder.setId(orderId);
        otherOrder.setOrderCode("OTHER-ORD");
        otherOrder.setCustomer(otherCustomer); // Jiný zákazník

        when(orderService.findOrderById(orderId)).thenReturn(Optional.of(otherOrder));
        // getCustomerByEmail je mockováno v setUp() pro přihlášeného uživatele

        mockMvc.perform(MockMvcRequestBuilders.get("/muj-ucet/objednavky/{orderId}", orderId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/muj-ucet/objednavky"))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL);
        verify(orderService).findOrderById(orderId);
    }

    // --- Testy Adres ---

    @Test
    @DisplayName("GET /muj-ucet/adresy - Zobrazí formuláře adres")
    void viewAddresses_ShouldReturnAddressesView() throws Exception {
        // getCustomerByEmail je mockováno v setUp()
        mockMvc.perform(MockMvcRequestBuilders.get("/muj-ucet/adresy"))
                .andExpect(status().isOk())
                .andExpect(view().name("muj-ucet/adresy"))
                .andExpect(model().attributeExists("customer", "invoiceAddress", "deliveryAddress"))
                .andExpect(model().attribute("invoiceAddress", isA(AddressDto.class))) // <-- Opraveno
                .andExpect(model().attribute("invoiceAddress", hasProperty("street", is(loggedInCustomer.getInvoiceStreet()))));

        verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL);
    }

    @Test
    @DisplayName("POST /muj-ucet/adresy/fakturacni - Úspěšná aktualizace")
    void updateInvoiceAddress_Success_ShouldRedirect() throws Exception {
        // Nahrazení doNothing() za when().thenReturn()
        when(customerService.updateAddress(eq(loggedInCustomer.getId()), eq(CustomerService.AddressType.INVOICE), any(AddressDto.class)))
                .thenReturn(loggedInCustomer); // Vracíme mockovaného zákazníka

        mockMvc.perform(MockMvcRequestBuilders.post("/muj-ucet/adresy/fakturacni")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("street", addressDto.getStreet())
                                .param("city", addressDto.getCity())
                                .param("zipCode", addressDto.getZipCode())
                                .param("country", addressDto.getCountry())
                                .param("firstName", addressDto.getFirstName()) // Potřebné pro validaci
                                .param("lastName", addressDto.getLastName())
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/muj-ucet/adresy"))
                .andExpect(flash().attributeExists("addressSuccess"));

        ArgumentCaptor<AddressDto> dtoCaptor = ArgumentCaptor.forClass(AddressDto.class);
        verify(customerService).updateAddress(eq(loggedInCustomer.getId()), eq(CustomerService.AddressType.INVOICE), dtoCaptor.capture());
        assertEquals(addressDto.getStreet(), dtoCaptor.getValue().getStreet());
    }

    @Test
    @DisplayName("POST /muj-ucet/adresy/dodaci - Úspěšná aktualizace")
    void updateDeliveryAddress_Success_ShouldRedirect() throws Exception {
        // Nahrazení doNothing() za when().thenReturn()
        when(customerService.updateAddress(eq(loggedInCustomer.getId()), eq(CustomerService.AddressType.DELIVERY), any(AddressDto.class)))
                .thenReturn(loggedInCustomer); // Vracíme mockovaného zákazníka

        mockMvc.perform(MockMvcRequestBuilders.post("/muj-ucet/adresy/dodaci")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("street", addressDto.getStreet())
                                .param("city", addressDto.getCity())
                                .param("zipCode", addressDto.getZipCode())
                                .param("country", addressDto.getCountry())
                                .param("firstName", addressDto.getFirstName()) // Potřebné pro validaci
                                .param("lastName", addressDto.getLastName())
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/muj-ucet/adresy"))
                .andExpect(flash().attributeExists("addressSuccess"));

        ArgumentCaptor<AddressDto> dtoCaptor = ArgumentCaptor.forClass(AddressDto.class);
        verify(customerService).updateAddress(eq(loggedInCustomer.getId()), eq(CustomerService.AddressType.DELIVERY), dtoCaptor.capture());
        assertEquals(addressDto.getCity(), dtoCaptor.getValue().getCity());
    }


    @Test
    @DisplayName("POST /muj-ucet/adresy/fakturacni - Chyba validace vrátí formulář")
    void updateInvoiceAddress_ValidationError_ShouldReturnForm() throws Exception {
        // Mock pro načtení zákazníka v chybové cestě
        when(customerService.getCustomerByEmail(MOCK_USER_EMAIL)).thenReturn(Optional.of(loggedInCustomer));

        mockMvc.perform(MockMvcRequestBuilders.post("/muj-ucet/adresy/fakturacni")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("street", "") // Prázdná ulice -> chyba
                                .param("city", "City")
                                .param("zipCode", "12345")
                                .param("country", "CR")
                                .param("firstName", "Test") // Nutné pro validaci hasRecipient
                                .param("lastName", "User")
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().isOk())
                .andExpect(view().name("muj-ucet/adresy"))
                .andExpect(model().attributeExists("customer", "invoiceAddress", "deliveryAddress"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("invoiceAddress", "street"))
                .andExpect(model().attributeExists("invoiceAddressError")); // Obecná chyba pro sekci

        verify(customerService, never()).updateAddress(anyLong(), any(), any());
        verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL); // Ověření načtení pro formulář
    }


    @Test
    @DisplayName("POST /muj-ucet/adresy/prepnout-dodaci - Nastaví na false")
    void setUseInvoiceAddressAsDelivery_SetFalse_ShouldRedirect() throws Exception {
        // Nahrazení doNothing() za when().thenReturn()
        when(customerService.setUseInvoiceAddressAsDelivery(loggedInCustomer.getId(), false))
                .thenReturn(loggedInCustomer); // Vracíme mockovaného zákazníka

        mockMvc.perform(MockMvcRequestBuilders.post("/muj-ucet/adresy/prepnout-dodaci")
                        // Parametr 'useInvoiceAddress' NENÍ poslán, když je checkbox odškrtnutý
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/muj-ucet/adresy"))
                .andExpect(flash().attributeExists("addressSuccess"));

        verify(customerService).setUseInvoiceAddressAsDelivery(loggedInCustomer.getId(), false);
    }

    @Test
    @DisplayName("POST /muj-ucet/adresy/prepnout-dodaci - Nastaví na true")
    void setUseInvoiceAddressAsDelivery_SetTrue_ShouldRedirect() throws Exception {
        // Nahrazení doNothing() za when().thenReturn()
        when(customerService.setUseInvoiceAddressAsDelivery(loggedInCustomer.getId(), true))
                .thenReturn(loggedInCustomer); // Vracíme mockovaného zákazníka

        mockMvc.perform(MockMvcRequestBuilders.post("/muj-ucet/adresy/prepnout-dodaci")
                                .param("useInvoiceAddress", "true") // Parametr JE poslán, když je zaškrtnutý
                        // .with(csrf()) // Odstraněno
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/muj-ucet/adresy"))
                .andExpect(flash().attributeExists("addressSuccess"));

        verify(customerService).setUseInvoiceAddressAsDelivery(loggedInCustomer.getId(), true);
    }

}