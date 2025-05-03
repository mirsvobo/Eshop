package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.config.PriceConstants;
import org.example.eshop.dto.CartItemDto;
import org.example.eshop.dto.CreateOrderRequest;
import org.example.eshop.model.*;
import org.example.eshop.repository.*;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderService implements PriceConstants {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    // Konstanty pro stavy
    private static final String INITIAL_ORDER_STATE_CODE = "NEW";
    private static final String PAYMENT_STATUS_AWAITING_DEPOSIT = "AWAITING_DEPOSIT";
    private static final String PAYMENT_STATUS_DEPOSIT_PAID = "DEPOSIT_PAID";
    private static final String PAYMENT_STATUS_PAID = "PAID"; // Používáme 'static final' pro konstanty
    private static final String PAYMENT_STATUS_PENDING = "PENDING";
    @Value("${app.base-url:https://www.drevniknamiru.cz}") // Načte hodnotu z application.properties, s výchozí hodnotou
    private String baseUrl;

    // Repositories
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private OrderStateRepository orderStateRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private AddonsRepository addonsRepository;
    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private TaxRateRepository taxRateRepository;
    @Autowired
    private DesignRepository designRepository;
    @Autowired
    private GlazeRepository glazeRepository;
    @Autowired
    private RoofColorRepository roofColorRepository;

    // Services
    @Autowired
    private ProductService productService;
    @Autowired
    private DiscountService discountService;
    @Autowired
    private CouponService couponService;
    @Autowired
    private EmailService emailService;
    @Autowired
    @Qualifier("googleMapsShippingService")
    private ShippingService shippingService;
    // Použijeme @Lazy zde, abychom předešli cyklické závislosti při startu aplikace
    // (pokud by SuperFakturaInvoiceService měla závislost na OrderService)
    @Autowired
    @Lazy
    private SuperFakturaInvoiceService invoiceService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private OrderCodeGeneratorService orderCodeGeneratorService;

    // Vlož nebo nahraď ve třídě OrderService

    @Transactional
// --- ZMĚNA: Přidán parametr boolean applyReverseCharge ---
    public Order createOrder(CreateOrderRequest request, boolean applyReverseCharge) {
        log.info("Attempting to create order for customer ID: {} with explicit currency: {}. Apply RC: {}", request.getCustomerId(), request.getCurrency(), applyReverseCharge); // Upraveno logování
        String orderCurrency = request.getCurrency();
        Order order = null; // Initialization to be accessible in catch block
        Customer customer = null; // Initialization
        Coupon appliedCoupon = null; // Initialization

        try {
            // 1. Validation & Loading Main Entities
            log.debug("[Order Creation - Step 1] Validating request and loading entities for currency: {}", orderCurrency);
            validateOrderRequest(request); // Validate input DTO
            customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new EntityNotFoundException("Customer not found with id: " + request.getCustomerId()));
            if (request.isUseCustomerAddresses() && !hasSufficientAddress(customer)) {
                throw new IllegalArgumentException("Customer (ID: " + customer.getId() + ") missing required address info for selected address usage.");
            }
            OrderState initialState = orderStateRepository.findByCodeIgnoreCase(INITIAL_ORDER_STATE_CODE)
                    .orElseThrow(() -> new IllegalStateException("Initial OrderState missing (code: " + INITIAL_ORDER_STATE_CODE + ")."));

            // 2. Initialization of Order Object
            log.debug("[Order Creation - Step 2] Initializing Order object for currency: {}", orderCurrency);
            order = new Order();
            order.setCustomer(customer);
            order.setStateOfOrder(initialState);
            order.setPaymentMethod(request.getPaymentMethod());
            order.setOrderItems(new ArrayList<>());
            order.setNote(request.getCustomerNote());
            copyAddressesToOrder(order, customer, request); // Copy address from Customer or DTO
            order.setCurrency(orderCurrency);
            // Initialize numeric fields to zero
            order.setSubTotalWithoutTax(BigDecimal.ZERO);
            order.setTotalItemsTax(BigDecimal.ZERO);
            order.setCouponDiscountAmount(BigDecimal.ZERO);
            order.setShippingCostWithoutTax(BigDecimal.ZERO);
            order.setShippingTaxRate(BigDecimal.ZERO);
            order.setShippingTax(BigDecimal.ZERO);
            order.setTotalPriceWithoutTax(BigDecimal.ZERO);
            order.setTotalTax(BigDecimal.ZERO);
            order.setTotalPrice(BigDecimal.ZERO);
            // Initialize originalTotalPrice as well
            order.setOriginalTotalPrice(BigDecimal.ZERO);

            BigDecimal runningSubTotalWithoutTax = BigDecimal.ZERO;
            BigDecimal runningTotalTaxFromItems = BigDecimal.ZERO;
            boolean containsCustomProduct = false;

            // 3. Processing Order Items
            log.debug("[Order Creation - Step 3] Processing order items for currency: {}", orderCurrency);
            if (request.getItems() == null) {
                throw new IllegalArgumentException("Order items list cannot be null.");
            }
            for (CartItemDto itemDto : request.getItems()) {
                if (itemDto == null || itemDto.getQuantity() <= 0) {
                    log.warn("Skipping null or zero quantity item.");
                    continue;
                }
                try {
                    log.debug("Processing item with Product ID: {}", itemDto.getProductId());
                    // --- ZMĚNA: Předání applyReverseCharge do processCartItem ---
                    OrderItem orderItem = processCartItem(itemDto, order, orderCurrency, applyReverseCharge);
                    // --- KONEC ZMĚNY ---
                    order.getOrderItems().add(orderItem);
                    // Add to running totals (with null checks)
                    BigDecimal itemSubtotal = Optional.ofNullable(orderItem.getTotalPriceWithoutTax()).orElse(BigDecimal.ZERO);
                    BigDecimal itemTax = Optional.ofNullable(orderItem.getTotalTaxAmount()).orElse(BigDecimal.ZERO);
                    runningSubTotalWithoutTax = runningSubTotalWithoutTax.add(itemSubtotal);
                    runningTotalTaxFromItems = runningTotalTaxFromItems.add(itemTax);
                    if (orderItem.isCustomConfigured()) containsCustomProduct = true;
                    log.debug("Processed item successfully. Running Subtotal: {}, Running Item Tax: {}", runningSubTotalWithoutTax, runningTotalTaxFromItems);
                } catch (Exception e) {
                    log.error("!!! CRITICAL ERROR processing item (Product ID: {}): {}", itemDto.getProductId(), e.getMessage(), e);
                    // Re-throw to interrupt order creation
                    throw new RuntimeException("Failed to process order item (Product ID: " + itemDto.getProductId() + "): " + e.getMessage(), e);
                }
            }
            // Check if any valid items remain
            if (order.getOrderItems().isEmpty()) {
                throw new IllegalArgumentException("Order must contain at least one valid item.");
            }
            // Set final item totals
            order.setSubTotalWithoutTax(runningSubTotalWithoutTax.setScale(PRICE_SCALE, ROUNDING_MODE));
            order.setTotalItemsTax(runningTotalTaxFromItems.setScale(PRICE_SCALE, ROUNDING_MODE));
            log.debug("Finished items processing. Final Subtotal: {}, Final Item Tax: {}", order.getSubTotalWithoutTax(), order.getTotalItemsTax());

            // 4. Applying Coupon
            BigDecimal couponDiscount = BigDecimal.ZERO; // Default discount is 0
            appliedCoupon = null; // Reset before validation
            if (StringUtils.hasText(request.getCouponCode())) {
                log.debug("[Order Creation - Step 4] Applying coupon '{}' for currency: {}", request.getCouponCode(), orderCurrency);
                try {
                    // Use the helper method to validate and apply the coupon
                    appliedCoupon = validateAndApplyCoupon(order, request.getCouponCode(), customer);
                    couponDiscount = Optional.ofNullable(order.getCouponDiscountAmount()).orElse(BigDecimal.ZERO); // Get the set discount
                    log.debug("Coupon validation finished. Applied Coupon: {}, Discount Amount: {}", (appliedCoupon != null ? appliedCoupon.getCode() : "None"), couponDiscount);
                } catch (Exception e) {
                    // Log the error but continue without the coupon
                    log.error("Exception during coupon application (Code: {}): {}. Ignoring coupon for this order.", request.getCouponCode(), e.getMessage());
                    appliedCoupon = null;
                    couponDiscount = BigDecimal.ZERO;
                    order.setCouponDiscountAmount(couponDiscount); // Ensure zero discount
                }
            }
            // Store the coupon code (even if invalid) and the reference to the valid coupon
            order.setAppliedCouponCode(StringUtils.hasText(request.getCouponCode()) ? request.getCouponCode().trim().toUpperCase() : null);
            order.setCouponDiscountAmount(couponDiscount.setScale(PRICE_SCALE, ROUNDING_MODE));
            order.setAppliedCoupon(appliedCoupon); // Will be null if coupon was not valid

            // 5. Calculating Shipping Cost
            BigDecimal shippingCostNoTax = BigDecimal.ZERO;
            BigDecimal shippingTaxRate = BigDecimal.ZERO;
            BigDecimal shippingTax = BigDecimal.ZERO;
            BigDecimal shippingDiscountAmount = BigDecimal.ZERO; // Variable to track shipping discount

            // Calculate shipping only if there's no active coupon for free shipping
            if (appliedCoupon == null || !appliedCoupon.isFreeShipping()) {
                log.debug("[Order Creation - Step 5] Calculating shipping cost (no free shipping coupon). Currency: {}", orderCurrency);
                try {
                    // Use values from request if valid and non-negative
                    if (request.getShippingCostNoTax() != null && request.getShippingTax() != null && request.getShippingCostNoTax().compareTo(BigDecimal.ZERO) >= 0 && request.getShippingTax().compareTo(BigDecimal.ZERO) >=0) { //Added >= 0 check for tax
                        shippingCostNoTax = request.getShippingCostNoTax();
                        shippingTax = request.getShippingTax();
                        // Estimate rate if cost > 0
                        if (shippingCostNoTax.compareTo(BigDecimal.ZERO) > 0) {
                            // Use higher precision for division
                            shippingTaxRate = shippingTax.divide(shippingCostNoTax, CALCULATION_SCALE, ROUNDING_MODE);
                        } else {
                            shippingTaxRate = BigDecimal.ZERO; // Rate is 0 if cost is 0
                        }
                        log.debug("Using shipping costs from request. CostNoTax: {}, Tax: {}, Estimated Rate: {}", shippingCostNoTax, shippingTax, shippingTaxRate);
                    } else {
                        // Recalculate if request values are missing or invalid
                        log.warn("Shipping costs missing or invalid in request, recalculating for order {}...", (order.getOrderCode() != null ? order.getOrderCode() : "(new)"));
                        shippingCostNoTax = Optional.ofNullable(shippingService.calculateShippingCost(order, orderCurrency)).orElse(BigDecimal.ZERO);
                        shippingTaxRate = Optional.ofNullable(shippingService.getShippingTaxRate()).orElse(BigDecimal.ZERO);
                        if (shippingCostNoTax.compareTo(BigDecimal.ZERO) > 0) {
                            if (shippingTaxRate.compareTo(BigDecimal.ZERO) <= 0) {
                                log.error("Shipping tax rate missing or zero during recalculation for order {}!", (order.getOrderCode() != null ? order.getOrderCode() : "(new)"));
                                shippingTaxRate = new BigDecimal("0.21"); // Fallback to 21%? Or leave 0?
                                log.warn("Falling back to default shipping tax rate: {}", shippingTaxRate);
                            }
                            shippingTax = shippingCostNoTax.multiply(shippingTaxRate).setScale(PRICE_SCALE, ROUNDING_MODE);
                        } else {
                            shippingTax = BigDecimal.ZERO; // Zero cost = zero tax
                        }
                        log.debug("Shipping recalculated. CostNoTax: {}, Tax: {}, Rate: {}", shippingCostNoTax, shippingTax, shippingTaxRate);
                    }
                } catch (Exception e) {
                    log.error("!!! CRITICAL ERROR during shipping calculation for order {}: {}", (order.getOrderCode() != null ? order.getOrderCode() : "(new)"), e.getMessage(), e);
                    // Re-throw to interrupt order creation
                    throw new RuntimeException("Failed to calculate shipping cost: " + e.getMessage(), e);
                }
            } else {
                // Free shipping coupon IS active
                log.info("Free shipping applied due to coupon '{}' for order {}.", appliedCoupon.getCode(), (order.getOrderCode() != null ? order.getOrderCode() : "(new)"));
                // Determine the original shipping cost to know the discount amount
                try {
                    // Try from request first (if AJAX calculated)
                    if (request.getShippingCostNoTax() != null && request.getShippingCostNoTax().compareTo(BigDecimal.ZERO) > 0) {
                        shippingDiscountAmount = request.getShippingCostNoTax();
                    } else {
                        // If not in request, try recalculating
                        shippingDiscountAmount = Optional.ofNullable(shippingService.calculateShippingCost(order, orderCurrency)).orElse(BigDecimal.ZERO);
                    }
                } catch (Exception e) {
                    log.error("Could not determine original shipping cost to calculate discount amount for order {}: {}", (order.getOrderCode() != null ? order.getOrderCode() : "(new)"), e.getMessage());
                    shippingDiscountAmount = BigDecimal.ZERO; // Fallback
                }
                shippingDiscountAmount = shippingDiscountAmount.max(BigDecimal.ZERO); // Ensure non-negative

                // Set final costs to zero
                shippingCostNoTax = BigDecimal.ZERO;
                shippingTaxRate = BigDecimal.ZERO; // Rate is zero if cost is zero
                shippingTax = BigDecimal.ZERO;
                log.info("Shipping costs set to ZERO for order {}. Discount amount recorded: {}", (order.getOrderCode() != null ? order.getOrderCode() : "(new)"), shippingDiscountAmount);
            }
            // Save final (potentially zero) costs and rate to the order
            order.setShippingCostWithoutTax(shippingCostNoTax.setScale(PRICE_SCALE, ROUNDING_MODE));
            order.setShippingTaxRate(shippingTaxRate.setScale(CALCULATION_SCALE, ROUNDING_MODE));
            order.setShippingTax(shippingTax.setScale(PRICE_SCALE, ROUNDING_MODE));
            // NOTE: If shippingDiscountAmount needs to be stored, add a field to the Order entity.

            // 6. Calculating Final Totals - UPRAVENO PRO ZAOKROUHLENÍ
            log.debug("[Order Creation - Step 6] Calculating final totals for currency: {}", orderCurrency);
            try {
                BigDecimal subTotalAfterDiscount = Optional.ofNullable(order.getSubTotalWithoutTax()).orElse(BigDecimal.ZERO)
                        .subtract(Optional.ofNullable(order.getCouponDiscountAmount()).orElse(BigDecimal.ZERO));

                // Celková cena bez DPH (po slevě, vč. dopravy)
                BigDecimal finalTotalWithoutTax = subTotalAfterDiscount
                        .add(Optional.ofNullable(order.getShippingCostWithoutTax()).orElse(BigDecimal.ZERO));

                // Celkové DPH (z položek + z dopravy)
                BigDecimal finalTotalTax = Optional.ofNullable(order.getTotalItemsTax()).orElse(BigDecimal.ZERO)
                        .add(Optional.ofNullable(order.getShippingTax()).orElse(BigDecimal.ZERO));

                // Původní (přesná) celková cena s DPH
                BigDecimal originalTotalPriceWithTax = finalTotalWithoutTax.add(finalTotalTax);

                // Zaokrouhlení CELKOVÉ ceny DOLŮ na celé číslo
                BigDecimal roundedTotalPrice = originalTotalPriceWithTax.setScale(0, RoundingMode.DOWN);

                // Uložení finálních hodnot do objednávky
                order.setTotalPriceWithoutTax(finalTotalWithoutTax.setScale(PRICE_SCALE, ROUNDING_MODE));
                order.setTotalTax(finalTotalTax.setScale(PRICE_SCALE, ROUNDING_MODE));
                order.setTotalPrice(roundedTotalPrice); // <-- ULOŽÍME ZAOKROUHLENOU CENU
                order.setOriginalTotalPrice(originalTotalPriceWithTax.setScale(PRICE_SCALE, ROUNDING_MODE)); // <<< ULOŽÍME PŮVODNÍ PŘESNOU CENU

                log.debug("Final totals calculated: TotalNoTax={}, TotalTax={}, OriginalTotalWithTax={}, RoundedTotalToSave={}",
                        order.getTotalPriceWithoutTax(), order.getTotalTax(),
                        order.getOriginalTotalPrice(), // Logujeme původní
                        order.getTotalPrice());        // Logujeme zaokrouhlenou

            } catch (Exception e) {
                String orderCodeLog = order.getOrderCode() != null ? order.getOrderCode() : "(new)";
                log.error("!!! CRITICAL ERROR calculating final totals for order {}: {}", orderCodeLog, e.getMessage(), e);
                throw new RuntimeException("Failed to calculate final totals: " + e.getMessage(), e);
            }

            // 7. Processing Payment Status and Deposit
            log.debug("[Order Creation - Step 7] Determining payment status/deposit for currency: {}", orderCurrency);
            try {
                // Use PaymentService to determine initial status
                order.setPaymentStatus(paymentService.determineInitialPaymentStatus(order));
                // Calculate deposit only for custom products
                if (containsCustomProduct) {
                    BigDecimal deposit = paymentService.calculateDeposit(order.getTotalPrice());
                    order.setDepositAmount(Optional.ofNullable(deposit).orElse(BigDecimal.ZERO).setScale(PRICE_SCALE, ROUNDING_MODE));
                    // If service didn't return AWAITING_DEPOSIT but should have, override
                    if (order.getDepositAmount().compareTo(BigDecimal.ZERO) > 0 && !PAYMENT_STATUS_AWAITING_DEPOSIT.equals(order.getPaymentStatus())) {
                        String orderCodeLog = order.getOrderCode() != null ? order.getOrderCode() : "(new)";
                        log.warn("Order {} has custom product and deposit amount > 0, overriding initial payment status from '{}' to '{}'.",
                                orderCodeLog, order.getPaymentStatus(), PAYMENT_STATUS_AWAITING_DEPOSIT);
                        order.setPaymentStatus(PAYMENT_STATUS_AWAITING_DEPOSIT);
                    }
                    String orderCodeLog = order.getOrderCode() != null ? order.getOrderCode() : "(new)";
                    log.info("Deposit amount {} {} calculated for custom order {}.", order.getDepositAmount(), order.getCurrency(), orderCodeLog);
                } else {
                    order.setDepositAmount(null); // Standard product has no deposit
                }
                log.debug("Final Payment status: {}, Deposit Amount: {}", order.getPaymentStatus(), order.getDepositAmount());
            } catch (Exception e) {
                String orderCodeLog = order.getOrderCode() != null ? order.getOrderCode() : "(new)";
                log.error("!!! CRITICAL ERROR determining payment status/deposit for order {}: {}", orderCodeLog, e.getMessage(), e);
                throw new RuntimeException("Failed to determine payment status/deposit: " + e.getMessage(), e);
            }

            // Set order code
            order.setOrderCode(orderCodeGeneratorService.getNextOrderCode());
            log.debug("Generated Order Code: {}", order.getOrderCode());

            // 8. Saving the Order
            log.debug("[Order Creation - Step 8] Saving order...");
            Order savedOrder;
            try {
                savedOrder = orderRepository.save(order);
                log.info("Order {} created successfully. Total: {} {}", savedOrder.getOrderCode(), savedOrder.getTotalPrice(), savedOrder.getCurrency());
            } catch (Exception e) {
                log.error("!!! CRITICAL ERROR during order save for potential order code {}: {}", order.getOrderCode(), e.getMessage(), e);
                // Re-throw to interrupt order creation
                throw new RuntimeException("Failed to save order: " + e.getMessage(), e);
            }

            // --- Non-critical steps (Log errors, but don't fail the transaction) ---
            // 9. Marking Coupon Used
            if (appliedCoupon != null) {
                log.debug("[Order Creation - Step 9] Marking coupon {} used...", appliedCoupon.getCode());
                try {
                    couponService.markCouponAsUsed(appliedCoupon);
                } catch (Exception e) {
                    log.error("Non-critical error marking coupon used (ID: {}): {}. Order creation continues.",
                            appliedCoupon.getId(), e.getMessage(), e);
                }
            }
            // 10. Sending Confirmation Email
            log.debug("[Order Creation - Step 10] Preparing to send confirmation email for order {}...", savedOrder.getOrderCode());
            try {
                // Získání příznaku isGuest a baseUrl
                boolean isGuest = savedOrder.getCustomer() != null && savedOrder.getCustomer().isGuest();
                // Volání upravené metody v EmailService s this.baseUrl (pole třídy OrderService)
                emailService.sendOrderConfirmationEmail(savedOrder, isGuest, this.baseUrl);
                // Logování úspěšného odeslání požadavku je nyní uvnitř EmailService
            } catch (Exception e) {
                // Logování chyby zůstává pro případ selhání ještě před odesláním
                log.error("Non-critical error preparing or requesting confirmation email for order {}: {}. Order creation continues.",
                        savedOrder.getOrderCode(), e.getMessage(), e);
            }
            try {
                // Zde zadej email admina, ideálně z konfigurace
                String adminEmail = "info@drevniky-kolar.cz"; // <-- NAHRAĎ SKUTEČNÝM EMAILEM ADMINA (nebo načti z properties)
                if (StringUtils.hasText(adminEmail)) {
                    log.debug("Attempting to send new order notification to admin {} for order {}", adminEmail, savedOrder.getOrderCode());
                    emailService.sendNewOrderAdminNotification(savedOrder, adminEmail);
                } else {
                    log.warn("Admin notification email not configured. Skipping notification for order {}.", savedOrder.getOrderCode());
                }
            } catch (Exception e) {
                log.error("Non-critical error sending admin notification email for order {}: {}. Order creation continues.",
                        savedOrder.getOrderCode(), e.getMessage(), e);
            }
            // 11. Generating Proforma Invoice (if needed)
            if (PAYMENT_STATUS_AWAITING_DEPOSIT.equals(savedOrder.getPaymentStatus()) && savedOrder.getDepositAmount() != null && savedOrder.getDepositAmount().compareTo(BigDecimal.ZERO) > 0) {
                log.debug("[Order Creation - Step 11] Triggering proforma invoice generation for order {}. Currency: {}", savedOrder.getOrderCode(), savedOrder.getCurrency());
                try {
                    invoiceService.generateProformaInvoice(savedOrder);
                } catch (Exception e) {
                    log.error("Non-critical error generating proforma invoice for order {}: {}. Order creation continues.",
                            savedOrder.getOrderCode(), e.getMessage(), e);
                }
            }

            return savedOrder; // Return the successfully saved order

        } catch (Exception mainException) {
            // Log the main error
            String customerIdStr = (request != null && request.getCustomerId() != null) ? request.getCustomerId().toString() : "Unknown";
            String orderCodeLog = (order != null && order.getOrderCode() != null) ? order.getOrderCode() : "(new)";
            log.error("!!! ORDER CREATION FAILED for customer ID {} / Order Code {}. Error message: {} !!!", customerIdStr, orderCodeLog, mainException.getMessage());
            // Log the stack trace for detailed analysis
            log.error("Order Creation Exception Stack Trace:", mainException);
            // Re-throw the exception to ensure transaction rollback and controller can react
            throw mainException;
        }
    }


    /**
     * Zpracuje jednu položku z DTO (CartItemDto) a převede ji na entitu OrderItem,
     * včetně výpočtu ceny, načtení atributů, addonů a DPH.
     * Nastavuje příznak isReverseCharge POUZE na základě hodnoty applyReverseCharge z pokladny.
     *
     * @param itemDto Položka DTO z košíku nebo requestu.
     * @param order Objednávka, ke které položka patří.
     * @param currency Měna objednávky.
     * @param applyReverseCharge Příznak z pokladny, zda má být uplatněno PDP.
     * @return Objekt OrderItem připravený k uložení.
     * @throws EntityNotFoundException Pokud produkt, sazba, atribut nebo addon nejsou nalezeny.
     * @throws IllegalArgumentException Pokud chybí povinná data (ID sazby, rozměry u custom produktu).
     * @throws IllegalStateException Pokud chybí konfigurace cen produktu.
     */
    private OrderItem processCartItem(CartItemDto itemDto, Order order, String currency, boolean applyReverseCharge) {
        log.debug("Starting processCartItem for Product ID: {}, TaxRate ID: {}, Apply RC Flag from Checkout: {}",
                itemDto.getProductId(), itemDto.getSelectedTaxRateId(), applyReverseCharge);

        Product product = productRepository.findByIdWithDetails(itemDto.getProductId()) // Načteme s detaily včetně asociací
                .orElseThrow(() -> new EntityNotFoundException("Produkt nenalezen: " + itemDto.getProductId()));
        if (!product.isActive())
            throw new IllegalArgumentException("Produkt '" + product.getName() + "' není aktivní a nelze jej objednat.");

        // --- Načtení a validace vybrané TaxRate (zůstává důležité pro sazbu DPH) ---
        if (itemDto.getSelectedTaxRateId() == null) {
            throw new IllegalArgumentException("Chybí ID vybrané daňové sazby pro produkt: " + product.getName());
        }
        TaxRate selectedTaxRate = taxRateRepository.findById(itemDto.getSelectedTaxRateId())
                .orElseThrow(() -> new EntityNotFoundException("Daňová sazba nenalezena: ID " + itemDto.getSelectedTaxRateId()));

        Set<TaxRate> availableRates = product.getAvailableTaxRates();
        if (availableRates == null || availableRates.isEmpty() || !availableRates.contains(selectedTaxRate)) {
            log.error("Selected TaxRate ID {} ('{}') is not available for Product ID {}", selectedTaxRate.getId(), selectedTaxRate.getName(), product.getId());
            throw new IllegalArgumentException("Vybraná daňová sazba '" + selectedTaxRate.getName() + "' není pro produkt '" + product.getName() + "' povolena.");
        }
        // Logujeme i původní nastavení RC u sazby pro přehlednost
        log.debug("Selected Tax Rate ID {} ('{}', Rate: {}, Original RC Flag: {}) validated for Product ID {}",
                selectedTaxRate.getId(), selectedTaxRate.getName(), selectedTaxRate.getRate(), selectedTaxRate.isReverseCharge(), product.getId());
        // --- Konec načtení a validace TaxRate ---

        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setProduct(product);
        orderItem.setCount(itemDto.getQuantity());
        orderItem.setCustomConfigured(itemDto.isCustom());

        // --- Uložení VYBRANÉ sazby DPH a jejího ID ---
        orderItem.setTaxRate(selectedTaxRate.getRate()); // Stále ukládáme původní sazbu (např. 0.21)
        orderItem.setSelectedTaxRateId(selectedTaxRate.getId());
        orderItem.setSelectedTaxRateName(selectedTaxRate.getName());

        // Načtení vybraných atributů (Design, Glaze, RoofColor)
        Design selectedDesign = designRepository.findById(itemDto.getSelectedDesignId())
                .orElseThrow(() -> new EntityNotFoundException("Design nenalezen: ID " + itemDto.getSelectedDesignId()));
        Glaze selectedGlaze = glazeRepository.findById(itemDto.getSelectedGlazeId())
                .orElseThrow(() -> new EntityNotFoundException("Lazura nenalezena: ID " + itemDto.getSelectedGlazeId()));
        RoofColor selectedRoofColor = roofColorRepository.findById(itemDto.getSelectedRoofColorId())
                .orElseThrow(() -> new EntityNotFoundException("Barva střechy nenalezena: ID " + itemDto.getSelectedRoofColorId()));

        // Výpočet příplatků za atributy podle měny objednávky
        BigDecimal attributeSurcharge = BigDecimal.ZERO;
        BigDecimal surchargeCZK = (Optional.ofNullable(selectedDesign.getPriceSurchargeCZK()).orElse(BigDecimal.ZERO))
                .add(Optional.ofNullable(selectedGlaze.getPriceSurchargeCZK()).orElse(BigDecimal.ZERO))
                .add(Optional.ofNullable(selectedRoofColor.getPriceSurchargeCZK()).orElse(BigDecimal.ZERO));
        BigDecimal surchargeEUR = (Optional.ofNullable(selectedDesign.getPriceSurchargeEUR()).orElse(BigDecimal.ZERO))
                .add(Optional.ofNullable(selectedGlaze.getPriceSurchargeEUR()).orElse(BigDecimal.ZERO))
                .add(Optional.ofNullable(selectedRoofColor.getPriceSurchargeEUR()).orElse(BigDecimal.ZERO));
        attributeSurcharge = EURO_CURRENCY.equals(currency) ? surchargeEUR : surchargeCZK;
        log.debug("Attribute surcharge calculated for currency {}: {}", currency, attributeSurcharge);

        // Výpočet základní jednotkové ceny a addonů
        BigDecimal baseUnitPriceNoTax;
        List<OrderItemAddon> orderItemAddonsToSave = new ArrayList<>();
        BigDecimal totalAddonPriceNoTax = BigDecimal.ZERO;

        if (itemDto.isCustom()) {
            Map<String, BigDecimal> dimensionsMap = itemDto.getCustomDimensions();
            if (dimensionsMap == null || dimensionsMap.get("length") == null || dimensionsMap.get("width") == null || dimensionsMap.get("height") == null) {
                throw new IllegalArgumentException("Chybí kompletní rozměry v mapě customDimensions pro custom produkt.");
            }
            baseUnitPriceNoTax = calculateBaseUnitPrice(product, itemDto, currency);
            orderItem.setLength(dimensionsMap.get("length"));
            orderItem.setWidth(dimensionsMap.get("width"));
            orderItem.setHeight(dimensionsMap.get("height"));

            List<Long> selectedAddonIds = itemDto.getSelectedAddonIds();
            if (selectedAddonIds != null && !selectedAddonIds.isEmpty()) {
                log.debug("[processCartItem] Processing addons for custom item. Addon IDs from DTO: {}", selectedAddonIds);
                Set<Long> requestedAddonIds = new HashSet<>(selectedAddonIds);
                Map<Long, Addon> addonsMap = addonsRepository.findAllById(requestedAddonIds).stream()
                        .filter(Addon::isActive)
                        .collect(Collectors.toMap(Addon::getId, a -> a));
                log.debug("[processCartItem] Found active addons from DB for custom item: {}", addonsMap.keySet());

                Set<Long> allowedAddonIds = Optional.ofNullable(product.getAvailableAddons())
                        .orElse(Collections.emptySet())
                        .stream()
                        .map(Addon::getId).collect(Collectors.toSet());
                log.debug("[processCartItem] Allowed addon IDs for product {}: {}", product.getId(), allowedAddonIds);

                for (Long addonId : selectedAddonIds) {
                    Addon addon = addonsMap.get(addonId);
                    if (addon != null && allowedAddonIds.contains(addon.getId())) {
                        log.debug("[processCartItem] Processing valid addon for OrderItem: ID={}, Name={}", addon.getId(), addon.getName());
                        OrderItemAddon oia = new OrderItemAddon();
                        oia.setOrderItem(orderItem);
                        oia.setAddon(addon);
                        oia.setAddonName(addon.getName());
                        oia.setQuantity(1); // Assume quantity 1 for addons added this way

                        BigDecimal addonUnitPrice = productService.calculateSingleAddonPriceBackend(addon, dimensionsMap, currency); // Helper method call

                        if (addonUnitPrice == null || addonUnitPrice.compareTo(BigDecimal.ZERO) < 0) {
                            log.error("Invalid price for addon ID {} ('{}') in currency {}. Price: {}", addon.getId(), addon.getName(), currency, addonUnitPrice);
                            throw new IllegalStateException("Invalid price configuration for addon " + addon.getName() + " in " + currency);
                        }
                        oia.setAddonPriceWithoutTax(addonUnitPrice.setScale(PRICE_SCALE, ROUNDING_MODE));
                        BigDecimal addonLineTotal = addonUnitPrice.multiply(BigDecimal.valueOf(oia.getQuantity())).setScale(PRICE_SCALE, ROUNDING_MODE);
                        oia.setTotalPriceWithoutTax(addonLineTotal);
                        log.debug("[processCartItem] Addon '{}' line total price ({}): {}", addon.getName(), currency, addonLineTotal);

                        orderItemAddonsToSave.add(oia);
                        totalAddonPriceNoTax = totalAddonPriceNoTax.add(addonLineTotal);
                    } else {
                        log.warn("[processCartItem] Requested addon ID {} is invalid, inactive, or not allowed for product {}. Skipping.", addonId, product.getId());
                    }
                }
                log.debug("[processCartItem] Total calculated addon price for item ({}): {}", currency, totalAddonPriceNoTax);
            }
            orderItem.setSelectedAddons(orderItemAddonsToSave);

        } else {
            // --- Standardní produkt ---
            baseUnitPriceNoTax = calculateBaseUnitPrice(product, itemDto, currency);
            orderItem.setLength(product.getLength());
            orderItem.setWidth(product.getWidth());
            orderItem.setHeight(product.getHeight());
            orderItem.setSelectedAddons(Collections.emptyList());
        }

        // Uložení historických dat
        saveHistoricalItemData(orderItem, product, itemDto, selectedDesign, selectedGlaze, selectedRoofColor);

        // Aplikace slevy na produkt
        BigDecimal unitPriceAfterDiscountNoTax = Optional.ofNullable(discountService.applyBestPercentageDiscount(baseUnitPriceNoTax, product))
                .orElse(baseUnitPriceNoTax);
        if (unitPriceAfterDiscountNoTax.compareTo(baseUnitPriceNoTax) != 0) {
            log.debug("[processCartItem] Unit Price after Product Discount applied: {}", unitPriceAfterDiscountNoTax);
        }

        // Finální jednotková cena bez DPH
        BigDecimal finalUnitPriceNoTax = unitPriceAfterDiscountNoTax
                .add(attributeSurcharge)
                .add(totalAddonPriceNoTax);
        orderItem.setUnitPriceWithoutTax(finalUnitPriceNoTax.setScale(PRICE_SCALE, ROUNDING_MODE));


        // --- ÚPRAVA ZDE: Nastavení příznaku Reverse Charge POUZE podle checkboxu z pokladny ---
        orderItem.setReverseCharge(applyReverseCharge); // Přímo přiřadíme hodnotu z parametru
        log.debug("Setting OrderItem.isReverseCharge to: {} (based SOLELY on checkout flag)", applyReverseCharge);
        // --- KONEC ÚPRAVY ---

        // --- VÝPOČET CEN S DPH (používá metodu recalculateTaxAmounts, která zohlední isReverseCharge) ---
        recalculateTaxAmounts(orderItem); // Tato metoda již počítá s příznakem orderItem.isReverseCharge()

        log.info("Finished processCartItem for Product ID: {}. RC applied: {}, Total Price w/ Tax: {}, Selected Rate ID: {}",
                itemDto.getProductId(), orderItem.isReverseCharge(), orderItem.getTotalPriceWithTax(), orderItem.getSelectedTaxRateId());

        return orderItem;
    }


    /**
     * Přepočítá částky DPH a celkové ceny s DPH pro OrderItem
     * na základě aktuálně nastavené sazby DPH a příznaku isReverseCharge.
     * @param orderItem Položka objednávky k přepočítání.
     */
    private void recalculateTaxAmounts(OrderItem orderItem) {
        BigDecimal finalUnitPriceNoTax = Optional.ofNullable(orderItem.getUnitPriceWithoutTax()).orElse(BigDecimal.ZERO);
        BigDecimal quantity = BigDecimal.valueOf(orderItem.getCount());
        // Původní sazba (21% nebo 12%) je již uložena v orderItem.getTaxRate()
        BigDecimal originalTaxRateValue = Optional.ofNullable(orderItem.getTaxRate()).orElse(BigDecimal.ZERO);

        if (orderItem.isReverseCharge()) {
            // Pokud je PDP, DPH je nula
            orderItem.setUnitTaxAmount(BigDecimal.ZERO.setScale(PRICE_SCALE, ROUNDING_MODE));
            orderItem.setTotalTaxAmount(BigDecimal.ZERO.setScale(PRICE_SCALE, ROUNDING_MODE));
            orderItem.setUnitPriceWithTax(finalUnitPriceNoTax.setScale(PRICE_SCALE, ROUNDING_MODE)); // Cena s DPH = Cena bez DPH
            // Vypočteme celkovou cenu bez DPH z jednotkové ceny bez DPH a množství
            orderItem.setTotalPriceWithoutTax(finalUnitPriceNoTax.multiply(quantity).setScale(PRICE_SCALE, ROUNDING_MODE));
            orderItem.setTotalPriceWithTax(orderItem.getTotalPriceWithoutTax()); // Celková cena s DPH = Celková cena bez DPH
            log.debug("Recalculated Tax (RC=true): UnitTax=0, TotalTax=0 for OrderItem ID {}", orderItem.getId() != null ? orderItem.getId() : "(new)");
        } else {
            // Standardní výpočet DPH podle původní sazby
            BigDecimal unitTaxAmount = finalUnitPriceNoTax.multiply(originalTaxRateValue).setScale(PRICE_SCALE, ROUNDING_MODE);
            orderItem.setUnitTaxAmount(unitTaxAmount);
            orderItem.setTotalTaxAmount(unitTaxAmount.multiply(quantity).setScale(PRICE_SCALE, ROUNDING_MODE));
            orderItem.setUnitPriceWithTax(finalUnitPriceNoTax.add(unitTaxAmount).setScale(PRICE_SCALE, ROUNDING_MODE));
            // Vypočteme celkovou cenu bez DPH a s DPH
            orderItem.setTotalPriceWithoutTax(finalUnitPriceNoTax.multiply(quantity).setScale(PRICE_SCALE, ROUNDING_MODE));
            orderItem.setTotalPriceWithTax(orderItem.getTotalPriceWithoutTax().add(orderItem.getTotalTaxAmount()).setScale(PRICE_SCALE, ROUNDING_MODE));
            log.debug("Recalculated Tax (RC=false): UnitTax={}, TotalTax={} for OrderItem ID {}", orderItem.getUnitTaxAmount(), orderItem.getTotalTaxAmount(), orderItem.getId() != null ? orderItem.getId() : "(new)");
        }

        // Přidání logování finálních cen položky po přepočtu
        log.debug("Final recalculated prices for OrderItem ID {}: UnitPriceWithoutTax={}, UnitTaxAmount={}, UnitPriceWithTax={}, TotalPriceWithoutTax={}, TotalTaxAmount={}, TotalPriceWithTax={}",
                orderItem.getId() != null ? orderItem.getId() : "(new)",
                orderItem.getUnitPriceWithoutTax(),
                orderItem.getUnitTaxAmount(),
                orderItem.getUnitPriceWithTax(),
                orderItem.getTotalPriceWithoutTax(),
                orderItem.getTotalTaxAmount(),
                orderItem.getTotalPriceWithTax());
    }

