package org.example.eshop.init;

import jakarta.transaction.Transactional;
import org.example.eshop.config.PriceConstants;
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
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode; // Added for rounding
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet; // Added for creating sets

@Component
@org.springframework.core.annotation.Order(Ordered.LOWEST_PRECEDENCE)
public class DataInitializer implements ApplicationRunner, PriceConstants {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final BigDecimal EUR_CONVERSION_RATE = new BigDecimal("25.00"); // Kurz 1 EUR = 25 CZK

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

    // References for product creation
    private TaxRate standardRate;
    private TaxRate reducedRate;
    private Set<Design> allDesigns;
    private Set<Glaze> allGlazes;
    private Set<RoofColor> allRoofColors;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        log.info("Initializing application data for Drevniky Kolar...");
        try {
            // 1. Core Data
            createTaxRates(); // Create or load tax rates
            createOrderStates();
            createEmailTemplates();

            // 2. Attributes (Designs, Glazes, Roof Colors) - without surcharge
            log.info("Creating attributes (Designs, Glazes, Roof Colors)...");
            Design designF = createDesign("František", "Design František", null, null, null);
            Design designT = createDesign("Tomáš", "Design Tomáš", null, null, null);
            Design designM = createDesign("Martin", "Design Martin", null, null, null);
            Design designVlastni = createDesign("Vlastní design", "Specifikováno zákazníkem", null, null, null); // Add "Vlastní" option
            allDesigns = Set.of(designF, designT, designM, designVlastni);

            Glaze glazeAfr = createGlaze("Afromorsia", "Lazura Afromorsia", null, null);
            Glaze glazeBor = createGlaze("Borovice", "Lazura Borovice", null, null);
            Glaze glazeKas = createGlaze("Kaštan", "Lazura Kaštan", null, null);
            Glaze glazeOreR = createGlaze("Ořech - rustic", "Lazura Ořech rustic", null, null);
            Glaze glazeOreS = createGlaze("Ořech - světlý", "Lazura Ořech světlý", null, null);
            Glaze glazeOreT = createGlaze("Ořech - tmavý", "Lazura Ořech tmavý", null, null);
            Glaze glazeVlastni = createGlaze("Vlastní lazura", "Specifikováno zákazníkem", null, null); // Add "Vlastní" option
            allGlazes = Set.of(glazeAfr, glazeBor, glazeKas, glazeOreR, glazeOreS, glazeOreT, glazeVlastni);

            RoofColor rcAnt = createRoofColor("Antracit", "Barva střechy Antracit", null, null);
            RoofColor rcCer = createRoofColor("Černá", "Barva střechy Černá", null, null);
            RoofColor rcCih = createRoofColor("Cihlová", "Barva střechy Cihlová", null, null);
            RoofColor rcSvh = createRoofColor("Světle hnědá", "Barva střechy Světle hnědá", null, null);
            RoofColor rcTmh = createRoofColor("Tmavě hnědá", "Barva střechy Tmavě hnědá", null, null);
            RoofColor rcVlastni = createRoofColor("Vlastní barva střechy", "Specifikováno zákazníkem", null, null); // Add "Vlastní" option
            allRoofColors = Set.of(rcAnt, rcCer, rcCih, rcSvh, rcTmh, rcVlastni);

            // 3. Addons with categories and pricing logic
            log.info("Creating addons...");
            BigDecimal pol90Czk = new BigDecimal("1452.00");
            BigDecimal pol130Czk = new BigDecimal("1815.00");
            BigDecimal drzakyCzk = new BigDecimal("580.00");
            BigDecimal pricka1Czk = new BigDecimal("1500.00");
            BigDecimal pricka2Czk = pricka1Czk.multiply(BigDecimal.valueOf(2));
            BigDecimal pricka3Czk = pricka1Czk.multiply(BigDecimal.valueOf(3));
            BigDecimal okapPerCmCzk = new BigDecimal("20.00");
            BigDecimal domekPerCmCzk = new BigDecimal("62.50");

            createAddon("Žádná polička", null, "Polička", "FIXED", BigDecimal.ZERO, BigDecimal.ZERO, null, null, "POL_NONE", true);
            createAddon("Polička 90 cm", null, "Polička", "FIXED", pol90Czk, convertToEur(pol90Czk), null, null, "POL90", true);
            createAddon("Polička 130 cm", null, "Polička", "FIXED", pol130Czk, convertToEur(pol130Czk), null, null, "POL130", true);

            createAddon("Žádné držáky", null, "Držáky", "FIXED", BigDecimal.ZERO, BigDecimal.ZERO, null, null, "DRZ_NONE", true);
            createAddon("Držáky 2 páry", null, "Držáky", "FIXED", drzakyCzk, convertToEur(drzakyCzk), null, null, "DRZ2P", true);

            createAddon("Žádná příčka", null, "Příčka", "FIXED", BigDecimal.ZERO, BigDecimal.ZERO, null, null, "PRICKA_NONE", true);
            createAddon("1x příčka", null, "Příčka", "FIXED", pricka1Czk, convertToEur(pricka1Czk), null, null, "PRICKA1", true);
            createAddon("2x příčka", null, "Příčka", "FIXED", pricka2Czk, convertToEur(pricka2Czk), null, null, "PRICKA2", true);
            createAddon("3x příčka", null, "Příčka", "FIXED", pricka3Czk, convertToEur(pricka3Czk), null, null, "PRICKA3", true);

            createAddon("Žádný okap", null, "Okap", "FIXED", BigDecimal.ZERO, BigDecimal.ZERO, null, null, "OKAP_NONE", true);
            // Price per cm WIDTH for Okap
            createAddon("Okap", "Kompletní okapový systém", "Okap", "PER_CM_WIDTH", null, null, okapPerCmCzk, convertToEur(okapPerCmCzk), "OKAP_SYST", true);

            createAddon("Bez zahradního domku", null, "Zahradní domek", "FIXED", BigDecimal.ZERO, BigDecimal.ZERO, null, null, "DOMEK_NONE", true);
            // Price per cm WIDTH for Zahradní domek
            createAddon("Zahradní domek", null, "Zahradní domek", "PER_CM_WIDTH", null, null, domekPerCmCzk, convertToEur(domekPerCmCzk), "DOMEK_SYST", true);


            // 4. Products
            log.info("Creating products...");
            createProduct("Dřevník Kompakt", "Kompaktní dřevník z kovové konstrukce, který se hodí i na ty nejmenší zahrady. Uskladní až 1,30 m³ palivového dříví.",
                    14694.00, 220.0, 100.0, 73.0, "Standard", "Smrk");
            createProduct("Dřevník Klasik", "Robustní řešení pro střední zahrady. Spojuje kompaktnost Dřevníku Klasik a kapacitu větších rozměrů. Pojme až 2 m³ dříví.",
                    20149.00, 220.0, 160.0, 73.0, "Standard", "Smrk");
            createProduct("Dřevník L", "Odolné řešení pro uskladnění až 2,90 m³ dříví. Dřevník L je vyráběn z kvalitních materiálů. Ideální pro větší zahrady.",
                    22344.00, 220.0, 160.0, 109.0, "Standard", "Smrk");
            createProduct("Dřevník XXL", "Prémiové řešení ochrany velkého množství dříví pro větší zahrady. Před vnějšími vlivy ochrání až 4,70 m³ dřeva.",
                    28890.00, 220.0, 260.0, 109.0, "Standard", "Smrk");

            // 5. Customers (Admins)
            log.info("Creating admin users...");
            createAdmin("Miroslav", "Svoboda", "miroslav.svoboda@drevniky-kolar.cz", null, "heslo");
            createAdmin("Martin", "Kolář", "martin.kolar@drevniky-kolar.cz", null, "heslo");


            log.info("Application data initialization finished successfully.");
        } catch (Exception e) {
            log.error("Error during application data initialization!", e);
            throw new RuntimeException("Failed to initialize data", e);
        }
    }

    // --- Helper Methods ---

    private void createTaxRates() {
        if (taxRateRepository.count() == 0) {
            log.info("Creating default Tax Rates...");
            TaxRate rate21 = new TaxRate(null, "Standardní", new BigDecimal("0.2100"), false, "Fakturace pro právnické osoby / místem realizace je objekt určený k rekreaci.", null);
            TaxRate rate12 = new TaxRate(null, "Snížená", new BigDecimal("0.1200"), false, "Dle §48 zákona o DPH – fyzické osoby + místem realizace je rodinný, či bytový dům určený k bydlení – podpis čestného prohlášení.", null);
            taxRateRepository.saveAll(List.of(rate21, rate12));
            log.info("Default Tax Rates (21%, 12%) created.");
        } else {
            log.info("Tax Rates already exist, checking/updating notes...");
            // Update notes if needed
            updateTaxRateNote("Standardní", "Fakturace pro právnické osoby / místem realizace je objekt určený k rekreaci.");
            updateTaxRateNote("Snížená", "Dle §48 zákona o DPH – fyzické osoby + místem realizace je rodinný, či bytový dům určený k bydlení – podpis čestného prohlášení.");
        }
        // Load rates for product creation
        standardRate = taxRateRepository.findByNameIgnoreCase("Standardní").orElseThrow();
        reducedRate = taxRateRepository.findByNameIgnoreCase("Snížená").orElseThrow();
    }

    private void updateTaxRateNote(String rateName, String note) {
        Optional<TaxRate> rateOpt = taxRateRepository.findByNameIgnoreCase(rateName);
        if (rateOpt.isPresent()) {
            TaxRate rate = rateOpt.get();
            if (!note.equals(rate.getNote())) {
                rate.setNote(note);
                taxRateRepository.save(rate);
                log.info("Updated note for Tax Rate '{}'.", rateName);
            }
        } else {
            log.warn("Tax Rate '{}' not found during note update check.", rateName);
        }
    }


    private void createOrderStates() {
        if (orderStateRepository.count() == 0) {
            log.info("Creating default Order States...");
            createState("NEW", "Nová", "Objednávka přijata systémem.", 10, false);
            createState("AWAITING_DEPOSIT", "Čeká na zálohu", "Čeká na zaplacení zálohy.", 20, false);
            // AWAITING_PAYMENT - removed as per logic, covered by AWAITING_DEPOSIT or final payment after READY_TO_SHIP
            //createState("AWAITING_PAYMENT", "Čeká na platbu", "Čeká na platbu bankovním převodem.", 30, false);
            createState("PROCESSING", "Zpracovává se", "Objednávka se připravuje k výrobě.", 40, false);
            createState("IN_PRODUCTION", "Ve výrobě", "Produkt je aktuálně ve výrobě.", 50, false);
            createState("AT_ZINC_PLATING", "V zinkovně", "Konstrukce je v zinkovně.", 60, false);
            createState("READY_TO_SHIP", "Připraveno k montáži", "Objednávka je připravena k montáži/dodání.", 70, false); // Updated name
            // SHIPPED - removed, implicit in READY_TO_SHIP or DELIVERED
            //createState("SHIPPED", "Odesláno", "Objednávka byla předána dopravci.", 80, false);
            createState("DELIVERED", "Dokončeno", "Objednávka byla úspěšně dokončena.", 90, true); // Updated name
            createState("CANCELLED", "Zrušeno", "Objednávka byla zrušena.", 100, true);
            log.info("Default Order States created.");
        } else {
            log.info("Order States already exist, skipping creation.");
        }
    }

    // Helper createState - no changes needed
    private OrderState createState(String code, String name, String description, int order, boolean isFinal) {
        return orderStateRepository.findByCodeIgnoreCase(code)
                .orElseGet(() -> {
                    OrderState state = new OrderState();
                    state.setCode(code.toUpperCase());
                    state.setName(name);
                    state.setDescription(description);
                    state.setDisplayOrder(order);
                    state.setFinalState(isFinal);
                    log.debug("Creating OrderState: {}", code);
                    return orderStateRepository.save(state);
                });
    }

    private void createEmailTemplates() {
        // Odesílá se pro všechny stavy kromě "NEW"
        createEmailConfigIfNeeded("AWAITING_DEPOSIT", true, "emails/order-status-update", "{shopName} - Objednávka č. {orderCode} - {stateName}", "Info o čekání na zálohu.");
        createEmailConfigIfNeeded("PROCESSING", true, "emails/order-status-update", "{shopName} - Objednávka č. {orderCode} - {stateName}", "Info o zpracování.");
        createEmailConfigIfNeeded("IN_PRODUCTION", true, "emails/order-status-update", "{shopName} - Objednávka č. {orderCode} - {stateName}", "Info o zahájení výroby.");
        createEmailConfigIfNeeded("AT_ZINC_PLATING", true, "emails/order-status-update", "{shopName} - Objednávka č. {orderCode} - {stateName}", "Info o zinkování.");
        createEmailConfigIfNeeded("READY_TO_SHIP", true, "emails/order-status-update", "{shopName} - Objednávka č. {orderCode} - {stateName}", "Info o připravenosti.");
        createEmailConfigIfNeeded("DELIVERED", true, "emails/order-status-update", "{shopName} - Objednávka č. {orderCode} - {stateName}", "Info o dokončení.");
        createEmailConfigIfNeeded("CANCELLED", true, "emails/order-status-update", "{shopName} - Objednávka č. {orderCode} - {stateName}", "Info o zrušení.");
    }

    private void createEmailConfigIfNeeded(String stateCode, boolean send, String template, String subject, String description) {
        if (emailTemplateConfigRepository.findByStateCodeIgnoreCase(stateCode.toUpperCase()).isEmpty()) {
            EmailTemplateConfig config = new EmailTemplateConfig();
            config.setStateCode(stateCode.toUpperCase());
            config.setSendEmail(send);
            config.setTemplateName(template); // Jednotná šablona
            config.setSubjectTemplate(subject); // Jednotný vzor předmětu
            config.setDescription(description);
            emailTemplateConfigRepository.save(config);
            log.info("Created EmailTemplateConfig for state: {}", stateCode);
        } else {
            log.debug("EmailTemplateConfig for state {} already exists, skipping.", stateCode);
        }
    }

    // Helper for converting CZK to EUR
    private BigDecimal convertToEur(BigDecimal czkAmount) {
        if (czkAmount == null || EUR_CONVERSION_RATE == null || EUR_CONVERSION_RATE.compareTo(BigDecimal.ZERO) <= 0) {
            return null; // Or return BigDecimal.ZERO based on requirements
        }
        // Use PRICE_SCALE for the final result
        return czkAmount.divide(EUR_CONVERSION_RATE, PRICE_SCALE, RoundingMode.HALF_UP);
    }


    // Helper for attributes (ensure no surcharge)
    private Design createDesign(String name, String description, BigDecimal surchargeCZK, BigDecimal surchargeEUR, String imageUrl) {
        return designRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    Design d = new Design();
                    d.setName(name);
                    d.setDescription(description);
                    d.setActive(true);
                    d.setImageUrl(imageUrl);
                    d.setPriceSurchargeCZK(null); // Ensure no surcharge
                    d.setPriceSurchargeEUR(null); // Ensure no surcharge
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
                    g.setPriceSurchargeCZK(null); // Ensure no surcharge
                    g.setPriceSurchargeEUR(null); // Ensure no surcharge
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
                    rc.setPriceSurchargeCZK(null); // Ensure no surcharge
                    rc.setPriceSurchargeEUR(null); // Ensure no surcharge
                    log.debug("Creating RoofColor: {}", name);
                    return roofColorRepository.save(rc);
                });
    }

    // UPDATED Helper for Addons
    private Addon createAddon(String name, String desc, String category, String pricingType,
                              BigDecimal priceCZK, BigDecimal priceEUR,
                              BigDecimal pricePerUnitCZK, BigDecimal pricePerUnitEUR,
                              String sku, boolean active) {

        // Ensure defaults if null
        category = StringUtils.hasText(category) ? category : "Ostatní";
        pricingType = StringUtils.hasText(pricingType) ? pricingType : "FIXED";

        // Check for existing addon by name or SKU to avoid duplicates
        Optional<Addon> existingByName = addonsRepository.findByNameIgnoreCase(name);
        if (existingByName.isPresent()) {
            log.info("Addon '{}' already exists by name. Skipping creation.", name);
            return existingByName.get();
        }
        if (StringUtils.hasText(sku)) {
            Optional<Addon> existingBySku = addonsRepository.findBySkuIgnoreCase(sku.trim());
            if (existingBySku.isPresent()) {
                log.info("Addon with SKU '{}' already exists. Skipping creation.", sku);
                return existingBySku.get();
            }
        }

        // Create new addon
        Addon addon = new Addon();
        addon.setName(name);
        addon.setDescription(desc);
        addon.setCategory(category.trim());
        addon.setPricingType(pricingType);
        addon.setSku(StringUtils.hasText(sku) ? sku.trim() : null);
        addon.setActive(active);

        // Set prices based on type
        if ("FIXED".equals(pricingType)) {
            addon.setPriceCZK(priceCZK != null ? priceCZK.setScale(PRICE_SCALE, ROUNDING_MODE) : BigDecimal.ZERO);
            addon.setPriceEUR(priceEUR != null ? priceEUR.setScale(PRICE_SCALE, ROUNDING_MODE) : BigDecimal.ZERO);
            addon.setPricePerUnitCZK(null); // Ensure per unit is null for FIXED
            addon.setPricePerUnitEUR(null);
        } else { // Dimensional pricing
            addon.setPriceCZK(null); // Ensure fixed is null for dimensional
            addon.setPriceEUR(null);
            // Use CALCULATION_SCALE for per unit prices
            addon.setPricePerUnitCZK(pricePerUnitCZK != null ? pricePerUnitCZK.setScale(CALCULATION_SCALE, ROUNDING_MODE) : BigDecimal.ZERO);
            addon.setPricePerUnitEUR(pricePerUnitEUR != null ? pricePerUnitEUR.setScale(CALCULATION_SCALE, ROUNDING_MODE) : BigDecimal.ZERO);
        }

        log.debug("Creating Addon: Name='{}', Category='{}', Type='{}', SKU='{}', PriceCZK='{}', PriceEUR='{}', UnitCZK='{}', UnitEUR='{}'",
                addon.getName(), addon.getCategory(), addon.getPricingType(), addon.getSku(),
                addon.getPriceCZK(), addon.getPriceEUR(), addon.getPricePerUnitCZK(), addon.getPricePerUnitEUR());
        return addonsRepository.save(addon);
    }

    // Helper for Products
    private Product createProduct(String name, String description, double basePriceCzk,
                                  double height, double length, double width,
                                  String model, String material) {
        return productRepository.findBySlugIgnoreCase(ProductService.generateSlug(name))
                .orElseGet(() -> {
                    Product p = new Product();
                    p.setName(name);
                    p.setSlug(ProductService.generateSlug(name));
                    p.setDescription(description);
                    p.setShortDescription(description.length() > 100 ? description.substring(0, 100) + "..." : description); // Basic short desc
                    p.setActive(true);
                    p.setCustomisable(false); // Standard product

                    p.setBasePriceCZK(BigDecimal.valueOf(basePriceCzk).setScale(PRICE_SCALE, ROUNDING_MODE));
                    p.setBasePriceEUR(convertToEur(p.getBasePriceCZK()));

                    p.setHeight(BigDecimal.valueOf(height).setScale(PRICE_SCALE, ROUNDING_MODE));
                    p.setLength(BigDecimal.valueOf(length).setScale(PRICE_SCALE, ROUNDING_MODE));
                    p.setWidth(BigDecimal.valueOf(width).setScale(PRICE_SCALE, ROUNDING_MODE));
                    p.setModel(model);
                    p.setMaterial(material);

                    // Assign all attributes and tax rates
                    p.setAvailableDesigns(new HashSet<>(allDesigns));
                    p.setAvailableGlazes(new HashSet<>(allGlazes));
                    p.setAvailableRoofColors(new HashSet<>(allRoofColors));
                    p.setAvailableTaxRates(Set.of(standardRate, reducedRate)); // Assign standard and reduced rates

                    log.debug("Creating standard Product: {}", name);
                    return productRepository.save(p);
                });
    }


    // Helper for Admins
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
        c.setPassword(passwordEncoder.encode(password)); // Encode the password
        c.setEnabled(true);
        c.setGuest(false);
        c.setRoles(Set.of("ROLE_USER", "ROLE_ADMIN")); // Assign roles
        c.setInvoiceFirstName(fname); // Default invoice/delivery info
        c.setInvoiceLastName(lname);
        c.setUseInvoiceAddressAsDelivery(true);
        log.info("Creating admin user: {}", email);
        return customerRepository.save(c);
    }

}