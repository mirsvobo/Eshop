// Soubor: src/test/java/org/example/eshop/controller/CheckoutControllerTest.java

package org.example.eshop.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.eshop.config.PriceConstants;
import org.example.eshop.config.SecurityTestConfig; // Import sdílené konfigurace
import org.example.eshop.dto.*;
import org.example.eshop.model.*;
import org.example.eshop.repository.*; // <-- Import pro repository
import org.example.eshop.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
// Odebráno: import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean; // Ignorujme varování o depreciaci
import org.springframework.context.annotation.Import; // Import pro @Import
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException; // Import pro ověření exception

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*; // Import Collections

// Import specific Hamcrest matchers
import static org.hamcrest.Matchers.*;
// Import specific Mockito methods and ArgumentMatchers
import static org.mockito.ArgumentMatchers.any;
// Odebráno: import static org.mockito.ArgumentMatchers.anyBoolean;
// Odebráno: import static org.mockito.ArgumentMatchers.anyList;
// Odebráno: import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
// import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf; // Odstraněno
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;


@WebMvcTest(CheckoutController.class)
@Import(SecurityTestConfig.class) // Aplikace sdílené konfigurace
class CheckoutControllerTest implements PriceConstants {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private Cart sessionCart;
    @MockBean private CustomerService customerService;
    @MockBean private OrderService orderService;
    @MockBean private CouponService couponService;
    @MockBean @Qualifier("googleMapsShippingService") private ShippingService shippingService;
    @MockBean private ProductService productService;
    @MockBean private CurrencyService currencyService;
    @MockBean private TaxRateService taxRateService;
    // Přidáno mockování TaxRateRepository, i když ho CheckoutController přímo nepoužívá,
    // může být potřeba pro jiné závislosti nebo budoucí rozšíření.
    @MockBean private TaxRateRepository taxRateRepository;

    // ===== @MockBean pro repository (potřeba pro setUp lenient mockování, pokud by je controller používal) =====
    @MockBean private DesignRepository designRepository;
    @MockBean private GlazeRepository glazeRepository;
    @MockBean private RoofColorRepository roofColorRepository;
    @MockBean private AddonsRepository addonsRepository;
    // ===== KONEC =====

    private Customer loggedInCustomer;
    private CheckoutFormDataDto checkoutFormDto;
    private CartItem testCartItem;
    private Coupon testCoupon;
    private Order createdOrder;
    // Přidáno pro inicializaci CartItem
    private TaxRate testTaxRate;

    private final String MOCK_USER_EMAIL = "user@checkout.com";
    private final String CURRENT_CURRENCY = "CZK";
    private final String CURRENCY_SYMBOL = "Kč";
    private final BigDecimal MOCK_SHIPPING_COST = new BigDecimal("120.00");
    private final BigDecimal MOCK_SHIPPING_TAX = new BigDecimal("25.20");
    private final BigDecimal MOCK_SHIPPING_RATE = new BigDecimal("0.21");
    private final BigDecimal MOCK_SUBTOTAL = new BigDecimal("500.00");
    private final BigDecimal MOCK_TOTAL_VAT = new BigDecimal("105.00"); // 500 * 0.21
    private final BigDecimal MOCK_TOTAL_PRICE_BEFORE_SHIPPING = MOCK_SUBTOTAL.add(MOCK_TOTAL_VAT); // 605.00
    // Celková cena: Subtotal + ItemsVAT + ShippingNoTax + ShippingTax (pro tento test, bez slevy)
    // 500.00 + 105.00 + 120.00 + 25.20 = 750.20
    private final BigDecimal MOCK_TOTAL_PRICE = new BigDecimal("750.20");


