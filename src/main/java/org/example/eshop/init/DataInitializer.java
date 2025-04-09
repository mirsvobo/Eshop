package org.example.eshop.init;

import jakarta.transaction.Transactional; // Používat jakarta.transaction
import org.example.eshop.config.PriceConstants; // Přidat import
import org.example.eshop.dto.AddonDto;
import org.example.eshop.dto.CartItemDto;
import org.example.eshop.dto.CreateOrderRequest;
import org.example.eshop.model.*;
import org.example.eshop.repository.*;
import org.example.eshop.service.*; // Přidat import pro OrderService atd.
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier; // Pro ShippingService
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
// import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.example.eshop.model.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode; // Přidat import
import java.time.LocalDate; // Přidat import
import java.util.*; // Import pro Set, List, Collections atd.
import java.util.stream.Collectors;

@Component

@org.springframework.core.annotation.Order(Ordered.LOWEST_PRECEDENCE)
public class DataInitializer implements ApplicationRunner, PriceConstants { // Implementovat PriceConstants

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    // --- Repositories (stávající i nové) ---
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
    @Autowired private OrderRepository orderRepository; // Přidat OrderRepository

    // --- Services (nově potřebné) ---
    @Autowired private OrderService orderService;
    // Použijeme Google Maps Service, ale v createTestOrder budeme simulovat cenu
    @Autowired @Qualifier("googleMapsShippingService") private ShippingService shippingService;

    // --- Proměnné pro uchování vytvořených entit ---
    private Customer testCustomer;
    private Customer adminCustomer;
    private Product drevnikKlasik;
    private Product drevnikModern;
    private Product drevnikNaMiru;
    private Design designKlasik;
    private Design designKlasikPlus; // Přidáno pro přiřazení
    private Design designModernSimple;
    private Design designModernBox; // Přidáno pro přiřazení
    private Glaze glazeOrech;
    private Glaze glazePalisandr; // Přidáno pro přiřazení
    private Glaze glazeBezbarvy; // Přidáno pro přiřazení
    private Glaze glazeSeda; // Přidáno pro přiřazení
    private Glaze glazeAntracit; // Přidáno pro přiřazení
    private Glaze glazeTeak; // Přidáno pro přiřazení
    private Glaze glazeTeakSpecial;
    private RoofColor colorAntracit;
    private RoofColor colorCervena;
    private RoofColor colorZelena; // Přidáno pro přiřazení
    private RoofColor colorStribrna; // Přidáno pro přiřazení
    private RoofColor colorStribrnaSpecial;
    private Addon policka;
    private Addon drzakNarzadi;
    private TaxRate standardRate;
    private OrderState stateNew;
    private OrderState stateProcessing;
    private OrderState stateShipped;
    private OrderState stateDepositPaid;
    private OrderState stateInProduction; // Přidáno pro test
    // ... další potřebné entity ...

