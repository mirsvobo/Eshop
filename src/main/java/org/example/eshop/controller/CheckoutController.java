package org.example.eshop.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator; // Import Validator
import org.example.eshop.config.PriceConstants;
import org.example.eshop.dto.*;
// Import validation groups from DTO
import org.example.eshop.dto.CheckoutFormDataDto.GuestValidation;
import org.example.eshop.dto.CheckoutFormDataDto.DeliveryAddressValidation;
import org.example.eshop.model.*;
import org.example.eshop.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.annotation.Validated; // Import @Validated
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/pokladna")
public class CheckoutController implements PriceConstants {

    private static final Logger log = LoggerFactory.getLogger(CheckoutController.class);

    // Helper interface for default validation group
    interface DefaultValidationGroup {}
    // Custom exception for validation flow control
    static class ValidationException extends Exception {
        public ValidationException(String message) { super(message); }
    }

    // Výjimka pro chybu výpočtu dopravy (zůstává)
    public static class ShippingCalculationException extends RuntimeException {
        public ShippingCalculationException(String message) { super(message); }
        public ShippingCalculationException(String message, Throwable cause) { super(message, cause); }
    }

    @Autowired private Cart sessionCart;
    @Autowired private CustomerService customerService;
    @Autowired private OrderService orderService;
    @Autowired private CouponService couponService;
    @Autowired @Qualifier("googleMapsShippingService") private ShippingService shippingService;
    @Autowired private CurrencyService currencyService;
    // Validator bude získán z CustomerService

    private static final Map<String, String> ALLOWED_PAYMENT_METHODS = Map.of(
            "BANK_TRANSFER", "Bankovní převod",
            "CASH_ON_DELIVERY", "Dobírka"
    );

