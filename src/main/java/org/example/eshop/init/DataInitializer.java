package org.example.eshop.init;

import jakarta.transaction.Transactional;
import org.example.eshop.config.PriceConstants;
import org.example.eshop.model.*;
import org.example.eshop.repository.*;
import org.example.eshop.service.OrderCodeGeneratorService;
import org.example.eshop.service.OrderService;
import org.example.eshop.service.PaymentService;
import org.example.eshop.service.ShippingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@org.springframework.core.annotation.Order(Ordered.LOWEST_PRECEDENCE)
public class DataInitializer implements ApplicationRunner, PriceConstants {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    // --- Repositories ---
    @Autowired
    private TaxRateRepository taxRateRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private AddonsRepository addonsRepository;
    @Autowired
    private OrderStateRepository orderStateRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ImageRepository imageRepository;
    @Autowired
    private EmailTemplateConfigRepository emailTemplateConfigRepository;
    @Autowired
    private DesignRepository designRepository;
    @Autowired
    private GlazeRepository glazeRepository;
    @Autowired
    private RoofColorRepository roofColorRepository;
    @Autowired
    private OrderRepository orderRepository;

    // --- Services (Keep if helper methods rely on them) ---
    @Autowired
    private OrderService orderService;
    @Autowired
    @Qualifier("googleMapsShippingService")
    private ShippingService shippingService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private OrderCodeGeneratorService orderCodeGeneratorService;

