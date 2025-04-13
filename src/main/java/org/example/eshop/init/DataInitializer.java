package org.example.eshop.init;

import jakarta.transaction.Transactional;
import org.example.eshop.config.PriceConstants;
import org.example.eshop.dto.AddonDto;
import org.example.eshop.dto.CartItemDto;
import org.example.eshop.dto.CreateOrderRequest;
import org.example.eshop.model.*;
import org.example.eshop.repository.*;
import org.example.eshop.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Component
@org.springframework.core.annotation.Order(Ordered.LOWEST_PRECEDENCE)
public class DataInitializer implements ApplicationRunner, PriceConstants {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    // --- Repositories ---
    @Autowired private TaxRateRepository taxRateRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private AddonsRepository addonsRepository;
    @Autowired private OrderStateRepository orderStateRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ImageRepository imageRepository;
    @Autowired private EmailTemplateConfigRepository emailTemplateConfigRepository;
    @Autowired private DesignRepository designRepository;
    @Autowired private GlazeRepository glazeRepository;
    @Autowired private RoofColorRepository roofColorRepository;
    @Autowired private OrderRepository orderRepository;

    // --- Services ---
    @Autowired private OrderService orderService;
    @Autowired @Qualifier("googleMapsShippingService") private ShippingService shippingService;
    @Autowired private PaymentService paymentService;
    @Autowired private OrderCodeGeneratorService orderCodeGeneratorService;

