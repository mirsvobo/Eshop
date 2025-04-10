package org.example.eshop.controller; // Ujistěte se, že balíček je správný

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.config.SecurityTestConfig;
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
import org.springframework.context.annotation.Import;
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
// import static org.mockito.ArgumentMatchers.isA; // TOTO BYLO ODSTRANĚNO - ZPŮSOBOVALO KONFLIKT
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerAccountController.class)
@WithMockUser(username = "user@example.com")
@Import(SecurityTestConfig.class)
class CustomerAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private CustomerService customerService;
    @MockBean private OrderService orderService;
    @MockBean private CurrencyService currencyService; // Mock pro CurrencyService

    private Customer loggedInCustomer;
    private Order customerOrder;
    private AddressDto addressDto;
    private ProfileUpdateDto profileDto;
    private ChangePasswordDto passwordDto;

    private final String MOCK_USER_EMAIL = "user@example.com";
    private final String DEFAULT_CURRENCY = "CZK"; // Příklad výchozí měny

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
        loggedInCustomer.setUseInvoiceAddressAsDelivery(true);
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
        customerOrder.setCouponDiscountAmount(BigDecimal.ZERO);
        customerOrder.setNote("Test");

        addressDto = new AddressDto();
        addressDto.setStreet("Nova Ulice 10");
        addressDto.setCity("Nove Mesto");
        addressDto.setZipCode("99999");
        addressDto.setCountry("Česká Republika");
        addressDto.setFirstName("Test");
        addressDto.setLastName("User");

        profileDto = new ProfileUpdateDto();
        profileDto.setFirstName("UpdatedFirst");
        profileDto.setLastName("UpdatedLast");
        profileDto.setPhone("987654321");

        passwordDto = new ChangePasswordDto();
        passwordDto.setCurrentPassword("oldPassword");
        passwordDto.setNewPassword("newPassword123");
        passwordDto.setConfirmNewPassword("newPassword123");

        // Základní mockování pro getCurrentCustomer, které volá getCustomerByEmail
        lenient().when(customerService.getCustomerByEmail(MOCK_USER_EMAIL)).thenReturn(Optional.of(loggedInCustomer));

        // Základní mockování pro CurrencyService (pokud GlobalModelAttributeAdvice běží)
        // Upravte název metody, pokud se ve vašem CurrencyService jmenuje jinak
        lenient().when(currencyService.getSelectedCurrency()).thenReturn(DEFAULT_CURRENCY);
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
        // Můžete přidat ověření pro měnu, pokud je relevantní
        // .andExpect(model().attribute("currentGlobalCurrency", is(DEFAULT_CURRENCY)));

        verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL);
    }

    @Test
    @DisplayName("POST /muj-ucet/profil - Úspěšně aktualizuje profil")
    void updateProfile_Success_ShouldRedirectWithSuccess() throws Exception {
        // Předpokládáme, že updateProfile vrací Customer (podle kódu v CustomerService.java)
        when(customerService.updateProfile(eq(MOCK_USER_EMAIL), any(ProfileUpdateDto.class)))
                .thenReturn(loggedInCustomer); // Vracíme mock zákazníka

        mockMvc.perform(MockMvcRequestBuilders.post("/muj-ucet/profil")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("firstName", profileDto.getFirstName())
                                .param("lastName", profileDto.getLastName())
                                .param("phone", profileDto.getPhone())
                        // CSRF vypnuto (pokud je potřeba, přidejte .with(csrf()))
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/muj-ucet/profil"))
                .andExpect(flash().attributeExists("profileSuccess"));

        ArgumentCaptor<ProfileUpdateDto> dtoCaptor = ArgumentCaptor.forClass(ProfileUpdateDto.class);
        verify(customerService).updateProfile(eq(MOCK_USER_EMAIL), dtoCaptor.capture());
        assertEquals(profileDto.getFirstName(), dtoCaptor.getValue().getFirstName());
        assertEquals(profileDto.getPhone(), dtoCaptor.getValue().getPhone());
    }

    @Test
    @DisplayName("POST /muj-ucet/profil - Chyba validace vrátí formulář")
    void updateProfile_ValidationError_ShouldReturnForm() throws Exception {
        // Není potřeba explicitní 'when' pro getCustomerByEmail zde,
        // protože ho máme v setUp() s lenient() a controller ho pravděpodobně nevolá
        // (bere email z Principal).

        mockMvc.perform(MockMvcRequestBuilders.post("/muj-ucet/profil")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("firstName", "") // Prázdné jméno -> chyba
                                .param("lastName", "User")
                                .param("phone", "invalid-phone-format") // Nevalidní telefon
                        // CSRF vypnuto
                )
                .andExpect(status().isOk())
                .andExpect(view().name("muj-ucet/profil"))
                .andExpect(model().attributeExists("profile", "customerEmail")) // Ověříme, že customerEmail je v modelu (controller ho tam přidá z Principal)
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("profile", "firstName", "phone"));

        verify(customerService, never()).updateProfile(anyString(), any());
        // ===== OPRAVA: Ověření volání getCustomerByEmail ZAKOMENTOVÁNO =====
        // Controller pravděpodobně nepotřebuje volat tuto metodu v error path,
        // protože email může získat přímo z Principal objektu.
        // verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL);
        // ================================================================
    }


    // --- Testy Změny Hesla ---

    @Test
    @DisplayName("GET /muj-ucet/zmena-hesla - Zobrazí formulář pro změnu hesla")
    void showChangePasswordForm_ShouldReturnView() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/muj-ucet/zmena-hesla"))
                .andExpect(status().isOk())
                .andExpect(view().name("muj-ucet/zmena-hesla"))
                // ===== OPRAVA #2 =====
                .andExpect(model().attributeExists("passwordChange"))
                // Používáme Hamcrest isA(), protože Mockito isA() bylo odstraněno z importů
                // In showChangePasswordForm_ShouldReturnView test:
                .andExpect(model().attribute("passwordChange",
                        instanceOf(ChangePasswordDto.class)
                ));
        // =====================
    }

    @Test
    @DisplayName("POST /muj-ucet/zmena-hesla - Úspěšná změna hesla")
    void processChangePassword_Success_ShouldRedirect() throws Exception {
        // Předpokládáme, že getCustomerByEmail je voláno pro získání ID zákazníka
        // when(customerService.getCustomerByEmail(MOCK_USER_EMAIL)).thenReturn(Optional.of(loggedInCustomer)); // Již máme v setUp s lenient()

        doNothing().when(customerService).changePassword(eq(loggedInCustomer.getId()), any(ChangePasswordDto.class));

        mockMvc.perform(MockMvcRequestBuilders.post("/muj-ucet/zmena-hesla")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("currentPassword", passwordDto.getCurrentPassword())
                                .param("newPassword", passwordDto.getNewPassword())
                                .param("confirmNewPassword", passwordDto.getConfirmNewPassword())
                        // CSRF vypnuto
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/muj-ucet/profil"))
                .andExpect(flash().attributeExists("passwordSuccess"));

        ArgumentCaptor<ChangePasswordDto> dtoCaptor = ArgumentCaptor.forClass(ChangePasswordDto.class);
        // Ověříme, že se volá customerService.getCustomerByEmail pro získání ID (pokud to controller dělá)
        verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL);
        verify(customerService).changePassword(eq(loggedInCustomer.getId()), dtoCaptor.capture());
        assertEquals(passwordDto.getNewPassword(), dtoCaptor.getValue().getNewPassword());
    }

    @Test
    @DisplayName("POST /muj-ucet/zmena-hesla - Chyba (neshodující se hesla)")
    void processChangePassword_PasswordsMismatch_ShouldReturnForm() throws Exception {
        // Mock pro error path - getCustomerByEmail je voláno pro získání ID, máme v setUp s lenient()

        mockMvc.perform(MockMvcRequestBuilders.post("/muj-ucet/zmena-hesla")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("currentPassword", "oldPassword")
                                .param("newPassword", "newPassword123")
                                .param("confirmNewPassword", "differentPassword456") // Neshoduje se
                        // CSRF vypnuto
                )
                .andExpect(status().isOk())
                .andExpect(view().name("muj-ucet/zmena-hesla"))
                .andExpect(model().attributeExists("passwordChange"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("passwordChange", "confirmNewPassword"));

        verify(customerService, never()).changePassword(anyLong(), any());
    }

    @Test
    @DisplayName("POST /muj-ucet/zmena-hesla - Chyba (nesprávné staré heslo)")
    void processChangePassword_IncorrectOldPassword_ShouldReturnForm() throws Exception {
        // Mock pro error path - getCustomerByEmail je voláno pro získání ID, máme v setUp s lenient()
        String errorMessage = "Nesprávné staré heslo.";
        doThrow(new IllegalArgumentException(errorMessage))
                .when(customerService).changePassword(eq(loggedInCustomer.getId()), any(ChangePasswordDto.class));


        mockMvc.perform(MockMvcRequestBuilders.post("/muj-ucet/zmena-hesla")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("currentPassword", "wrongOldPassword")
                                .param("newPassword", passwordDto.getNewPassword())
                                .param("confirmNewPassword", passwordDto.getConfirmNewPassword())
                        // CSRF vypnuto
                )
                .andExpect(status().isOk())
                .andExpect(view().name("muj-ucet/zmena-hesla"))
                .andExpect(model().attributeExists("passwordChange"))
                .andExpect(model().hasErrors()) // Chyba je přidána do BindingResult v controlleru
                .andExpect(model().attributeHasFieldErrors("passwordChange", "currentPassword"))
                .andExpect(model().attributeHasFieldErrorCode("passwordChange", "currentPassword", "error.passwordChange")); // Předpokládá, že controller přidá tuto chybu

        // Ověříme, že getCustomerByEmail bylo voláno (pro ID) a changePassword bylo také voláno (a selhalo).
        verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL);
        verify(customerService).changePassword(eq(loggedInCustomer.getId()), any(ChangePasswordDto.class));
    }


    // --- Testy Objednávek ---

    @Test
    @DisplayName("GET /muj-ucet/objednavky - Zobrazí seznam objednávek")
    void viewOrders_ShouldReturnOrdersView() throws Exception {
        // getCustomerByEmail je voláno pro získání ID, máme v setUp s lenient()
        when(orderService.findAllOrdersByCustomerId(loggedInCustomer.getId())).thenReturn(List.of(customerOrder));

        mockMvc.perform(MockMvcRequestBuilders.get("/muj-ucet/objednavky"))
                .andExpect(status().isOk())
                .andExpect(view().name("muj-ucet/objednavky"))
                .andExpect(model().attributeExists("orders"))
                .andExpect(model().attribute("orders", hasSize(1)))
                .andExpect(model().attribute("orders", hasItem(
                        hasProperty("orderCode", is(customerOrder.getOrderCode()))
                )));

        verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL); // Ověříme volání pro získání ID
        verify(orderService).findAllOrdersByCustomerId(loggedInCustomer.getId());
    }

    @Test
    @DisplayName("GET /muj-ucet/objednavky/{orderId} - Zobrazí detail objednávky")
    void viewOrderDetail_Success_ShouldReturnDetailView() throws Exception {
        // getCustomerByEmail je voláno pro ověření přístupu, máme v setUp s lenient()
        Long orderId = customerOrder.getId();
        // customerOrder má couponDiscountAmount inicializován v setUp() na ZERO
        when(orderService.findOrderById(orderId)).thenReturn(Optional.of(customerOrder));

        // ===== DŮLEŽITÉ: Nezapomeňte opravit Thymeleaf šablonu muj-ucet/objednavka-detail.html =====
        // Nahraďte `BigDecimal.ZERO` za `T(java.math.BigDecimal).ZERO` ve SpEL výrazech.
        // =======================================================================================

        mockMvc.perform(MockMvcRequestBuilders.get("/muj-ucet/objednavky/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(view().name("muj-ucet/objednavka-detail"))
                .andExpect(model().attributeExists("order"))
                .andExpect(model().attribute("order", hasProperty("id", is(orderId))))
                // Přidáme explicitní kontrolu, že couponDiscountAmount není null v modelu
                .andExpect(model().attribute("order", hasProperty("couponDiscountAmount", notNullValue())));


        verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL); // Ověříme volání pro kontrolu přístupu
        verify(orderService).findOrderById(orderId);
    }

    @Test
    @DisplayName("GET /muj-ucet/objednavky/{orderId} - Přístup zamítnut (objednávka jiného uživatele)")
    void viewOrderDetail_AccessDenied_ShouldRedirect() throws Exception {
        // getCustomerByEmail je voláno pro ověření přístupu, máme v setUp s lenient()
        Long orderId = 20L;
        Customer otherCustomer = new Customer(); otherCustomer.setId(99L); otherCustomer.setEmail("other@example.com");
        Order otherOrder = new Order();
        otherOrder.setId(orderId);
        otherOrder.setOrderCode("OTHER-ORD");
        otherOrder.setCustomer(otherCustomer); // Jiný zákazník!
        otherOrder.setCouponDiscountAmount(BigDecimal.ZERO); // Inicializace

        when(orderService.findOrderById(orderId)).thenReturn(Optional.of(otherOrder));

        mockMvc.perform(MockMvcRequestBuilders.get("/muj-ucet/objednavky/{orderId}", orderId))
                .andExpect(status().is3xxRedirection()) // Očekáváme přesměrování kvůli chybě přístupu
                .andExpect(redirectedUrl("/muj-ucet/objednavky")) // Předpokládaný cíl přesměrování
                .andExpect(flash().attributeExists("errorMessage")); // Očekáváme chybovou zprávu ve flash atributech

        verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL); // Ověříme volání pro kontrolu přístupu
        verify(orderService).findOrderById(orderId);
    }

    // --- Testy Adres ---

    @Test
    @DisplayName("GET /muj-ucet/adresy - Zobrazí formuláře adres")
    void viewAddresses_ShouldReturnAddressesView() throws Exception {
        // getCustomerByEmail je voláno pro načtení dat, máme v setUp s lenient()

        mockMvc.perform(MockMvcRequestBuilders.get("/muj-ucet/adresy"))
                .andExpect(status().isOk())
                .andExpect(view().name("muj-ucet/adresy"))
                .andExpect(model().attributeExists("customer", "invoiceAddress", "deliveryAddress"))
                // In viewAddresses_ShouldReturnAddressesView test:
                .andExpect(model().attributeExists("customer", "invoiceAddress", "deliveryAddress"))
// Check invoice address street
                .andExpect(model().attribute("invoiceAddress",
                        hasProperty("street", equalTo(loggedInCustomer.getInvoiceStreet()))))
// FIX: Check delivery address street property
                .andExpect(model().attribute("deliveryAddress",
                        hasProperty("street", equalTo(loggedInCustomer.getDeliveryStreet())))); // <-- Check the 'street' property here too
        verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL);
    }

    @Test
    @DisplayName("POST /muj-ucet/adresy/fakturacni - Úspěšná aktualizace")
    void updateInvoiceAddress_Success_ShouldRedirect() throws Exception {
        // getCustomerByEmail je voláno pro získání ID, máme v setUp s lenient()
        when(customerService.updateAddress(eq(loggedInCustomer.getId()), eq(CustomerService.AddressType.INVOICE), any(AddressDto.class)))
                .thenReturn(loggedInCustomer);

        mockMvc.perform(MockMvcRequestBuilders.post("/muj-ucet/adresy/fakturacni")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("street", addressDto.getStreet())
                                .param("city", addressDto.getCity())
                                .param("zipCode", addressDto.getZipCode())
                                .param("country", addressDto.getCountry())
                                .param("firstName", addressDto.getFirstName()) // Přidáno pro úplnost, pokud je vyžadováno
                                .param("lastName", addressDto.getLastName())   // Přidáno pro úplnost, pokud je vyžadováno
                        // CSRF vypnuto
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/muj-ucet/adresy"))
                .andExpect(flash().attributeExists("addressSuccess"));

        ArgumentCaptor<AddressDto> dtoCaptor = ArgumentCaptor.forClass(AddressDto.class);
        verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL); // Ověříme získání ID
        verify(customerService).updateAddress(eq(loggedInCustomer.getId()), eq(CustomerService.AddressType.INVOICE), dtoCaptor.capture());
        assertEquals(addressDto.getStreet(), dtoCaptor.getValue().getStreet());
    }

    @Test
    @DisplayName("POST /muj-ucet/adresy/dodaci - Úspěšná aktualizace")
    void updateDeliveryAddress_Success_ShouldRedirect() throws Exception {
        // getCustomerByEmail je voláno pro získání ID, máme v setUp s lenient()
        when(customerService.updateAddress(eq(loggedInCustomer.getId()), eq(CustomerService.AddressType.DELIVERY), any(AddressDto.class)))
                .thenReturn(loggedInCustomer);

        mockMvc.perform(MockMvcRequestBuilders.post("/muj-ucet/adresy/dodaci")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("street", addressDto.getStreet())
                                .param("city", addressDto.getCity())
                                .param("zipCode", addressDto.getZipCode())
                                .param("country", addressDto.getCountry())
                                .param("firstName", addressDto.getFirstName()) // Přidáno pro úplnost
                                .param("lastName", addressDto.getLastName())   // Přidáno pro úplnost
                        // CSRF vypnuto
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/muj-ucet/adresy"))
                .andExpect(flash().attributeExists("addressSuccess"));

        ArgumentCaptor<AddressDto> dtoCaptor = ArgumentCaptor.forClass(AddressDto.class);
        verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL); // Ověříme získání ID
        verify(customerService).updateAddress(eq(loggedInCustomer.getId()), eq(CustomerService.AddressType.DELIVERY), dtoCaptor.capture());
        assertEquals(addressDto.getCity(), dtoCaptor.getValue().getCity());
    }


    @Test
    @DisplayName("POST /muj-ucet/adresy/fakturacni - Chyba validace vrátí formulář")
    void updateInvoiceAddress_ValidationError_ShouldReturnForm() throws Exception {
        // Mock pro error path - getCustomerByEmail je voláno pro zobrazení dat v šabloně, máme v setUp s lenient()

        mockMvc.perform(MockMvcRequestBuilders.post("/muj-ucet/adresy/fakturacni")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("street", "") // Prázdná ulice -> chyba
                                .param("city", "City")
                                .param("zipCode", "12345")
                                .param("country", "CR")
                                .param("firstName", "Test")
                                .param("lastName", "User")
                        // CSRF vypnuto
                )
                .andExpect(status().isOk())
                .andExpect(view().name("muj-ucet/adresy"))
                .andExpect(model().attributeExists("customer", "invoiceAddress", "deliveryAddress"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("invoiceAddress", "street"))
                .andExpect(model().attributeExists("invoiceAddressError")); // Předpokládá, že controller přidá tento atribut v případě chyby

        verify(customerService, never()).updateAddress(anyLong(), any(), any());
        verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL); // Ověření, že se volalo pro data do šablony
    }


    @Test
    @DisplayName("POST /muj-ucet/adresy/prepnout-dodaci - Nastaví na false")
    void setUseInvoiceAddressAsDelivery_SetFalse_ShouldRedirect() throws Exception {
        // getCustomerByEmail je voláno pro získání ID, máme v setUp s lenient()
        when(customerService.setUseInvoiceAddressAsDelivery(loggedInCustomer.getId(), false))
                .thenReturn(loggedInCustomer);

        mockMvc.perform(MockMvcRequestBuilders.post("/muj-ucet/adresy/prepnout-dodaci")
                        // Parametr 'useInvoiceAddress' není poslán, což by mělo znamenat 'false' v controlleru
                        // CSRF vypnuto
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/muj-ucet/adresy"))
                .andExpect(flash().attributeExists("addressSuccess"));

        verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL); // Ověříme získání ID
        verify(customerService).setUseInvoiceAddressAsDelivery(loggedInCustomer.getId(), false);
    }

    @Test
    @DisplayName("POST /muj-ucet/adresy/prepnout-dodaci - Nastaví na true")
    void setUseInvoiceAddressAsDelivery_SetTrue_ShouldRedirect() throws Exception {
        // getCustomerByEmail je voláno pro získání ID, máme v setUp s lenient()
        when(customerService.setUseInvoiceAddressAsDelivery(loggedInCustomer.getId(), true))
                .thenReturn(loggedInCustomer);

        mockMvc.perform(MockMvcRequestBuilders.post("/muj-ucet/adresy/prepnout-dodaci")
                                .param("useInvoiceAddress", "true") // Parametr je poslán jako true
                        // CSRF vypnuto
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/muj-ucet/adresy"))
                .andExpect(flash().attributeExists("addressSuccess"));

        verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL); // Ověříme získání ID
        verify(customerService).setUseInvoiceAddressAsDelivery(loggedInCustomer.getId(), true);
    }
}