    @Override
    @Transactional // Celá metoda poběží v jedné transakci
    public void run(ApplicationArguments args) throws Exception {
        log.info("Initializing test data...");

        try {
            // 0. Základní číselníky
            createTaxRates();
            createOrderStates();
            createEmailTemplates();
            // Uložíme si reference na klíčové entity pro snadnější použití
            standardRate = taxRateRepository.findByNameIgnoreCase("Základní 21%").orElseThrow();
            stateNew = orderStateRepository.findByCodeIgnoreCase("NEW").orElseThrow();
            stateProcessing = orderStateRepository.findByCodeIgnoreCase("PROCESSING").orElseThrow();
            stateShipped = orderStateRepository.findByCodeIgnoreCase("SHIPPED").orElseThrow();
            stateDepositPaid = orderStateRepository.findByCodeIgnoreCase("DEPOSIT_PAID").orElseThrow(() -> new RuntimeException("Order state DEPOSIT_PAID not found!"));
            stateInProduction = orderStateRepository.findByCodeIgnoreCase("IN_PRODUCTION").orElse(stateProcessing); // Fallback na processing

            // 1. Vytvoření Atributů
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
            drzakNarzadi = createAddon("Držák na nářadí", "Praktický držák na zahradní nářadí", new BigDecimal("500.00"), new BigDecimal("20.00"), "DRZ-01", true);
            createAddon("Starý doplněk", "Toto už nenabízíme", new BigDecimal("100.00"), new BigDecimal("4.00"), "OLD-01", false);


            // 3. Produkty
            drevnikKlasik = createProduct(
                    "Dřevník Klasik", "klasik", "Robustní dřevník klasického designu.",
                    new BigDecimal("5990.00"), new BigDecimal("250.00"),
                    "Klasik", "Smrk", new BigDecimal("180"), new BigDecimal("200"), new BigDecimal("80"), "Standardní",
                    standardRate, Collections.emptySet(), // Standardní nemá Addons
                    Set.of(designKlasik, designKlasikPlus), // Přiřazení existujících designů
                    Set.of(glazeOrech, glazePalisandr, glazeBezbarvy), // Přiřazení existujících lazur
                    Set.of(colorAntracit, colorCervena, colorZelena), // Přiřazení existujících barev
                    false // Není customisable
            );
            createImage(drevnikKlasik, "/images/drevnik-klasik-1.jpg", "Dřevník Klasik čelní pohled", "Dřevník Klasik 1", 0);
            createImage(drevnikKlasik, "/images/drevnik-klasik-2.jpg", "Dřevník Klasik boční pohled", "Dřevník Klasik 2", 1);


            drevnikModern = createProduct(
                    "Dřevník Modern", "modern", "Moderní dřevník s pultovou střechou.",
                    new BigDecimal("7490.00"), new BigDecimal("310.00"),
                    "Modern", "Modřín", new BigDecimal("190"), new BigDecimal("250"), new BigDecimal("90"), "Minimální",
                    standardRate, Collections.emptySet(), // Standardní nemá Addons
                    Set.of(designModernSimple, designModernBox), // Přiřazení existujících designů
                    Set.of(glazeSeda, glazeAntracit, glazeTeak, glazeTeakSpecial), // Přiřazení existujících lazur
                    Set.of(colorAntracit, colorStribrna, colorStribrnaSpecial), // Přiřazení existujících barev
                    false // Není customisable
            );
            createImage(drevnikModern, "/images/drevnik-modern-1.jpg", "Dřevník Modern", "Dřevník Modern 1", 0);

            drevnikNaMiru = createProduct(
                    "Dřevník na míru", "na-miru", "Navrhněte si dřevník přesně podle vašich představ.",
                    null, null, "Na míru", "Smrk/Modřín", null, null, null, null,
                    standardRate, Set.of(policka, drzakNarzadi), // Má addony
                    Collections.emptySet(), // Nemá pevné designy k výběru
                    Collections.emptySet(), // Nemá pevné lazury k výběru
                    Collections.emptySet(), // Nemá pevné barvy k výběru
                    true // JE customisable
            );
            createOrUpdateConfigurator(drevnikNaMiru);
            createImage(drevnikNaMiru, "/images/drevnik-na-miru.jpg", "Dřevník na míru - ilustrační", "Dřevník na míru", 0);


            // 4. Testovací zákazníci
            testCustomer = createCustomer("Test", "Uživatel", "test@example.com", "123456789", "heslo123");
            adminCustomer = createAdmin("Admin", "Eshopu", "admin@example.com", "987654321", "adminHeslo123");

            // 5. Vytvoření testovacích objednávek (pouze pokud v DB ještě žádné nejsou)
            if (orderRepository.count() == 0) {
                log.info("Creating test orders...");
                createSampleOrders();
            } else {
                log.info("Orders already exist ({}), skipping test order creation.", orderRepository.count());
            }

            log.info("Test data initialization finished successfully.");

        } catch (Exception e) {
            log.error("Error during test data initialization!", e);
            // Zde můžeš rozhodnout, zda aplikace má spadnout, nebo pokračovat
            // throw new RuntimeException("Failed to initialize data", e);
        }
    }