    @BeforeEach
    void setUp() {
        // OPRAVA: Inicializace TaxRate dle modelu
        testTaxRate = new TaxRate();
        testTaxRate.setId(1L);
        testTaxRate.setName("Standard 21%");
        testTaxRate.setRate(new BigDecimal("0.21"));
        testTaxRate.setReverseCharge(false);


        loggedInCustomer = new Customer();
        loggedInCustomer.setId(1L);
        loggedInCustomer.setEmail(MOCK_USER_EMAIL);
        loggedInCustomer.setFirstName("Checkout");
        loggedInCustomer.setLastName("User");
        loggedInCustomer.setInvoiceStreet("Fakt St 1");
        loggedInCustomer.setInvoiceCity("Fak City");
        loggedInCustomer.setInvoiceZipCode("11111");
        loggedInCustomer.setInvoiceCountry("ČR");
        loggedInCustomer.setUseInvoiceAddressAsDelivery(true);
        loggedInCustomer.setDeliveryStreet(loggedInCustomer.getInvoiceStreet());
        loggedInCustomer.setDeliveryCity(loggedInCustomer.getInvoiceCity());
        loggedInCustomer.setDeliveryZipCode(loggedInCustomer.getInvoiceZipCode());
        loggedInCustomer.setDeliveryCountry(loggedInCustomer.getInvoiceCountry());

        checkoutFormDto = new CheckoutFormDataDto();
        checkoutFormDto.setEmail("guest@checkout.com");
        checkoutFormDto.setFirstName("GuestFirst");
        checkoutFormDto.setLastName("GuestLast");
        checkoutFormDto.setPhone("111222333");
        checkoutFormDto.setInvoiceStreet("Guest St 1");
        checkoutFormDto.setInvoiceCity("Guest City");
        checkoutFormDto.setInvoiceZipCode("99999");
        checkoutFormDto.setInvoiceCountry("Česká republika");
        checkoutFormDto.setUseInvoiceAddressAsDelivery(true);
        checkoutFormDto.setPaymentMethod("BANK_TRANSFER");
        checkoutFormDto.setAgreeTerms(true);

        testCartItem = new CartItem();
        testCartItem.setProductId(100L);
        testCartItem.setQuantity(1);
        testCartItem.setUnitPriceCZK(MOCK_SUBTOTAL);
        testCartItem.setUnitPriceEUR(new BigDecimal("20.00")); // Přidána EUR cena pro konzistenci
        // OPRAVA: Nastavení nových polí pro DPH místo taxRatePercent
        testCartItem.setSelectedTaxRateId(testTaxRate.getId());
        testCartItem.setSelectedTaxRateValue(testTaxRate.getRate());
        testCartItem.setSelectedIsReverseCharge(testTaxRate.isReverseCharge());
        // Generování ID položky (předpokládáme jednoduchou variantu pro tento test)
        testCartItem.setCartItemId(CartItem.generateCartItemId(100L, false, null, null, null, null, null, null, null, testTaxRate.getId(), null, false, false, false, null));


        testCoupon = new Coupon();
        testCoupon.setId(1L);
        testCoupon.setCode("TESTC");
        testCoupon.setActive(true);
        testCoupon.setFreeShipping(false);

        createdOrder = new Order();
        createdOrder.setId(55L);
        createdOrder.setOrderCode("ORD-CREATED");
        createdOrder.setCustomer(loggedInCustomer);
        createdOrder.setTotalPrice(MOCK_TOTAL_PRICE);
        createdOrder.setCurrency(CURRENT_CURRENCY);

        // Základní mockování
        lenient().when(sessionCart.hasItems()).thenReturn(true);
        lenient().when(sessionCart.getItemsList()).thenReturn(List.of(testCartItem));
        lenient().when(sessionCart.calculateSubtotal(anyString())).thenReturn(MOCK_SUBTOTAL);
        lenient().when(sessionCart.getAppliedCoupon()).thenReturn(null);
        lenient().when(sessionCart.getAppliedCouponCode()).thenReturn(null);
        lenient().when(sessionCart.calculateDiscountAmount(anyString())).thenReturn(BigDecimal.ZERO);
        lenient().when(sessionCart.calculateTotalVatAmount(anyString())).thenReturn(MOCK_TOTAL_VAT);
        lenient().when(sessionCart.calculateVatBreakdown(anyString())).thenReturn(Map.of(new BigDecimal("0.21").setScale(2, RoundingMode.HALF_UP), MOCK_TOTAL_VAT));
        lenient().when(sessionCart.calculateTotalPriceBeforeShipping(anyString())).thenReturn(MOCK_TOTAL_PRICE_BEFORE_SHIPPING);
        lenient().doNothing().when(sessionCart).clearCart();

        lenient().when(currencyService.getSelectedCurrency()).thenReturn(CURRENT_CURRENCY);
        lenient().when(shippingService.calculateShippingCost(any(Order.class), eq(CURRENT_CURRENCY))).thenReturn(MOCK_SHIPPING_COST);
        lenient().when(shippingService.getShippingTaxRate()).thenReturn(MOCK_SHIPPING_RATE);
        lenient().when(customerService.getCustomerByEmail(MOCK_USER_EMAIL)).thenReturn(Optional.of(loggedInCustomer));
        lenient().when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(createdOrder);
        lenient().when(couponService.findByCode(isNull())).thenReturn(Optional.empty());
        lenient().when(couponService.isCouponGenerallyValid(any())).thenReturn(true);
        lenient().when(couponService.checkMinimumOrderValue(any(), any(), anyString())).thenReturn(true);
        lenient().when(couponService.checkCustomerUsageLimit(any(), any())).thenReturn(true);

        // Lenient mockování pro findAllById s prázdným SETEM (pokud by bylo potřeba)
        lenient().when(designRepository.findAllById(eq(Collections.emptySet()))).thenReturn(Collections.emptyList());
        lenient().when(glazeRepository.findAllById(eq(Collections.emptySet()))).thenReturn(Collections.emptyList());
        lenient().when(roofColorRepository.findAllById(eq(Collections.emptySet()))).thenReturn(Collections.emptyList());
        lenient().when(addonsRepository.findAllById(eq(Collections.emptySet()))).thenReturn(Collections.emptyList());
    }