    // Variable references (commented out as sample products/orders are not created)
    // private Customer adminUser1;
    // private Customer adminUser2;
    // private TaxRate standardRate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        log.info("Initializing application data for Drevniky Kolar...");
        try {
            // 1. Core Data (Tax Rates, Order States, Email Templates)
            createTaxRates(); // Upravená metoda
            createOrderStates();
            createEmailTemplates();

            // 2. Attributes (Design, Glaze, Roof Color) - All without surcharge
            log.info("Creating attributes (Designs, Glazes, Roof Colors)...");
            createDesign("Tomáš", "Standardní design Tomáš", null, null, null);
            createDesign("František", "Standardní design František", null, null, null);
            createDesign("Martin", "Standardní design Martin", null, null, null);

            createGlaze("Afromorsia", null, null, null);
            createGlaze("Borovice", null, null, null);
            createGlaze("Kaštan", null, null, null);
            createGlaze("Ořech - rustic", null, null, null);
            createGlaze("Ořech - světlý", null, null, null);
            createGlaze("Ořech - tmavý", null, null, null);
            createGlaze("Vlastní", "Zákazník specifikuje v poznámce objednávky", null, null);

            createRoofColor("Antracit", null, null, null);
            createRoofColor("Černá", null, null, null);
            createRoofColor("Cihlová", null, null, null);
            createRoofColor("Světle hnědá", null, null, null);
            createRoofColor("Tmavě hnědá", null, null, null);
            createRoofColor("Vlastní", "Zákazník specifikuje v poznámce objednávky", null, null);

            // 3. Addons
            log.info("Creating addons...");
            // Předpokládáme, že všechny stávající jsou typu FIXED
            // Přidány kategorie a null pro dimenzionální ceny
            createAddon("Polička 130 cm", null, "Poličky", "FIXED", new BigDecimal("1815.00"), null, null, null, "POL130", true);
            createAddon("Polička 90 cm", null, "Poličky", "FIXED", new BigDecimal("1452.00"), null, null, null, "POL90", true);
            createAddon("Držáky 2 páry", null, "Montážní materiál", "FIXED", new BigDecimal("580.00"), null, null, null, "DRZ2P", true);
            createAddon("Příčka 1x", "Dřevěná příčka", "Konstrukce", "FIXED", new BigDecimal("1500.00"), null, null, null, "PRICKA1", true);
            createAddon("Příčka 2x", "Dřevěná příčka", "Konstrukce", "FIXED", new BigDecimal("3000.00"), null, null, null, "PRICKA2", true);
            createAddon("Příčka 3x", "Dřevěná příčka", "Konstrukce", "FIXED", new BigDecimal("4500.00"), null, null, null, "PRICKA3", true);
            createAddon("Okap", "Kompletní okapový systém", "Střecha", "FIXED", new BigDecimal("5000.00"), null, null, null, "OKAP", true);

            // 4. Products - None initially
            log.info("Skipping initial product creation as requested.");

            // 5. Customers (Admins)
            log.info("Creating admin users...");
            createAdmin("Miroslav", "Svoboda", "mirsvobo@gmail.com", null, "heslo");
            createAdmin("Martin", "Kolář", "drevnikykolar@gmail.com", null, "heslo");

            // 6. Sample Orders - None initially
            log.info("Skipping initial order creation as requested.");

            log.info("Application data initialization finished successfully.");
        } catch (Exception e) {
            log.error("Error during application data initialization!", e);
            throw new RuntimeException("Failed to initialize data", e);
        }
    }

    // --- Helper Methods ---

    // === UPRAVENÁ METODA ===
    private void createTaxRates() {
        if (taxRateRepository.count() == 0) {
            log.info("Creating default Tax Rates...");
            // Základní sazba (21%), bez PDP, bez poznámky
            TaxRate rate21 = new TaxRate(null, "Základní", new BigDecimal("0.2100"), false, null, null); // Added null for 'note'
            // Snížená sazba (12%), bez PDP, s poznámkou
            TaxRate rate12 = new TaxRate(null, "Snížená", new BigDecimal("0.1200"), false, null, null); // Added note
            // !!! ODEBRÁNO: Sazba pro Přenesenou daňovou povinnost !!!
            // TaxRate rateRC = new TaxRate(null, "Přenesená daňová povinnost", BigDecimal.ZERO, true, null, null); // Removed

            taxRateRepository.saveAll(List.of(rate21, rate12)); // Uložíme jen 21% a 12%
            log.info("Default Tax Rates (21%, 12%) created.");
        } else {
            log.info("Tax Rates already exist, skipping creation.");
            // Zde můžeme případně přidat logiku pro aktualizaci existujících sazeb, pokud by bylo potřeba přidat poznámky zpětně
            Optional<TaxRate> rate12Opt = taxRateRepository.findByNameIgnoreCase("Snížená");
            if (rate12Opt.isPresent() && rate12Opt.get().getNote() == null) {
                TaxRate rate12 = rate12Opt.get();
                rate12.setNote("Pouze pro stavby k bydlení");
                taxRateRepository.save(rate12);
                log.info("Added default note to existing 'Snížená' tax rate.");
            }
        }
    }
    // === KONEC UPRAVENÉ METODY ===

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
            createState("READY_TO_SHIP", "Připraveno k montáži", "Objednávka je připravena k montáži.", 70, false);
            createState("SHIPPED", "Odesláno", "Objednávka byla předána dopravci.", 80, false);
            createState("DELIVERED", "Doručeno", "Objednávka byla úspěšně doručena.", 90, true);
            createState("CANCELLED", "Zrušeno", "Objednávka byla zrušena.", 100, true);
            log.info("Default Order States created.");
        } else {
            log.info("Order States already exist, checking/updating 'READY_TO_SHIP' description...");
            Optional<OrderState> readyToShipOpt = orderStateRepository.findByCodeIgnoreCase("READY_TO_SHIP");
            if (readyToShipOpt.isPresent()) {
                OrderState stateReadyToShip = readyToShipOpt.get();
                if (!"Připraveno k montáži".equals(stateReadyToShip.getName())) {
                    stateReadyToShip.setName("Připraveno k montáži");
                    orderStateRepository.save(stateReadyToShip);
                    log.info("Updated name/description for existing OrderState 'READY_TO_SHIP'.");
                }
            } else {
                log.warn("OrderState with code 'READY_TO_SHIP' not found, could not update description.");
            }
        }
    }

    // Helper createState
    private OrderState createState(String code, String name, String description, int order, boolean isFinal) {
        OrderState state = new OrderState();
        state.setCode(code.toUpperCase());
        state.setName(name);
        state.setDescription(description);
        state.setDisplayOrder(order);
        state.setFinalState(isFinal);
        return orderStateRepository.save(state);
    }

    private void createEmailTemplates() {
        if (emailTemplateConfigRepository.count() == 0) {
            log.info("Creating default Email Template Configs...");
            createEmailConfig("NEW", false, "emails/order-confirmation-admin", "{shopName} - Nová objednávka č. {orderCode}", "Potvrzení přijetí nové objednávky pro admina.");
            createEmailConfig("SHIPPED", true, "emails/order-status-shipped", "{shopName} - Vaše objednávka č. {orderCode} byla odeslána", "Informace pro zákazníka o odeslání.");
            createEmailConfig("DELIVERED", false, "emails/order-status-delivered", "{shopName} - Vaše objednávka č. {orderCode} byla doručena", "Interní nebo volitelný email o doručení.");
            createEmailConfig("CANCELLED", true, "emails/order-status-cancelled", "{shopName} - Vaše objednávka č. {orderCode} byla zrušena", "Informace o zrušení objednávky.");
            createEmailConfig("AWAITING_DEPOSIT", true, "emails/order-status-awaiting-deposit", "{shopName} - Výzva k úhradě zálohy k obj. č. {orderCode}", "Výzva k úhradě zálohy.");
            createEmailConfig("AWAITING_PAYMENT", true, "emails/order-status-awaiting-payment", "{shopName} - Výzva k úhradě obj. č. {orderCode}", "Výzva k platbě převodem.");
            createEmailConfig("DEPOSIT_PAID", true, "emails/order-status-deposit-paid", "{shopName} - Přijali jsme Vaši zálohu k obj. č. {orderCode}", "Potvrzení přijetí zálohy.");
            createEmailConfig("PAID", true, "emails/order-status-paid", "{shopName} - Objednávka č. {orderCode} byla zaplacena", "Potvrzení plné úhrady.");
            createEmailConfig("PROCESSING", true, "emails/order-status-processing", "{shopName} - Objednávka č. {orderCode} se zpracovává", "Informace o zpracování.");
            createEmailConfig("IN_PRODUCTION", true, "emails/order-status-in-production", "{shopName} - Objednávka č. {orderCode} je ve výrobě", "Informace o zahájení výroby.");
            createEmailConfig("AT_ZINC_PLATING", true, "emails/order-status-zinc", "{shopName} - Objednávka č. {orderCode} je v zinkovně", "Informace o zinkování.");
            createEmailConfig("READY_TO_SHIP", true, "emails/order-status-ready", "{shopName} - Objednávka č. {orderCode} připravena", "Informace o připravenosti k odeslání/montáži.");
            log.info("Default Email Template Configs created.");
        } else {
            log.info("Email Template Configs already exist, skipping creation.");
        }
    }

    // Helper to create email configs
    private void createEmailConfig(String stateCode, boolean send, String template, String subject, String description) {
        if (emailTemplateConfigRepository.findByStateCodeIgnoreCase(stateCode.toUpperCase()).isEmpty()) {
            EmailTemplateConfig config = new EmailTemplateConfig();
            config.setStateCode(stateCode.toUpperCase());
            config.setSendEmail(send);
            config.setTemplateName(template);
            config.setSubjectTemplate(subject);
            config.setDescription(description);
            emailTemplateConfigRepository.save(config);
            log.debug("Created EmailTemplateConfig for state: {}", stateCode);
        } else {
            log.trace("EmailTemplateConfig for state {} already exists, skipping.", stateCode);
        }
    }

    // --- Helpers for Attributes (no surcharge) ---
    private Design createDesign(String name, String description, BigDecimal surchargeCZK, BigDecimal surchargeEUR, String imageUrl) {
        return designRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    Design d = new Design();
                    d.setName(name);
                    d.setDescription(description);
                    d.setActive(true);
                    d.setImageUrl(imageUrl);
                    d.setPriceSurchargeCZK(null);
                    d.setPriceSurchargeEUR(null);
                    log.debug("Creating Design: {}", name);
                    return designRepository.save(d);
                });
    }

    private Glaze createGlaze(String name, String description, BigDecimal surchargeCZK, BigDecimal surchargeEUR) {
        return glazeRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    Glaze g = new Glaze();
                    g.setName(name);
                    g.setDescription(description);
                    g.setActive(true);
                    g.setPriceSurchargeCZK(null);
                    g.setPriceSurchargeEUR(null);
                    log.debug("Creating Glaze: {}", name);
                    return glazeRepository.save(g);
                });
    }

    private RoofColor createRoofColor(String name, String description, BigDecimal surchargeCZK, BigDecimal surchargeEUR) {
        return roofColorRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    RoofColor rc = new RoofColor();
                    rc.setName(name);
                    rc.setDescription(description);
                    rc.setActive(true);
                    rc.setPriceSurchargeCZK(null);
                    rc.setPriceSurchargeEUR(null);
                    log.debug("Creating RoofColor: {}", name);
                    return roofColorRepository.save(rc);
                });
    }

    // --- Helper for Addons ---
    private Addon createAddon(String name, String desc, String category, String pricingType,
                              BigDecimal priceCZK, BigDecimal priceEUR,
                              BigDecimal pricePerUnitCZK, BigDecimal pricePerUnitEUR,
                              String sku, boolean active) {

        category = StringUtils.hasText(category) ? category : "Ostatní";
        pricingType = StringUtils.hasText(pricingType) ? pricingType : "FIXED";

        Optional<Addon> existingByName = addonsRepository.findByNameIgnoreCase(name);
        if (existingByName.isPresent()) {
            log.warn("Addon with name '{}' already exists. Skipping creation.", name);
            return existingByName.get();
        }
        if (sku != null && !sku.trim().isEmpty()) {
            Optional<Addon> existingBySku = addonsRepository.findBySkuIgnoreCase(sku.trim());
            if (existingBySku.isPresent()) {
                log.warn("Addon with SKU '{}' already exists. Skipping creation.", sku.trim());
                return existingBySku.get();
            }
        }

        Addon addon = new Addon();
        addon.setName(name);
        addon.setDescription(desc);
        addon.setCategory(category.trim());
        addon.setPricingType(pricingType);

        if ("FIXED".equals(pricingType)) {
            addon.setPriceCZK(priceCZK != null ? priceCZK : BigDecimal.ZERO);
            addon.setPriceEUR(priceEUR != null ? priceEUR : BigDecimal.ZERO);
            addon.setPricePerUnitCZK(null);
            addon.setPricePerUnitEUR(null);
        } else {
            addon.setPriceCZK(null);
            addon.setPriceEUR(null);
            addon.setPricePerUnitCZK(pricePerUnitCZK != null ? pricePerUnitCZK : BigDecimal.ZERO);
            addon.setPricePerUnitEUR(pricePerUnitEUR != null ? pricePerUnitEUR : BigDecimal.ZERO);
        }

        addon.setSku(sku != null ? sku.trim() : null);
        addon.setActive(active);

        log.debug("Creating Addon: Name='{}', Category='{}', Type='{}', SKU='{}', CZK='{}', EUR='{}', UnitCZK='{}', UnitEUR='{}'",
                name, category, pricingType, sku, addon.getPriceCZK(), addon.getPriceEUR(), addon.getPricePerUnitCZK(), addon.getPricePerUnitEUR());
        return addonsRepository.save(addon);
    }

    // --- Helper for Admins ---
    private Customer createAdmin(String fname, String lname, String email, String phone, String password) {
        if (customerRepository.findByEmailIgnoreCase(email).isPresent()) {
            log.warn("Admin user {} already exists. Skipping creation.", email);
            return customerRepository.findByEmailIgnoreCase(email).get();
        }
        Customer c = new Customer();
        c.setFirstName(fname);
        c.setLastName(lname);
        c.setEmail(email.toLowerCase().trim());
        c.setPhone(phone);
        c.setPassword(passwordEncoder.encode(password));
        c.setEnabled(true);
        c.setGuest(false);
        c.setRoles(Set.of("ROLE_USER", "ROLE_ADMIN"));
        c.setInvoiceFirstName(fname);
        c.setInvoiceLastName(lname);
        c.setUseInvoiceAddressAsDelivery(true);
        log.info("Creating admin user: {}", email);
        return customerRepository.save(c);
    }

}