    // --- Metoda pro vytvoření ukázkových objednávek ---
    private void createSampleOrders() {
        if (testCustomer == null || adminCustomer == null || drevnikKlasik == null || drevnikModern == null || drevnikNaMiru == null ||
                designKlasik == null || glazeOrech == null || colorAntracit == null || designModernSimple == null || glazeTeakSpecial == null || colorStribrnaSpecial == null ||
                policka == null || drzakNarzadi == null) {
            log.error("Cannot create sample orders, required base data is missing! Check entity references in DataInitializer fields.");
            return;
        }

        // Objednávka 1: Standardní produkt, Bankovní převod, CZK, Stav: Nová
        try {
            CartItemDto item1_1 = new CartItemDto();
            item1_1.setProductId(drevnikKlasik.getId());
            item1_1.setQuantity(1);
            item1_1.setCustom(false);
            // Použij ID atributů, které jsou skutečně přiřazeny k produktu drevnikKlasik!
            // Pokud drevnikKlasik nemá přiřazený designKlasik, vyhodí to chybu v OrderService.
            // Ověř si v createProduct, jaké sety atributů jsou přiřazeny.
            item1_1.setSelectedDesignId(designKlasik.getId());
            item1_1.setSelectedGlazeId(glazeOrech.getId());
            item1_1.setSelectedRoofColorId(colorAntracit.getId());
            createTestOrder(testCustomer, "BANK_TRANSFER", "CZK", List.of(item1_1), null, "První testovací objednávka standardního dřevníku.");
        } catch (Exception e) { log.error("Failed to create sample order 1", e); }

        // Objednávka 2: Produkt na míru, Dobírka, EUR, Stav: Čeká na zálohu (protože je custom)
        try {
            CartItemDto item2_1 = new CartItemDto();
            item2_1.setProductId(drevnikNaMiru.getId());
            item2_1.setQuantity(1);
            item2_1.setCustom(true);
            item2_1.setCustomDimensions(Map.of(
                    "length", new BigDecimal("350"),
                    "width", new BigDecimal("120"),
                    "height", new BigDecimal("210")
            ));
            item2_1.setCustomGlaze("Teak Premium"); // Textový název
            item2_1.setCustomRoofColor("Antracit"); // Textový název
            item2_1.setCustomDesign("Moderní s přesahem"); // Textový název
            item2_1.setCustomHasDivider(true);
            item2_1.setCustomHasGutter(false);
            item2_1.setCustomHasGardenShed(false);
            AddonDto addonPolicka = new AddonDto(policka.getId(), 2); // 2 poličky
            AddonDto addonDrzak = new AddonDto(drzakNarzadi.getId(), 1);
            item2_1.setSelectedAddons(List.of(addonPolicka, addonDrzak));
            createTestOrder(testCustomer, "CASH_ON_DELIVERY", "EUR", List.of(item2_1), null, "Objednávka na míru s doplňky, platba dobírkou.");
        } catch (Exception e) { log.error("Failed to create sample order 2", e); }


        // Objednávka 3: Jiný standardní produkt, Zaplaceno převodem, CZK, Stav: Zpracovává se
        try {
            CartItemDto item3_1 = new CartItemDto();
            item3_1.setProductId(drevnikModern.getId());
            item3_1.setQuantity(2); // Dva kusy
            item3_1.setCustom(false);
            // Použij ID atributů přiřazených k drevnikModern
            item3_1.setSelectedDesignId(designModernSimple.getId());
            item3_1.setSelectedGlazeId(glazeTeakSpecial.getId()); // Použití příplatkové lazury
            item3_1.setSelectedRoofColorId(colorStribrnaSpecial.getId()); // Použití příplatkové barvy
            Order order3 = createTestOrder(adminCustomer, "BANK_TRANSFER", "CZK", List.of(item3_1), null, "Objednávka admina, 2 kusy moderního dřevníku.");

            // Manuálně označit jako zaplaceno a změnit stav (pro testování)
            if (order3 != null) {
                orderService.markOrderAsFullyPaid(order3.getId(), LocalDate.now().minusDays(2)); // Zaplaceno před 2 dny
                orderService.updateOrderState(order3.getId(), stateProcessing.getId()); // Nastavit stav Zpracovává se
                log.info("Manually updated order {} to PAID and state PROCESSING", order3.getOrderCode());
            }
        } catch (Exception e) { log.error("Failed to create/update sample order 3", e); }

        // Objednávka 4: Produkt na míru, Zaplacená záloha, Stav: Ve výrobě
        try {
            CartItemDto item4_1 = new CartItemDto();
            item4_1.setProductId(drevnikNaMiru.getId());
            item4_1.setQuantity(1);
            item4_1.setCustom(true);
            item4_1.setCustomDimensions(Map.of("length", new BigDecimal("280"), "width", new BigDecimal("90"), "height", new BigDecimal("195")));
            item4_1.setCustomGlaze("Bezbarvý lak");
            item4_1.setCustomRoofColor("Červená");
            item4_1.setCustomDesign("Klasik Mini");
            item4_1.setCustomHasDivider(false);
            item4_1.setCustomHasGutter(true); // S okapem
            item4_1.setCustomHasGardenShed(false);
            AddonDto addonPolicka4 = new AddonDto(policka.getId(), 1);
            item4_1.setSelectedAddons(List.of(addonPolicka4));
            Order order4 = createTestOrder(testCustomer, "BANK_TRANSFER", "CZK", List.of(item4_1), null, "Malý na míru s okapem.");

            // Manuálně označit zálohu jako zaplacenou a změnit stav
            if (order4 != null && order4.getDepositAmount() != null && order4.getDepositAmount().compareTo(BigDecimal.ZERO) > 0) {
                orderService.markDepositAsPaid(order4.getId(), LocalDate.now().minusDays(3)); // Záloha zaplacena před 3 dny
                orderService.updateOrderState(order4.getId(), stateInProduction.getId());
                log.info("Manually updated order {} to DEPOSIT_PAID and state {}", order4.getOrderCode(), stateInProduction.getCode());
            } else if (order4 != null) {
                log.warn("Could not mark deposit paid for order {}, deposit amount is missing or zero.", order4.getOrderCode());
            }
        } catch (Exception e) { log.error("Failed to create/update sample order 4", e); }
    }