    // --- Testy GET /pokladna ---

    @Test
    @DisplayName("GET /pokladna - Zobrazí stránku pro přihlášeného uživatele")
    @WithMockUser(username = MOCK_USER_EMAIL)
    void showCheckoutPage_LoggedInUser_ShouldShowPage() throws Exception {
        when(sessionCart.calculateSubtotal(CURRENT_CURRENCY)).thenReturn(MOCK_SUBTOTAL);
        when(sessionCart.getAppliedCouponCode()).thenReturn(null);
        when(sessionCart.calculateTotalVatAmount(CURRENT_CURRENCY)).thenReturn(MOCK_TOTAL_VAT);
        when(sessionCart.calculateVatBreakdown(CURRENT_CURRENCY)).thenReturn(Map.of(new BigDecimal("0.21").setScale(2), MOCK_TOTAL_VAT));
        when(sessionCart.calculateTotalPriceBeforeShipping(CURRENT_CURRENCY)).thenReturn(MOCK_TOTAL_PRICE_BEFORE_SHIPPING);

        // Mock pro getShippingTaxRate() pro initial výpočet v controlleru
        when(shippingService.getShippingTaxRate()).thenReturn(MOCK_SHIPPING_RATE);

        mockMvc.perform(MockMvcRequestBuilders.get("/pokladna"))
                .andExpect(status().isOk())
                .andExpect(view().name("pokladna"))
                .andExpect(model().attributeExists("checkoutForm", "customer", "cart", "allowedPaymentMethods", "currency", "currencySymbol"))
                .andExpect(model().attribute("customer", notNullValue()))
                .andExpect(model().attribute("customer", hasProperty("email", is(MOCK_USER_EMAIL))))
                .andExpect(model().attribute("checkoutForm", hasProperty("email", is(MOCK_USER_EMAIL))))
                // Ověření cen v modelu
                .andExpect(model().attribute("subtotal", comparesEqualTo(MOCK_SUBTOTAL)))
                .andExpect(model().attribute("totalVat", comparesEqualTo(MOCK_TOTAL_VAT)))
                .andExpect(model().attribute("shippingCostNoTax", comparesEqualTo(MOCK_SHIPPING_COST)))
                .andExpect(model().attribute("shippingTax", comparesEqualTo(MOCK_SHIPPING_TAX)))
                .andExpect(model().attribute("totalPrice", comparesEqualTo(MOCK_TOTAL_PRICE.setScale(0, RoundingMode.DOWN)))) // Ověřujeme zaokrouhlenou cenu
                .andExpect(model().attribute("roundingDifference", comparesEqualTo(MOCK_TOTAL_PRICE.subtract(MOCK_TOTAL_PRICE.setScale(0, RoundingMode.DOWN)).setScale(PRICE_SCALE, RoundingMode.HALF_UP)))); // Ověřujeme rozdíl


        verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL);
        verify(currencyService, times(2)).getSelectedCurrency();
        verify(shippingService).calculateShippingCost(any(Order.class), eq(CURRENT_CURRENCY));
        verify(shippingService, atLeastOnce()).getShippingTaxRate(); // Ověření volání sazby DPH dopravy
        verify(sessionCart).calculateSubtotal(CURRENT_CURRENCY);
        verify(sessionCart, atLeastOnce()).getAppliedCouponCode();
        verify(sessionCart).calculateTotalVatAmount(CURRENT_CURRENCY);
        verify(sessionCart, atLeastOnce()).calculateVatBreakdown(CURRENT_CURRENCY);
        verify(sessionCart).calculateTotalPriceBeforeShipping(CURRENT_CURRENCY);
    }

    @Test
    @DisplayName("GET /pokladna - Zobrazí stránku pro nepřihlášeného uživatele (hosta)")
    void showCheckoutPage_GuestUser_ShouldShowPage() throws Exception {
        when(currencyService.getSelectedCurrency()).thenReturn(CURRENT_CURRENCY);
        when(sessionCart.calculateSubtotal(CURRENT_CURRENCY)).thenReturn(MOCK_SUBTOTAL);
        when(sessionCart.getAppliedCouponCode()).thenReturn(null);
        when(sessionCart.calculateTotalVatAmount(CURRENT_CURRENCY)).thenReturn(MOCK_TOTAL_VAT);
        when(sessionCart.calculateVatBreakdown(CURRENT_CURRENCY)).thenReturn(Map.of(new BigDecimal("0.21").setScale(2), MOCK_TOTAL_VAT));
        when(sessionCart.calculateTotalPriceBeforeShipping(CURRENT_CURRENCY)).thenReturn(MOCK_TOTAL_PRICE_BEFORE_SHIPPING);
        // Mock pro getShippingTaxRate() - volá se i pro hosta
        when(shippingService.getShippingTaxRate()).thenReturn(MOCK_SHIPPING_RATE);

        mockMvc.perform(MockMvcRequestBuilders.get("/pokladna"))
                .andExpect(status().isOk())
                .andExpect(view().name("pokladna"))
                .andExpect(model().attribute("customer", is(nullValue())))
                .andExpect(model().attributeExists("checkoutForm"))
                .andExpect(model().attribute("cart", notNullValue()))
                .andExpect(model().attribute("allowedPaymentMethods", notNullValue()))
                .andExpect(model().attribute("currency", notNullValue()))
                .andExpect(model().attribute("currencySymbol", notNullValue()))
                .andExpect(model().attribute("subtotal", comparesEqualTo(MOCK_SUBTOTAL)))
                .andExpect(model().attribute("validatedCoupon", is(nullValue())))
                .andExpect(model().attribute("couponDiscount", comparesEqualTo(BigDecimal.ZERO)))
                .andExpect(model().attribute("totalVat", comparesEqualTo(MOCK_TOTAL_VAT)))
                .andExpect(model().attribute("vatBreakdown", notNullValue()))
                // Pro hosta bez adresy by cena dopravy měla být 0 nebo null, a chyba nastavena
                .andExpect(model().attribute("originalShippingCostNoTax", is(nullValue())))
                .andExpect(model().attribute("shippingError", not(nullValue())))
                .andExpect(model().attribute("shippingCostNoTax", comparesEqualTo(BigDecimal.ZERO)))
                .andExpect(model().attribute("shippingTax", comparesEqualTo(BigDecimal.ZERO)))
                // Celková cena pro hosta bez vypočtené dopravy je cena před dopravou (zaokrouhlená)
                .andExpect(model().attribute("totalPrice", comparesEqualTo(MOCK_TOTAL_PRICE_BEFORE_SHIPPING.setScale(0, RoundingMode.DOWN))))
                .andExpect(model().attribute("roundingDifference", comparesEqualTo(MOCK_TOTAL_PRICE_BEFORE_SHIPPING.subtract(MOCK_TOTAL_PRICE_BEFORE_SHIPPING.setScale(0, RoundingMode.DOWN)).setScale(PRICE_SCALE, RoundingMode.HALF_UP))));

        verify(customerService, never()).getCustomerByEmail(anyString());
        verify(currencyService, times(2)).getSelectedCurrency();
        verify(shippingService, never()).calculateShippingCost(any(), anyString()); // Doprava se nepočítá
        verify(shippingService, atLeastOnce()).getShippingTaxRate(); // Sazba se ale načítá
        verify(sessionCart).calculateSubtotal(CURRENT_CURRENCY);
        verify(sessionCart, atLeastOnce()).getAppliedCouponCode();
        verify(sessionCart).calculateTotalVatAmount(CURRENT_CURRENCY);
        verify(sessionCart, atLeastOnce()).calculateVatBreakdown(CURRENT_CURRENCY);
        verify(sessionCart).calculateTotalPriceBeforeShipping(CURRENT_CURRENCY);
    }


    @Test
    @DisplayName("GET /pokladna - Přesměruje na košík, pokud je prázdný")
    void showCheckoutPage_EmptyCart_ShouldRedirectToCart() throws Exception {
        when(sessionCart.hasItems()).thenReturn(false);

        mockMvc.perform(MockMvcRequestBuilders.get("/pokladna"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kosik"))
                .andExpect(flash().attributeExists("cartError"));

        verify(sessionCart).hasItems();
        verify(customerService, never()).getCustomerByEmail(anyString());
    }

    // --- Testy POST /pokladna/odeslat ---

    @Test
    @DisplayName("POST /pokladna/odeslat - Úspěšné odeslání pro přihlášeného uživatele")
    @WithMockUser(username = MOCK_USER_EMAIL)
    void processCheckout_LoggedInUser_Success() throws Exception {
        // Mockujeme shipping cost a tax, které se nyní předávají ve skrytých polích
        // Tyto hodnoty by měly být získány z předchozího AJAXového volání
        String submittedShippingCost = MOCK_SHIPPING_COST.toPlainString();
        String submittedShippingTax = MOCK_SHIPPING_TAX.toPlainString();

        mockMvc.perform(MockMvcRequestBuilders.post("/pokladna/odeslat")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("paymentMethod", "BANK_TRANSFER")
                                .param("agreeTerms", "true")
                                // Přidání parametrů pro shipping cost
                                .param("shippingCostNoTax", submittedShippingCost)
                                .param("shippingTax", submittedShippingTax)
                        // .with(csrf())
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/muj-ucet/objednavky"))
                .andExpect(flash().attributeExists("orderSuccess"));

        ArgumentCaptor<CreateOrderRequest> requestCaptor = ArgumentCaptor.forClass(CreateOrderRequest.class);
        verify(orderService).createOrder(requestCaptor.capture());

        CreateOrderRequest capturedRequest = requestCaptor.getValue();
        assertEquals(loggedInCustomer.getId(), capturedRequest.getCustomerId());
        assertTrue(capturedRequest.isUseCustomerAddresses());
        assertEquals(CURRENT_CURRENCY, capturedRequest.getCurrency());
        assertEquals("BANK_TRANSFER", capturedRequest.getPaymentMethod());
        assertNotNull(capturedRequest.getItems());
        assertFalse(capturedRequest.getItems().isEmpty());
        // Ověření, že předané ceny dopravy jsou správně nastaveny v requestu
        assertEquals(0, MOCK_SHIPPING_COST.compareTo(capturedRequest.getShippingCostNoTax()));
        assertEquals(0, MOCK_SHIPPING_TAX.compareTo(capturedRequest.getShippingTax()));

        verify(sessionCart).clearCart();
        verify(customerService).getCustomerByEmail(MOCK_USER_EMAIL); // Ověření načtení zákazníka
    }

    @Test
    @DisplayName("POST /pokladna/odeslat - Úspěšné odeslání pro hosta")
    void processCheckout_GuestUser_Success() throws Exception {
        Customer newGuest = new Customer(); newGuest.setId(5L); newGuest.setGuest(true);
        newGuest.setInvoiceStreet(checkoutFormDto.getInvoiceStreet());
        newGuest.setInvoiceCity(checkoutFormDto.getInvoiceCity());
        newGuest.setInvoiceZipCode(checkoutFormDto.getInvoiceZipCode());
        newGuest.setInvoiceCountry(checkoutFormDto.getInvoiceCountry());
        newGuest.setUseInvoiceAddressAsDelivery(true);
        newGuest.setDeliveryStreet(newGuest.getInvoiceStreet());
        newGuest.setDeliveryCity(newGuest.getInvoiceCity());
        newGuest.setDeliveryZipCode(newGuest.getInvoiceZipCode());
        newGuest.setDeliveryCountry(newGuest.getInvoiceCountry());

        when(customerService.getOrCreateGuestFromCheckoutData(any(CheckoutFormDataDto.class))).thenReturn(newGuest);
        createdOrder.setCustomer(newGuest);
        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(createdOrder);

        // Mockujeme shipping cost a tax
        String submittedShippingCost = MOCK_SHIPPING_COST.toPlainString();
        String submittedShippingTax = MOCK_SHIPPING_TAX.toPlainString();


        mockMvc.perform(MockMvcRequestBuilders.post("/pokladna/odeslat")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("email", checkoutFormDto.getEmail())
                                .param("firstName", checkoutFormDto.getFirstName())
                                .param("lastName", checkoutFormDto.getLastName())
                                .param("phone", checkoutFormDto.getPhone())
                                .param("invoiceStreet", checkoutFormDto.getInvoiceStreet())
                                .param("invoiceCity", checkoutFormDto.getInvoiceCity())
                                .param("invoiceZipCode", checkoutFormDto.getInvoiceZipCode())
                                .param("invoiceCountry", checkoutFormDto.getInvoiceCountry())
                                .param("useInvoiceAddressAsDelivery", String.valueOf(checkoutFormDto.isUseInvoiceAddressAsDelivery()))
                                .param("paymentMethod", checkoutFormDto.getPaymentMethod())
                                .param("agreeTerms", String.valueOf(checkoutFormDto.isAgreeTerms()))
                                // Přidání parametrů pro shipping cost
                                .param("shippingCostNoTax", submittedShippingCost)
                                .param("shippingTax", submittedShippingTax)
                        // .with(csrf())
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dekujeme?orderCode=ORD-CREATED"))
                .andExpect(flash().attributeExists("orderSuccess"));

        ArgumentCaptor<CheckoutFormDataDto> dtoCaptor = ArgumentCaptor.forClass(CheckoutFormDataDto.class);
        verify(customerService).getOrCreateGuestFromCheckoutData(dtoCaptor.capture());
        assertEquals(checkoutFormDto.getEmail(), dtoCaptor.getValue().getEmail());

        ArgumentCaptor<CreateOrderRequest> requestCaptor = ArgumentCaptor.forClass(CreateOrderRequest.class);
        verify(orderService).createOrder(requestCaptor.capture());
        assertEquals(newGuest.getId(), requestCaptor.getValue().getCustomerId());
        assertTrue(requestCaptor.getValue().isUseCustomerAddresses());
        // Ověření cen dopravy v requestu
        assertEquals(0, MOCK_SHIPPING_COST.compareTo(requestCaptor.getValue().getShippingCostNoTax()));
        assertEquals(0, MOCK_SHIPPING_TAX.compareTo(requestCaptor.getValue().getShippingTax()));


        verify(sessionCart).clearCart();
    }


    @Test
    @DisplayName("POST /pokladna/odeslat - Chyba validace (nesouhlas s podmínkami)")
    void processCheckout_ValidationError_TermsNotAgreed() throws Exception {
        // Mockování pro prepareModelForError
        when(currencyService.getSelectedCurrency()).thenReturn(CURRENT_CURRENCY);
        when(sessionCart.calculateSubtotal(CURRENT_CURRENCY)).thenReturn(MOCK_SUBTOTAL);
        when(sessionCart.getAppliedCouponCode()).thenReturn(null);
        when(sessionCart.calculateTotalVatAmount(CURRENT_CURRENCY)).thenReturn(MOCK_TOTAL_VAT);
        when(sessionCart.calculateVatBreakdown(CURRENT_CURRENCY)).thenReturn(Map.of(new BigDecimal("0.21").setScale(2), MOCK_TOTAL_VAT));
        when(sessionCart.calculateTotalPriceBeforeShipping(CURRENT_CURRENCY)).thenReturn(MOCK_TOTAL_PRICE_BEFORE_SHIPPING);
        // Mock getShippingTaxRate pro prepareModelForError
        when(shippingService.getShippingTaxRate()).thenReturn(MOCK_SHIPPING_RATE);

        mockMvc.perform(MockMvcRequestBuilders.post("/pokladna/odeslat")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("paymentMethod", "BANK_TRANSFER")
                                .param("agreeTerms", "false") // Nesouhlasí
                                .param("email", checkoutFormDto.getEmail())
                                .param("firstName", checkoutFormDto.getFirstName())
                                .param("lastName", checkoutFormDto.getLastName())
                                .param("phone", checkoutFormDto.getPhone())
                                .param("invoiceStreet", checkoutFormDto.getInvoiceStreet())
                                .param("invoiceCity", checkoutFormDto.getInvoiceCity())
                                .param("invoiceZipCode", checkoutFormDto.getInvoiceZipCode())
                                .param("invoiceCountry", checkoutFormDto.getInvoiceCountry())
                                .param("useInvoiceAddressAsDelivery", "true")
                        // Chybí parametry pro shipping cost - chyba by se měla projevit ve formuláři
                        // .with(csrf())
                )
                .andExpect(status().isOk()) // Zůstane na stránce
                .andExpect(view().name("pokladna"))
                .andExpect(model().attributeExists("checkoutForm", "cart", "allowedPaymentMethods", "currency", "currencySymbol"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("checkoutForm", "agreeTerms")) // Chyba u agreeTerms
                .andExpect(model().attributeExists("checkoutError")); // Obecná chyba formuláře

        verify(orderService, never()).createOrder(any());
        verify(sessionCart, never()).clearCart();
        verify(currencyService, times(2)).getSelectedCurrency(); // Voláno při přípravě modelu pro chybu
    }

    @Test
    @DisplayName("POST /pokladna/odeslat - Chyba (prázdný košík)")
    void processCheckout_EmptyCart_ShouldRedirectWithError() throws Exception {
        when(sessionCart.hasItems()).thenReturn(false);

        mockMvc.perform(MockMvcRequestBuilders.post("/pokladna/odeslat")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("paymentMethod", "BANK_TRANSFER")
                                .param("agreeTerms", "true")
                                .param("email", "test@test.com")
                                .param("firstName", "a").param("lastName", "b").param("phone", "1")
                                .param("invoiceStreet", "c").param("invoiceCity", "d")
                                .param("invoiceZipCode", "11111").param("invoiceCountry", "e")
                        // Chybí shipping cost, ale nemělo by vadit, protože košík je prázdný
                        // .with(csrf())
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kosik"))
                .andExpect(flash().attributeExists("checkoutError")); // Flash atribut z controlleru

        verify(sessionCart).hasItems();
        verify(orderService, never()).createOrder(any());
        verify(sessionCart, never()).clearCart();
    }

    // --- Testy AJAX /pokladna/calculate-shipping ---

    @Test
    @DisplayName("POST /pokladna/calculate-shipping - Úspěšný výpočet")
    void calculateShippingAjax_Success() throws Exception {
        ShippingAddressDto addressDto = new ShippingAddressDto();
        addressDto.setStreet("Test Address 10");
        addressDto.setCity("Test City");
        addressDto.setZipCode("12345");
        addressDto.setCountry("Test Country");

        // Mock pro výpočet DPH zboží a celkové ceny
        when(sessionCart.calculateSubtotal(CURRENT_CURRENCY)).thenReturn(MOCK_SUBTOTAL);
        when(sessionCart.calculateVatBreakdown(CURRENT_CURRENCY)).thenReturn(Map.of(new BigDecimal("0.21").setScale(2), MOCK_TOTAL_VAT));
        when(sessionCart.calculateTotalVatAmount(CURRENT_CURRENCY)).thenReturn(MOCK_TOTAL_VAT);
        when(sessionCart.calculateTotalPriceBeforeShipping(CURRENT_CURRENCY)).thenReturn(MOCK_TOTAL_PRICE_BEFORE_SHIPPING);
        when(sessionCart.getAppliedCoupon()).thenReturn(null); // Bez kupónu
        when(sessionCart.calculateDiscountAmount(CURRENT_CURRENCY)).thenReturn(BigDecimal.ZERO); // Bez slevy

        // Mock pro shipping service
        when(shippingService.calculateShippingCost(any(Order.class), eq(CURRENT_CURRENCY))).thenReturn(MOCK_SHIPPING_COST);
        when(shippingService.getShippingTaxRate()).thenReturn(MOCK_SHIPPING_RATE);


        mockMvc.perform(MockMvcRequestBuilders.post("/pokladna/calculate-shipping")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(addressDto))
                        // .with(csrf())
                )
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorMessage", is(nullValue())))
                .andExpect(jsonPath("$.shippingCostNoTax", is(MOCK_SHIPPING_COST.doubleValue())))
                .andExpect(jsonPath("$.shippingTax", is(MOCK_SHIPPING_TAX.doubleValue())))
                .andExpect(jsonPath("$.totalPrice", is(MOCK_TOTAL_PRICE.doubleValue()))) // Ověřujeme přesnou cenu PŘED zaokrouhlením pro AJAX
                .andExpect(jsonPath("$.currencySymbol", is(CURRENCY_SYMBOL)))
                .andExpect(jsonPath("$.originalShippingCostNoTax", is(MOCK_SHIPPING_COST.doubleValue())))
                .andExpect(jsonPath("$.originalShippingTax", is(MOCK_SHIPPING_TAX.doubleValue())));


        verify(shippingService).calculateShippingCost(any(Order.class), eq(CURRENT_CURRENCY));
        verify(shippingService).getShippingTaxRate();
        verify(sessionCart, times(1)).calculateVatBreakdown(CURRENT_CURRENCY);
        verify(sessionCart).calculateTotalVatAmount(CURRENT_CURRENCY);
        verify(sessionCart).calculateTotalPriceBeforeShipping(CURRENT_CURRENCY); // Voláno pro výpočet
        verify(sessionCart).calculateSubtotal(CURRENT_CURRENCY); // Voláno pro výpočet
        verify(sessionCart).calculateDiscountAmount(CURRENT_CURRENCY); // Voláno pro výpočet
    }

    @Test
    @DisplayName("POST /pokladna/calculate-shipping - Chyba (neúplná adresa)")
    void calculateShippingAjax_IncompleteAddress_ShouldReturnBadRequest() throws Exception {
        ShippingAddressDto incompleteAddressDto = new ShippingAddressDto();
        incompleteAddressDto.setStreet("Test Address 10");

        mockMvc.perform(MockMvcRequestBuilders.post("/pokladna/calculate-shipping")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incompleteAddressDto))
                        // .with(csrf())
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorMessage", containsString("Chybí povinné údaje adresy")));

        verify(shippingService, never()).calculateShippingCost(any(), anyString());
    }

    @Test
    @DisplayName("POST /pokladna/calculate-shipping - Chyba (chyba výpočtu v service)")
    void calculateShippingAjax_CalculationError_ShouldReturnAppropriateError() throws Exception {
        ShippingAddressDto addressDto = new ShippingAddressDto();
        addressDto.setStreet("Problem Address 1");
        addressDto.setCity("Problem City");
        addressDto.setZipCode("54321");
        addressDto.setCountry("Problem Country");

        String errorMessage = "Simulated shipping service error";
        when(shippingService.calculateShippingCost(any(Order.class), eq(CURRENT_CURRENCY)))
                .thenThrow(new RuntimeException(errorMessage)); // Simulace obecné chyby
        // Mockování pro DPH zboží (volá se i při chybě dopravy)
        when(sessionCart.calculateSubtotal(CURRENT_CURRENCY)).thenReturn(MOCK_SUBTOTAL); // Potřeba pro výpočet ceny bez daně po slevě
        when(sessionCart.calculateDiscountAmount(CURRENT_CURRENCY)).thenReturn(BigDecimal.ZERO); // Potřeba pro výpočet ceny bez daně po slevě
        when(sessionCart.calculateVatBreakdown(CURRENT_CURRENCY)).thenReturn(Map.of(new BigDecimal("0.21").setScale(2), MOCK_TOTAL_VAT));
        when(sessionCart.calculateTotalVatAmount(CURRENT_CURRENCY)).thenReturn(MOCK_TOTAL_VAT);
        when(sessionCart.getAppliedCoupon()).thenReturn(null);


        mockMvc.perform(MockMvcRequestBuilders.post("/pokladna/calculate-shipping")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(addressDto))
                        // .with(csrf())
                )
                .andExpect(status().isInternalServerError()) // Očekáváme 500 kvůli RuntimeException
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorMessage", containsString("Výpočet dopravy selhal"))) // Obecná chyba pro uživatele
                .andExpect(jsonPath("$.shippingCostNoTax", is(-1))) // Chyba je signalizována jako -1
                .andExpect(jsonPath("$.shippingTax", is(0))) // DPH z dopravy je 0
                .andExpect(jsonPath("$.totalVatWithShipping", is(MOCK_TOTAL_VAT.doubleValue()))); // Jen DPH zboží

        verify(shippingService).calculateShippingCost(any(Order.class), eq(CURRENT_CURRENCY));
        // Ověření, že se volaly metody pro DPH zboží
        verify(sessionCart, times(1)).calculateVatBreakdown(CURRENT_CURRENCY);
        verify(sessionCart).calculateTotalVatAmount(CURRENT_CURRENCY);
        verify(sessionCart).calculateSubtotal(CURRENT_CURRENCY); // Voláno
        verify(sessionCart).calculateDiscountAmount(CURRENT_CURRENCY); // Voláno
    }
}