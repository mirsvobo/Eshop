// Soubor: src/test/java/org/example/eshop/service/OrderServiceTest.java
package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.config.PriceConstants;
import org.example.eshop.dto.*; // Import všech DTO
import org.example.eshop.model.*; // Import všech modelů
import org.example.eshop.repository.*; // Import všech repozitářů
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Qualifier;

import java.math.BigDecimal;
import java.math.RoundingMode; // Přidáno pro jistotu
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest implements PriceConstants {

    // Mockování VŠECH závislostí OrderService
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private OrderStateRepository orderStateRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private AddonsRepository addonsRepository;
    @Mock
    private CouponRepository couponRepository;
    @Mock
    private TaxRateRepository taxRateRepository; // Nyní velmi důležité
    @Mock
    private DesignRepository designRepository;
    @Mock
    private GlazeRepository glazeRepository;
    @Mock
    private RoofColorRepository roofColorRepository;
    @Mock
    private ProductService productService;
    @Mock
    private DiscountService discountService;
    @Mock
    private CouponService couponService;
    @Mock
    private EmailService emailService;
    // OPRAVA: Kvalifikátor pro ShippingService
    @Mock
    @Qualifier("googleMapsShippingService")
    private ShippingService shippingService;
    @Mock
    private SuperFakturaInvoiceService invoiceService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private OrderCodeGeneratorService orderCodeGeneratorService;

    @InjectMocks
    private OrderService orderService;

    // Testovací data (instance)
    private Customer testCustomer;
    private Product standardProduct;
    private Product customProduct;
    private TaxRate standardTaxRate;
    private OrderState initialState;
    private Design testDesign;
    private Glaze testGlaze;
    private RoofColor testRoofColor;
    private Addon testAddon;
    private Coupon percentCoupon;
    private Coupon freeShippingCoupon;
    // Přidáno pro testy s neplatným kupónem
    private Coupon inactiveCoupon;
    private Coupon expiredCoupon;


    @BeforeEach
    void setUp() {
        // --- Inicializace Testovacích Dat ---
        testCustomer = new Customer();
        testCustomer.setId(1L);
        testCustomer.setEmail("customer@test.com");
        testCustomer.setFirstName("Test");
        testCustomer.setLastName("Customer");
        testCustomer.setPhone("123456789");
        testCustomer.setInvoiceFirstName("Test");
        testCustomer.setInvoiceLastName("Customer");
        testCustomer.setInvoiceStreet("Test Street 123");
        testCustomer.setInvoiceCity("Testovo");
        testCustomer.setInvoiceZipCode("12345");
        testCustomer.setInvoiceCountry("Česká republika");
        testCustomer.setUseInvoiceAddressAsDelivery(true);
        testCustomer.setDeliveryFirstName(testCustomer.getInvoiceFirstName());
        testCustomer.setDeliveryLastName(testCustomer.getInvoiceLastName());
        testCustomer.setDeliveryStreet(testCustomer.getInvoiceStreet());
        testCustomer.setDeliveryCity(testCustomer.getInvoiceCity());
        testCustomer.setDeliveryZipCode(testCustomer.getInvoiceZipCode());
        testCustomer.setDeliveryCountry(testCustomer.getInvoiceCountry());
        testCustomer.setDeliveryPhone(testCustomer.getPhone());

        // OPRAVA: Použití setterů TaxRate
        standardTaxRate = new TaxRate();
        standardTaxRate.setId(1L);
        standardTaxRate.setName("Standard 21%");
        standardTaxRate.setRate(new BigDecimal("0.21"));
        standardTaxRate.setReverseCharge(false);

        initialState = new OrderState();
        initialState.setId(1L);
        initialState.setCode("NEW");
        initialState.setName("Nová");
        testDesign = new Design();
        testDesign.setId(10L);
        testDesign.setName("Klasik Design");
        testDesign.setPriceSurchargeCZK(null);
        testDesign.setPriceSurchargeEUR(null); // Přidáno EUR
        testGlaze = new Glaze();
        testGlaze.setId(20L);
        testGlaze.setName("Ořech Lazura");
        testGlaze.setPriceSurchargeCZK(BigDecimal.ZERO);
        testGlaze.setPriceSurchargeEUR(new BigDecimal("8.00")); // EUR příplatek
        testRoofColor = new RoofColor();
        testRoofColor.setId(30L);
        testRoofColor.setName("Antracit Střecha");
        testRoofColor.setPriceSurchargeCZK(BigDecimal.ZERO);
        testRoofColor.setPriceSurchargeEUR(BigDecimal.ZERO);

        standardProduct = new Product();
        standardProduct.setId(100L);
        standardProduct.setName("Standard Product");
        standardProduct.setActive(true);
        standardProduct.setCustomisable(false);
        standardProduct.setBasePriceCZK(new BigDecimal("1000.00"));
        standardProduct.setBasePriceEUR(new BigDecimal("40.00"));
        // OPRAVA: Nastavení Setu sazeb
        standardProduct.setAvailableTaxRates(new HashSet<>(Collections.singletonList(standardTaxRate)));
        standardProduct.setAvailableDesigns(Set.of(testDesign));
        standardProduct.setAvailableGlazes(Set.of(testGlaze));
        standardProduct.setAvailableRoofColors(Set.of(testRoofColor));

        testAddon = new Addon();
        testAddon.setId(50L);
        testAddon.setName("Test Addon");
        testAddon.setActive(true);
        testAddon.setPriceCZK(new BigDecimal("50.00"));
        testAddon.setPriceEUR(new BigDecimal("2.00"));
        customProduct = new Product();
        customProduct.setId(200L);
        customProduct.setName("Custom Product");
        customProduct.setActive(true);
        customProduct.setCustomisable(true);
        // OPRAVA: Nastavení Setu sazeb
        customProduct.setAvailableTaxRates(new HashSet<>(Collections.singletonList(standardTaxRate)));
        customProduct.setAvailableAddons(Set.of(testAddon));
        customProduct.setAvailableDesigns(Set.of(testDesign));
        customProduct.setAvailableGlazes(Set.of(testGlaze));
        customProduct.setAvailableRoofColors(Set.of(testRoofColor));
        // Nastavení konfigurátoru
        ProductConfigurator configurator = new ProductConfigurator(); /*...*/
        configurator.setProduct(customProduct);
        configurator.setMinLength(new BigDecimal("100"));
        configurator.setMaxLength(new BigDecimal("500"));
        configurator.setMinWidth(new BigDecimal("50"));
        configurator.setMaxWidth(new BigDecimal("200"));
        configurator.setMinHeight(new BigDecimal("150"));
        configurator.setMaxHeight(new BigDecimal("300"));
        configurator.setPricePerCmLengthCZK(new BigDecimal("10.00"));
        configurator.setPricePerCmWidthCZK(new BigDecimal("5.00"));
        configurator.setPricePerCmHeightCZK(new BigDecimal("8.00"));
        configurator.setDividerPricePerCmDepthCZK(new BigDecimal("3.00"));
        configurator.setGutterPriceCZK(new BigDecimal("500.00"));
        configurator.setShedPriceCZK(new BigDecimal("2000.00"));
        configurator.setDesignPriceCZK(new BigDecimal("100.00"));
        configurator.setPricePerCmLengthEUR(new BigDecimal("0.40"));
        configurator.setPricePerCmWidthEUR(new BigDecimal("0.20"));
        configurator.setPricePerCmHeightEUR(new BigDecimal("0.32"));
        configurator.setDividerPricePerCmDepthEUR(new BigDecimal("0.12"));
        configurator.setGutterPriceEUR(new BigDecimal("20.00"));
        configurator.setShedPriceEUR(new BigDecimal("80.00"));
        configurator.setDesignPriceEUR(new BigDecimal("4.00"));
        customProduct.setConfigurator(configurator);

        percentCoupon = new Coupon();
        percentCoupon.setId(1L); /* ... ostatní nastavení ... */
        percentCoupon.setCode("SLEVA10");
        percentCoupon.setActive(true);
        percentCoupon.setPercentage(true);
        percentCoupon.setValue(new BigDecimal("10.00"));
        percentCoupon.setStartDate(LocalDateTime.now().minusDays(1));
        percentCoupon.setExpirationDate(LocalDateTime.now().plusDays(1));
        freeShippingCoupon = new Coupon();
        freeShippingCoupon.setId(2L); /* ... ostatní nastavení ... */
        freeShippingCoupon.setCode("DOPRAVAZDARMA");
        freeShippingCoupon.setActive(true);
        freeShippingCoupon.setPercentage(false);
        freeShippingCoupon.setFreeShipping(true);
        freeShippingCoupon.setStartDate(LocalDateTime.now().minusDays(1));
        freeShippingCoupon.setExpirationDate(LocalDateTime.now().plusDays(1));
        // Inicializace neplatných kupónů
        inactiveCoupon = new Coupon();
        inactiveCoupon.setId(98L);
        inactiveCoupon.setCode("NEPLATNY");
        inactiveCoupon.setActive(false);
        expiredCoupon = new Coupon();
        expiredCoupon.setId(99L);
        expiredCoupon.setCode("EXP");
        expiredCoupon.setActive(true);
        expiredCoupon.setExpirationDate(LocalDateTime.now().minusDays(1));

        // --- Základní Mockování s lenient() ---
        lenient().when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        lenient().when(orderStateRepository.findByCodeIgnoreCase("NEW")).thenReturn(Optional.of(initialState));
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order orderToSave = invocation.getArgument(0);
            if (orderToSave.getId() == null) orderToSave.setId(System.currentTimeMillis() % 1000 + 100);
            if (orderToSave.getOrderItems() != null) {
                orderToSave.getOrderItems().forEach(item -> item.setOrder(orderToSave));
            }
            return orderToSave;
        });
        lenient().when(paymentService.determineInitialPaymentStatus(any(Order.class))).thenReturn("PENDING");
        lenient().when(paymentService.calculateDeposit(any(BigDecimal.class))).thenReturn(BigDecimal.ZERO);
        lenient().when(discountService.applyBestPercentageDiscount(any(BigDecimal.class), any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().doNothing().when(emailService).sendOrderConfirmationEmail(any(Order.class));
        lenient().doNothing().when(emailService).sendOrderStatusUpdateEmail(any(Order.class), any(OrderState.class));
        lenient().doNothing().when(invoiceService).generateProformaInvoice(any(Order.class));
        lenient().doNothing().when(invoiceService).generateTaxDocumentForDeposit(any(Order.class));
        lenient().doNothing().when(invoiceService).markInvoiceAsPaidInSF(anyLong(), any(BigDecimal.class), any(LocalDate.class), anyString(), anyString());
        lenient().doNothing().when(couponService).markCouponAsUsed(any(Coupon.class));
        lenient().when(orderCodeGeneratorService.getNextOrderCode()).thenReturn("TEST-206");
        // Mock pro TaxRateRepository - potřebné pro createOrder
        lenient().when(taxRateRepository.findById(standardTaxRate.getId())).thenReturn(Optional.of(standardTaxRate));
    }


    @Test
    @DisplayName("[createOrder] Úspěšné vytvoření - Standardní produkt, CZK, Bez kupónu")
    void createOrder_StandardProduct_CZK_NoCoupon_Success() {
        // --- Příprava ---
        // OPRAVA: Mockujeme findByIdWithDetails, protože processCartItem ho volá
        when(productRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(standardProduct));
        when(designRepository.findById(10L)).thenReturn(Optional.of(testDesign));
        when(glazeRepository.findById(20L)).thenReturn(Optional.of(testGlaze));
        when(roofColorRepository.findById(30L)).thenReturn(Optional.of(testRoofColor));
        when(shippingService.calculateShippingCost(any(Order.class), eq("CZK"))).thenReturn(new BigDecimal("150.00"));
        when(shippingService.getShippingTaxRate()).thenReturn(new BigDecimal("0.21"));

        CartItemDto itemDto = new CartItemDto();
        itemDto.setProductId(100L);
        itemDto.setQuantity(2);
        itemDto.setCustom(false);
        itemDto.setSelectedDesignId(10L);
        itemDto.setSelectedGlazeId(20L);
        itemDto.setSelectedRoofColorId(30L);
        // OPRAVA: Přidání ID sazby do DTO
        itemDto.setSelectedTaxRateId(standardTaxRate.getId());

        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(1L);
        request.setUseCustomerAddresses(true);
        request.setPaymentMethod("BANK_TRANSFER");
        request.setCurrency("CZK");
        request.setItems(List.of(itemDto));
        request.setShippingCostNoTax(new BigDecimal("150.00"));
        request.setShippingTax(new BigDecimal("31.50"));

        // --- Provedení ---
        Order createdOrder = orderService.createOrder(request);

        // --- Ověření ---
        assertNotNull(createdOrder);
        assertEquals("TEST-206", createdOrder.getOrderCode());
        assertEquals("PENDING", createdOrder.getPaymentStatus());
        assertNull(createdOrder.getDepositAmount());
        assertEquals(1, createdOrder.getOrderItems().size());

        OrderItem savedItem = createdOrder.getOrderItems().get(0);
        assertNotNull(savedItem);
        // OPRAVA: Ověření nových polí pro DPH v OrderItem
        assertNotNull(savedItem.getTaxRate());
        assertEquals(0, standardTaxRate.getRate().compareTo(savedItem.getTaxRate()));
        assertEquals(standardTaxRate.getId(), savedItem.getSelectedTaxRateId());
        assertEquals(standardTaxRate.getName(), savedItem.getSelectedTaxRateName());
        assertFalse(savedItem.isReverseCharge());

        // Ověření cen
        assertEquals(0, new BigDecimal("2000.00").compareTo(createdOrder.getSubTotalWithoutTax())); // 2 * 1000
        assertEquals(0, new BigDecimal("420.00").compareTo(createdOrder.getTotalItemsTax()));       // 2 * 1000 * 0.21
        assertEquals(0, new BigDecimal("150.00").compareTo(createdOrder.getShippingCostWithoutTax()));
        assertEquals(0, new BigDecimal("31.50").compareTo(createdOrder.getShippingTax()));         // 150 * 0.21
        assertEquals(0, new BigDecimal("2150.00").compareTo(createdOrder.getTotalPriceWithoutTax())); // 2000 + 150
        assertEquals(0, new BigDecimal("451.50").compareTo(createdOrder.getTotalTax()));           // 420 + 31.50
        // Ověření zaokrouhlené celkové ceny
        BigDecimal expectedTotal = new BigDecimal("2601.50");
        BigDecimal expectedRoundedTotal = expectedTotal.setScale(0, RoundingMode.DOWN); // 2601.00
        assertEquals(0, expectedRoundedTotal.compareTo(createdOrder.getTotalPrice()));
        // Ověření původní ceny (pokud ji ukládáte)
        assertNotNull(createdOrder.getOriginalTotalPrice());
        assertEquals(0, expectedTotal.compareTo(createdOrder.getOriginalTotalPrice()));

        verify(orderCodeGeneratorService).getNextOrderCode();
        verify(orderRepository).save(createdOrder);
        verify(emailService).sendOrderConfirmationEmail(createdOrder);
        verify(invoiceService, never()).generateProformaInvoice(any(Order.class));
        // OPRAVA: Ověření volání findByIdWithDetails a taxRateRepository.findById
        verify(productRepository).findByIdWithDetails(100L);
        verify(taxRateRepository).findById(standardTaxRate.getId());
    }

    @Test
    @DisplayName("[createOrder] Úspěšné vytvoření - Custom produkt, EUR, Vyžaduje zálohu, s příplatky")
    void createOrder_CustomProduct_EUR_RequiresDeposit_WithSurcharge_Success() {
        // --- Příprava ---
        // OPRAVA: Mockujeme findByIdWithDetails
        when(productRepository.findByIdWithDetails(200L)).thenReturn(Optional.of(customProduct));
        // Mockujeme načtení atributů s příplatkem (Teak Premium)
        when(designRepository.findById(10L)).thenReturn(Optional.of(testDesign)); // Bez příplatku
        // Vytvoříme Glaze s EUR příplatkem pro tento test
        Glaze glazeWithSurcharge = new Glaze();
        glazeWithSurcharge.setId(21L);
        glazeWithSurcharge.setName("Teak Premium");
        glazeWithSurcharge.setPriceSurchargeEUR(new BigDecimal("8.00"));
        when(glazeRepository.findById(21L)).thenReturn(Optional.of(glazeWithSurcharge));
        when(roofColorRepository.findById(30L)).thenReturn(Optional.of(testRoofColor)); // Bez příplatku
        when(addonsRepository.findAllById(eq(Set.of(50L)))).thenReturn(List.of(testAddon)); // Addon za 2 EUR

        BigDecimal dynamicPriceEUR = new BigDecimal("150.00"); // Cena za rozměry atd.
        when(productService.calculateDynamicProductPrice(eq(customProduct), anyMap(), isNull(), eq(false), eq(true), eq(false), eq("EUR")))
                .thenReturn(dynamicPriceEUR);

        when(shippingService.calculateShippingCost(any(Order.class), eq("EUR"))).thenReturn(new BigDecimal("10.00"));
        when(shippingService.getShippingTaxRate()).thenReturn(new BigDecimal("0.21"));
        when(paymentService.determineInitialPaymentStatus(any(Order.class))).thenReturn("AWAITING_DEPOSIT");

        // Očekávaná jednotková cena = dynamicPrice + surchargeGlaze + priceAddon = 150 + 8 + 2 = 160 EUR
        BigDecimal expectedFinalUnitPrice = new BigDecimal("160.00");
        // Očekávaná celková cena = (jednotková + doprava) * (1 + DPH) = (160 + 10) * 1.21 = 170 * 1.21 = 205.70 EUR
        BigDecimal expectedTotalPriceWithTax = new BigDecimal("205.70");
        BigDecimal expectedDeposit = expectedTotalPriceWithTax.multiply(new BigDecimal("0.50")).setScale(PRICE_SCALE, RoundingMode.HALF_UP); // 102.85 EUR
        when(paymentService.calculateDeposit(any(BigDecimal.class))).thenReturn(expectedDeposit); // Použijeme any() zde

        // Vstupní DTO
        CartItemDto itemDto = new CartItemDto();
        itemDto.setProductId(200L);
        itemDto.setQuantity(1);
        itemDto.setCustom(true);
        itemDto.setCustomDimensions(Map.of("length", new BigDecimal("250"), "width", new BigDecimal("150"), "height", new BigDecimal("200")));
        itemDto.setSelectedDesignId(10L);
        itemDto.setSelectedGlazeId(21L);  // Teak Premium (s příplatkem)
        itemDto.setSelectedRoofColorId(30L);
        itemDto.setCustomHasGutter(true);
        AddonDto addonDto = new AddonDto(testAddon.getId(), 1);
        itemDto.setSelectedAddons(List.of(addonDto));
        // OPRAVA: Přidání ID sazby do DTO
        itemDto.setSelectedTaxRateId(standardTaxRate.getId());

        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(1L);
        request.setUseCustomerAddresses(true);
        request.setPaymentMethod("BANK_TRANSFER");
        request.setCurrency("EUR");
        request.setItems(List.of(itemDto));
        request.setShippingCostNoTax(new BigDecimal("10.00"));
        request.setShippingTax(new BigDecimal("2.10"));


        // --- Provedení ---
        Order createdOrder = orderService.createOrder(request);

        // --- Ověření ---
        assertNotNull(createdOrder);
        assertEquals("TEST-206", createdOrder.getOrderCode());
        assertEquals("EUR", createdOrder.getCurrency());
        assertEquals("AWAITING_DEPOSIT", createdOrder.getPaymentStatus());
        assertNotNull(createdOrder.getDepositAmount());
        assertEquals(0, expectedDeposit.compareTo(createdOrder.getDepositAmount()), "Vypočtená záloha nesouhlasí");
        assertEquals(1, createdOrder.getOrderItems().size());

        OrderItem savedItem = createdOrder.getOrderItems().get(0);
        assertNotNull(savedItem);
        // Ověření jmen atributů
        assertEquals(testDesign.getName(), savedItem.getModel());
        assertEquals(glazeWithSurcharge.getName(), savedItem.getGlaze()); // Ověříme správný název glazury
        assertEquals(testRoofColor.getName(), savedItem.getRoofColor());
        // Ověření ceny položky (včetně příplatků a addonů)
        assertEquals(0, expectedFinalUnitPrice.compareTo(savedItem.getUnitPriceWithoutTax()), "Jednotková cena položky nesouhlasí");
        // OPRAVA: Ověření sazby DPH v OrderItem
        assertEquals(0, standardTaxRate.getRate().compareTo(savedItem.getTaxRate()));
        assertEquals(standardTaxRate.getId(), savedItem.getSelectedTaxRateId());

        // Ověření celkové ceny (zaokrouhlené)
        BigDecimal expectedRoundedTotal = expectedTotalPriceWithTax.setScale(0, RoundingMode.DOWN); // 205.00 EUR
        assertEquals(0, expectedRoundedTotal.compareTo(createdOrder.getTotalPrice()), "Celková cena objednávky nesouhlasí");
        assertNotNull(createdOrder.getOriginalTotalPrice());
        assertEquals(0, expectedTotalPriceWithTax.compareTo(createdOrder.getOriginalTotalPrice()));

        // Ověření volání služeb
        verify(orderCodeGeneratorService).getNextOrderCode();
        verify(orderRepository).save(createdOrder);
        verify(emailService).sendOrderConfirmationEmail(createdOrder);
        verify(invoiceService).generateProformaInvoice(createdOrder);
        verify(paymentService).determineInitialPaymentStatus(any(Order.class));
        verify(paymentService).calculateDeposit(any(BigDecimal.class)); // Použijeme any()
        // OPRAVA: Ověření volání findByIdWithDetails a taxRateRepository.findById
        verify(productRepository).findByIdWithDetails(200L);
        verify(taxRateRepository).findById(standardTaxRate.getId());
        verify(designRepository).findById(10L);
        verify(glazeRepository).findById(21L);
        verify(roofColorRepository).findById(30L);
        verify(addonsRepository).findAllById(eq(Set.of(50L)));
    }


    @Test
    @DisplayName("[createOrder] Objednávka s platným kupónem na dopravu zdarma")
    void createOrder_FreeShippingCoupon_Success() {
        // --- Příprava ---
        when(productRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(standardProduct));
        when(designRepository.findById(10L)).thenReturn(Optional.of(testDesign));
        when(glazeRepository.findById(20L)).thenReturn(Optional.of(testGlaze));
        when(roofColorRepository.findById(30L)).thenReturn(Optional.of(testRoofColor));
        when(shippingService.calculateShippingCost(any(Order.class), eq("CZK"))).thenReturn(new BigDecimal("150.00"));
        when(shippingService.getShippingTaxRate()).thenReturn(new BigDecimal("0.21"));
        when(couponService.findByCode("DOPRAVAZDARMA")).thenReturn(Optional.of(freeShippingCoupon));
        when(couponService.isCouponGenerallyValid(freeShippingCoupon)).thenReturn(true);
        when(couponService.checkMinimumOrderValue(eq(freeShippingCoupon), any(BigDecimal.class), eq("CZK"))).thenReturn(true);
        when(couponService.checkCustomerUsageLimit(eq(testCustomer), eq(freeShippingCoupon))).thenReturn(true);
        // Kupón na dopravu neposkytuje slevu na zboží
        lenient().when(couponService.calculateDiscountAmount(any(BigDecimal.class), eq(freeShippingCoupon), eq("CZK"))).thenReturn(BigDecimal.ZERO);
        doNothing().when(couponService).markCouponAsUsed(freeShippingCoupon);

        CartItemDto itemDto = new CartItemDto();
        itemDto.setProductId(100L);
        itemDto.setQuantity(1);
        itemDto.setCustom(false);
        itemDto.setSelectedDesignId(10L);
        itemDto.setSelectedGlazeId(20L);
        itemDto.setSelectedRoofColorId(30L);
        // OPRAVA: Přidání ID sazby do DTO
        itemDto.setSelectedTaxRateId(standardTaxRate.getId());

        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(1L);
        request.setUseCustomerAddresses(true);
        request.setPaymentMethod("BANK_TRANSFER");
        request.setCurrency("CZK");
        request.setItems(List.of(itemDto));
        request.setCouponCode("DOPRAVAZDARMA");
        request.setShippingCostNoTax(new BigDecimal("150.00")); // Cena z AJAXu
        request.setShippingTax(new BigDecimal("31.50"));      // Daň z AJAXu

        // --- Provedení ---
        Order createdOrder = orderService.createOrder(request);

        // --- Ověření ---
        assertNotNull(createdOrder);
        assertEquals("TEST-206", createdOrder.getOrderCode());
        assertEquals(freeShippingCoupon, createdOrder.getAppliedCoupon());
        assertEquals("DOPRAVAZDARMA", createdOrder.getAppliedCouponCode());
        assertEquals(0, BigDecimal.ZERO.compareTo(createdOrder.getCouponDiscountAmount())); // Žádná sleva na zboží
        // Doprava by měla být NULA
        assertEquals(0, BigDecimal.ZERO.compareTo(createdOrder.getShippingCostWithoutTax()));
        assertEquals(0, BigDecimal.ZERO.compareTo(createdOrder.getShippingTaxRate())); // Sazba DPH dopravy je 0
        assertEquals(0, BigDecimal.ZERO.compareTo(createdOrder.getShippingTax()));
        // Celková cena = Subtotal + ItemsVAT = 1000 + (1000 * 0.21) = 1000 + 210 = 1210
        assertEquals(0, new BigDecimal("1000.00").compareTo(createdOrder.getSubTotalWithoutTax()));
        assertEquals(0, new BigDecimal("210.00").compareTo(createdOrder.getTotalItemsTax()));
        // Celková cena je zaokrouhlená
        BigDecimal expectedTotal = new BigDecimal("1210.00");
        BigDecimal expectedRoundedTotal = expectedTotal.setScale(0, RoundingMode.DOWN); // 1210.00
        assertEquals(0, expectedRoundedTotal.compareTo(createdOrder.getTotalPrice()));
        assertNotNull(createdOrder.getOriginalTotalPrice());
        assertEquals(0, expectedTotal.compareTo(createdOrder.getOriginalTotalPrice()));


        verify(orderCodeGeneratorService).getNextOrderCode();
        verify(couponService).markCouponAsUsed(freeShippingCoupon);
        // verify(shippingService).calculateShippingCost(any(Order.class), eq("CZK")); // Volá se pro zjištění původní ceny, pokud není v requestu
        verify(productRepository).findByIdWithDetails(100L);
        verify(taxRateRepository).findById(standardTaxRate.getId());
    }

    @Test
    @DisplayName("[createOrder] Objednávka s platným fixním kupónem CZK")
    void createOrder_ValidFixedCoupon_CZK() {
        // --- Příprava ---
        when(productRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(standardProduct));
        when(designRepository.findById(10L)).thenReturn(Optional.of(testDesign));
        when(glazeRepository.findById(20L)).thenReturn(Optional.of(testGlaze));
        when(roofColorRepository.findById(30L)).thenReturn(Optional.of(testRoofColor));
        when(shippingService.calculateShippingCost(any(Order.class), eq("CZK"))).thenReturn(new BigDecimal("150.00"));
        when(shippingService.getShippingTaxRate()).thenReturn(new BigDecimal("0.21"));

        Coupon fixedCoupon = new Coupon();
        fixedCoupon.setId(3L);
        fixedCoupon.setCode("FIX50");
        fixedCoupon.setActive(true);
        fixedCoupon.setPercentage(false);
        fixedCoupon.setValueCZK(new BigDecimal("50.00"));
        fixedCoupon.setStartDate(LocalDateTime.now().minusDays(1));
        fixedCoupon.setExpirationDate(LocalDateTime.now().plusDays(1));
        when(couponService.findByCode("FIX50")).thenReturn(Optional.of(fixedCoupon));
        when(couponService.isCouponGenerallyValid(fixedCoupon)).thenReturn(true);
        when(couponService.checkMinimumOrderValue(eq(fixedCoupon), any(BigDecimal.class), eq("CZK"))).thenReturn(true);
        when(couponService.checkCustomerUsageLimit(eq(testCustomer), eq(fixedCoupon))).thenReturn(true);
        // Mock pro výpočet slevy službou CouponService
        when(couponService.calculateDiscountAmount(eq(new BigDecimal("1000.00")), eq(fixedCoupon), eq("CZK"))).thenReturn(new BigDecimal("50.00"));
        doNothing().when(couponService).markCouponAsUsed(fixedCoupon);

        CartItemDto itemDto = new CartItemDto();
        itemDto.setProductId(100L);
        itemDto.setQuantity(1);
        itemDto.setCustom(false);
        itemDto.setSelectedDesignId(10L);
        itemDto.setSelectedGlazeId(20L);
        itemDto.setSelectedRoofColorId(30L);
        // OPRAVA: Přidání ID sazby do DTO
        itemDto.setSelectedTaxRateId(standardTaxRate.getId());

        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(1L);
        request.setUseCustomerAddresses(true);
        request.setPaymentMethod("BANK_TRANSFER");
        request.setCurrency("CZK");
        request.setItems(List.of(itemDto));
        request.setCouponCode("FIX50");
        request.setShippingCostNoTax(new BigDecimal("150.00"));
        request.setShippingTax(new BigDecimal("31.50"));

        // --- Provedení ---
        Order createdOrder = orderService.createOrder(request);

        // --- Ověření ---
        assertNotNull(createdOrder);
        assertEquals("TEST-206", createdOrder.getOrderCode());
        assertEquals(fixedCoupon, createdOrder.getAppliedCoupon());
        assertEquals("FIX50", createdOrder.getAppliedCouponCode());
        assertEquals(0, new BigDecimal("50.00").compareTo(createdOrder.getCouponDiscountAmount()));
        // Subtotal = 1000, Discount = 50 => SubtotalAfterDiscount = 950
        // ItemsTax = 1000 * 0.21 = 210
        // ShippingNoTax = 150, ShippingTax = 31.50
        // TotalTax = 210 + 31.50 = 241.50
        // OriginalTotalPrice = (950 + 150) + (210 + 31.50) = 1100 + 241.50 = 1341.50
        // RoundedTotalPrice = 1341.00
        assertEquals(0, new BigDecimal("1341.00").compareTo(createdOrder.getTotalPrice()));
        assertNotNull(createdOrder.getOriginalTotalPrice());
        assertEquals(0, new BigDecimal("1341.50").compareTo(createdOrder.getOriginalTotalPrice()));


        verify(orderCodeGeneratorService).getNextOrderCode();
        verify(couponService).markCouponAsUsed(fixedCoupon);
        verify(productRepository).findByIdWithDetails(100L);
        verify(taxRateRepository).findById(standardTaxRate.getId());
    }

    @Test
    @DisplayName("[createOrder] Objednávka s neplatným kupónem")
    void createOrder_InvalidCoupon() {
        // --- Příprava ---
        when(productRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(standardProduct));
        when(designRepository.findById(10L)).thenReturn(Optional.of(testDesign));
        when(glazeRepository.findById(20L)).thenReturn(Optional.of(testGlaze));
        when(roofColorRepository.findById(30L)).thenReturn(Optional.of(testRoofColor));
        when(shippingService.calculateShippingCost(any(Order.class), eq("CZK"))).thenReturn(new BigDecimal("150.00"));
        when(shippingService.getShippingTaxRate()).thenReturn(new BigDecimal("0.21"));
        when(couponService.findByCode("NEPLATNY")).thenReturn(Optional.of(inactiveCoupon));
        when(couponService.isCouponGenerallyValid(inactiveCoupon)).thenReturn(false); // Kupón není platný

        CartItemDto itemDto = new CartItemDto();
        itemDto.setProductId(100L);
        itemDto.setQuantity(1);
        itemDto.setCustom(false);
        itemDto.setSelectedDesignId(10L);
        itemDto.setSelectedGlazeId(20L);
        itemDto.setSelectedRoofColorId(30L);
        // OPRAVA: Přidání ID sazby do DTO
        itemDto.setSelectedTaxRateId(standardTaxRate.getId());

        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(1L);
        request.setUseCustomerAddresses(true);
        request.setPaymentMethod("BANK_TRANSFER");
        request.setCurrency("CZK");
        request.setItems(List.of(itemDto));
        request.setCouponCode("NEPLATNY");
        request.setShippingCostNoTax(new BigDecimal("150.00"));
        request.setShippingTax(new BigDecimal("31.50"));

        // --- Provedení ---
        Order createdOrder = orderService.createOrder(request);

        // --- Ověření ---
        assertNotNull(createdOrder);
        assertEquals("TEST-206", createdOrder.getOrderCode());
        assertNull(createdOrder.getAppliedCoupon()); // Kupón nebyl aplikován
        assertEquals("NEPLATNY", createdOrder.getAppliedCouponCode()); // Kód se uloží
        assertEquals(0, BigDecimal.ZERO.compareTo(createdOrder.getCouponDiscountAmount())); // Sleva je nula
        // Celková cena jako by kupón nebyl = 1000 + 210 + 150 + 31.50 = 1391.50 -> zaokr. 1391.00
        BigDecimal expectedTotal = new BigDecimal("1391.50");
        BigDecimal expectedRoundedTotal = expectedTotal.setScale(0, RoundingMode.DOWN); // 1391.00
        assertEquals(0, expectedRoundedTotal.compareTo(createdOrder.getTotalPrice()));
        assertNotNull(createdOrder.getOriginalTotalPrice());
        assertEquals(0, expectedTotal.compareTo(createdOrder.getOriginalTotalPrice()));


        verify(orderCodeGeneratorService).getNextOrderCode();
        verify(couponService, never()).markCouponAsUsed(any(Coupon.class)); // Kupón se nepoužil
        verify(productRepository).findByIdWithDetails(100L);
        verify(taxRateRepository).findById(standardTaxRate.getId());
    }


    @Test
    @DisplayName("[createOrder] Chyba při výpočtu dopravy vede k výjimce")
    void createOrder_ShippingCalculationFails() {
        // --- Příprava ---
        when(productRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(standardProduct));
        when(designRepository.findById(10L)).thenReturn(Optional.of(testDesign));
        when(glazeRepository.findById(20L)).thenReturn(Optional.of(testGlaze));
        when(roofColorRepository.findById(30L)).thenReturn(Optional.of(testRoofColor));
        when(shippingService.calculateShippingCost(any(Order.class), eq("CZK")))
                .thenThrow(new RuntimeException("Simulated Google API Error")); // Chyba při výpočtu

        CartItemDto itemDto = new CartItemDto();
        itemDto.setProductId(100L);
        itemDto.setQuantity(1);
        itemDto.setCustom(false);
        itemDto.setSelectedDesignId(10L);
        itemDto.setSelectedGlazeId(20L);
        itemDto.setSelectedRoofColorId(30L);
        // OPRAVA: Přidání ID sazby do DTO
        itemDto.setSelectedTaxRateId(standardTaxRate.getId());

        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(1L);
        request.setUseCustomerAddresses(true);
        request.setPaymentMethod("BANK_TRANSFER");
        request.setCurrency("CZK");
        request.setItems(List.of(itemDto));
        // Neposíláme shipping cost, služba si ho má dopočítat

        // --- Provedení & Ověření ---
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            orderService.createOrder(request);
        });

        // Očekáváme RuntimeException zabalenou z OrderService
        assertTrue(exception.getMessage().contains("Failed to calculate shipping cost"));
        // Ověříme, že kritické kroky neproběhly
        verify(orderCodeGeneratorService, never()).getNextOrderCode();
        verify(orderRepository, never()).save(any(Order.class));
        verify(emailService, never()).sendOrderConfirmationEmail(any(Order.class));
        verify(productRepository).findByIdWithDetails(100L); // Načtení produktu proběhne
        verify(taxRateRepository).findById(standardTaxRate.getId()); // Načtení sazby proběhne
    }

    // --- Testy pro updateOrderState, markDepositAsPaid, markOrderAsFullyPaid (zůstávají stejné) ---

    @Test
    @DisplayName("[updateOrderState] Úspěšně změní stav objednávky")
    void updateOrderState_Success() {
        Long orderId = 5L;
        Long newStateId = 2L;
        Order existingOrder = new Order();
        existingOrder.setId(orderId);
        existingOrder.setOrderCode("TEST-STATE");
        existingOrder.setStateOfOrder(initialState);

        OrderState processingState = new OrderState();
        processingState.setId(newStateId);
        processingState.setCode("PROCESSING");
        processingState.setName("Zpracovává se");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(existingOrder));
        when(orderStateRepository.findById(newStateId)).thenReturn(Optional.of(processingState));
        when(orderRepository.save(any(Order.class))).thenReturn(existingOrder);
        doNothing().when(emailService).sendOrderStatusUpdateEmail(any(Order.class), any(OrderState.class));

        Order updatedOrder = orderService.updateOrderState(orderId, newStateId);

        assertNotNull(updatedOrder);
        assertEquals(processingState, updatedOrder.getStateOfOrder());
        verify(orderRepository).save(existingOrder);
        verify(emailService).sendOrderStatusUpdateEmail(existingOrder, processingState);
    }

    @Test
    @DisplayName("[markDepositAsPaid] Úspěšně označí zálohu zaplacenou")
    void markDepositAsPaid_Success() {
        Long orderId = 6L;
        Order orderAwaitingDeposit = new Order();
        orderAwaitingDeposit.setId(orderId);
        orderAwaitingDeposit.setOrderCode("TEST-DEPOSIT");
        orderAwaitingDeposit.setDepositAmount(new BigDecimal("100.00"));
        orderAwaitingDeposit.setPaymentStatus("AWAITING_DEPOSIT");
        orderAwaitingDeposit.setSfProformaInvoiceId(555L);
        orderAwaitingDeposit.setCurrency("CZK");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderAwaitingDeposit));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        doNothing().when(invoiceService).generateTaxDocumentForDeposit(any(Order.class));
        doNothing().when(invoiceService).markInvoiceAsPaidInSF(anyLong(), any(BigDecimal.class), any(LocalDate.class), anyString(), anyString());


        Order updatedOrder = orderService.markDepositAsPaid(orderId, LocalDate.now());

        assertEquals("DEPOSIT_PAID", updatedOrder.getPaymentStatus());
        assertNotNull(updatedOrder.getDepositPaidDate());
        verify(orderRepository).save(orderAwaitingDeposit);
        verify(invoiceService).generateTaxDocumentForDeposit(orderAwaitingDeposit);
        verify(invoiceService).markInvoiceAsPaidInSF(eq(555L), eq(new BigDecimal("100.00")), eq(LocalDate.now()), anyString(), eq("TEST-DEPOSIT"));
    }

    @Test
    @DisplayName("[markOrderAsFullyPaid] Úspěšně označí objednávku zaplacenou (bez předchozí zálohy)")
    void markOrderAsFullyPaid_NoDeposit_Success() {
        Long orderId = 7L;
        Order orderPending = new Order();
        orderPending.setId(orderId);
        orderPending.setOrderCode("TEST-FULLPAY");
        orderPending.setPaymentStatus("PENDING");
        orderPending.setTotalPrice(new BigDecimal("500.00"));
        orderPending.setSfFinalInvoiceId(777L);
        orderPending.setCurrency("EUR");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderPending));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        doNothing().when(invoiceService).markInvoiceAsPaidInSF(anyLong(), any(BigDecimal.class), any(LocalDate.class), anyString(), anyString());


        Order updatedOrder = orderService.markOrderAsFullyPaid(orderId, LocalDate.now());

        assertEquals("PAID", updatedOrder.getPaymentStatus());
        assertNotNull(updatedOrder.getPaymentDate());
        assertNull(updatedOrder.getDepositPaidDate());
        verify(orderRepository).save(orderPending);
        verify(invoiceService).markInvoiceAsPaidInSF(eq(777L), eq(new BigDecimal("500.00")), eq(LocalDate.now()), anyString(), eq("TEST-FULLPAY"));
    }

    @Test
    @DisplayName("[markOrderAsFullyPaid] Úspěšně označí objednávku zaplacenou (po záloze)")
    void markOrderAsFullyPaid_AfterDeposit_Success() {
        Long orderId = 8L;
        Order orderDepositPaid = new Order();
        orderDepositPaid.setId(orderId);
        orderDepositPaid.setOrderCode("TEST-FINALPAY");
        orderDepositPaid.setPaymentStatus("DEPOSIT_PAID");
        orderDepositPaid.setTotalPrice(new BigDecimal("600.00")); // Celková cena
        orderDepositPaid.setDepositAmount(new BigDecimal("300.00")); // Zaplacená záloha
        orderDepositPaid.setDepositPaidDate(LocalDateTime.now().minusDays(1));
        orderDepositPaid.setSfFinalInvoiceId(888L);
        orderDepositPaid.setCurrency("CZK");


        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderDepositPaid));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        doNothing().when(invoiceService).markInvoiceAsPaidInSF(anyLong(), any(BigDecimal.class), any(LocalDate.class), anyString(), anyString());

        Order updatedOrder = orderService.markOrderAsFullyPaid(orderId, LocalDate.now());

        assertEquals("PAID", updatedOrder.getPaymentStatus());
        assertNotNull(updatedOrder.getPaymentDate());
        assertNotNull(updatedOrder.getDepositPaidDate());
        verify(orderRepository).save(orderDepositPaid);
        // Ověříme, že se do SF posílá ZBYTEK k úhradě (Total - Deposit)
        BigDecimal remainingAmount = new BigDecimal("300.00"); // 600 - 300
        verify(invoiceService).markInvoiceAsPaidInSF(eq(888L), eq(remainingAmount), eq(LocalDate.now()), anyString(), eq("TEST-FINALPAY"));
    }
} // Konec třídy OrderServiceTest