    // --- Pomocná metoda pro vytvoření objednávky přes OrderService ---
    private Order createTestOrder(Customer customer, String paymentMethod, String currency, List<CartItemDto> items, String couponCode, String note) {
        log.info("Attempting to create test order for customer {} with {} items in {}", customer.getEmail(), items.size(), currency);
        try {
            // Simulace výpočtu dopravy - POUŽIJ DUMMY HODNOTY
            BigDecimal shippingCostNoTax;
            BigDecimal shippingTaxRate = new BigDecimal("0.21"); // Pevně daná sazba pro dopravu

            if (EURO_CURRENCY.equals(currency)) {
                shippingCostNoTax = new BigDecimal("25.00").setScale(PRICE_SCALE, ROUNDING_MODE); // Dummy EUR shipping
            } else {
                shippingCostNoTax = new BigDecimal("500.00").setScale(PRICE_SCALE, ROUNDING_MODE); // Dummy CZK shipping
            }
            BigDecimal shippingTax = shippingCostNoTax.multiply(shippingTaxRate).setScale(PRICE_SCALE, ROUNDING_MODE);

            // Vytvoření requestu pro OrderService
            CreateOrderRequest request = new CreateOrderRequest();
            request.setCustomerId(customer.getId());
            request.setUseCustomerAddresses(true); // Zajisti, že testovací zákazníci mají adresy!
            request.setPaymentMethod(paymentMethod);
            request.setCurrency(currency);
            request.setCustomerNote(note);
            request.setCouponCode(couponCode);
            request.setItems(items);
            request.setShippingCostNoTax(shippingCostNoTax);
            request.setShippingTax(shippingTax);

            // Zavolání hlavní servisní metody pro vytvoření objednávky
            Order createdOrder = orderService.createOrder(request);
            log.info("Successfully created test order: {} (Total: {} {})",
                    createdOrder.getOrderCode(), createdOrder.getTotalPrice(), createdOrder.getCurrency());

            // --- SMAZAT NEBO ZAKOMENTOVAT NÁSLEDUJÍCÍ BLOK ---
         /*
         // Optional: Update order state for testing different scenarios
          updateTestOrderState(createdOrder, "PROCESSING"); // Toto už neděláme zde
          if (createdOrder.getDepositAmount() != null && createdOrder.getDepositAmount().compareTo(BigDecimal.ZERO) > 0) {
              // orderService.markDepositAsPaid(createdOrder.getId(), LocalDate.now().minusDays(1)); // Toto neděláme zde
              // updateTestOrderState(createdOrder, "IN_PRODUCTION"); // Toto neděláme zde
          } else {
              // orderService.markOrderAsFullyPaid(createdOrder.getId(), LocalDate.now().minusDays(1)); // Toto neděláme zde
              // updateTestOrderState(createdOrder, "SHIPPED"); // Toto neděláme zde
          }
         */
            // --- KONEC BLOKU K ODSTRANĚNÍ ---

            return createdOrder; // Vrátíme vytvořenou objednávku

        } catch (Exception e) {
            // Logování chyby, ale neházení výjimky, aby inicializace mohla pokračovat
            String itemIds = items.stream()
                    .map(i -> i.getProductId() != null ? i.getProductId().toString() : "null")
                    .collect(Collectors.joining(", "));
            log.error("Failed to create test order for customer {} (Items: [{}]): {}",
                    customer.getEmail(), itemIds, e.getMessage(), e);
            return null; // Vrátíme null v případě chyby
        }
    }