// --- ZBYTEK TŘÍDY OrderService (metody find*, update*, mark*Paid, helpers atd.) ---
// ... (vložte sem zbytek metod třídy OrderService beze změny) ...

    private void saveHistoricalItemData(OrderItem orderItem, Product product, CartItemDto itemDto, Design design, Glaze glaze, RoofColor roofColor) {
        log.debug("Saving historical data for OrderItem (Product ID: {})", product.getId());
        orderItem.setProductName(product.getName());
        orderItem.setMaterial(product.getMaterial()); // Základní materiál

        if (itemDto.isCustom()) {
            orderItem.setSku("CUSTOM-" + product.getId());
            StringBuilder variantSb = new StringBuilder("Na míru"); // Začátek popisu

            // Přidání rozměrů
            if (itemDto.getCustomDimensions() != null && !itemDto.getCustomDimensions().isEmpty()) {
                variantSb.append("|Rozměry (DxHxV): ") // Používáme '|' jako oddělovač pro snadné nahrazení <br/> v Thymeleaf
                        .append(itemDto.getCustomDimensions().get("length") != null ? itemDto.getCustomDimensions().get("length").stripTrailingZeros().toPlainString() : "?")
                        .append("x")
                        .append(itemDto.getCustomDimensions().get("width") != null ? itemDto.getCustomDimensions().get("width").stripTrailingZeros().toPlainString() : "?")
                        .append("x")
                        .append(itemDto.getCustomDimensions().get("height") != null ? itemDto.getCustomDimensions().get("height").stripTrailingZeros().toPlainString() : "?")
                        .append(" cm");
                // Uložíme rozměry i do samostatných polí
                orderItem.setLength(itemDto.getCustomDimensions().get("length"));
                orderItem.setWidth(itemDto.getCustomDimensions().get("width"));
                orderItem.setHeight(itemDto.getCustomDimensions().get("height"));
            } else {
                log.warn("Custom dimensions map missing for custom OrderItem (Product ID: {})", product.getId());
                orderItem.setLength(null);
                orderItem.setWidth(null);
                orderItem.setHeight(null);
            }

            // Přidání vybraných atributů (použijeme předané entity)
            if (design != null) variantSb.append("|Design: ").append(design.getName());
            if (glaze != null) variantSb.append("|Lazura: ").append(glaze.getName());
            if (roofColor != null) variantSb.append("|Střecha: ").append(roofColor.getName());

            // Uložení jmen pro historické účely (pokud je potřebuješ i jinde)
            orderItem.setModel(design != null ? design.getName() : null); // 'model' pro design u custom
            orderItem.setGlaze(glaze != null ? glaze.getName() : null);
            orderItem.setRoofColor(roofColor != null ? roofColor.getName() : null);
            orderItem.setDesign(null); // Textový design se už nepoužívá

            // Přidání ostatních custom voleb
            if (StringUtils.hasText(itemDto.getCustomRoofOverstep()))
                variantSb.append("|Přesah: ").append(itemDto.getCustomRoofOverstep());
            orderItem.setRoofOverstep(itemDto.getCustomRoofOverstep());

            if (itemDto.isCustomHasDivider()) variantSb.append("|Příčka: Ano");
            orderItem.setHasDivider(itemDto.isCustomHasDivider());
            if (itemDto.isCustomHasGutter()) variantSb.append("|Okap: Ano");
            orderItem.setHasGutter(itemDto.isCustomHasGutter());
            if (itemDto.isCustomHasGardenShed()) variantSb.append("|Zahr. domek: Ano");
            orderItem.setHasGardenShed(itemDto.isCustomHasGardenShed());

            orderItem.setVariantInfo(variantSb.toString()); // Uložíme sestavený popis

        } else { // Standardní produkt (logika zůstává stejná)
            orderItem.setSku(product.getSlug());
            orderItem.setLength(product.getLength());
            orderItem.setWidth(product.getWidth());
            orderItem.setHeight(product.getHeight());
            orderItem.setRoofOverstep(product.getRoofOverstep());
            orderItem.setModel(design != null ? design.getName() : null);
            orderItem.setGlaze(glaze != null ? glaze.getName() : null);
            orderItem.setRoofColor(roofColor != null ? roofColor.getName() : null);
            orderItem.setDesign(null);
            orderItem.setHasDivider(null);
            orderItem.setHasGutter(null);
            orderItem.setHasGardenShed(null);
            // Sestavení variantInfo pro standardní produkt
            StringBuilder variantSb = new StringBuilder();
            if (design != null) variantSb.append("Design: ").append(design.getName());
            if (glaze != null) {
                if (!variantSb.isEmpty()) variantSb.append(" | ");
                variantSb.append("Lazura: ").append(glaze.getName());
            }
            if (roofColor != null) {
                if (!variantSb.isEmpty()) variantSb.append(" | ");
                variantSb.append("Střecha: ").append(roofColor.getName());
            }
            orderItem.setVariantInfo(variantSb.toString());
        }
        log.debug("Finished saving historical data for OrderItem. VariantInfo: {}", orderItem.getVariantInfo());
    }

    @Transactional(readOnly = true)
    public Optional<Order> findOrderById(Long id) {
        log.debug("Finding order by ID with full details using new repo method: {}", id);
        // Volání NOVÉ repository metody
        return orderRepository.findFullDetailById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Order> findOrderByCode(String orderCode) {
        if (!StringUtils.hasText(orderCode)) return Optional.empty();
        log.debug("Finding order by code with full details using new repo method: {}", orderCode.trim());
        // Volání NOVÉ repository metody
        return orderRepository.findFullDetailByOrderCode(orderCode.trim());
    }

    @Transactional(readOnly = true)
    public long countOrdersCreatedBetween(LocalDateTime start, LocalDateTime end) {
        log.debug("Counting orders created between {} and {}", start, end);
        try {
            return orderRepository.countByOrderDateBetween(start, end);
        } catch (Exception e) {
            log.error("Error counting orders between dates: {}", e.getMessage(), e);
            return 0L; // Vrátit 0 v případě chyby
        }
    }

    @Transactional(readOnly = true)
    public long countOrdersByPaymentStatus(String paymentStatus) {
        if (!StringUtils.hasText(paymentStatus)) {
            return 0L;
        }
        log.debug("Counting orders with payment status: {}", paymentStatus);
        try {
            return orderRepository.countByPaymentStatusIgnoreCase(paymentStatus);
        } catch (Exception e) {
            log.error("Error counting orders by payment status '{}': {}", paymentStatus, e.getMessage(), e);
            return 0L;
        }
    }

    @Transactional(readOnly = true)
    public long countOrdersByStatusCode(String statusCode) {
        if (!StringUtils.hasText(statusCode)) {
            return 0L;
        }
        log.debug("Counting orders with status code: {}", statusCode);
        try {
            // Použijeme metodu z OrderRepository, kterou přidáme níže
            return orderRepository.countByStateOfOrder_CodeIgnoreCase(statusCode);
        } catch (Exception e) {
            log.error("Error counting orders by status code '{}': {}", statusCode, e.getMessage(), e);
            return 0L;
        }
    }
    // V třídě OrderService

    // UPRAVENÁ METODA: Volá productService.calculateDynamicProductPrice s novou signaturou
    private BigDecimal calculateBaseUnitPrice(Product product, CartItemDto itemDto, String currency) {
        if (itemDto.isCustom()) {
            if (itemDto.getCustomDimensions() == null) {
                log.error("!!! Missing custom dimensions for price calculation of product ID {}", product.getId());
                throw new IllegalArgumentException("Custom dimensions missing for price calculation.");
            }
            // --- OPRAVENÉ VOLÁNÍ calculateDynamicProductPrice ---
            BigDecimal dynamicPrice = productService.calculateDynamicProductPrice(
                    product,
                    itemDto.getCustomDimensions(), // Mapa rozměrů
                    currency                       // Měna
            );
            // --- KONEC OPRAVY ---
            return Optional.ofNullable(dynamicPrice).orElse(BigDecimal.ZERO);
        } else {
            BigDecimal price = EURO_CURRENCY.equals(currency) ? product.getBasePriceEUR() : product.getBasePriceCZK();
            if (price == null) {
                log.error("!!! Base price missing for standard product ID {} in currency {}", product.getId(), currency);
                throw new IllegalStateException("Base price missing for product " + product.getId() + " in " + currency);
            }
            return price.max(BigDecimal.ZERO); // Zajistit, že cena není záporná
        }
    }

    private void validateOrderRequest(CreateOrderRequest request) {
        if (request == null) throw new IllegalArgumentException("Request cannot be null.");
        if (request.getCustomerId() == null) throw new IllegalArgumentException("Customer ID missing.");
        if (CollectionUtils.isEmpty(request.getItems())) throw new IllegalArgumentException("Items missing.");
        if (!StringUtils.hasText(request.getPaymentMethod()))
            throw new IllegalArgumentException("Payment method missing.");
        if (!StringUtils.hasText(request.getCurrency()) || !(DEFAULT_CURRENCY.equals(request.getCurrency()) || EURO_CURRENCY.equals(request.getCurrency()))) {
            throw new IllegalArgumentException("Invalid or missing currency in request: " + request.getCurrency());
        }
        // Shipping cost validation is now handled within the createOrder logic
    }

    private boolean hasSufficientAddress(Customer customer) {
        // Check invoice address
        boolean hasInvoiceRecipient = StringUtils.hasText(customer.getInvoiceCompanyName()) ||
                (StringUtils.hasText(customer.getInvoiceFirstName()) && StringUtils.hasText(customer.getInvoiceLastName()));
        boolean hasInvoiceCore = StringUtils.hasText(customer.getInvoiceStreet()) &&
                StringUtils.hasText(customer.getInvoiceCity()) &&
                StringUtils.hasText(customer.getInvoiceZipCode()) &&
                StringUtils.hasText(customer.getInvoiceCountry());
        if (!hasInvoiceRecipient || !hasInvoiceCore) return false;

        // Check delivery address if it's different
        if (!customer.isUseInvoiceAddressAsDelivery()) {
            boolean hasDeliveryRecipient = StringUtils.hasText(customer.getDeliveryCompanyName()) ||
                    (StringUtils.hasText(customer.getDeliveryFirstName()) && StringUtils.hasText(customer.getDeliveryLastName()));
            boolean hasDeliveryCore = StringUtils.hasText(customer.getDeliveryStreet()) &&
                    StringUtils.hasText(customer.getDeliveryCity()) &&
                    StringUtils.hasText(customer.getDeliveryZipCode()) &&
                    StringUtils.hasText(customer.getDeliveryCountry());
            return hasDeliveryRecipient && hasDeliveryCore;
        }
        return true; // Invoice address is sufficient if delivery is the same
    }

    // V src/main/java/org/example/eshop/service/OrderService.java

    // V src/main/java/org/example/eshop/service/OrderService.java

    private void copyAddressesToOrder(Order order, Customer customer, CreateOrderRequest request) {
        log.debug("Copying addresses for customer ID: {} to order.", customer.getId());

        // --- Fakturační adresa ---
        order.setInvoiceCompanyName(customer.getInvoiceCompanyName());
        order.setInvoiceStreet(customer.getInvoiceStreet());
        order.setInvoiceCity(customer.getInvoiceCity());
        order.setInvoiceZipCode(customer.getInvoiceZipCode());
        order.setInvoiceCountry(customer.getInvoiceCountry());
        order.setInvoiceTaxId(customer.getInvoiceTaxId());
        order.setInvoiceVatId(customer.getInvoiceVatId());

        // Nastavení fakturačního jména/příjmení v Order entitě
        if (StringUtils.hasText(customer.getInvoiceCompanyName())) {
            order.setInvoiceFirstName(null); // Firma má přednost, jméno na faktuře bude null
            order.setInvoiceLastName(null);
            log.debug("Invoice recipient is company: {}", customer.getInvoiceCompanyName());
        } else {
            order.setInvoiceFirstName(customer.getFirstName()); // Není firma, použij kontaktní jméno
            order.setInvoiceLastName(customer.getLastName());
            log.debug("Invoice recipient is person: {} {}", customer.getFirstName(), customer.getLastName());
        }

        // --- Dodací adresa ---
        if (customer.isUseInvoiceAddressAsDelivery()) {
            log.debug("Delivery address is same as invoice. Copying invoice address details AND customer contact name/phone.");
            // Kopírujeme detaily adresy z fakturačních údajů zákazníka
            order.setDeliveryStreet(customer.getInvoiceStreet());
            order.setDeliveryCity(customer.getInvoiceCity());
            order.setDeliveryZipCode(customer.getInvoiceZipCode());
            order.setDeliveryCountry(customer.getInvoiceCountry());
            order.setDeliveryCompanyName(customer.getInvoiceCompanyName()); // Kopírujeme i firmu

            // ***** KLÍČOVÁ ZMĚNA ZDE *****
            // Pro jméno, příjmení a telefon VŽDY použijeme kontaktní údaje zákazníka,
            // protože dodací adresa má sloužit primárně pro dopravce.
            order.setDeliveryFirstName(customer.getFirstName());
            order.setDeliveryLastName(customer.getLastName());
            order.setDeliveryPhone(StringUtils.hasText(customer.getPhone()) ? customer.getPhone() : null); // Použij hlavní telefon
            // ***** KONEC KLÍČOVÉ ZMĚNY *****

        } else {
            log.debug("Copying specific delivery address to order.");
            // Používáme specifickou dodací adresu z Customer entity
            order.setDeliveryCompanyName(customer.getDeliveryCompanyName());
            order.setDeliveryFirstName(customer.getDeliveryFirstName());
            order.setDeliveryLastName(customer.getDeliveryLastName());
            order.setDeliveryStreet(customer.getDeliveryStreet());
            order.setDeliveryCity(customer.getDeliveryCity());
            order.setDeliveryZipCode(customer.getDeliveryZipCode());
            order.setDeliveryCountry(customer.getDeliveryCountry());
            order.setDeliveryPhone(customer.getDeliveryPhone());
        }

        // --- Finální kontrola a fallback pro dodací telefon ---
        // (Tato část je teď méně kritická po úpravě výše, ale necháme ji pro jistotu)
        if (!StringUtils.hasText(order.getDeliveryPhone()) && StringUtils.hasText(customer.getPhone())) {
            order.setDeliveryPhone(customer.getPhone());
            log.debug("Setting delivery phone from main customer phone as fallback (if specific was empty).");
        }

        // --- Kontrola kompletnosti adres (bez změny) ---
        // ... (zbytek metody zůstává stejný) ...
        boolean invoiceAddrOk = StringUtils.hasText(order.getInvoiceStreet()) &&
                StringUtils.hasText(order.getInvoiceCity()) &&
                StringUtils.hasText(order.getInvoiceZipCode()) &&
                StringUtils.hasText(order.getInvoiceCountry()) &&
                (StringUtils.hasText(order.getInvoiceCompanyName()) ||
                        (StringUtils.hasText(order.getInvoiceFirstName()) && StringUtils.hasText(order.getInvoiceLastName())));

        boolean deliveryAddrOk = StringUtils.hasText(order.getDeliveryStreet()) &&
                StringUtils.hasText(order.getDeliveryCity()) &&
                StringUtils.hasText(order.getDeliveryZipCode()) &&
                StringUtils.hasText(order.getDeliveryCountry()) &&
                (StringUtils.hasText(order.getDeliveryCompanyName()) ||
                        (StringUtils.hasText(order.getDeliveryFirstName()) && StringUtils.hasText(order.getDeliveryLastName())));


        if (!invoiceAddrOk || !deliveryAddrOk) {
            String orderCodeLog = (order.getOrderCode() != null) ? order.getOrderCode() : "(new)";
            log.error("!!! Copied address is incomplete for order {}. Invoice OK: {}, Delivery OK: {}. Check Customer ID {} data and copy logic.",
                    orderCodeLog, invoiceAddrOk, deliveryAddrOk, customer.getId());
        } else {
            log.debug("Address copy for order {} completed successfully.", (order.getOrderCode() != null) ? order.getOrderCode() : "(new)");
        }
    }

    // --- Metody pro čtení a aktualizaci stavů (zůstávají stejné) ---


    @Transactional(readOnly = true)
    public List<Order> findAllOrdersByCustomerId(Long customerId) {
        if (customerId == null) return Collections.emptyList();
        log.debug("Finding all orders for customer ID: {}", customerId);
        return orderRepository.findByCustomerIdOrderByOrderDateDesc(customerId);
    }

    @Transactional
    public Order updateOrderState(Long orderId, Long newOrderStateId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
        OrderState newOrderState = orderStateRepository.findById(newOrderStateId)
                .orElseThrow(() -> new EntityNotFoundException("OrderState not found: " + newOrderStateId));

        OrderState oldState = order.getStateOfOrder();
        if (oldState != null && oldState.getId().equals(newOrderState.getId())) {
            log.info("Order {} is already in state '{}'. No change needed.", order.getOrderCode(), newOrderState.getName());
            return order; // No need to change anything
        }

        log.info("Updating order {} state from '{}' to '{}'",
                order.getOrderCode(),
                oldState != null ? oldState.getName() : "null",
                newOrderState.getName());

        order.setStateOfOrder(newOrderState);
        updateOrderTimestamps(order, newOrderState); // Update timestamps based on the new state
        Order savedOrder = orderRepository.save(order);

        // Send email (asynchronously)
        try {
            emailService.sendOrderStatusUpdateEmail(savedOrder, newOrderState);
        } catch (Exception e) {
            // Log the error, but don't prevent returning the saved order
            log.error("Failed to send status update email for order {}: {}", savedOrder.getOrderCode(), e.getMessage());
        }

        return savedOrder;
    }

    private void updateOrderTimestamps(Order order, OrderState newState) {
        LocalDateTime now = LocalDateTime.now();
        if (newState == null || newState.getCode() == null) return;

        String code = newState.getCode().toUpperCase();
        switch (code) {
            case "SHIPPED":
                if (order.getShippedDate() == null) order.setShippedDate(now);
                break;
            case "DELIVERED":
                if (order.getDeliveredDate() == null) order.setDeliveredDate(now);
                // If delivered, it should also be shipped
                if (order.getShippedDate() == null) order.setShippedDate(now);
                break;
            case "CANCELLED":
                if (order.getCancelledDate() == null) order.setCancelledDate(now);
                break;
            // Potentially add other automatic date settings for other states
        }
    }

    @Transactional(readOnly = true)
    public Page<Order> findOrders(Pageable pageable,
                                  Optional<String> customerEmail,
                                  Optional<Long> stateId,
                                  Optional<String> paymentStatus,
                                  Optional<LocalDateTime> dateTimeFrom,
                                  Optional<LocalDateTime> dateTimeTo) {

        log.debug("OrderService.findOrders called with filters - Email: '{}', StateID: {}, PaymentStatus: '{}', From: {}, To: {}. Pageable: {}",
                customerEmail.orElse("N/A"), stateId.orElse(null), paymentStatus.orElse("N/A"),
                dateTimeFrom.orElse(null), dateTimeTo.orElse(null), pageable);

        Specification<Order> spec = Specification.where(null); // Start with empty spec

        if (customerEmail.filter(StringUtils::hasText).isPresent()) {
            spec = spec.and(OrderSpecifications.customerEmailContains(customerEmail.get()));
        }
        if (stateId.isPresent()) {
            spec = spec.and(OrderSpecifications.hasStateId(stateId.get()));
        }
        if (paymentStatus.filter(StringUtils::hasText).isPresent()) {
            spec = spec.and(OrderSpecifications.hasPaymentStatus(paymentStatus.get()));
        }
        if (dateTimeFrom.isPresent()) {
            spec = spec.and(OrderSpecifications.orderDateFrom(dateTimeFrom.get()));
        }
        if (dateTimeTo.isPresent()) {
            spec = spec.and(OrderSpecifications.orderDateTo(dateTimeTo.get()));
        }

        Page<Order> result = orderRepository.findAll(spec, pageable);
        log.info("Found {} orders matching criteria using specifications.", result.getTotalElements());
        return result;
    }

    // --- Metody pro platby (zůstávají stejné) ---
    @Transactional
    public Order markDepositAsPaid(Long orderId, LocalDate paymentDate) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        // Validation checks
        if (order.getDepositAmount() == null || order.getDepositAmount().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Attempted to mark deposit paid for order {} which does not require a deposit.", order.getOrderCode());
            throw new IllegalStateException("Objednávka " + order.getOrderCode() + " nevyžaduje zálohu.");
        }
        if (order.getDepositPaidDate() != null || PAYMENT_STATUS_DEPOSIT_PAID.equals(order.getPaymentStatus()) || PAYMENT_STATUS_PAID.equals(order.getPaymentStatus())) {
            log.warn("Attempted to mark deposit paid for order {} which is already marked as deposit paid or fully paid.", order.getOrderCode());
            throw new IllegalStateException("Záloha pro objednávku " + order.getOrderCode() + " je již označena jako zaplacená.");
        }
        if (paymentDate == null) {
            throw new IllegalArgumentException("Datum platby zálohy je povinné.");
        }

        // Update order status
        order.setPaymentStatus(PAYMENT_STATUS_DEPOSIT_PAID);
        order.setDepositPaidDate(paymentDate.atStartOfDay()); // Store time as well (start of day)
        log.info("Marking deposit paid for order {} on {}", order.getOrderCode(), paymentDate);
        Order savedOrder = orderRepository.save(order);

        // --- Call SuperFaktura API ---
        Long invoiceIdToMark = savedOrder.getSfProformaInvoiceId(); // Try to mark the proforma
        if (invoiceIdToMark == null) {
            // If proforma has no ID, try the tax document (shouldn't happen if paid before tax doc, but just in case)
            invoiceIdToMark = savedOrder.getSfTaxDocumentId();
            if (invoiceIdToMark != null) {
                log.warn("Marking deposit paid on Tax Document SF ID {} instead of Proforma for order {}", invoiceIdToMark, savedOrder.getOrderCode());
            }
        }

        if (invoiceIdToMark != null) {
            try {
                String sfPaymentType = mapPaymentMethodToSf(savedOrder.getPaymentMethod());
                // Assume amount is the full deposit amount
                log.info("Attempting to mark invoice SF ID {} as paid in SuperFaktura (Amount: {}, Date: {}, Type: {}) for order {}",
                        invoiceIdToMark, savedOrder.getDepositAmount(), paymentDate, sfPaymentType, savedOrder.getOrderCode());
                invoiceService.markInvoiceAsPaidInSF(invoiceIdToMark, savedOrder.getDepositAmount(), paymentDate, sfPaymentType, savedOrder.getOrderCode());
                log.info("Successfully requested marking invoice SF ID {} as paid in SuperFaktura.", invoiceIdToMark);
            } catch (Exception e) {
                // Only log, don't block saving in our system
                log.error("Failed to mark invoice SF ID {} as paid in SuperFaktura for order {}: {}. Operation continues.",
                        invoiceIdToMark, savedOrder.getOrderCode(), e.getMessage(), e);
                // Could add an internal note to the order here
            }
        } else {
            log.warn("Cannot mark deposit paid in SuperFaktura for order {}: No associated Proforma or Tax Document SF ID found.", savedOrder.getOrderCode());
        }
        // --- End SuperFaktura API Call ---

        // Trigger Tax Document generation (AFTER marking deposit paid)
        if (savedOrder.getSfTaxDocumentId() == null) {
            log.info("Triggering tax document generation after marking deposit paid for order {}", savedOrder.getOrderCode());
            try {
                invoiceService.generateTaxDocumentForDeposit(savedOrder);
            } catch (Exception e) {
                log.error("Non-critical error generating tax document after marking deposit paid for order {}: {}. Operation continues.",
                        savedOrder.getOrderCode(), e.getMessage(), e);
            }
        } else {
            log.warn("Tax document already exists (SF ID: {}) for order {}. Skipping generation.", savedOrder.getSfTaxDocumentId(), savedOrder.getOrderCode());
        }


        return savedOrder;
    }

    @Transactional
    public Order markOrderAsFullyPaid(Long orderId, LocalDate paymentDate) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        // Validation checks
        if (paymentDate == null) {
            throw new IllegalArgumentException("Datum platby je povinné.");
        }
        if (PAYMENT_STATUS_AWAITING_DEPOSIT.equals(order.getPaymentStatus())) {
            log.warn("Attempted to mark fully paid for order {} which is awaiting deposit.", order.getOrderCode());
            throw new IllegalStateException("Nelze označit jako plně zaplaceno, pokud objednávka (" + order.getOrderCode() + ") čeká na zálohu.");
        }
        if (PAYMENT_STATUS_PAID.equals(order.getPaymentStatus())) {
            log.warn("Attempted to mark fully paid for order {} which is already paid.", order.getOrderCode());
            throw new IllegalStateException("Objednávka " + order.getOrderCode() + " je již označena jako zaplacená.");
        }

        // Calculate amount just paid (remaining or full)
        BigDecimal amountJustPaid;
        if (PAYMENT_STATUS_DEPOSIT_PAID.equals(order.getPaymentStatus()) && order.getDepositAmount() != null && order.getDepositAmount().compareTo(BigDecimal.ZERO) > 0) {
            amountJustPaid = Optional.ofNullable(order.getTotalPrice()).orElse(BigDecimal.ZERO)
                    .subtract(Optional.of(order.getDepositAmount()).orElse(BigDecimal.ZERO));
            log.debug("Calculating remaining amount for order {}: Total({}) - Deposit({}) = {}",
                    order.getOrderCode(), order.getTotalPrice(), order.getDepositAmount(), amountJustPaid);
        } else {
            amountJustPaid = Optional.ofNullable(order.getTotalPrice()).orElse(BigDecimal.ZERO);
            log.debug("Calculating full amount for order {}: {}", order.getOrderCode(), amountJustPaid);
        }
        amountJustPaid = amountJustPaid.max(BigDecimal.ZERO); // Ensure non-negative

        // Update order status
        order.setPaymentStatus(PAYMENT_STATUS_PAID);
        order.setPaymentDate(paymentDate.atStartOfDay());
        // If deposit was required but not marked, mark it now
        if (order.getDepositAmount() != null && order.getDepositAmount().compareTo(BigDecimal.ZERO) > 0 && order.getDepositPaidDate() == null) {
            log.warn("Marking order {} fully paid also sets missing deposit paid date to {}", order.getOrderCode(), paymentDate);
            order.setDepositPaidDate(paymentDate.atStartOfDay());
        }
        log.info("Marking order {} fully paid on {}", order.getOrderCode(), paymentDate);
        Order savedOrder = orderRepository.save(order);

        // --- Call SuperFaktura API ---
        // Mark payment on the FINAL invoice if it exists
        Long invoiceIdToMark = savedOrder.getSfFinalInvoiceId();
        if (invoiceIdToMark != null) {
            try {
                String sfPaymentType = mapPaymentMethodToSf(savedOrder.getPaymentMethod());
                log.info("Attempting to mark final invoice SF ID {} as paid in SuperFaktura (Amount: {}, Date: {}, Type: {}) for order {}",
                        invoiceIdToMark, amountJustPaid, paymentDate, sfPaymentType, savedOrder.getOrderCode());
                // Call SF API with the amount that was just paid (remaining or full)
                invoiceService.markInvoiceAsPaidInSF(invoiceIdToMark, amountJustPaid, paymentDate, sfPaymentType, savedOrder.getOrderCode());
                log.info("Successfully requested marking final invoice SF ID {} as paid in SuperFaktura.", invoiceIdToMark);
            } catch (Exception e) {
                log.error("Failed to mark final invoice SF ID {} as paid in SuperFaktura for order {}: {}. Operation continues.",
                        invoiceIdToMark, savedOrder.getOrderCode(), e.getMessage(), e);
                // addInternalOrderNote(savedOrder.getId(), "Nepodařilo se označit platbu finální faktury v SF: " + e.getMessage());
            }
        } else {
            log.warn("Cannot mark payment in SuperFaktura for order {}: Final Invoice SF ID is missing.", savedOrder.getOrderCode());
        }
        // --- End SuperFaktura API Call ---

        return savedOrder;
    }

    // Pomocná metoda pro mapování platebních metod (zůstává stejná)
    private String mapPaymentMethodToSf(String localPaymentMethod) {
        if (localPaymentMethod == null) return "transfer"; // Default
        return switch (localPaymentMethod.toUpperCase()) {
            case "CASH_ON_DELIVERY" -> "cash"; // Or "cod", check SF API docs!
            case "BANK_TRANSFER" -> "transfer";
            default -> {
                log.warn("Unknown local payment method '{}', defaulting to 'transfer' for SuperFaktura.", localPaymentMethod);
                yield "transfer";
            }
        };
    }
    // ----- NOVÁ POMOCNÁ METODA PRO KUPÓN -----

    /**
     * Validates the coupon based on general rules, minimum order value, and customer usage limits.
     * If valid, calculates the discount and applies it to the order object.
     *
     * @param order      The order object (used to set the discount amount).
     * @param couponCode The code entered by the user.
     * @param customer   The customer placing the order.
     * @return The validated Coupon object if it's valid and applied, otherwise null.
     */
    private Coupon validateAndApplyCoupon(Order order, String couponCode, Customer customer) {
        // Reset discount and coupon on order before validation
        order.setCouponDiscountAmount(BigDecimal.ZERO);
        order.setAppliedCoupon(null);

        if (!StringUtils.hasText(couponCode) || customer == null || order.getSubTotalWithoutTax() == null) {
            log.debug("validateAndApplyCoupon: Missing coupon code, order, customer, or subtotal. No coupon applied.");
            return null; // Missing required data
        }

        String currency = order.getCurrency();
        BigDecimal subTotalWithoutTax = order.getSubTotalWithoutTax();

        Optional<Coupon> couponOpt = couponService.findByCode(couponCode.trim());
        if (couponOpt.isEmpty()) {
            log.warn("Coupon code '{}' not found during order creation.", couponCode);
            return null; // Coupon not found
        }

        Coupon coupon = couponOpt.get();
        boolean isGuest = customer.isGuest();

        // Perform all validity checks using CouponService methods
        if (!couponService.isCouponGenerallyValid(coupon)) {
            log.warn("Coupon '{}' is generally invalid (inactive, expired, limit reached).", couponCode);
            return null;
        }
        if (!couponService.checkMinimumOrderValue(coupon, subTotalWithoutTax, currency)) {
            log.warn("Coupon '{}' minimum order value not met for subtotal {} {}.", couponCode, subTotalWithoutTax, currency);
            return null;
        }
        // Check customer usage limit for registered users
        if (!isGuest && !couponService.checkCustomerUsageLimit(customer, coupon)) {
            log.warn("Coupon '{}' customer usage limit reached for customer {}.", couponCode, customer.getId());
            return null;
        }
        // Special warning for guests using limited coupons
        if (isGuest && coupon.getUsageLimitPerCustomer() != null && coupon.getUsageLimitPerCustomer() > 0) {
            log.warn("Applying customer-limited coupon '{}' to guest user {}. Limit check will occur if guest converts.", couponCode, customer.getId());
        }

        // If all checks pass, calculate and apply the discount
        BigDecimal discountAmount = couponService.calculateDiscountAmount(subTotalWithoutTax, coupon, currency);
        order.setCouponDiscountAmount(discountAmount.setScale(PRICE_SCALE, ROUNDING_MODE)); // Set discount directly on the order
        order.setAppliedCoupon(coupon); // Set reference to the valid coupon
        log.info("Applied coupon {} for cust={}, isGuest={}, discount={} {}", couponCode, customer.getId(), isGuest, order.getCouponDiscountAmount(), currency);

        return coupon; // Return the valid coupon
    }
}