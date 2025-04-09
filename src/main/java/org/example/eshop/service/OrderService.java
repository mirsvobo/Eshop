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
    static final String PAYMENT_STATUS_PAID = "PAID";
    private static final String EURO_CURRENCY = "EUR";
    private static final String DEFAULT_CURRENCY = "CZK";

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
    @Autowired
    @Qualifier("superFakturaInvoiceService")
    @Lazy
    private SuperFakturaInvoiceService invoiceService;
    @Autowired
    @Qualifier("paymentService")
    private PaymentService paymentService;

    @Autowired private OrderCodeGeneratorService orderCodeGeneratorService; // <-- PŘIDAT

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        log.info("Attempting to create order for customer ID: {} with explicit currency: {}", request.getCustomerId(), request.getCurrency());
        String orderCurrency = request.getCurrency();
        Order order = null;
        Customer customer = null;
        Coupon appliedCoupon = null;

        try {
            // 1. Validace & Načtení Hlavních Entit
            log.debug("[Order Creation - Step 1] Validating request and loading entities for currency: {}", orderCurrency);
            validateOrderRequest(request);
            customer = customerRepository.findById(request.getCustomerId()).orElseThrow(() -> new EntityNotFoundException("Customer not found with id: " + request.getCustomerId()));
            if (request.isUseCustomerAddresses() && !hasSufficientAddress(customer)) {
                throw new IllegalArgumentException("Customer (ID: " + customer.getId() + ") missing required address info.");
            }
            OrderState initialState = orderStateRepository.findByCodeIgnoreCase(INITIAL_ORDER_STATE_CODE).orElseThrow(() -> new IllegalStateException("Initial OrderState missing (code: " + INITIAL_ORDER_STATE_CODE + ")."));

            // 2. Inicializace Objektu Order
            log.debug("[Order Creation - Step 2] Initializing Order object for currency: {}", orderCurrency);
            order = new Order();
            order.setCustomer(customer);
            order.setStateOfOrder(initialState);
            order.setPaymentMethod(request.getPaymentMethod());
            order.setOrderItems(new ArrayList<>());
            order.setNote(request.getCustomerNote());
            copyAddressesToOrder(order, customer, request);
            order.setCurrency(orderCurrency);
            log.info("Order processing with currency: {}", orderCurrency);
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

            // 3. Zpracování Položek Objednávky
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
                    OrderItem orderItem = processCartItem(itemDto, order, orderCurrency);
                    order.getOrderItems().add(orderItem);
                    BigDecimal itemSubtotal = Optional.ofNullable(orderItem.getTotalPriceWithoutTax()).orElse(BigDecimal.ZERO);
                    BigDecimal itemTax = Optional.ofNullable(orderItem.getTotalTaxAmount()).orElse(BigDecimal.ZERO);
                    runningSubTotalWithoutTax = Optional.ofNullable(runningSubTotalWithoutTax).orElse(BigDecimal.ZERO).add(itemSubtotal);
                    runningTotalTaxFromItems = Optional.ofNullable(runningTotalTaxFromItems).orElse(BigDecimal.ZERO).add(itemTax);
                    if (orderItem.isCustomConfigured()) containsCustomProduct = true;
                    log.debug("Processed item successfully. Subtotal: {}, Tax: {}", runningSubTotalWithoutTax, runningTotalTaxFromItems);
                } catch (Exception e) {
                    log.error("!!! CRITICAL ERROR processing item (Product ID: {}): {}", itemDto.getProductId(), e.getMessage(), e);
                    throw new RuntimeException("Failed to process order item (Product ID: " + itemDto.getProductId() + "): " + e.getMessage(), e);
                }
            }
            if (order.getOrderItems().isEmpty())
                throw new IllegalArgumentException("Order must contain at least one valid item.");
            order.setSubTotalWithoutTax(runningSubTotalWithoutTax.setScale(PRICE_SCALE, ROUNDING_MODE));
            order.setTotalItemsTax(runningTotalTaxFromItems.setScale(PRICE_SCALE, ROUNDING_MODE));
            log.debug("Finished items. Subtotal: {}, Item Tax: {}", order.getSubTotalWithoutTax(), order.getTotalItemsTax());

            // 4. Aplikace Kupónu
            BigDecimal couponDiscount = BigDecimal.ZERO;
            appliedCoupon = null;
            if (StringUtils.hasText(request.getCouponCode())) {
                log.debug("[Order Creation - Step 4] Applying coupon '{}' currency: {}", request.getCouponCode(), orderCurrency);
                try {
                    appliedCoupon = applyCouponToOrder(order, request.getCouponCode(), order.getSubTotalWithoutTax(), customer, orderCurrency);
                    couponDiscount = Optional.ofNullable(order.getCouponDiscountAmount()).orElse(BigDecimal.ZERO);
                    log.debug("Coupon applied. Discount: {}", couponDiscount);
                } catch (Exception e) {
                    log.error("Exception during coupon application (Code: {}): {}. Ignoring.", request.getCouponCode(), e.getMessage(), e);
                    appliedCoupon = null;
                    couponDiscount = BigDecimal.ZERO;
                    order.setCouponDiscountAmount(couponDiscount);
                }
            }
            order.setAppliedCouponCode(StringUtils.hasText(request.getCouponCode()) ? request.getCouponCode() : null);
            order.setCouponDiscountAmount(couponDiscount.setScale(PRICE_SCALE, ROUNDING_MODE));
            order.setAppliedCoupon(appliedCoupon);

            // 5. Výpočet Ceny Dopravy
            BigDecimal shippingCostNoTax = BigDecimal.ZERO;
            BigDecimal shippingTaxRate = BigDecimal.ZERO;
            BigDecimal shippingTax = BigDecimal.ZERO;
            if (appliedCoupon == null || !appliedCoupon.isFreeShipping()) {
                log.debug("[Order Creation - Step 5] Calculating shipping cost currency: {}", orderCurrency);
                try {
                    shippingCostNoTax = Optional.ofNullable(shippingService.calculateShippingCost(order, orderCurrency)).orElse(BigDecimal.ZERO);
                    shippingTaxRate = Optional.ofNullable(shippingService.getShippingTaxRate()).orElse(BigDecimal.ZERO);
                    if (shippingCostNoTax.compareTo(BigDecimal.ZERO) > 0) {
                        if (shippingTaxRate.compareTo(BigDecimal.ZERO) <= 0)
                            throw new IllegalStateException("Shipping tax rate missing or zero.");
                        shippingTax = shippingCostNoTax.multiply(shippingTaxRate).setScale(PRICE_SCALE, ROUNDING_MODE);
                    }
                    log.debug("Shipping calculated. CostNoTax: {}, Tax: {}", shippingCostNoTax, shippingTax);
                } catch (Exception e) {
                    log.error("!!! CRITICAL ERROR shipping calculation: {}", e.getMessage(), e);
                    throw new RuntimeException("Failed to calculate shipping cost: " + e.getMessage(), e);
                }
            } else {
                log.info("Free shipping applied due to coupon '{}'", appliedCoupon.getCode());
            }
            order.setShippingCostWithoutTax(shippingCostNoTax.setScale(PRICE_SCALE, ROUNDING_MODE));
            order.setShippingTaxRate(shippingTaxRate.setScale(CALCULATION_SCALE, ROUNDING_MODE));
            order.setShippingTax(shippingTax.setScale(PRICE_SCALE, ROUNDING_MODE));

            // 6. Výpočet Finálních Součtů
            log.debug("[Order Creation - Step 6] Calculating final totals currency: {}", orderCurrency);
            try {
                BigDecimal subTotalAfterDiscount = Optional.ofNullable(order.getSubTotalWithoutTax()).orElse(BigDecimal.ZERO).subtract(Optional.ofNullable(order.getCouponDiscountAmount()).orElse(BigDecimal.ZERO));
                BigDecimal finalTotalWithoutTax = subTotalAfterDiscount.add(Optional.ofNullable(order.getShippingCostWithoutTax()).orElse(BigDecimal.ZERO));
                BigDecimal finalTotalTax = Optional.ofNullable(order.getTotalItemsTax()).orElse(BigDecimal.ZERO).add(Optional.ofNullable(order.getShippingTax()).orElse(BigDecimal.ZERO));
                BigDecimal finalTotalWithTax = finalTotalWithoutTax.add(finalTotalTax);
                order.setTotalPriceWithoutTax(finalTotalWithoutTax.setScale(PRICE_SCALE, ROUNDING_MODE));
                order.setTotalTax(finalTotalTax.setScale(PRICE_SCALE, ROUNDING_MODE));
                order.setTotalPrice(finalTotalWithTax.setScale(PRICE_SCALE, ROUNDING_MODE));
                log.debug("Final totals calculated. TotalNoTax: {}, TotalTax: {}, TotalWithTax: {}", order.getTotalPriceWithoutTax(), order.getTotalTax(), order.getTotalPrice());
            } catch (Exception e) {
                log.error("!!! CRITICAL ERROR calculating final totals: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to calculate final totals: " + e.getMessage(), e);
            }

            // 7. Zpracování Stavu Platby a Zálohy
            log.debug("[Order Creation - Step 7] Determining payment status/deposit currency: {}", orderCurrency);
            try {
                order.setPaymentStatus(paymentService.determineInitialPaymentStatus(order));
                if (containsCustomProduct) {
                    BigDecimal deposit = paymentService.calculateDeposit(order.getTotalPrice());
                    order.setDepositAmount(Optional.ofNullable(deposit).orElse(BigDecimal.ZERO).setScale(PRICE_SCALE, ROUNDING_MODE));
                    if (!PAYMENT_STATUS_AWAITING_DEPOSIT.equals(order.getPaymentStatus())) {
                        log.warn("Order has custom product, overriding status to '{}'.", PAYMENT_STATUS_AWAITING_DEPOSIT);
                        order.setPaymentStatus(PAYMENT_STATUS_AWAITING_DEPOSIT);
                    }
                    log.info("Deposit amount {} {} calculated.", order.getDepositAmount(), order.getCurrency());
                } else {
                    order.setDepositAmount(null);
                }
                log.debug("Payment status: {}, Deposit: {}", order.getPaymentStatus(), order.getDepositAmount());
            } catch (Exception e) {
                log.error("!!! CRITICAL ERROR determining payment status/deposit: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to determine payment status/deposit: " + e.getMessage(), e);
            }

            order.setOrderCode(orderCodeGeneratorService.getNextOrderCode());
            log.debug("Generated Order Code: {}", order.getOrderCode());

            // 8. Uložení Objednávky
            log.debug("[Order Creation - Step 8] Saving order...");
            Order savedOrder;
            try {
                savedOrder = orderRepository.save(order);
                log.info("Order {} created successfully. Total: {} {}", savedOrder.getOrderCode(), savedOrder.getTotalPrice(), savedOrder.getCurrency());
            } catch (Exception e) {
                log.error("!!! CRITICAL ERROR during order save: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to save order: " + e.getMessage(), e);
            }

            // --- Nekritické kroky ---
            if (appliedCoupon != null) {
                log.debug("[Order Creation - Step 9] Marking coupon used...");
                try {
                    couponService.markCouponAsUsed(appliedCoupon);
                } catch (Exception e) {
                    log.error("Non-critical error marking coupon used (ID: {}): {}. Cont.", appliedCoupon.getId(), e.getMessage(), e);
                }
            }
            log.debug("[Order Creation - Step 10] Sending confirmation email...");
            try {
                emailService.sendOrderConfirmationEmail(savedOrder);
            } catch (Exception e) {
                log.error("Non-critical error sending confirmation email for order {}: {}. Cont.", savedOrder.getOrderCode(), e.getMessage(), e);
            }
            if (savedOrder.getDepositAmount() != null && savedOrder.getDepositAmount().compareTo(BigDecimal.ZERO) > 0) {
                log.debug("[Order Creation - Step 11] Triggering proforma invoice gen. Currency: {}", savedOrder.getCurrency());
                try {
                    invoiceService.generateProformaInvoice(savedOrder);
                } catch (Exception e) {
                    log.error("Non-critical error generating proforma invoice for order {}: {}. Cont.", savedOrder.getOrderCode(), e.getMessage(), e);
                }
            }

            return savedOrder;

        } catch (Exception mainException) {
            String customerId = (request != null && request.getCustomerId() != null) ? request.getCustomerId().toString() : "Unknown";
            // --- OPRAVENÝ LOGOVACÍ PŘÍKAZ ---
            // Logování zprávy a stack trace odděleně
            log.error("!!! ORDER CREATION FAILED for customer ID {} in step: {}. Error message: {} !!!",
                    customerId,
                    determineStepFromException(mainException),
                    mainException.getMessage());
            log.error("Order Creation Exception Stack Trace:", mainException); // Oddělený log pro stack trace
            // --- KONEC OPRAVY ---
            throw mainException; // Znovu vyhodit pro rollback
        }
    }

    // Pomocná metoda pro odhad kroku (volitelné, jen pro logování)
    private String determineStepFromException(Exception e) {
        if (e.getMessage().contains("process order item")) return "3 (Item Processing)";
        if (e.getMessage().contains("calculate shipping cost")) return "5 (Shipping Calculation)";
        if (e.getMessage().contains("save order")) return "8 (Order Save)";
        return "Unknown";
    }

    /**
     * Zpracuje položku košíku a vytvoří z ní OrderItem.
     * Verze s ještě robustnějšími null checky.
     */
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


    // --- PŮVODNÍ POMOCNÉ METODY (z nahrání uživatelem) ---

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
            orderItem.setVariantInfo(String.format("Design: %s, Lazura: %s, Střecha: %s",
                    Optional.ofNullable(orderItem.getModel()).orElse("-"),
                    Optional.ofNullable(orderItem.getGlaze()).orElse("-"),
                    Optional.ofNullable(orderItem.getRoofColor()).orElse("-")
            ));
            orderItem.setDesign(null);
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
            // Voláme metodu pro dynamický výpočet
            BigDecimal dynamicPrice = productService.calculateDynamicProductPrice(
                    product,
                    itemDto.getCustomDimensions(),
                    itemDto.getCustomDesign(),
                    itemDto.isCustomHasDivider(),
                    itemDto.isCustomHasGutter(),
                    itemDto.isCustomHasGardenShed(),
                    currency
            );
            // Zajistíme, že nevrátíme null
            return Optional.ofNullable(dynamicPrice).orElse(BigDecimal.ZERO);
        } else {
            // Pro standardní produkt bereme cenu z entity
            BigDecimal price = EURO_CURRENCY.equals(currency) ? product.getBasePriceEUR() : product.getBasePriceCZK();
            if (price == null) {
                log.error("!!! Base price missing for standard product ID {} in currency {}", product.getId(), currency);
                throw new IllegalStateException("Base price missing for product " + product.getId() + " in " + currency);
            }
            // Zajistíme, že cena není záporná
            return price.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : price;
        }
    }

    private BigDecimal processAndCalculateAddons(OrderItem orderItem, CartItemDto itemDto, String currency) {
        BigDecimal totalAddonPrice = BigDecimal.ZERO;
        if (!itemDto.isCustom() || CollectionUtils.isEmpty(itemDto.getSelectedAddons())) {
            orderItem.setSelectedAddons(Collections.emptyList()); // Ensure list is empty if no addons
            return totalAddonPrice; // Vracíme ZERO, ne null
        }

        List<AddonDto> validAddonDtos = itemDto.getSelectedAddons().stream()
                .filter(dto -> dto != null && dto.getAddonId() != null && dto.getQuantity() > 0)
                .collect(Collectors.toList());

        if (validAddonDtos.isEmpty()) {
            orderItem.setSelectedAddons(Collections.emptyList());
            return totalAddonPrice; // Vracíme ZERO
        }

        Set<Long> requestedAddonIds = validAddonDtos.stream().map(AddonDto::getAddonId).collect(Collectors.toSet());
        // Načteme doplňky z DB
        Map<Long, Addon> addonsMap = addonsRepository.findAllById(requestedAddonIds).stream()
                .collect(Collectors.toMap(Addon::getId, a -> a));
        // Získáme povolené doplňky pro daný produkt
        Set<Long> allowedAddonIds = orderItem.getProduct().getAvailableAddons().stream()
                .map(Addon::getId).collect(Collectors.toSet());

        List<OrderItemAddon> addonsToSave = new ArrayList<>();
        for (AddonDto addonDto : validAddonDtos) {
            Addon addon = addonsMap.get(addonDto.getAddonId());
            // Ověříme, zda doplněk existuje, je aktivní a je povolený pro produkt
            if (addon != null && addon.isActive() && allowedAddonIds.contains(addon.getId())) {
                OrderItemAddon oia = new OrderItemAddon();
                oia.setOrderItem(orderItem);
                oia.setAddon(addon); // Odkaz na původní addon
                oia.setAddonName(addon.getName());
                oia.setQuantity(addonDto.getQuantity());

                // Získáme cenu doplňku ve správné měně
                BigDecimal addonUnitPrice = EURO_CURRENCY.equals(currency) ? addon.getPriceEUR() : addon.getPriceCZK();
                if (addonUnitPrice == null || addonUnitPrice.compareTo(BigDecimal.ZERO) < 0) {
                    log.error("!!! Invalid price found for addon ID {} ('{}') in currency {}. Price: {}", addon.getId(), addon.getName(), currency, addonUnitPrice);
                    // Můžeme hodit výjimku nebo pokračovat s nulovou cenou? Raději výjimku.
                    throw new IllegalStateException("Invalid price configuration for addon " + addon.getName() + " in " + currency);
                }

                oia.setAddonPriceWithoutTax(addonUnitPrice.setScale(PRICE_SCALE, ROUNDING_MODE));
                // Vypočítáme celkovou cenu za tento doplněk (cena * množství)
                BigDecimal addonLineTotal = addonUnitPrice.multiply(BigDecimal.valueOf(addonDto.getQuantity())).setScale(PRICE_SCALE, ROUNDING_MODE);
                oia.setTotalPriceWithoutTax(addonLineTotal);

                addonsToSave.add(oia);
                // Přičteme k celkové ceně doplňků pro tuto položku
                totalAddonPrice = totalAddonPrice.add(addonLineTotal); // Bezpečné sčítání

            } else {
                log.warn("Requested addon ID {} is invalid, inactive, or not allowed for product {}. Skipping.", addonDto.getAddonId(), orderItem.getProduct().getId());
            }
        }
        orderItem.setSelectedAddons(addonsToSave); // Uložíme zpracované doplňky
        log.debug("Total addons price calculated: {}", totalAddonPrice);
        return totalAddonPrice.setScale(PRICE_SCALE, ROUNDING_MODE); // Vracíme sečtenou cenu
    }

    private Coupon applyCouponToOrder(Order order, String couponCode, BigDecimal subTotalWithoutTax, Customer customer, String currency) {
        if (!StringUtils.hasText(couponCode) || order == null || subTotalWithoutTax == null || customer == null) {
            if (order != null) order.setCouponDiscountAmount(BigDecimal.ZERO);
            return null;
        }
        Optional<Coupon> couponOpt = couponService.findByCode(couponCode);
        if (couponOpt.isEmpty()) {
            log.warn("Coupon code '{}' not found.", couponCode);
            order.setCouponDiscountAmount(BigDecimal.ZERO);
            return null;
        }
        Coupon coupon = couponOpt.get();
        boolean isGuest = customer.isGuest();
        if (!couponService.isCouponGenerallyValid(coupon) || !couponService.checkMinimumOrderValue(coupon, subTotalWithoutTax, currency) || (!isGuest && !couponService.checkCustomerUsageLimit(customer, coupon))) {
            log.warn("Coupon {} is invalid for cust={}, isGuest={}, subtotal={} {}", couponCode, customer.getId(), isGuest, subTotalWithoutTax, currency);
            order.setCouponDiscountAmount(BigDecimal.ZERO);
            return null;
        }
        if (isGuest && coupon.getUsageLimitPerCustomer() != null && coupon.getUsageLimitPerCustomer() > 0) {
            log.warn("Applying customer-limited coupon {} to guest user {}. Limit check needed if guest converts.", couponCode, customer.getId());
        }
        BigDecimal discountAmount = Optional.ofNullable(couponService.calculateDiscountAmount(subTotalWithoutTax, coupon, currency)).orElse(BigDecimal.ZERO);
        order.setCouponDiscountAmount(discountAmount);
        log.info("Applied coupon {} for cust={}, isGuest={}, discount={} {}", couponCode, customer.getId(), isGuest, order.getCouponDiscountAmount(), currency);
        return coupon;
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
        // Validace cen dopravy už zde není potřeba, pokud je Controller ověřuje
        // if (request.getShippingCostNoTax() == null) throw new IllegalArgumentException("ShippingCostNoTax missing in request.");
        // if (request.getShippingTax() == null) throw new IllegalArgumentException("ShippingTax missing in request.");
    }

    private boolean hasSufficientAddress(Customer customer) {
        boolean hasInv = StringUtils.hasText(customer.getInvoiceStreet()) && StringUtils.hasText(customer.getInvoiceCity()) && StringUtils.hasText(customer.getInvoiceZipCode()) && StringUtils.hasText(customer.getInvoiceCountry()) && (StringUtils.hasText(customer.getInvoiceCompanyName()) || (StringUtils.hasText(customer.getInvoiceFirstName()) && StringUtils.hasText(customer.getInvoiceLastName())));
        if (!hasInv) return false;
        if (!customer.isUseInvoiceAddressAsDelivery()) {
            boolean hasDel = StringUtils.hasText(customer.getDeliveryStreet()) && StringUtils.hasText(customer.getDeliveryCity()) && StringUtils.hasText(customer.getDeliveryZipCode()) && StringUtils.hasText(customer.getDeliveryCountry()) && (StringUtils.hasText(customer.getDeliveryCompanyName()) || (StringUtils.hasText(customer.getDeliveryFirstName()) && StringUtils.hasText(customer.getDeliveryLastName())));
            return hasDel;
        }
        return true;
    }

    private void copyAddressesToOrder(Order order, Customer customer, CreateOrderRequest request) {
        order.setInvoiceFirstName(customer.getInvoiceFirstName());
        order.setInvoiceLastName(customer.getInvoiceLastName());
        order.setInvoiceCompanyName(customer.getInvoiceCompanyName());
        order.setInvoiceStreet(customer.getInvoiceStreet());
        order.setInvoiceCity(customer.getInvoiceCity());
        order.setInvoiceZipCode(customer.getInvoiceZipCode());
        order.setInvoiceCountry(customer.getInvoiceCountry());
        order.setInvoiceTaxId(customer.getInvoiceTaxId());
        order.setInvoiceVatId(customer.getInvoiceVatId());
        if (customer.isUseInvoiceAddressAsDelivery()) {
            order.setDeliveryFirstName(order.getInvoiceFirstName());
            order.setDeliveryLastName(order.getInvoiceLastName());
            order.setDeliveryCompanyName(order.getInvoiceCompanyName());
            order.setDeliveryStreet(order.getInvoiceStreet());
            order.setDeliveryCity(order.getInvoiceCity());
            order.setDeliveryZipCode(order.getInvoiceZipCode());
            order.setDeliveryCountry(order.getInvoiceCountry());
            order.setDeliveryPhone(customer.getPhone());
        } else {
            order.setDeliveryFirstName(customer.getDeliveryFirstName());
            order.setDeliveryLastName(customer.getDeliveryLastName());
            order.setDeliveryCompanyName(customer.getDeliveryCompanyName());
            order.setDeliveryStreet(customer.getDeliveryStreet());
            order.setDeliveryCity(customer.getDeliveryCity());
            order.setDeliveryZipCode(customer.getDeliveryZipCode());
            order.setDeliveryCountry(customer.getDeliveryCountry());
            order.setDeliveryPhone(StringUtils.hasText(customer.getDeliveryPhone()) ? customer.getDeliveryPhone() : customer.getPhone());
        }
        if (!StringUtils.hasText(order.getDeliveryPhone())) {
            order.setDeliveryPhone(customer.getPhone());
        }
        if (!StringUtils.hasText(order.getDeliveryStreet()) || !StringUtils.hasText(order.getDeliveryCity()) || !StringUtils.hasText(order.getDeliveryZipCode()) || !StringUtils.hasText(order.getDeliveryCountry()) || (!StringUtils.hasText(order.getDeliveryCompanyName()) && (!StringUtils.hasText(order.getDeliveryFirstName()) || !StringUtils.hasText(order.getDeliveryLastName())))) {
            log.error("!!! Inconsistency: Copied delivery address for order is incomplete! Order Code: {}", order.getOrderCode());
            throw new IllegalStateException("Incomplete delivery address copied to order.");
        }
    }

    // --- Ostatní veřejné metody (Beze změny) ---
    @Transactional(readOnly = true)
    public Optional<Order> findOrderById(Long id) {
        return orderRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Order> findOrderByCode(String orderCode) {
        if (!StringUtils.hasText(orderCode)) return Optional.empty();
        return orderRepository.findByOrderCode(orderCode.trim());
    }

    @Transactional(readOnly = true)
    public List<Order> findAllOrdersByCustomerId(Long customerId) {
        if (customerId == null) return Collections.emptyList();
        return orderRepository.findByCustomerIdOrderByOrderDateDesc(customerId);
    }

    @Transactional
    public Order updateOrderState(Long orderId, Long newOrderStateId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
        OrderState newOrderState = orderStateRepository.findById(newOrderStateId).orElseThrow(() -> new EntityNotFoundException("OrderState not found: " + newOrderStateId));
        OrderState oldState = order.getStateOfOrder();
        if (oldState != null && oldState.getId().equals(newOrderState.getId())) return order;
        log.info("Updating order {} state from '{}' to '{}'", order.getOrderCode(), oldState != null ? oldState.getName() : "null", newOrderState.getName());
        order.setStateOfOrder(newOrderState);
        updateOrderTimestamps(order, newOrderState);
        Order savedOrder = orderRepository.save(order);
        try {
            emailService.sendOrderStatusUpdateEmail(savedOrder, newOrderState);
        } catch (Exception e) {
            log.error("Failed status email for order {}: {}", savedOrder.getOrderCode(), e.getMessage());
        }
        return savedOrder;
    }

    private void updateOrderTimestamps(Order order, OrderState newState) {
        LocalDateTime now = LocalDateTime.now();
        String code = newState.getCode().toUpperCase();
        switch (code) {
            case "SHIPPED":
                if (order.getShippedDate() == null) order.setShippedDate(now);
                break;
            case "DELIVERED":
                if (order.getDeliveredDate() == null) order.setDeliveredDate(now);
                break;
            case "CANCELLED":
                if (order.getCancelledDate() == null) order.setCancelledDate(now);
                break;
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

        // Sestavení finální specifikace kombinací jednotlivých filtrů
        Specification<Order> spec = Specification.where(null); // Začínáme s prázdnou specifikací

        if (customerEmail.isPresent() && StringUtils.hasText(customerEmail.get())) {
            spec = spec.and(OrderSpecifications.customerEmailContains(customerEmail.get()));
            log.trace("Adding spec: customerEmailContains");
        }
        if (stateId.isPresent()) {
            spec = spec.and(OrderSpecifications.hasStateId(stateId.get()));
            log.trace("Adding spec: hasStateId");
        }
        if (paymentStatus.isPresent() && StringUtils.hasText(paymentStatus.get())) {
            spec = spec.and(OrderSpecifications.hasPaymentStatus(paymentStatus.get()));
            log.trace("Adding spec: hasPaymentStatus");
        }
        if (dateTimeFrom.isPresent()) {
            spec = spec.and(OrderSpecifications.orderDateFrom(dateTimeFrom.get()));
            log.trace("Adding spec: orderDateFrom");
        }
        if (dateTimeTo.isPresent()) {
            spec = spec.and(OrderSpecifications.orderDateTo(dateTimeTo.get()));
            log.trace("Adding spec: orderDateTo");
        }

        // Zavolání metody findAll z JpaSpecificationExecutor
        Page<Order> result = orderRepository.findAll(spec, pageable);
        log.info("Found {} orders matching criteria.", result.getTotalElements());
        return result;
    }

    @Transactional
    public Order markDepositAsPaid(Long orderId, LocalDate paymentDate) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
        if (order.getDepositAmount() == null || order.getDepositAmount().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalStateException("No deposit needed for order " + order.getOrderCode());
        if (order.getDepositPaidDate() != null || PAYMENT_STATUS_DEPOSIT_PAID.equals(order.getPaymentStatus()) || PAYMENT_STATUS_PAID.equals(order.getPaymentStatus()))
            throw new IllegalStateException("Deposit already marked as paid for order " + order.getOrderCode());
        if (paymentDate == null) throw new IllegalArgumentException("Payment date required.");
        order.setPaymentStatus(PAYMENT_STATUS_DEPOSIT_PAID);
        order.setDepositPaidDate(paymentDate.atStartOfDay());
        log.info("Marking deposit paid for order {} on {}", order.getOrderCode(), paymentDate);
        Order saved = orderRepository.save(order);
        if (order.getSfTaxDocumentId() == null) {
            log.info("Triggering tax doc gen for order {} currency {}", saved.getOrderCode(), saved.getCurrency());
            try {
                invoiceService.generateTaxDocumentForDeposit(saved);
            } catch (Exception e) {
                log.error("Non-critical error tax doc gen order {}: {}. Cont.", saved.getOrderCode(), e.getMessage(), e);
            }
        } else {
            log.warn("Tax doc already exists (SF ID: {}) for order {}. Skip.", order.getSfTaxDocumentId(), order.getOrderCode());
        }
        Long invoiceIdToMark = saved.getSfProformaInvoiceId();
        if (invoiceIdToMark != null) {
            log.info("Attempting mark Proforma SF ID {} paid in SF...", invoiceIdToMark);
            try {
                String sfPaymentType = mapPaymentMethodToSf(saved.getPaymentMethod());
                invoiceService.markInvoiceAsPaidInSF(invoiceIdToMark, saved.getDepositAmount(), paymentDate, sfPaymentType, saved.getOrderCode());
                log.info("Success mark Proforma SF ID {} paid.", invoiceIdToMark);
            } catch (Exception e) {
                log.error("Failed mark proforma SF ID {} paid: {}", invoiceIdToMark, e.getMessage());
            }
        } else {
            log.warn("Cannot mark deposit paid in SF order {}: Proforma SF ID missing.", saved.getOrderCode());
        }
        return saved;
    }

    @Transactional
    public Order markOrderAsFullyPaid(Long orderId, LocalDate paymentDate) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
        if (paymentDate == null) throw new IllegalArgumentException("Payment date required.");
        if (PAYMENT_STATUS_AWAITING_DEPOSIT.equals(order.getPaymentStatus()))
            throw new IllegalStateException("Cannot mark fully paid order " + order.getOrderCode() + " when deposit awaiting.");
        BigDecimal amountJustPaid;
        if (PAYMENT_STATUS_DEPOSIT_PAID.equals(order.getPaymentStatus()) && order.getDepositAmount() != null && order.getDepositAmount().compareTo(BigDecimal.ZERO) > 0 && order.getDepositPaidDate() != null) {
            amountJustPaid = Optional.ofNullable(order.getTotalPrice()).orElse(BigDecimal.ZERO).subtract(Optional.ofNullable(order.getDepositAmount()).orElse(BigDecimal.ZERO));
        } else {
            amountJustPaid = Optional.ofNullable(order.getTotalPrice()).orElse(BigDecimal.ZERO);
        }
        amountJustPaid = amountJustPaid.max(BigDecimal.ZERO);
        order.setPaymentStatus(PAYMENT_STATUS_PAID);
        order.setPaymentDate(paymentDate.atStartOfDay());
        if (order.getDepositAmount() != null && order.getDepositAmount().compareTo(BigDecimal.ZERO) > 0 && order.getDepositPaidDate() == null) {
            log.warn("Marking order {} fully paid also sets missing deposit date to {}", order.getOrderCode(), paymentDate);
            order.setDepositPaidDate(paymentDate.atStartOfDay());
        }
        log.info("Marking order {} fully paid on {}", order.getOrderCode(), paymentDate);
        Order saved = orderRepository.save(order);
        Long invoiceIdToMark = saved.getSfFinalInvoiceId();
        if (invoiceIdToMark != null) {
            log.info("Attempting mark Final SF ID {} paid in SF. Amount: {}", invoiceIdToMark, amountJustPaid);
            try {
                String sfPaymentType = mapPaymentMethodToSf(saved.getPaymentMethod());
                invoiceService.markInvoiceAsPaidInSF(invoiceIdToMark, amountJustPaid, paymentDate, sfPaymentType, saved.getOrderCode());
                log.info("Success mark Final SF ID {} paid.", invoiceIdToMark);
            } catch (Exception e) {
                log.error("Failed mark final SF ID {} paid: {}", invoiceIdToMark, e.getMessage());
            }
        } else {
            log.warn("Cannot mark payment in SF order {}: Final SF ID missing.", saved.getOrderCode());
        }
        return saved;
    }

    private String mapPaymentMethodToSf(String localPaymentMethod) {
        if (localPaymentMethod == null) return "transfer";
        return switch (localPaymentMethod.toUpperCase()) {
            case "CASH_ON_DELIVERY" -> "cash";
            case "BANK_TRANSFER" -> "transfer";
            default -> "transfer";
        };
    }
}