    // Metodu updateTestOrderState můžeš smazat, pokud ji už nebudeš potřebovat
 /*
 private void updateTestOrderState(Order order, String stateCode) {
      try {
          OrderState newState = orderStateRepository.findByCodeIgnoreCase(stateCode)
                  .orElseThrow(() -> new RuntimeException("Test OrderState not found: " + stateCode));
          orderService.updateOrderState(order.getId(), newState.getId());
          log.info("Updated test order {} state to {}", order.getOrderCode(), stateCode);
      } catch (Exception e) {
          log.error("Failed to update state for test order {}: {}", order.getOrderCode(), e.getMessage());
      }
  }
 */

    // --- Ostatní pomocné metody pro vytváření entit ---
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
            createState("DEPOSIT_PAID", "Záloha zaplacena", "Záloha byla zaplacena.", 33, false); // Přidáno
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
        // Uložíme kód vždy velkými písmeny
        state.setCode(code.toUpperCase());
        state.setName(name);
        state.setDescription(description);
        state.setDisplayOrder(order);
        state.setFinalState(isFinal);
        try {
            OrderState savedState = orderStateRepository.save(state);
            // Ponecháme logování úspěšného uložení
            log.info("Saved OrderState: Code='{}', Name='{}', ID={}", savedState.getCode(), savedState.getName(), savedState.getId());
            return savedState;
        } catch (Exception e) {
            // Logujeme chybu A ZÁROVEŇ JI ZNOVU VYHODÍME
            log.error("!!! CRITICAL FAILURE: Failed to save OrderState with code '{}': {} !!!", code.toUpperCase(), e.getMessage(), e);
            // Tímto zastavíme inicializaci a uvidíme původní chybu
            throw new RuntimeException("Failed to save OrderState with code '" + code.toUpperCase() + "'", e);
        }
    }

    private void createEmailTemplates() {
        if (emailTemplateConfigRepository.count() == 0) {
            log.info("Creating default Email Template Configs...");
            createEmailConfig("NEW", false, "emails/order-confirmation-admin", "{shopName} - Nová objednávka č. {orderCode}");
            createEmailConfig("SHIPPED", true, "emails/order-status-shipped", "{shopName} - Vaše objednávka č. {orderCode} byla odeslána");
            createEmailConfig("DELIVERED", false, "emails/order-status-delivered", "{shopName} - Vaše objednávka č. {orderCode} byla doručena");
            createEmailConfig("CANCELLED", true, "emails/order-status-cancelled", "{shopName} - Vaše objednávka č. {orderCode} byla zrušena");
            createEmailConfig("AWAITING_DEPOSIT", true, "emails/order-status-awaiting-deposit", "{shopName} - Výzva k úhradě zálohy k obj. č. {orderCode}");
            createEmailConfig("AWAITING_PAYMENT", true, "emails/order-status-awaiting-payment", "{shopName} - Výzva k úhradě obj. č. {orderCode}");
            // Přidat konfiguraci pro další stavy, pokud je potřeba
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
        // Explicitně nastavit dodací adresu, pokud useInvoiceAddressAsDelivery je true
        if (c.isUseInvoiceAddressAsDelivery()) {
            c.setDeliveryStreet(c.getInvoiceStreet());
            c.setDeliveryCity(c.getInvoiceCity());
            c.setDeliveryZipCode(c.getInvoiceZipCode());
            c.setDeliveryCountry(c.getInvoiceCountry());
            c.setDeliveryPhone(c.getPhone()); // Použijeme hlavní telefon
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
        c.setRoles(Set.of("ROLE_USER", "ROLE_ADMIN")); // Role admina

        // --- PŘIDAT ADRESNÍ ÚDAJE ---
        c.setInvoiceFirstName(fname);
        c.setInvoiceLastName(lname);
        c.setInvoiceStreet("Adminova 1"); // Příklad
        c.setInvoiceCity("Hlavní Město"); // Příklad
        c.setInvoiceZipCode("11000"); // Příklad
        c.setInvoiceCountry("Česká republika"); // Příklad
        c.setUseInvoiceAddressAsDelivery(true); // Nastavit dle potřeby

        // Explicitně nastavit dodací adresu, pokud je stejná jako fakturační
        if (c.isUseInvoiceAddressAsDelivery()) {
            c.setDeliveryStreet(c.getInvoiceStreet());
            c.setDeliveryCity(c.getInvoiceCity());
            c.setDeliveryZipCode(c.getInvoiceZipCode());
            c.setDeliveryCountry(c.getInvoiceCountry());
            c.setDeliveryPhone(c.getPhone()); // Použijeme hlavní telefon
        }
        // --- KONEC PŘIDÁNÍ ADRESNÍCH ÚDAJŮ ---

        log.info("Creating admin user: {}", email);
        return customerRepository.save(c);
    }
}