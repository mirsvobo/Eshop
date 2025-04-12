package org.example.eshop.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.config.PriceConstants;
import org.example.eshop.dto.AddonDto;
import org.example.eshop.dto.CartItemDto;
import org.example.eshop.dto.CreateOrderRequest;
import org.example.eshop.model.*; // Import všech modelů
import org.example.eshop.repository.*; // Import všech repositories
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode; // Import pro RoundingMode
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
    // Konstanty pro měny již máme z PriceConstants interface (EURO_CURRENCY, DEFAULT_CURRENCY)

    // Repositories
    @Autowired private OrderRepository orderRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private OrderStateRepository orderStateRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private AddonsRepository addonsRepository;
    @Autowired private CouponRepository couponRepository;
    @Autowired private TaxRateRepository taxRateRepository;
    @Autowired private DesignRepository designRepository;
    @Autowired private GlazeRepository glazeRepository;
    @Autowired private RoofColorRepository roofColorRepository;

    // Services
    @Autowired private ProductService productService;
    @Autowired private DiscountService discountService;
    @Autowired private CouponService couponService;
    @Autowired private EmailService emailService;
    @Autowired @Qualifier("googleMapsShippingService") private ShippingService shippingService;
    // Použijeme @Lazy zde, abychom předešli cyklické závislosti při startu aplikace
    // (pokud by SuperFakturaInvoiceService měla závislost na OrderService)
    @Autowired @Lazy private SuperFakturaInvoiceService invoiceService;
    @Autowired private PaymentService paymentService;
    @Autowired private OrderCodeGeneratorService orderCodeGeneratorService;

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        log.info("Attempting to create order for customer ID: {} with explicit currency: {}", request.getCustomerId(), request.getCurrency());
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
                    OrderItem orderItem = processCartItem(itemDto, order, orderCurrency); // Process one item
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
                    if (request.getShippingCostNoTax() != null && request.getShippingTax() != null && request.getShippingCostNoTax().compareTo(BigDecimal.ZERO) >= 0) {
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

            // 6. Calculating Final Totals
            log.debug("[Order Creation - Step 6] Calculating final totals for currency: {}", orderCurrency);
            try {
                BigDecimal subTotalAfterDiscount = Optional.ofNullable(order.getSubTotalWithoutTax()).orElse(BigDecimal.ZERO)
                        .subtract(Optional.ofNullable(order.getCouponDiscountAmount()).orElse(BigDecimal.ZERO));
                // Use final (potentially zero) shipping costs
                BigDecimal finalTotalWithoutTax = subTotalAfterDiscount
                        .add(Optional.ofNullable(order.getShippingCostWithoutTax()).orElse(BigDecimal.ZERO));
                // Use final (potentially zero) shipping tax
                BigDecimal finalTotalTax = Optional.ofNullable(order.getTotalItemsTax()).orElse(BigDecimal.ZERO)
                        .add(Optional.ofNullable(order.getShippingTax()).orElse(BigDecimal.ZERO));
                BigDecimal finalTotalWithTax = finalTotalWithoutTax.add(finalTotalTax);

                order.setTotalPriceWithoutTax(finalTotalWithoutTax.setScale(PRICE_SCALE, ROUNDING_MODE));
                order.setTotalTax(finalTotalTax.setScale(PRICE_SCALE, ROUNDING_MODE));
                order.setTotalPrice(finalTotalWithTax.setScale(PRICE_SCALE, ROUNDING_MODE));
                log.debug("Final totals calculated. TotalNoTax: {}, TotalTax: {}, TotalWithTax: {}",
                        order.getTotalPriceWithoutTax(), order.getTotalTax(), order.getTotalPrice());
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
            log.debug("[Order Creation - Step 10] Sending confirmation email for order {}...", savedOrder.getOrderCode());
            try {
                emailService.sendOrderConfirmationEmail(savedOrder);
            } catch (Exception e) {
                log.error("Non-critical error sending confirmation email for order {}: {}. Order creation continues.",
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


    // ... (processCartItem a ostatní pomocné metody zůstávají stejné) ...
    private OrderItem processCartItem(CartItemDto itemDto, Order order, String currency) {
        log.debug("Starting processCartItem for Product ID: {}", itemDto.getProductId());
        // --- Fetching Product and Tax Rate ---
        Product product = productRepository.findById(itemDto.getProductId())
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + itemDto.getProductId()));
        if (!product.isActive()) throw new IllegalArgumentException("Product '" + product.getName() + "' is inactive.");
        TaxRate taxRate = product.getTaxRate();
        if (taxRate == null || taxRate.getRate() == null) {
            log.error("!!! Product {} is missing valid TaxRate!", product.getId());
            throw new IllegalStateException("Product " + product.getId() + " invalid TaxRate.");
        }
        BigDecimal itemTaxRateValue = taxRate.getRate();

        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setProduct(product);
        orderItem.setCount(itemDto.getQuantity());
        orderItem.setCustomConfigured(itemDto.isCustom());
        orderItem.setTaxRate(itemTaxRateValue);
        orderItem.setReverseCharge(taxRate.isReverseCharge());

        // --- Calculate Surcharges (Safe) ---
        Design selectedDesign = null;
        Glaze selectedGlaze = null;
        RoofColor selectedRoofColor = null;
        BigDecimal surcharge = BigDecimal.ZERO;
        if (!itemDto.isCustom()) {
            if (itemDto.getSelectedDesignId() == null || itemDto.getSelectedGlazeId() == null || itemDto.getSelectedRoofColorId() == null) {
                throw new IllegalArgumentException("Missing attribute selection for standard product item.");
            }
            selectedDesign = designRepository.findById(itemDto.getSelectedDesignId()).orElseThrow(() -> new IllegalArgumentException("Design not found: " + itemDto.getSelectedDesignId()));
            selectedGlaze = glazeRepository.findById(itemDto.getSelectedGlazeId()).orElseThrow(() -> new IllegalArgumentException("Glaze not found: " + itemDto.getSelectedGlazeId()));
            selectedRoofColor = roofColorRepository.findById(itemDto.getSelectedRoofColorId()).orElseThrow(() -> new IllegalArgumentException("RoofColor not found: " + itemDto.getSelectedRoofColorId()));
            BigDecimal surchargeCZK = Optional.ofNullable(selectedGlaze.getPriceSurchargeCZK()).orElse(BigDecimal.ZERO).add(Optional.ofNullable(selectedRoofColor.getPriceSurchargeCZK()).orElse(BigDecimal.ZERO));
            BigDecimal surchargeEUR = Optional.ofNullable(selectedGlaze.getPriceSurchargeEUR()).orElse(BigDecimal.ZERO).add(Optional.ofNullable(selectedRoofColor.getPriceSurchargeEUR()).orElse(BigDecimal.ZERO));
            surcharge = EURO_CURRENCY.equals(currency) ? surchargeEUR : surchargeCZK;
        }
        log.debug("Surcharge calculated: {}", surcharge);

        // --- Save Historical Data ---
        saveHistoricalItemData(orderItem, product, itemDto, selectedDesign, selectedGlaze, selectedRoofColor);

        // --- Calculate Prices (Ensure Non-Null Intermediate Steps) ---
        BigDecimal baseUnitPriceNoTaxRaw = calculateBaseUnitPrice(product, itemDto, currency);
        BigDecimal baseUnitPriceNoTax = Optional.ofNullable(baseUnitPriceNoTaxRaw).orElse(BigDecimal.ZERO);
        log.debug("Base Unit Price (No Tax, Raw): {}", baseUnitPriceNoTax);

        BigDecimal unitPriceAfterDiscountNoTax = Optional.ofNullable(discountService.applyBestPercentageDiscount(baseUnitPriceNoTax, product)).orElse(baseUnitPriceNoTax);
        log.debug("Unit Price After Discount: {}", unitPriceAfterDiscountNoTax);

        BigDecimal totalFixedAddonsPriceNoTax = Optional.ofNullable(itemDto.isCustom() ? processAndCalculateAddons(orderItem, itemDto, currency) : BigDecimal.ZERO).orElse(BigDecimal.ZERO);
        log.debug("Total Fixed Addons Price: {}", totalFixedAddonsPriceNoTax);

        BigDecimal finalUnitPriceNoTax = Optional.ofNullable(unitPriceAfterDiscountNoTax).orElse(BigDecimal.ZERO)
                .add(Optional.ofNullable(surcharge).orElse(BigDecimal.ZERO))
                .add(Optional.ofNullable(totalFixedAddonsPriceNoTax).orElse(BigDecimal.ZERO));
        log.debug("Final Unit Price (No Tax): {}", finalUnitPriceNoTax);

        // --- Calculate Taxes (Ensure Non-Null) ---
        BigDecimal unitTaxAmount = finalUnitPriceNoTax.multiply(itemTaxRateValue).setScale(PRICE_SCALE, ROUNDING_MODE);
        unitTaxAmount = Optional.ofNullable(unitTaxAmount).orElse(BigDecimal.ZERO);
        log.debug("Unit Tax Amount: {}", unitTaxAmount);

        BigDecimal unitPriceWithTax = finalUnitPriceNoTax.add(unitTaxAmount);
        unitPriceWithTax = Optional.ofNullable(unitPriceWithTax).orElse(BigDecimal.ZERO);
        log.debug("Unit Price With Tax: {}", unitPriceWithTax);

        // --- Set Values on OrderItem ---
        orderItem.setUnitPriceWithoutTax(finalUnitPriceNoTax.setScale(PRICE_SCALE, ROUNDING_MODE));
        orderItem.setUnitTaxAmount(unitTaxAmount.setScale(PRICE_SCALE, ROUNDING_MODE));
        orderItem.setUnitPriceWithTax(unitPriceWithTax.setScale(PRICE_SCALE, ROUNDING_MODE));

        // --- Calculate Totals ---
        BigDecimal quantity = BigDecimal.valueOf(itemDto.getQuantity());
        BigDecimal calculatedTotalPriceWithoutTax = Optional.ofNullable(orderItem.getUnitPriceWithoutTax()).orElse(BigDecimal.ZERO).multiply(quantity).setScale(PRICE_SCALE, ROUNDING_MODE);
        BigDecimal calculatedTotalTaxAmount = Optional.ofNullable(orderItem.getUnitTaxAmount()).orElse(BigDecimal.ZERO).multiply(quantity).setScale(PRICE_SCALE, ROUNDING_MODE);
        BigDecimal calculatedTotalPriceWithTax = Optional.ofNullable(orderItem.getUnitPriceWithTax()).orElse(BigDecimal.ZERO).multiply(quantity).setScale(PRICE_SCALE, ROUNDING_MODE);

        log.debug("Calculated Totals: TotalPriceWithoutTax={}, TotalTaxAmount={}, TotalPriceWithTax={}",
                calculatedTotalPriceWithoutTax, calculatedTotalTaxAmount, calculatedTotalPriceWithTax);

        orderItem.setTotalPriceWithoutTax(calculatedTotalPriceWithoutTax);
        orderItem.setTotalTaxAmount(calculatedTotalTaxAmount);
        orderItem.setTotalPriceWithTax(calculatedTotalPriceWithTax);

        log.debug("Final OrderItem state before return: PriceNoTax={}, TaxAmount={}",
                orderItem.getTotalPriceWithoutTax(), orderItem.getTotalTaxAmount());

        return orderItem;
    }

    private void saveHistoricalItemData(OrderItem orderItem, Product product, CartItemDto itemDto, Design design, Glaze glaze, RoofColor roofColor) {
        orderItem.setProductName(product.getName());
        orderItem.setMaterial(product.getMaterial());
        if (itemDto.isCustom()) {
            orderItem.setSku("CUSTOM-" + product.getId());
            orderItem.setVariantInfo("Produkt na míru");
            if (itemDto.getCustomDimensions() != null) {
                orderItem.setLength(itemDto.getCustomDimensions().get("length"));
                orderItem.setWidth(itemDto.getCustomDimensions().get("width"));
                orderItem.setHeight(itemDto.getCustomDimensions().get("height"));
            } else {
                log.warn("Custom dimensions map missing for custom OrderItem (Product ID: {})", product.getId());
                orderItem.setLength(null);
                orderItem.setWidth(null);
                orderItem.setHeight(null);
            }
            orderItem.setGlaze(itemDto.getCustomGlaze());
            orderItem.setRoofColor(itemDto.getCustomRoofColor());
            orderItem.setRoofOverstep(itemDto.getCustomRoofOverstep());
            orderItem.setModel(null); // Standardní model není relevantní
            orderItem.setDesign(itemDto.getCustomDesign()); // Textový design
            orderItem.setHasDivider(itemDto.isCustomHasDivider());
            orderItem.setHasGutter(itemDto.isCustomHasGutter());
            orderItem.setHasGardenShed(itemDto.isCustomHasGardenShed());
        } else {
            orderItem.setSku(product.getSlug());
            orderItem.setLength(product.getLength());
            orderItem.setWidth(product.getWidth());
            orderItem.setHeight(product.getHeight());
            orderItem.setRoofOverstep(product.getRoofOverstep());
            orderItem.setModel(design != null ? design.getName() : null);
            orderItem.setGlaze(glaze != null ? glaze.getName() : null);
            orderItem.setRoofColor(roofColor != null ? roofColor.getName() : null);
            // Sestavení variantInfo
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
            orderItem.setDesign(null); // Textový design je null pro standardní
            orderItem.setHasDivider(null);
            orderItem.setHasGutter(null);
            orderItem.setHasGardenShed(null);
        }
    }

    private BigDecimal calculateBaseUnitPrice(Product product, CartItemDto itemDto, String currency) {
        if (itemDto.isCustom()) {
            if (itemDto.getCustomDimensions() == null) {
                log.error("!!! Missing custom dimensions for price calculation of product ID {}", product.getId());
                throw new IllegalArgumentException("Custom dimensions missing for price calculation.");
            }
            BigDecimal dynamicPrice = productService.calculateDynamicProductPrice(
                    product,
                    itemDto.getCustomDimensions(),
                    itemDto.getCustomDesign(),
                    itemDto.isCustomHasDivider(),
                    itemDto.isCustomHasGutter(),
                    itemDto.isCustomHasGardenShed(),
                    currency
            );
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

    private BigDecimal processAndCalculateAddons(OrderItem orderItem, CartItemDto itemDto, String currency) {
        BigDecimal totalAddonPrice = BigDecimal.ZERO;
        if (!itemDto.isCustom() || CollectionUtils.isEmpty(itemDto.getSelectedAddons())) {
            orderItem.setSelectedAddons(Collections.emptyList()); // Zajistit prázdný seznam
            return totalAddonPrice;
        }

        List<AddonDto> validAddonDtos = itemDto.getSelectedAddons().stream()
                .filter(dto -> dto != null && dto.getAddonId() != null && dto.getQuantity() > 0)
                .collect(Collectors.toList());

        if (validAddonDtos.isEmpty()) {
            orderItem.setSelectedAddons(Collections.emptyList());
            return totalAddonPrice;
        }

        Set<Long> requestedAddonIds = validAddonDtos.stream().map(AddonDto::getAddonId).collect(Collectors.toSet());
        Map<Long, Addon> addonsMap = addonsRepository.findAllById(requestedAddonIds).stream()
                .collect(Collectors.toMap(Addon::getId, a -> a));
        // Zajistit, že product.getAvailableAddons() není null
        Set<Long> allowedAddonIds = Optional.ofNullable(orderItem.getProduct().getAvailableAddons())
                .orElse(Collections.emptySet())
                .stream()
                .map(Addon::getId).collect(Collectors.toSet());

        List<OrderItemAddon> addonsToSave = new ArrayList<>();
        for (AddonDto addonDto : validAddonDtos) {
            Addon addon = addonsMap.get(addonDto.getAddonId());
            if (addon != null && addon.isActive() && allowedAddonIds.contains(addon.getId())) {
                OrderItemAddon oia = new OrderItemAddon();
                oia.setOrderItem(orderItem);
                oia.setAddon(addon);
                oia.setAddonName(addon.getName());
                oia.setQuantity(addonDto.getQuantity());

                BigDecimal addonUnitPrice = EURO_CURRENCY.equals(currency) ? addon.getPriceEUR() : addon.getPriceCZK();
                if (addonUnitPrice == null || addonUnitPrice.compareTo(BigDecimal.ZERO) < 0) {
                    log.error("!!! Invalid price for addon ID {} ('{}') in currency {}. Price: {}", addon.getId(), addon.getName(), currency, addonUnitPrice);
                    throw new IllegalStateException("Invalid price configuration for addon " + addon.getName() + " in " + currency);
                }

                oia.setAddonPriceWithoutTax(addonUnitPrice.setScale(PRICE_SCALE, ROUNDING_MODE));
                BigDecimal addonLineTotal = addonUnitPrice.multiply(BigDecimal.valueOf(addonDto.getQuantity())).setScale(PRICE_SCALE, ROUNDING_MODE);
                oia.setTotalPriceWithoutTax(addonLineTotal);

                addonsToSave.add(oia);
                totalAddonPrice = totalAddonPrice.add(addonLineTotal);

            } else {
                log.warn("Requested addon ID {} is invalid, inactive, or not allowed for product {}. Skipping.", addonDto.getAddonId(), orderItem.getProduct().getId());
            }
        }
        orderItem.setSelectedAddons(addonsToSave);
        log.debug("Total addons price calculated: {}", totalAddonPrice);
        return totalAddonPrice.setScale(PRICE_SCALE, ROUNDING_MODE);
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

    private void copyAddressesToOrder(Order order, Customer customer, CreateOrderRequest request) {
        // Copying invoice address (including Tax IDs)
        order.setInvoiceCompanyName(customer.getInvoiceCompanyName());
        order.setInvoiceStreet(customer.getInvoiceStreet());
        order.setInvoiceCity(customer.getInvoiceCity());
        order.setInvoiceZipCode(customer.getInvoiceZipCode());
        order.setInvoiceCountry(customer.getInvoiceCountry());
        order.setInvoiceTaxId(customer.getInvoiceTaxId());
        order.setInvoiceVatId(customer.getInvoiceVatId());

        // Setting invoice recipient name based on company/contact
        if (StringUtils.hasText(customer.getInvoiceCompanyName())) {
            order.setInvoiceFirstName(null); // Or customer contact name if needed by SF
            order.setInvoiceLastName(null);
        } else {
            order.setInvoiceFirstName(customer.getFirstName());
            order.setInvoiceLastName(customer.getLastName());
        }

        // Copying delivery address (uses current state from Customer entity)
        if (customer.isUseInvoiceAddressAsDelivery()) {
            log.debug("Copying invoice address to delivery address for order.");
            order.setDeliveryFirstName(order.getInvoiceFirstName());
            order.setDeliveryLastName(order.getInvoiceLastName());
            order.setDeliveryCompanyName(order.getInvoiceCompanyName());
            order.setDeliveryStreet(customer.getInvoiceStreet());
            order.setDeliveryCity(customer.getInvoiceCity());
            order.setDeliveryZipCode(customer.getInvoiceZipCode());
            order.setDeliveryCountry(customer.getInvoiceCountry());
            order.setDeliveryPhone(StringUtils.hasText(customer.getDeliveryPhone()) ? customer.getDeliveryPhone() : customer.getPhone());
        } else {
            log.debug("Copying specific delivery address to order.");
            order.setDeliveryFirstName(customer.getDeliveryFirstName());
            order.setDeliveryLastName(customer.getDeliveryLastName());
            order.setDeliveryCompanyName(customer.getDeliveryCompanyName());
            order.setDeliveryStreet(customer.getDeliveryStreet());
            order.setDeliveryCity(customer.getDeliveryCity());
            order.setDeliveryZipCode(customer.getDeliveryZipCode());
            order.setDeliveryCountry(customer.getDeliveryCountry());
            order.setDeliveryPhone(StringUtils.hasText(customer.getDeliveryPhone()) ? customer.getDeliveryPhone() : customer.getPhone());
        }
        // Fallback for delivery phone
        if (!StringUtils.hasText(order.getDeliveryPhone()) && StringUtils.hasText(customer.getPhone())) {
            order.setDeliveryPhone(customer.getPhone());
        }

        // Ensure copied delivery address is complete
        boolean deliveryAddrOk = StringUtils.hasText(order.getDeliveryStreet()) &&
                StringUtils.hasText(order.getDeliveryCity()) &&
                StringUtils.hasText(order.getDeliveryZipCode()) &&
                StringUtils.hasText(order.getDeliveryCountry()) &&
                (StringUtils.hasText(order.getDeliveryCompanyName()) ||
                        (StringUtils.hasText(order.getDeliveryFirstName()) && StringUtils.hasText(order.getDeliveryLastName())));
        if (!deliveryAddrOk) {
            String orderCodeLog = (order != null && order.getOrderCode() != null) ? order.getOrderCode() : "(new)";
            log.error("!!! Copied delivery address for order {} is incomplete after copying! Check Customer ID {} data.", orderCodeLog, customer.getId());
            throw new IllegalStateException("Incomplete delivery address copied to order. Check customer profile.");
        }
    }


    // --- Metody pro čtení a aktualizaci stavů (zůstávají stejné) ---

    @Transactional(readOnly = true)
    public Optional<Order> findOrderById(Long id) {
        log.debug("Finding order by ID: {}", id);
        return orderRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Order> findOrderByCode(String orderCode) {
        if (!StringUtils.hasText(orderCode)) return Optional.empty();
        log.debug("Finding order by code: {}", orderCode.trim());
        return orderRepository.findByOrderCode(orderCode.trim());
    }

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
                    .subtract(Optional.ofNullable(order.getDepositAmount()).orElse(BigDecimal.ZERO));
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
     * @param order The order object (used to set the discount amount).
     * @param couponCode The code entered by the user.
     * @param customer The customer placing the order.
     * @return The validated Coupon object if it's valid and applied, otherwise null.
     */
    private Coupon validateAndApplyCoupon(Order order, String couponCode, Customer customer) {
        // Reset discount and coupon on order before validation
        order.setCouponDiscountAmount(BigDecimal.ZERO);
        order.setAppliedCoupon(null);

        if (!StringUtils.hasText(couponCode) || order == null || customer == null || order.getSubTotalWithoutTax() == null) {
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
} // Konec třídy OrderService