    // --- Proměnné pro entity ---
    private Customer testCustomer;
    private Customer adminCustomer;
    private Product drevnikKlasik;
    private Product drevnikModern;
    private Product drevnikNaMiru;
    private Design designKlasik;
    private Design designKlasikPlus;
    private Design designModernSimple;
    private Design designModernBox;
    private Glaze glazeOrech;
    private Glaze glazePalisandr;
    private Glaze glazeBezbarvy;
    private Glaze glazeSeda;
    private Glaze glazeAntracit;
    private Glaze glazeTeak;
    private Glaze glazeTeakSpecial;
    private RoofColor colorAntracit;
    private RoofColor colorCervena;
    private RoofColor colorZelena;
    private RoofColor colorStribrna;
    private RoofColor colorStribrnaSpecial;
    private Addon policka;
    private Addon drzakNarzadi;
    private TaxRate standardRate;
    private OrderState stateNew;
    private OrderState stateProcessing;
    private OrderState stateShipped;
    private OrderState stateDepositPaid;
    private OrderState stateInProduction;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        log.info("Initializing test data...");
        try {
            // 0. Číselníky
            createTaxRates();
            createOrderStates();
            createEmailTemplates();
            standardRate = taxRateRepository.findByNameIgnoreCase("Základní 21%").orElseThrow();
            stateNew = orderStateRepository.findByCodeIgnoreCase("NEW").orElseThrow();
            stateProcessing = orderStateRepository.findByCodeIgnoreCase("PROCESSING").orElseThrow();
            stateShipped = orderStateRepository.findByCodeIgnoreCase("SHIPPED").orElseThrow();
            stateDepositPaid = orderStateRepository.findByCodeIgnoreCase("DEPOSIT_PAID").orElseThrow(() -> new RuntimeException("Order state DEPOSIT_PAID not found!"));
            stateInProduction = orderStateRepository.findByCodeIgnoreCase("IN_PRODUCTION").orElse(stateProcessing);

            // 1. Atributy
            designKlasik = createDesign("Klasik", "Standardní klasický vzhled");
            designKlasikPlus = createDesign("Klasik Plus", "Klasický vzhled s přidanými prvky");
            designModernSimple = createDesign("Modern Simple", "Jednoduchý moderní design");
            designModernBox = createDesign("Modern Box", "Moderní krabicový design");
            glazeOrech = createGlaze("Ořech", null, null, null);
            glazePalisandr = createGlaze("Palisandr", null, null, null);
            glazeBezbarvy = createGlaze("Bezbarvý lak", null, null, null);
            glazeSeda = createGlaze("Šedá lazura", null, null, null);
            glazeAntracit = createGlaze("Antracit", null, null, null);
            glazeTeak = createGlaze("Teak", null, null, null);
            glazeTeakSpecial = createGlaze("Teak Premium", new BigDecimal("200.00"), new BigDecimal("8.00"), "/images/vzorky/teak_prem.jpg");
            colorAntracit = createRoofColor("Antracit", null, null, null);
            colorCervena = createRoofColor("Červená", null, null, null);
            colorZelena = createRoofColor("Zelená", null, null, null);
            colorStribrna = createRoofColor("Stříbrná", null, null, null);
            colorStribrnaSpecial = createRoofColor("Stříbrná metalická", new BigDecimal("300.00"), new BigDecimal("12.00"), "/images/vzorky/stribrna_met.jpg");

            // 2. Doplňky
            policka = createAddon("Polička navíc", "Kvalitní dřevěná polička", new BigDecimal("350.00"), new BigDecimal("15.00"), "POL-01", true);
            drzakNarzadi = createAddon("Držák na nářadí", "Praktický držák", new BigDecimal("500.00"), new BigDecimal("20.00"), "DRZ-01", true);
            createAddon("Starý doplněk", "Toto už nenabízíme", new BigDecimal("100.00"), new BigDecimal("4.00"), "OLD-01", false);

            // 3. Produkty
            drevnikKlasik = createProduct("Dřevník Klasik", "klasik", "Robustní dřevník...", new BigDecimal("5990.00"), new BigDecimal("250.00"), "Klasik", "Smrk", new BigDecimal("180"), new BigDecimal("200"), new BigDecimal("80"), "Standardní", standardRate, Collections.emptySet(), Set.of(designKlasik, designKlasikPlus), Set.of(glazeOrech, glazePalisandr, glazeBezbarvy), Set.of(colorAntracit, colorCervena, colorZelena), false);
            createImage(drevnikKlasik, "/images/drevnik-klasik-1.jpg", "Dřevník Klasik 1", "Dřevník Klasik 1", 0);
            createImage(drevnikKlasik, "/images/drevnik-klasik-2.jpg", "Dřevník Klasik 2", "Dřevník Klasik 2", 1);

            drevnikModern = createProduct("Dřevník Modern", "modern", "Moderní dřevník...", new BigDecimal("7490.00"), new BigDecimal("310.00"), "Modern", "Modřín", new BigDecimal("190"), new BigDecimal("250"), new BigDecimal("90"), "Minimální", standardRate, Collections.emptySet(), Set.of(designModernSimple, designModernBox), Set.of(glazeSeda, glazeAntracit, glazeTeak, glazeTeakSpecial), Set.of(colorAntracit, colorStribrna, colorStribrnaSpecial), false);
            createImage(drevnikModern, "/images/drevnik-modern-1.jpg", "Dřevník Modern", "Dřevník Modern 1", 0);

            drevnikNaMiru = createProduct("Dřevník na míru", "na-miru", "Navrhněte si dřevník...", null, null, "Na míru", "Smrk/Modřín", null, null, null, null, standardRate, Set.of(policka, drzakNarzadi),
                    Set.of(designKlasik, designModernSimple), // Jaké designy jsou povoleny pro custom?
                    Set.of(glazeOrech, glazeTeakSpecial, glazeBezbarvy), // Jaké lazury?
                    Set.of(colorAntracit, colorCervena, colorStribrnaSpecial), // Jaké barvy?
                    true);
            createOrUpdateConfigurator(drevnikNaMiru);
            createImage(drevnikNaMiru, "/images/drevnik-na-miru.jpg", "Dřevník na míru", "Dřevník na míru", 0);

            // 4. Zákazníci
            testCustomer = createCustomer("Test", "Uživatel", "test@example.com", "123456789", "heslo123");
            adminCustomer = createAdmin("Admin", "Eshopu", "admin@example.com", "987654321", "adminHeslo123");

            // 5. Testovací objednávky
            if (orderRepository.count() == 0) {
                log.info("Creating test orders...");
                createSampleOrders();
            } else {
                log.info("Orders already exist ({}), skipping test order creation.", orderRepository.count());
            }

            log.info("Test data initialization finished successfully.");
        } catch (Exception e) {
            log.error("Error during test data initialization!", e);
            // throw new RuntimeException("Failed to initialize data", e); // Odkomentuj pro zastavení aplikace při chybě
        }
    }

    // Metoda pro vytvoření ukázkových objednávek - UPRAVENO
    private void createSampleOrders() {
        if (testCustomer == null || adminCustomer == null || drevnikKlasik == null || drevnikModern == null || drevnikNaMiru == null ||
                designKlasik == null || glazeOrech == null || colorAntracit == null || designModernSimple == null || glazeTeakSpecial == null || colorStribrnaSpecial == null ||
                policka == null || drzakNarzadi == null || stateProcessing == null || stateInProduction == null || glazeBezbarvy == null || colorCervena == null) {
            log.error("Cannot create sample orders, required base data is missing! Check entity references in DataInitializer fields.");
            return;
        }

        // Objednávka 1: Standardní produkt, Bankovní převod, CZK, Stav: Nová
        try {
            CartItemDto item1_1 = new CartItemDto();
            item1_1.setProductId(drevnikKlasik.getId()); item1_1.setQuantity(1); item1_1.setCustom(false);
            item1_1.setSelectedDesignId(designKlasik.getId());
            item1_1.setSelectedGlazeId(glazeOrech.getId());
            item1_1.setSelectedRoofColorId(colorAntracit.getId());
            createTestOrder(testCustomer, "BANK_TRANSFER", "CZK", List.of(item1_1), null, "První testovací obj.");
        } catch (Exception e) { log.error("Failed to create sample order 1", e); }

        // Objednávka 2: Produkt na míru, Dobírka, EUR - UPRAVENO pro ID atributů
        try {
            CartItemDto item2_1 = new CartItemDto();
            item2_1.setProductId(drevnikNaMiru.getId()); item2_1.setQuantity(1); item2_1.setCustom(true);
            item2_1.setCustomDimensions(Map.of( "length", new BigDecimal("350"), "width", new BigDecimal("120"), "height", new BigDecimal("210")));
            // --- Nastavení ID atributů ---
            item2_1.setSelectedGlazeId(glazeTeakSpecial.getId());
            item2_1.setSelectedRoofColorId(colorAntracit.getId());
            item2_1.setSelectedDesignId(designModernSimple.getId());
            // --- Konec Nastavení ID ---
            item2_1.setCustomHasDivider(true); item2_1.setCustomHasGutter(false); item2_1.setCustomHasGardenShed(false);
            AddonDto addonPolicka = new AddonDto(policka.getId(), 2);
            AddonDto addonDrzak = new AddonDto(drzakNarzadi.getId(), 1);
            item2_1.setSelectedAddons(List.of(addonPolicka, addonDrzak));
            // OrderService se postará o zjištění, že je třeba záloha a zavolá generateProformaInvoice
            createTestOrder(testCustomer, "CASH_ON_DELIVERY", "EUR", List.of(item2_1), null, "Obj. na míru s doplňky, dobírka.");
        } catch (Exception e) { log.error("Failed to create sample order 2", e); }


        // Objednávka 3: Jiný standardní produkt, Zaplaceno převodem, CZK, Stav: Zpracovává se
        try {
            CartItemDto item3_1 = new CartItemDto();
            item3_1.setProductId(drevnikModern.getId()); item3_1.setQuantity(2); item3_1.setCustom(false);
            item3_1.setSelectedDesignId(designModernSimple.getId());
            item3_1.setSelectedGlazeId(glazeTeakSpecial.getId());
            item3_1.setSelectedRoofColorId(colorStribrnaSpecial.getId());
            Order order3 = createTestOrder(adminCustomer, "BANK_TRANSFER", "CZK", List.of(item3_1), null, "Obj. admina, 2 kusy modern.");

            // Manuální update stavu a platby (POZOR: Může triggerovat SF API, pokud není mockováno/vypnuto)
            if (order3 != null) {
                try {
                    log.info("Manually updating order {} to PAID and state PROCESSING (SF API calls might be triggered if active)", order3.getOrderCode());
                    // ZAKOMENTOVÁNO - může volat SF API pro označení finální faktury
                    // orderService.markOrderAsFullyPaid(order3.getId(), LocalDate.now().minusDays(2));
                    // Místo toho nastavíme statusy přímo, abychom se vyhnuli SF API
                    order3.setPaymentStatus("PAID");
                    order3.setPaymentDate(LocalDate.now().minusDays(2).atStartOfDay());
                    order3.setStateOfOrder(stateProcessing); // Nastavíme rovnou i stav
                    orderRepository.save(order3); // Uložíme změny
                    // orderService.updateOrderState(order3.getId(), stateProcessing.getId()); // Toto už není potřeba
                    log.info("Manually updated order {} to PAID and state PROCESSING (bypassed SF calls)", order3.getOrderCode());
                } catch (Exception e) {
                    log.error("Failed to manually update state/payment for order {}: {}", order3.getOrderCode(), e.getMessage());
                }
            }
        } catch (Exception e) { log.error("Failed to create/update sample order 3", e); }

        // Objednávka 4: Produkt na míru, Zaplacená záloha, Stav: Ve výrobě - UPRAVENO
        try {
            CartItemDto item4_1 = new CartItemDto();
            item4_1.setProductId(drevnikNaMiru.getId()); item4_1.setQuantity(1); item4_1.setCustom(true);
            item4_1.setCustomDimensions(Map.of("length", new BigDecimal("280"), "width", new BigDecimal("90"), "height", new BigDecimal("195")));
            // --- Nastavení ID atributů ---
            item4_1.setSelectedGlazeId(glazeBezbarvy.getId());
            item4_1.setSelectedRoofColorId(colorCervena.getId());
            item4_1.setSelectedDesignId(designKlasik.getId());
            // --- Konec Nastavení ID ---
            item4_1.setCustomHasDivider(false); item4_1.setCustomHasGutter(true); item4_1.setCustomHasGardenShed(false);
            AddonDto addonPolicka4 = new AddonDto(policka.getId(), 1);
            item4_1.setSelectedAddons(List.of(addonPolicka4));
            Order order4 = createTestOrder(testCustomer, "BANK_TRANSFER", "CZK", List.of(item4_1), null, "Malý na míru s okapem.");

            // Manuální update stavu a platby zálohy (POZOR: Může triggerovat SF API a generování DDKP)
            if (order4 != null && order4.getDepositAmount() != null && order4.getDepositAmount().compareTo(BigDecimal.ZERO) > 0) {
                try {
                    log.info("Manually updating order {} to DEPOSIT_PAID and state IN_PRODUCTION (SF API calls might be triggered if active)", order4.getOrderCode());
                    // ZAKOMENTOVÁNO - volá SF API a DDKP
                    // orderService.markDepositAsPaid(order4.getId(), LocalDate.now().minusDays(3));
                    // orderService.updateOrderState(order4.getId(), stateInProduction.getId());
                    // Místo toho nastavíme statusy přímo
                    order4.setPaymentStatus("DEPOSIT_PAID");
                    order4.setDepositPaidDate(LocalDate.now().minusDays(3).atStartOfDay());
                    order4.setStateOfOrder(stateInProduction);
                    orderRepository.save(order4);
                    log.info("Manually updated order {} to DEPOSIT_PAID and state IN_PRODUCTION (bypassed SF calls)", order4.getOrderCode());

                } catch (Exception e) {
                    log.error("Failed to manually update state/deposit payment for order {}: {}", order4.getOrderCode(), e.getMessage());
                }
            } else if (order4 != null) {
                log.warn("Could not mark deposit paid for order {}, deposit amount is missing or zero.", order4.getOrderCode());
            }
        } catch (Exception e) { log.error("Failed to create/update sample order 4", e); }
    }


    /**
     * Pomocná metoda pro vytvoření testovací objednávky POUZE pomocí OrderService.
     * Explicitně NEVOLÁ metody pro generování faktur nebo odesílání emailů z této třídy.
     */
    private Order createTestOrder(Customer customer, String paymentMethod, String currency, List<CartItemDto> items, String couponCode, String note) {
        log.info("Attempting to create test order via OrderService for customer {} with {} items in {}", customer.getEmail(), items.size(), currency);
        try {
            // Simulace dummy dopravy
            BigDecimal shippingCostNoTax = EURO_CURRENCY.equals(currency) ? new BigDecimal("25.00") : new BigDecimal("500.00");
            BigDecimal shippingTaxRate = new BigDecimal("0.21");
            BigDecimal shippingTax = shippingCostNoTax.multiply(shippingTaxRate).setScale(PRICE_SCALE, ROUNDING_MODE);

            CreateOrderRequest request = new CreateOrderRequest();
            request.setCustomerId(customer.getId());
            request.setUseCustomerAddresses(true); // Ujisti se, že testovací zákazníci mají adresy!
            request.setPaymentMethod(paymentMethod);
            request.setCurrency(currency);
            request.setCustomerNote(note);
            request.setCouponCode(couponCode);
            request.setItems(items);
            request.setShippingCostNoTax(shippingCostNoTax);
            request.setShippingTax(shippingTax);

            // Voláme OrderService.createOrder, který zařídí vše ostatní
            // (včetně POTENCIÁLNÍHO volání generateProformaInvoice, pokud je to custom objednávka)
            Order createdOrder = orderService.createOrder(request);

            log.info("Successfully initiated test order creation via OrderService: {} (Total: {} {})",
                    createdOrder.getOrderCode(), createdOrder.getTotalPrice(), createdOrder.getCurrency());

            return createdOrder;

        } catch (Exception e) {
            String itemIds = items.stream().map(i -> i.getProductId() != null ? i.getProductId().toString() : "null").collect(Collectors.joining(", "));
            log.error("Failed to create test order via OrderService for customer {} (Items: [{}]): {}",
                    customer.getEmail(), itemIds, e.getMessage(), e);
            // Můžeme zvážit logování stack trace pro lepší diagnostiku
            // log.error("Stack trace:", e);
            return null;
        }
    }


    // --- Ostatní pomocné metody (createTaxRates, createOrderStates, createEmailTemplates, createDesign, etc.) ---
    // Tyto metody zůstávají beze změny oproti předchozí verzi

    private void createTaxRates() {
        if (taxRateRepository.count() == 0) {
            log.info("Creating default Tax Rates...");
            TaxRate rate21 = new TaxRate(null, "Základní 21%", new BigDecimal("0.2100"), false, null);
            TaxRate rate12 = new TaxRate(null, "Snížená 12%", new BigDecimal("0.1200"), false, null);
            TaxRate rateRC = new TaxRate(null, "Přenesená daňová povinnost", BigDecimal.ZERO, true, null);
            taxRateRepository.saveAll(List.of(rate21, rate12, rateRC));
        }
    }

    private void createOrderStates() {
        if (orderStateRepository.count() == 0) {
            log.info("Creating default Order States...");
            createState("NEW", "Nová", "Objednávka přijata systémem.", 10, false);
            createState("AWAITING_DEPOSIT", "Čeká na zálohu", "Čeká na zaplacení zálohy (produkt na míru).", 20, false);
            createState("AWAITING_PAYMENT", "Čeká na platbu", "Čeká na platbu bankovním převodem.", 30, false);
            createState("DEPOSIT_PAID", "Záloha zaplacena", "Záloha byla zaplacena.", 33, false);
            createState("PAID", "Zaplaceno", "Objednávka byla plně zaplacena.", 35, false);
            createState("PROCESSING", "Zpracovává se", "Objednávka se připravuje k výrobě/expedici.", 40, false);
            createState("IN_PRODUCTION", "Ve výrobě", "Produkt je aktuálně ve výrobě.", 50, false);
            createState("AT_ZINC_PLATING", "V zinkovně", "Produkt je v zinkovně.", 60, false);
            createState("READY_TO_SHIP", "Připraveno k odeslání", "Objednávka je připravena k expedici.", 70, false);
            createState("SHIPPED", "Odesláno", "Objednávka byla předána dopravci.", 80, false);
            createState("DELIVERED", "Doručeno", "Objednávka byla úspěšně doručena.", 90, true);
            createState("CANCELLED", "Zrušeno", "Objednávka byla zrušena.", 100, true);
        }
    }

    private OrderState createState(String code, String name, String description, int order, boolean isFinal) {
        OrderState state = new OrderState();
        state.setCode(code.toUpperCase()); state.setName(name); state.setDescription(description);
        state.setDisplayOrder(order); state.setFinalState(isFinal);
        try {
            return orderStateRepository.save(state);
        } catch (Exception e) {
            log.error("!!! CRITICAL FAILURE: Failed to save OrderState with code '{}': {} !!!", code.toUpperCase(), e.getMessage(), e);
            throw new RuntimeException("Failed to save OrderState with code '" + code.toUpperCase() + "'", e);
        }
    }

    private void createEmailTemplates() {
        if (emailTemplateConfigRepository.count() == 0) {
            log.info("Creating default Email Template Configs...");
            createEmailConfig("NEW", false, "emails/order-confirmation-admin", "{shopName} - Nová objednávka č. {orderCode}");
            createEmailConfig("SHIPPED", true, "emails/order-status-shipped", "{shopName} - Vaše objednávka č. {orderCode} byla odeslána");
            // ... (ostatní konfigurace emailů) ...
            createEmailConfig("DELIVERED", false, "emails/order-status-delivered", "{shopName} - Vaše objednávka č. {orderCode} byla doručena");
            createEmailConfig("CANCELLED", true, "emails/order-status-cancelled", "{shopName} - Vaše objednávka č. {orderCode} byla zrušena");
            createEmailConfig("AWAITING_DEPOSIT", true, "emails/order-status-awaiting-deposit", "{shopName} - Výzva k úhradě zálohy k obj. č. {orderCode}");
            createEmailConfig("AWAITING_PAYMENT", true, "emails/order-status-awaiting-payment", "{shopName} - Výzva k úhradě obj. č. {orderCode}");
            createEmailConfig("DEPOSIT_PAID", true, "emails/order-status-deposit-paid", "{shopName} - Přijali jsme Vaši zálohu k obj. č. {orderCode}");
            createEmailConfig("PAID", true, "emails/order-status-paid", "{shopName} - Objednávka č. {orderCode} byla zaplacena");
            createEmailConfig("PROCESSING", true, "emails/order-status-processing", "{shopName} - Objednávka č. {orderCode} se zpracovává");
            createEmailConfig("IN_PRODUCTION", true, "emails/order-status-in-production", "{shopName} - Objednávka č. {orderCode} je ve výrobě");
        }
    }

    private EmailTemplateConfig createEmailConfig(String stateCode, boolean send, String template, String subject) {
        if (emailTemplateConfigRepository.findByStateCodeIgnoreCase(stateCode).isEmpty()) {
            EmailTemplateConfig config = new EmailTemplateConfig();
            config.setStateCode(stateCode.toUpperCase()); config.setSendEmail(send);
            config.setTemplateName(template); config.setSubjectTemplate(subject);
            return emailTemplateConfigRepository.save(config);
        }
        return null;
    }

    private Design createDesign(String name, String description) {
        return designRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    Design d = new Design(); d.setName(name); d.setDescription(description); d.setActive(true);
                    log.debug("Creating Design: {}", name); return designRepository.save(d);
                });
    }

    private Glaze createGlaze(String name, BigDecimal surchargeCZK, BigDecimal surchargeEUR, String imageUrl) {
        return glazeRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    Glaze g = new Glaze(); g.setName(name); g.setPriceSurchargeCZK(surchargeCZK); g.setPriceSurchargeEUR(surchargeEUR); g.setImageUrl(imageUrl); g.setActive(true);
                    log.debug("Creating Glaze: {}", name); return glazeRepository.save(g);
                });
    }

    private RoofColor createRoofColor(String name, BigDecimal surchargeCZK, BigDecimal surchargeEUR, String imageUrl) {
        return roofColorRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    RoofColor rc = new RoofColor(); rc.setName(name); rc.setPriceSurchargeCZK(surchargeCZK); rc.setPriceSurchargeEUR(surchargeEUR); rc.setImageUrl(imageUrl); rc.setActive(true);
                    log.debug("Creating RoofColor: {}", name); return roofColorRepository.save(rc);
                });
    }

    private Addon createAddon(String name, String desc, BigDecimal czk, BigDecimal eur, String sku, boolean active) {
        return addonsRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    Addon addon = new Addon(); addon.setName(name); addon.setDescription(desc); addon.setPriceCZK(czk); addon.setPriceEUR(eur); addon.setSku(sku); addon.setActive(active);
                    log.debug("Creating Addon: {}", name); return addonsRepository.save(addon);
                });
    }

    private Product createProduct(String name, String slug, String desc, BigDecimal czk, BigDecimal eur,
                                  String modelInfo, String material, BigDecimal h, BigDecimal l, BigDecimal w,
                                  String roofOverstep, TaxRate taxRate, Set<Addon> addons,
                                  Set<Design> designs, Set<Glaze> glazes, Set<RoofColor> roofColors,
                                  boolean customisable) {
        Optional<Product> existingProductOpt = productRepository.findBySlugIgnoreCase(slug);
        if (existingProductOpt.isPresent()) {
            log.warn("Product with slug '{}' already exists. Returning existing.", slug);
            return existingProductOpt.get();
        }
        Product p = new Product();
        p.setName(name); p.setSlug(slug); p.setDescription(desc); p.setBasePriceCZK(czk);
        p.setBasePriceEUR(eur); p.setModel(modelInfo); p.setMaterial(material); p.setHeight(h);
        p.setLength(l); p.setWidth(w); p.setRoofOverstep(roofOverstep); p.setTaxRate(taxRate);
        p.setAvailableAddons(addons != null ? new HashSet<>(addons) : Collections.emptySet());
        p.setAvailableDesigns(designs != null ? new HashSet<>(designs) : Collections.emptySet());
        p.setAvailableGlazes(glazes != null ? new HashSet<>(glazes) : Collections.emptySet());
        p.setAvailableRoofColors(roofColors != null ? new HashSet<>(roofColors) : Collections.emptySet());
        p.setCustomisable(customisable); p.setActive(true);
        p.setMetaTitle(name + " - Dřevník Kolář"); p.setMetaDescription("Kupte si kvalitní dřevník " + name + ". " + (desc != null ? desc.substring(0, Math.min(desc.length(), 100)) : "") + "...");
        log.debug("Creating Product: {}", name);
        Product savedP = productRepository.save(p);
        if (savedP.isCustomisable()) {
            createOrUpdateConfigurator(savedP);
        }
        return savedP;
    }

    private void createOrUpdateConfigurator(Product product) {
        ProductConfigurator configurator = product.getConfigurator();
        if (configurator == null) {
            configurator = new ProductConfigurator();
            configurator.setProduct(product);
            log.info("Creating new configurator for product ID: {}", product.getId());
        } else {
            log.info("Updating existing configurator for product ID: {}", product.getId());
        }
        configurator.setMinLength(new BigDecimal("100.00")); configurator.setMaxLength(new BigDecimal("500.00"));
        configurator.setMinWidth(new BigDecimal("50.00")); configurator.setMaxWidth(new BigDecimal("200.00"));
        configurator.setMinHeight(new BigDecimal("150.00")); configurator.setMaxHeight(new BigDecimal("300.00"));
        configurator.setPricePerCmHeightCZK(new BigDecimal("14.00")); configurator.setPricePerCmLengthCZK(new BigDecimal("99.00"));
        configurator.setPricePerCmDepthCZK(new BigDecimal("25.00")); configurator.setDividerPricePerCmDepthCZK(new BigDecimal("13.00"));
        configurator.setDesignPriceCZK(BigDecimal.ZERO); configurator.setGutterPriceCZK(new BigDecimal("1000.00")); configurator.setShedPriceCZK(new BigDecimal("5000.00"));
        configurator.setPricePerCmHeightEUR(new BigDecimal("0.56")); configurator.setPricePerCmLengthEUR(new BigDecimal("3.96"));
        configurator.setPricePerCmDepthEUR(new BigDecimal("1.00")); configurator.setDividerPricePerCmDepthEUR(new BigDecimal("0.52"));
        configurator.setDesignPriceEUR(BigDecimal.ZERO); configurator.setGutterPriceEUR(new BigDecimal("40.00")); configurator.setShedPriceEUR(new BigDecimal("200.00"));
        product.setConfigurator(configurator); // Přiřadit zpět k produktu pro uložení
    }

    private Image createImage(Product product, String url, String alt, String title, int order) {
        Image img = new Image(); img.setProduct(product); img.setUrl(url); img.setAltText(alt); img.setTitleText(title); img.setDisplayOrder(order);
        return imageRepository.save(img);
    }

    private Customer createCustomer(String fname, String lname, String email, String phone, String password) {
        if (customerRepository.findByEmailIgnoreCase(email).isPresent()) {
            return customerRepository.findByEmailIgnoreCase(email).get();
        }
        Customer c = new Customer(); c.setFirstName(fname); c.setLastName(lname); c.setEmail(email); c.setPhone(phone); c.setPassword(passwordEncoder.encode(password)); c.setEnabled(true); c.setRoles(Set.of("ROLE_USER"));
        c.setInvoiceFirstName(fname); c.setInvoiceLastName(lname); c.setInvoiceStreet("Testovací 123"); c.setInvoiceCity("Testov"); c.setInvoiceZipCode("12345"); c.setInvoiceCountry("Česká republika");
        c.setUseInvoiceAddressAsDelivery(true);
        if (c.isUseInvoiceAddressAsDelivery()) {
            c.setDeliveryStreet(c.getInvoiceStreet()); c.setDeliveryCity(c.getInvoiceCity()); c.setDeliveryZipCode(c.getInvoiceZipCode()); c.setDeliveryCountry(c.getInvoiceCountry()); c.setDeliveryPhone(c.getPhone());
        }
        return customerRepository.save(c);
    }

    private Customer createAdmin(String fname, String lname, String email, String phone, String password) {
        if (customerRepository.findByEmailIgnoreCase(email).isPresent()) {
            log.warn("Admin user {} already exists.", email);
            return customerRepository.findByEmailIgnoreCase(email).get();
        }
        Customer c = new Customer();
        c.setFirstName(fname); c.setLastName(lname); c.setEmail(email); c.setPhone(phone);
        c.setPassword(passwordEncoder.encode(password)); c.setEnabled(true);
        c.setRoles(Set.of("ROLE_USER", "ROLE_ADMIN"));
        c.setInvoiceFirstName(fname); c.setInvoiceLastName(lname); c.setInvoiceStreet("Adminova 1"); c.setInvoiceCity("Hlavní Město"); c.setInvoiceZipCode("11000"); c.setInvoiceCountry("Česká republika");
        c.setUseInvoiceAddressAsDelivery(true);
        if (c.isUseInvoiceAddressAsDelivery()) {
            c.setDeliveryStreet(c.getInvoiceStreet()); c.setDeliveryCity(c.getInvoiceCity()); c.setDeliveryZipCode(c.getInvoiceZipCode()); c.setDeliveryCountry(c.getInvoiceCountry()); c.setDeliveryPhone(c.getPhone());
        }
        log.info("Creating admin user: {}", email);
        return customerRepository.save(c);
    }

} // Konec třídy DataInitializer