    @GetMapping
    @Transactional(readOnly = true)
    public String showCheckoutPage(Model model, @AuthenticationPrincipal UserDetails userDetails, RedirectAttributes redirectAttributes) {
        String userIdentifier = (userDetails != null) ? userDetails.getUsername() : "GUEST";
        String currentCurrency = currencyService.getSelectedCurrency();
        log.info("Displaying checkout page for user: {} in currency: {}", userIdentifier, currentCurrency);

        if (!sessionCart.hasItems()) {
            log.warn("User {} attempted to access checkout with an empty cart.", userIdentifier);
            redirectAttributes.addFlashAttribute("cartError", "Váš košík je prázdný. Nelze pokračovat k pokladně.");
            return "redirect:/kosik";
        }

        Customer customer = null;
        CheckoutFormDataDto checkoutForm = (CheckoutFormDataDto) model.getAttribute("checkoutForm");

        // --- Získání nebo inicializace formuláře ---
        if (checkoutForm == null) {
            checkoutForm = new CheckoutFormDataDto();
            if (userDetails != null) {
                try {
                    customer = customerService.getCustomerByEmail(userDetails.getUsername())
                            .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userDetails.getUsername()));
                    checkoutForm.initializeFromCustomer(customer);
                    log.debug("Initialized checkout form DTO from logged-in customer {}.", customer.getId());
                } catch (IllegalStateException e) {
                    log.error("Authenticated user {} not found in customer database.", userDetails.getUsername());
                    redirectAttributes.addFlashAttribute("checkoutError", "Chyba při načítání údajů zákazníka.");
                    return "redirect:/prihlaseni";
                }
            } else {
                checkoutForm.setPaymentMethod("BANK_TRANSFER");
                checkoutForm.setAgreeTerms(false);
                checkoutForm.setInvoiceCountry("Česká republika");
                checkoutForm.setUseInvoiceAddressAsDelivery(true);
                log.debug("Initialized checkout form DTO with defaults for guest.");
            }
        } else {
            if (userDetails != null && customer == null) {
                customer = customerService.getCustomerByEmail(userDetails.getUsername()).orElse(null);
                if (customer == null) {
                    log.error("Authenticated user {} not found when preparing model after error.", userDetails.getUsername());
                    return "redirect:/prihlaseni";
                }
            }
        }
        // Přidání základních objektů do modelu VŽDY
        model.addAttribute("checkoutForm", checkoutForm);
        model.addAttribute("customer", customer); // Může být null pro hosta
        model.addAttribute("cart", sessionCart);
        model.addAttribute("allowedPaymentMethods", ALLOWED_PAYMENT_METHODS);
        model.addAttribute("currency", currentCurrency);
        model.addAttribute("currencySymbol", EURO_CURRENCY.equals(currentCurrency) ? "€" : "Kč");

        // --- Počáteční výpočet dopravy a přidání sazby DPH do modelu ---
        BigDecimal initialShippingCostNoTax = null;
        String initialShippingError = null;
        BigDecimal shippingTaxRate = new BigDecimal("0.21"); // Default/Fallback

        try {
            // Vždy se pokusíme načíst sazbu DPH z shippingService
            BigDecimal fetchedRate = shippingService.getShippingTaxRate();
            if (fetchedRate != null) {
                shippingTaxRate = fetchedRate;
            } else {
                log.error("Shipping tax rate from service is null! Using default: {}", shippingTaxRate);
            }

            // Přidání sazby DPH do modelu pro použití v JS
            model.addAttribute("shippingTaxRate", shippingTaxRate); // Přidáváme jako BigDecimal
            log.debug("Added shippingTaxRate {} to model.", shippingTaxRate);

            // Výpočet počáteční ceny dopravy (pouze pro přihlášené s adresou)
            if (customer != null && hasSufficientAddress(customer)) {
                Order tempOrder = createTemporaryOrderForShipping(customer, currentCurrency);
                if (isShippingAddressAvailable(tempOrder)) {
                    try {
                        initialShippingCostNoTax = shippingService.calculateShippingCost(tempOrder, currentCurrency);
                        if (initialShippingCostNoTax == null || initialShippingCostNoTax.compareTo(BigDecimal.ZERO) < 0) {
                            initialShippingError = "Nepodařilo se vypočítat dopravu.";
                            initialShippingCostNoTax = null;
                        } else {
                            initialShippingCostNoTax = initialShippingCostNoTax.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
                        }
                    } catch (Exception e) {
                        log.error("Error calculating initial shipping for customer {}: {}", customer.getId(), e.getMessage());
                        initialShippingError = "Chyba výpočtu dopravy.";
                        initialShippingCostNoTax = null;
                    }
                } else {
                    initialShippingError = "Doplňte dodací adresu pro výpočet dopravy.";
                }
            } else if (customer == null) {
                initialShippingError = "Cena dopravy bude vypočtena po zadání adresy.";
            } else {
                initialShippingError = "Doplňte adresu pro výpočet dopravy.";
            }
        } catch (Exception e) {
            log.error("Failed to get shipping tax rate or calculate initial shipping: {}", e.getMessage(), e);
            initialShippingError = (initialShippingError != null) ? initialShippingError : "Chyba konfigurace dopravy.";
            // Pokud selže načtení sazby, přidáme do modelu fallback hodnotu
            if (!model.containsAttribute("shippingTaxRate")) {
                model.addAttribute("shippingTaxRate", shippingTaxRate); // Přidání fallback sazby
            }
        }

        // Přidání výsledků výpočtu dopravy do modelu
        model.addAttribute("originalShippingCostNoTax", initialShippingCostNoTax);
        model.addAttribute("shippingError", initialShippingError);

        // Příprava souhrnu (již používá proměnné z modelu a předané argumenty)
        prepareCheckoutSummaryModel(model, customer, checkoutForm, currentCurrency, initialShippingCostNoTax, initialShippingError);

        log.info("Checkout page model prepared for {}. Currency: {}", userIdentifier, currentCurrency);
        return "pokladna";
    }

    @PostMapping("/odeslat")
    @Transactional
    public String processCheckout(
            // Use @Validated for group validation
            @Validated({DefaultValidationGroup.class, GuestValidation.class})
            @ModelAttribute("checkoutForm") CheckoutFormDataDto checkoutForm,
            BindingResult bindingResult,
            // Get shipping costs from request parameters (mapped from hidden fields)
            @RequestParam(required = false) BigDecimal shippingCostNoTax,
            @RequestParam(required = false) BigDecimal shippingTax,
            Model model,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        Principal principal = (userDetails != null) ? () -> userDetails.getUsername() : null;
        boolean isGuest = (principal == null);
        String userIdentifierForLog = isGuest ? (checkoutForm.getEmail() != null ? checkoutForm.getEmail() : "GUEST") : principal.getName();
        String orderCurrency = currencyService.getSelectedCurrency();
        log.info("Processing checkout submission for {}: {} in currency: {}", (isGuest ? "guest" : "user"), userIdentifierForLog, orderCurrency);

        Customer customer;
        Validator validator = customerService.getValidator(); // Get validator instance

        // --- Perform Conditional Validation ---
        if (!checkoutForm.isUseInvoiceAddressAsDelivery()) {
            if (validator != null) {
                log.debug("Validating delivery address group...");
                ValidationUtils.invokeValidator((org.springframework.validation.Validator) validator, checkoutForm, bindingResult, DeliveryAddressValidation.class);
            } else {
                log.warn("Validator not available, skipping delivery address group validation!");
            }
        }

        // 1. Check basic terms agreement first
        if (!checkoutForm.isAgreeTerms()) {
            bindingResult.rejectValue("agreeTerms", "TermsNotAgreed", "Musíte souhlasit s podmínkami.");
        }

        // 2. Handle Guest vs Logged-in User and Address Update/Validation
        try {
            if (isGuest) {
                log.debug("Processing as GUEST.");
                // Guest validation (basic + invoice + maybe delivery) handled by @Validated and above check
                if (bindingResult.hasErrors()) {
                    log.warn("Guest form validation failed: {}", bindingResult.getAllErrors());
                    throw new ValidationException("Guest form validation failed.");
                }
                customer = customerService.getOrCreateGuestFromCheckoutData(checkoutForm);
            } else {
                log.debug("Processing as LOGGED-IN user {}.", principal.getName());
                customer = customerService.getCustomerByEmail(principal.getName())
                        .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + principal.getName()));

                // Check if address needs update based on DTO data compared to Customer data
                if (!addressMatches(customer, checkoutForm)) {
                    log.info("Customer {} address differs or useInvoice flag changed, updating from DTO.", customer.getId());
                    // Address validation (basic + invoice + maybe delivery) handled by @Validated and above check
                    if (bindingResult.hasErrors()) {
                        log.warn("Logged-in user form validation failed during address update: {}", bindingResult.getAllErrors());
                        throw new ValidationException("Logged-in user form validation failed.");
                    }
                    customerService.updateCustomerFromDto(customer, checkoutForm);
                    customer = customerService.saveCustomer(customer);
                    log.info("Updated address data saved for customer {}.", customer.getId());
                } else {
                    log.debug("Customer {} address matches DTO, no update needed.", customer.getId());
                    // Still need to check for basic validation errors on potentially pre-filled form
                    if(bindingResult.hasErrors()){
                        log.warn("Logged-in user form validation failed (address matched): {}", bindingResult.getAllErrors());
                        throw new ValidationException("Logged-in user form validation failed.");
                    }
                }
            }

            // 3. Check cart emptiness
            if (!sessionCart.hasItems()) {
                log.warn("User/guest {} submitted checkout form with an empty cart.", userIdentifierForLog);
                redirectAttributes.addFlashAttribute("checkoutError", "Váš košík je prázdný. Objednávku nelze dokončit.");
                return "redirect:/kosik";
            }

            // 4. Validate Shipping Costs (received from hidden fields)
            BigDecimal subtotalForCoupon = sessionCart.calculateSubtotal(orderCurrency);
            Coupon validatedCoupon = validateAndGetCoupon(sessionCart, customer, subtotalForCoupon, orderCurrency);
            boolean freeShippingApplied = (validatedCoupon != null && validatedCoupon.isFreeShipping());

            if (!freeShippingApplied) {
                if (shippingCostNoTax == null || shippingTax == null || shippingCostNoTax.compareTo(BigDecimal.ZERO) < 0 || shippingTax.compareTo(BigDecimal.ZERO) < 0) {
                    log.error("Invalid or missing shipping costs submitted for order (ShippingRequired=true). CostNoTax: {}, Tax: {}", shippingCostNoTax, shippingTax);
                    bindingResult.reject("shipping.cost.missing", "Cena dopravy nebyla správně vypočtena nebo odeslána. Klikněte prosím na 'Spočítat dopravu'.");
                    throw new ValidationException("Missing or invalid shipping costs.");
                }
                log.debug("Received valid shipping costs from form: NoTax={}, Tax={}", shippingCostNoTax, shippingTax);
            } else {
                shippingCostNoTax = BigDecimal.ZERO;
                shippingTax = BigDecimal.ZERO;
                log.info("Overriding shipping cost to ZERO due to free shipping coupon {}.", validatedCoupon.getCode());
            }


            // 5. Create Order Request
            CreateOrderRequest orderRequest = new CreateOrderRequest();
            orderRequest.setCustomerId(customer.getId());
            orderRequest.setUseCustomerAddresses(true); // Always use customer addresses now
            orderRequest.setPaymentMethod(checkoutForm.getPaymentMethod());
            orderRequest.setCustomerNote(checkoutForm.getCustomerNote());
            orderRequest.setCouponCode(validatedCoupon != null ? validatedCoupon.getCode() : null);
            orderRequest.setCurrency(orderCurrency);
            // Use validated shipping costs from request params
            orderRequest.setShippingCostNoTax(Optional.ofNullable(shippingCostNoTax).orElse(BigDecimal.ZERO).setScale(PRICE_SCALE, RoundingMode.HALF_UP));
            orderRequest.setShippingTax(Optional.ofNullable(shippingTax).orElse(BigDecimal.ZERO).setScale(PRICE_SCALE, RoundingMode.HALF_UP));
            List<CartItemDto> orderItemsDto = sessionCart.getItemsList().stream().map(this::mapCartItemToDto).collect(Collectors.toList());
            orderRequest.setItems(orderItemsDto);

            log.info("Attempting to create order with request: {}", orderRequest);
            Order createdOrder = orderService.createOrder(orderRequest);
            log.info("Order {} successfully created with currency {} for {}: {}", createdOrder.getOrderCode(), orderCurrency, (isGuest ? "guest" : "user"), userIdentifierForLog);

            sessionCart.clearCart();
            log.info("Session cart cleared for {}", userIdentifierForLog);
            redirectAttributes.addFlashAttribute("orderSuccess", "Vaše objednávka č. " + createdOrder.getOrderCode() + " byla úspěšně přijata. Děkujeme!");

            return isGuest ? "redirect:/dekujeme?orderCode=" + createdOrder.getOrderCode() : "redirect:/muj-ucet/objednavky";

        } catch (ValidationException | CustomerService.EmailRegisteredException e) {
            log.warn("Validation or processing error during checkout for {}: {}", userIdentifierForLog, e.getMessage());
            if (e instanceof CustomerService.EmailRegisteredException) {
                bindingResult.rejectValue("email", "email.registered", e.getMessage());
            }
            // Pass the BindingResult to prepareModelForError
            prepareModelForError(model, principal, checkoutForm, orderCurrency, bindingResult);
            return "pokladna";
        } catch (IllegalStateException | EntityNotFoundException e) {
            log.error("Illegal state or entity not found during checkout for {}: {}", userIdentifierForLog, e.getMessage());
            redirectAttributes.addFlashAttribute("checkoutError", "Nastala chyba při zpracování objednávky: " + e.getMessage());
            return isGuest ? "redirect:/kosik" : "redirect:/muj-ucet/objednavky"; // Redirect somewhere safe
        }
        catch (Exception e) {
            log.error("Unexpected error during checkout for {}: {}", userIdentifierForLog, e.getMessage(), e);
            model.addAttribute("checkoutError", "Při zpracování objednávky nastala neočekávaná chyba.");
            model.addAttribute("checkoutErrorDetail", e.getMessage());
            // Pass the BindingResult to prepareModelForError
            prepareModelForError(model, principal, checkoutForm, orderCurrency, bindingResult);
            return "pokladna";
        }
    }

    /**
     * Prepares model attributes needed for rendering the checkout page after a form error.
     * @param model The model to populate.
     * @param principal The current user principal (can be null).
     * @param checkoutFormWithError The DTO with user's submitted data and validation errors.
     * @param currency The current currency code.
     * @param bindingResult The binding result containing validation errors.
     */
    private void prepareModelForError(Model model, Principal principal, CheckoutFormDataDto checkoutFormWithError, String currency, BindingResult bindingResult) {
        log.debug("Preparing model for rendering checkout page after form error. Currency: {}", currency);
        Customer customer = null;
        if (principal != null) {
            try {
                // Fetch customer data to display alongside the erroneous form if logged in
                customer = customerService.getCustomerByEmail(principal.getName()).orElse(null);
            } catch (Exception e) {
                log.error("Error fetching customer {} during prepareModelForError", principal.getName(), e);
                // Don't crash, just log the error
            }
        }

        model.addAttribute("checkoutForm", checkoutFormWithError); // Keep submitted data with errors
        model.addAttribute("customer", customer); // Add customer if logged in
        model.addAttribute("cart", sessionCart);
        model.addAttribute("allowedPaymentMethods", ALLOWED_PAYMENT_METHODS);
        model.addAttribute("currency", currency);
        model.addAttribute("currencySymbol", EURO_CURRENCY.equals(currency) ? "€" : "Kč");
        model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "checkoutForm", bindingResult); // Explicitly add bindingResult

        // Re-evaluate summary based on current cart state
        // Shipping should be indicated as needing recalculation after error
        BigDecimal initialShippingCost = null;
        String shippingError = "Zkontrolujte chyby ve formuláři a znovu vypočítejte dopravu.";
        model.addAttribute("originalShippingCostNoTax", initialShippingCost); // Set to null
        model.addAttribute("shippingError", shippingError); // Add error message

        prepareCheckoutSummaryModel(model, customer, checkoutFormWithError, currency, initialShippingCost, shippingError);
        log.debug("Model prepared for error view. BindingResult errors: {}", bindingResult.getAllErrors());
    }

    /**
     * Calculates and adds checkout summary details to the model.
     * Relies on passed shipping cost/error status.
     */
    private void prepareCheckoutSummaryModel(Model model, Customer customer, CheckoutFormDataDto checkoutForm, String currentCurrency, BigDecimal shippingCostNoTax, String shippingError) {
        log.debug("Preparing checkout summary model data for currency: {}", currentCurrency);
        String currencySymbol = EURO_CURRENCY.equals(currentCurrency) ? "€" : "Kč";

        // Cart calculations
        BigDecimal subtotal = sessionCart.calculateSubtotal(currentCurrency);
        Coupon validatedCoupon = validateAndGetCoupon(sessionCart, customer, subtotal, currentCurrency);
        BigDecimal couponDiscount = (validatedCoupon != null && !validatedCoupon.isFreeShippingOnly())
                ? sessionCart.calculateDiscountAmount(currentCurrency) : BigDecimal.ZERO;
        BigDecimal totalItemVat = sessionCart.calculateTotalVatAmount(currentCurrency);
        Map<BigDecimal, BigDecimal> rawVatBreakdown = sessionCart.calculateVatBreakdown(currentCurrency);
        SortedMap<BigDecimal, BigDecimal> sortedVatBreakdown = new TreeMap<>(rawVatBreakdown);
        BigDecimal totalPriceWithoutTaxAfterDiscount = subtotal.subtract(couponDiscount).max(BigDecimal.ZERO).setScale(PRICE_SCALE, RoundingMode.HALF_UP);

        // Shipping calculations (use provided values)
        BigDecimal originalShippingCostNoTax = BigDecimal.ZERO; // Assume zero initially
        BigDecimal originalShippingTax = BigDecimal.ZERO;
        BigDecimal finalShippingCostNoTax = BigDecimal.ZERO;
        BigDecimal finalShippingTax = BigDecimal.ZERO;
        BigDecimal shippingDiscountAmount = BigDecimal.ZERO;
        boolean shippingValid = (shippingCostNoTax != null && shippingCostNoTax.compareTo(BigDecimal.ZERO) >= 0);

        if (shippingValid) {
            originalShippingCostNoTax = shippingCostNoTax; // Use the passed value
            BigDecimal shippingTaxRate = shippingService.getShippingTaxRate();
            if (originalShippingCostNoTax.compareTo(BigDecimal.ZERO) > 0 && shippingTaxRate != null) {
                originalShippingTax = originalShippingCostNoTax.multiply(shippingTaxRate).setScale(PRICE_SCALE, ROUNDING_MODE);
            }

            if (validatedCoupon != null && validatedCoupon.isFreeShipping()) {
                log.info("Free shipping applied during summary preparation (Coupon: {}). Orig cost: {}", validatedCoupon.getCode(), originalShippingCostNoTax);
                finalShippingCostNoTax = BigDecimal.ZERO;
                finalShippingTax = BigDecimal.ZERO;
                shippingDiscountAmount = originalShippingCostNoTax; // Discount is the original cost
            } else {
                finalShippingCostNoTax = originalShippingCostNoTax;
                finalShippingTax = originalShippingTax;
                shippingDiscountAmount = BigDecimal.ZERO;
            }
        } else {
            // If shippingCostNoTax is null or negative, treat as error/not calculated
            log.debug("Shipping cost not valid or not calculated for summary. Passed Value: {}", shippingCostNoTax);
            // Use the passed error message or a default one
            shippingError = shippingError != null ? shippingError : "Doprava nebyla vypočtena.";
            originalShippingCostNoTax = null; // Set to null to indicate error/unknown state in template
            originalShippingTax = BigDecimal.ZERO;
            finalShippingCostNoTax = BigDecimal.ZERO;
            finalShippingTax = BigDecimal.ZERO;
            shippingDiscountAmount = BigDecimal.ZERO;
        }

        // Final total price calculation (only if shipping is valid)
        BigDecimal finalTotalPrice = null; // Initialize to null
        BigDecimal finalTotalVatWithShipping = totalItemVat.add(finalShippingTax).setScale(PRICE_SCALE, RoundingMode.HALF_UP);

        if (shippingValid) {
            finalTotalPrice = totalPriceWithoutTaxAfterDiscount
                    .add(finalShippingCostNoTax) // Use final cost after potential discount
                    .add(totalItemVat)
                    .add(finalShippingTax)      // Use final tax after potential discount
                    .setScale(PRICE_SCALE, ROUNDING_MODE);
            log.debug("Final total calculated: {}", finalTotalPrice);
        } else {
            log.debug("Final total cannot be calculated because shipping is invalid.");
        }


        // Add attributes to model
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("validatedCoupon", validatedCoupon);
        model.addAttribute("couponDiscount", couponDiscount);
        model.addAttribute("totalPriceWithoutTaxAfterDiscount", totalPriceWithoutTaxAfterDiscount);
        model.addAttribute("totalVat", totalItemVat); // VAT from items only
        model.addAttribute("vatBreakdown", sortedVatBreakdown);
        model.addAttribute("originalShippingCostNoTax", originalShippingCostNoTax); // Could be null
        model.addAttribute("originalShippingTax", originalShippingTax);
        model.addAttribute("shippingDiscountAmount", shippingDiscountAmount.setScale(PRICE_SCALE, RoundingMode.HALF_UP));
        model.addAttribute("shippingCostNoTax", finalShippingCostNoTax.setScale(PRICE_SCALE, RoundingMode.HALF_UP));
        model.addAttribute("shippingTax", finalShippingTax.setScale(PRICE_SCALE, RoundingMode.HALF_UP));
        model.addAttribute("totalPrice", finalTotalPrice); // Total is null if shipping is invalid
        model.addAttribute("totalVatWithShipping", finalTotalVatWithShipping); // Total VAT (items + final shipping tax)

        if (shippingError != null) {
            model.addAttribute("shippingError", shippingError); // Pass the error message
        }

        log.debug("Checkout summary model prepared: subtotal={}, itemDiscount={}, itemVat={}, origShipCost={}, shipDiscount={}, finalShipCost={}, finalShipTax={}, totalPrice={}, totalVatWithShip={}, shippingError='{}'",
                subtotal, couponDiscount, totalItemVat, originalShippingCostNoTax, shippingDiscountAmount, finalShippingCostNoTax, finalShippingTax, finalTotalPrice, finalTotalVatWithShipping, shippingError);
    }


    // --- AJAX Endpoint remains largely the same ---
    @PostMapping("/calculate-shipping")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<ShippingCalculationResponseDto> calculateShippingAjax(@RequestBody @Valid ShippingAddressDto addressDto) {
        String currentCurrency = currencyService.getSelectedCurrency();
        String currencySymbol = EURO_CURRENCY.equals(currentCurrency) ? "€" : "Kč";
        log.info("AJAX request to calculate shipping for address: {} in currency {}", addressDto, currentCurrency);

        Order tempOrder = new Order();
        tempOrder.setDeliveryStreet(addressDto.getStreet());
        tempOrder.setDeliveryCity(addressDto.getCity());
        tempOrder.setDeliveryZipCode(addressDto.getZipCode());
        tempOrder.setDeliveryCountry(addressDto.getCountry());
        tempOrder.setCurrency(currentCurrency);

        BigDecimal subtotal = sessionCart.calculateSubtotal(currentCurrency);
        Coupon validatedCoupon = validateAndGetCoupon(sessionCart, null, subtotal, currentCurrency); // Pass null customer for AJAX check
        BigDecimal couponDiscount = (validatedCoupon != null && !validatedCoupon.isFreeShippingOnly())
                ? sessionCart.calculateDiscountAmount(currentCurrency) : BigDecimal.ZERO;
        BigDecimal totalItemVat = sessionCart.calculateTotalVatAmount(currentCurrency);
        BigDecimal totalPriceWithoutTaxAfterDiscount = subtotal.subtract(couponDiscount).max(BigDecimal.ZERO).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        Map<BigDecimal, BigDecimal> rawVatBreakdown = sessionCart.calculateVatBreakdown(currentCurrency);
        SortedMap<BigDecimal, BigDecimal> sortedVatBreakdown = new TreeMap<>(rawVatBreakdown);

        try {
            BigDecimal originalShippingCostNoTax = shippingService.calculateShippingCost(tempOrder, currentCurrency);
            BigDecimal shippingTaxRate = shippingService.getShippingTaxRate();
            BigDecimal originalShippingTax = BigDecimal.ZERO;

            if (originalShippingCostNoTax == null || originalShippingCostNoTax.compareTo(BigDecimal.ZERO) < 0) {
                log.warn("Shipping calculation returned invalid cost: {}", originalShippingCostNoTax);
                throw new ShippingCalculationException("Nepodařilo se vypočítat cenu dopravy pro zadanou adresu.");
            }
            originalShippingCostNoTax = originalShippingCostNoTax.setScale(PRICE_SCALE, RoundingMode.HALF_UP); // Ensure scale

            if (originalShippingCostNoTax.compareTo(BigDecimal.ZERO) > 0 && shippingTaxRate != null) {
                originalShippingTax = originalShippingCostNoTax.multiply(shippingTaxRate).setScale(PRICE_SCALE, ROUNDING_MODE);
            }

            BigDecimal finalShippingCostNoTax = originalShippingCostNoTax;
            BigDecimal finalShippingTax = originalShippingTax;
            BigDecimal shippingDiscountAmount = BigDecimal.ZERO;

            if (validatedCoupon != null && validatedCoupon.isFreeShipping()) {
                log.info("AJAX Shipping Calc: Free shipping applied (Coupon: {}). Orig cost: {}", validatedCoupon.getCode(), originalShippingCostNoTax);
                finalShippingCostNoTax = BigDecimal.ZERO;
                finalShippingTax = BigDecimal.ZERO;
                shippingDiscountAmount = originalShippingCostNoTax; // Discount is the original cost
            }

            // Calculate final totals
            BigDecimal finalTotalPrice = totalPriceWithoutTaxAfterDiscount
                    .add(finalShippingCostNoTax)
                    .add(totalItemVat)
                    .add(finalShippingTax)
                    .setScale(PRICE_SCALE, ROUNDING_MODE);
            BigDecimal finalTotalVatWithShipping = totalItemVat.add(finalShippingTax).setScale(PRICE_SCALE, RoundingMode.HALF_UP);


            log.info("AJAX Shipping Calc OK: origCost={}, origTax={}, shipDiscount={}, finalCost={}, finalTax={}, finalTotal={}, finalTotalVat={}",
                    originalShippingCostNoTax, originalShippingTax, shippingDiscountAmount, finalShippingCostNoTax, finalShippingTax, finalTotalPrice, finalTotalVatWithShipping);

            ShippingCalculationResponseDto response = new ShippingCalculationResponseDto(
                    finalShippingCostNoTax, finalShippingTax, finalTotalPrice, sortedVatBreakdown, finalTotalVatWithShipping, null, currencySymbol,
                    originalShippingCostNoTax, originalShippingTax, shippingDiscountAmount
            );
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error during AJAX shipping calculation for address {}: {}", addressDto, e.getMessage(), e);
            String userFriendlyError = (e instanceof ShippingCalculationException) ? e.getMessage() : "Výpočet dopravy selhal. Zkuste to prosím znovu.";

            ShippingCalculationResponseDto errorResponse = new ShippingCalculationResponseDto(
                    null,                           // shippingCostNoTax - indicate error
                    null,                           // shippingTax - cannot calculate
                    null,                           // totalPrice - cannot calculate
                    sortedVatBreakdown,             // vatBreakdown (items only)
                    totalItemVat,                   // totalVatWithShipping (items only)
                    userFriendlyError,              // errorMessage
                    currencySymbol,                 // currencySymbol
                    null,                           // originalShippingCostNoTax
                    null,                           // originalShippingTax
                    BigDecimal.ZERO                 // shippingDiscountAmount
            );
            // Return 500 for unexpected errors, 400 for calculation/address errors
            HttpStatus status = (e instanceof ShippingCalculationException || e instanceof IllegalArgumentException) ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(status).body(errorResponse);
        }
    }


    // --- Helper Methods (Unchanged) ---
    private boolean hasSufficientAddress(Customer customer) { if (customer == null) return false; return StringUtils.hasText(customer.getInvoiceStreet()); }
    private Order createTemporaryOrderForShipping(Customer customer, String currency) { if (customer == null) return null; Order tempOrder = new Order(); tempOrder.setCustomer(customer); tempOrder.setCurrency(currency); if (customer.isUseInvoiceAddressAsDelivery()) { tempOrder.setDeliveryStreet(customer.getInvoiceStreet()); tempOrder.setDeliveryCity(customer.getInvoiceCity()); tempOrder.setDeliveryZipCode(customer.getInvoiceZipCode()); tempOrder.setDeliveryCountry(customer.getInvoiceCountry()); } else { tempOrder.setDeliveryStreet(customer.getDeliveryStreet()); tempOrder.setDeliveryCity(customer.getDeliveryCity()); tempOrder.setDeliveryZipCode(customer.getDeliveryZipCode()); tempOrder.setDeliveryCountry(customer.getDeliveryCountry()); } return tempOrder; }
    private Order createTemporaryOrderForShippingFromDto(CheckoutFormDataDto dto, String currency) { if (dto == null) return null; Order tempOrder = new Order(); tempOrder.setCurrency(currency); if (dto.isUseInvoiceAddressAsDelivery()) { tempOrder.setDeliveryStreet(dto.getInvoiceStreet()); tempOrder.setDeliveryCity(dto.getInvoiceCity()); tempOrder.setDeliveryZipCode(dto.getInvoiceZipCode()); tempOrder.setDeliveryCountry(dto.getInvoiceCountry()); } else { tempOrder.setDeliveryStreet(dto.getDeliveryStreet()); tempOrder.setDeliveryCity(dto.getDeliveryCity()); tempOrder.setDeliveryZipCode(dto.getDeliveryZipCode()); tempOrder.setDeliveryCountry(dto.getDeliveryCountry()); } return tempOrder; }
    private Coupon validateAndGetCoupon(Cart cart, Customer customer, BigDecimal subtotal, String currency) { String couponCode = cart.getAppliedCouponCode(); if (!StringUtils.hasText(couponCode)) { if (cart.getAppliedCoupon() != null) cart.removeCoupon(); return null; } Optional<Coupon> couponOpt = couponService.findByCode(couponCode); if (couponOpt.isEmpty()) { cart.removeCoupon(); return null; } Coupon coupon = couponOpt.get(); boolean isValid = couponService.isCouponGenerallyValid(coupon) && couponService.checkMinimumOrderValue(coupon, subtotal, currency); if (isValid && customer != null && !customer.isGuest()) { isValid = couponService.checkCustomerUsageLimit(customer, coupon); } if (isValid) { if (!Objects.equals(coupon, cart.getAppliedCoupon())) { cart.applyCoupon(coupon, couponCode); } return coupon; } else { cart.removeCoupon(); return null; } }
    private CartItemDto mapCartItemToDto(CartItem cartItem) { CartItemDto dto = new CartItemDto(); dto.setProductId(cartItem.getProductId()); dto.setQuantity(cartItem.getQuantity()); dto.setCustom(cartItem.isCustom()); dto.setSelectedDesignId(cartItem.getSelectedDesignId()); dto.setSelectedGlazeId(cartItem.getSelectedGlazeId()); dto.setSelectedRoofColorId(cartItem.getSelectedRoofColorId()); dto.setCustomDimensions(cartItem.getCustomDimensions()); dto.setCustomGlaze(cartItem.getCustomGlaze()); dto.setCustomRoofColor(cartItem.getCustomRoofColor()); dto.setCustomRoofOverstep(cartItem.getCustomRoofOverstep()); dto.setCustomDesign(cartItem.getCustomDesign()); dto.setCustomHasDivider(cartItem.isCustomHasDivider()); dto.setCustomHasGutter(cartItem.isCustomHasGutter()); dto.setCustomHasGardenShed(cartItem.isCustomHasGardenShed()); dto.setSelectedAddons(cartItem.getSelectedAddons()); return dto; }
    private boolean isShippingAddressAvailable(Order tempOrder) { return tempOrder != null && StringUtils.hasText(tempOrder.getDeliveryStreet()) && StringUtils.hasText(tempOrder.getDeliveryCity()) && StringUtils.hasText(tempOrder.getDeliveryZipCode()) && StringUtils.hasText(tempOrder.getDeliveryCountry()); }
    // Helper to check if address data in DTO differs from Customer entity
    private boolean addressMatches(Customer customer, CheckoutFormDataDto dto) { if (customer == null || dto == null) return false; if (!Objects.equals(customer.getFirstName(), dto.getFirstName()) || !Objects.equals(customer.getLastName(), dto.getLastName()) || !Objects.equals(customer.getPhone(), dto.getPhone())) { return false; } if (!Objects.equals(customer.getInvoiceCompanyName(), dto.getInvoiceCompanyName()) || !Objects.equals(customer.getInvoiceStreet(), dto.getInvoiceStreet()) || !Objects.equals(customer.getInvoiceCity(), dto.getInvoiceCity()) || !Objects.equals(customer.getInvoiceZipCode(), dto.getInvoiceZipCode()) || !Objects.equals(customer.getInvoiceCountry(), dto.getInvoiceCountry()) || !Objects.equals(customer.getInvoiceTaxId(), dto.getInvoiceTaxId()) || !Objects.equals(customer.getInvoiceVatId(), dto.getInvoiceVatId()) ) { return false; } if (customer.isUseInvoiceAddressAsDelivery() != dto.isUseInvoiceAddressAsDelivery()) { return false; } if (!dto.isUseInvoiceAddressAsDelivery()) { if (!Objects.equals(customer.getDeliveryCompanyName(), dto.getDeliveryCompanyName()) || !Objects.equals(customer.getDeliveryFirstName(), dto.getDeliveryFirstName()) || !Objects.equals(customer.getDeliveryLastName(), dto.getDeliveryLastName()) || !Objects.equals(customer.getDeliveryStreet(), dto.getDeliveryStreet()) || !Objects.equals(customer.getDeliveryCity(), dto.getDeliveryCity()) || !Objects.equals(customer.getDeliveryZipCode(), dto.getDeliveryZipCode()) || !Objects.equals(customer.getDeliveryCountry(), dto.getDeliveryCountry()) || !Objects.equals(customer.getDeliveryPhone(), dto.getDeliveryPhone())) { return false; } } return true; }

} // Konec třídy CheckoutController