package org.example.eshop.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.example.eshop.config.PriceConstants;
import org.example.eshop.dto.*; // Import všech DTO
import org.example.eshop.model.*;
import org.example.eshop.service.*; // Import všech Service
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
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/pokladna")
public class CheckoutController implements PriceConstants {

    private static final Logger log = LoggerFactory.getLogger(CheckoutController.class);

    public static class ShippingCalculationException extends RuntimeException {
        public ShippingCalculationException(String message) { super(message); }
        public ShippingCalculationException(String message, Throwable cause) { super(message, cause); }
    }

    @Autowired private Cart sessionCart;
    @Autowired private CustomerService customerService;
    @Autowired private OrderService orderService;
    @Autowired private CouponService couponService;
    @Autowired @Qualifier("googleMapsShippingService") private ShippingService shippingService;
    @Autowired private ProductService productService;
    @Autowired private CurrencyService currencyService;
    @Autowired private TaxRateService taxRateService;

    private static final Map<String, String> ALLOWED_PAYMENT_METHODS = Map.of(
            "BANK_TRANSFER", "Bankovní převod",
            "CASH_ON_DELIVERY", "Dobírka"
    );

    // Metoda GET (Zobrazení stránky - Beze změny)
    @GetMapping
    @Transactional(readOnly = true)
    public String showCheckoutPage(Model model, @AuthenticationPrincipal UserDetails userDetails, RedirectAttributes redirectAttributes) {
        String userIdentifier = (userDetails != null) ? userDetails.getUsername() : "GUEST";
        String currentCurrency = currencyService.getSelectedCurrency();
        log.info("Displaying checkout page for user: {} in currency: {}", userIdentifier, currentCurrency);
        if (!sessionCart.hasItems()) { log.warn("User {} attempted to access checkout with an empty cart.", userIdentifier); redirectAttributes.addFlashAttribute("cartError", "Váš košík je prázdný. Nelze pokračovat k pokladně."); return "redirect:/kosik"; }
        Customer customer = null;
        CheckoutFormDataDto checkoutForm = (CheckoutFormDataDto) model.getAttribute("checkoutForm");
        if (checkoutForm == null) {
            checkoutForm = new CheckoutFormDataDto();
            if (userDetails != null) {
                try { customer = customerService.getCustomerByEmail(userDetails.getUsername()).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userDetails.getUsername())); checkoutForm.initializeFromCustomer(customer); log.debug("Initialized checkout form DTO from logged-in customer {}.", customer.getId()); }
                catch (IllegalStateException e) { log.error("Authenticated user {} not found in customer database.", userDetails.getUsername()); redirectAttributes.addFlashAttribute("checkoutError", "Chyba při načítání údajů zákazníka."); return "redirect:/"; }
            } else { checkoutForm.setPaymentMethod("BANK_TRANSFER"); checkoutForm.setAgreeTerms(false); checkoutForm.setInvoiceCountry("Česká republika"); checkoutForm.setUseInvoiceAddressAsDelivery(true); log.debug("Initialized checkout form DTO with defaults for guest."); }
        } else {
            log.debug("Using existing checkout form DTO from model (likely after validation error).");
            if (userDetails != null && customer == null) {
                customer = customerService.getCustomerByEmail(userDetails.getUsername()).orElse(null);
                if(customer == null){ log.error("Authenticated user {} not found when preparing model after error.", userDetails.getUsername()); return "redirect:/prihlaseni"; }
            }
        }
        model.addAttribute("checkoutForm", checkoutForm); model.addAttribute("customer", customer); model.addAttribute("cart", sessionCart); model.addAttribute("allowedPaymentMethods", ALLOWED_PAYMENT_METHODS); model.addAttribute("currency", currentCurrency); model.addAttribute("currencySymbol", EURO_CURRENCY.equals(currentCurrency) ? "€" : "Kč");
        prepareCheckoutSummaryModel(model, customer, checkoutForm, currentCurrency);
        log.info("Checkout page model prepared for {}. Currency: {}", userIdentifier, currentCurrency);
        return "pokladna";
    }

    // Metoda POST (Zpracování objednávky - Beze změny oproti předchozí verzi)
    @PostMapping("/odeslat")
    @Transactional
    public String processCheckout(@Valid @ModelAttribute("checkoutForm") CheckoutFormDataDto checkoutForm,
                                  BindingResult bindingResult, Model model,
                                  @AuthenticationPrincipal UserDetails userDetails, RedirectAttributes redirectAttributes) {
        Principal principal = (userDetails != null) ? () -> userDetails.getUsername() : null;
        boolean isGuest = (principal == null);
        String userIdentifierForLog = isGuest ? (checkoutForm.getEmail() != null ? checkoutForm.getEmail() : "GUEST") : principal.getName();
        String orderCurrency = currencyService.getSelectedCurrency();
        log.info("Processing checkout submission for {}: {} in currency: {}", (isGuest ? "guest" : "user"), userIdentifierForLog, orderCurrency);
        if (isGuest) validateGuestData(checkoutForm, bindingResult);
        if (!checkoutForm.isUseInvoiceAddressAsDelivery()) validateDeliveryAddress(checkoutForm, bindingResult);
        if (bindingResult.hasErrors()) { log.warn("Checkout form validation failed for {}: {}", userIdentifierForLog, bindingResult.getAllErrors()); prepareModelForError(model, principal, checkoutForm, orderCurrency); model.addAttribute("checkoutError", "Prosím, opravte chyby ve formuláři."); return "pokladna"; }
        if (!sessionCart.hasItems()) { log.warn("User/guest {} submitted checkout form with an empty cart.", userIdentifierForLog); redirectAttributes.addFlashAttribute("checkoutError", "Váš košík je prázdný. Objednávku nelze dokončit."); return "redirect:/kosik"; }
        Customer customer;
        try {
            if (isGuest) { customer = customerService.getOrCreateGuestFromCheckoutData(checkoutForm); }
            else { customer = customerService.getCustomerByEmail(principal.getName()).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + principal.getName())); log.info("Processing checkout for existing customer {}. Using addresses from customer profile.", customer.getId()); }
            Order tempOrderForShipping = createTemporaryOrderForShipping(customer, orderCurrency);
            if (!isShippingAddressAvailable(tempOrderForShipping)) { log.error("Missing shipping address in customer profile (ID {}) for final shipping calculation.", customer.getId()); bindingResult.rejectValue("invoiceStreet", "address.missing.profile", "Chybí kompletní dodací adresa. Prosím, doplňte ji ve svém profilu v sekci 'Moje adresy'."); prepareModelForError(model, principal, checkoutForm, orderCurrency); return "pokladna"; }
            BigDecimal shippingCostNoTax = BigDecimal.ZERO; BigDecimal shippingTax = BigDecimal.ZERO; BigDecimal subtotalForCoupon = sessionCart.calculateSubtotal(orderCurrency); Coupon validatedCoupon = validateAndGetCoupon(sessionCart, customer, subtotalForCoupon, orderCurrency);
            if (validatedCoupon == null || !validatedCoupon.isFreeShipping()) {
                try { shippingCostNoTax = shippingService.calculateShippingCost(tempOrderForShipping, orderCurrency); BigDecimal shippingTaxRate = shippingService.getShippingTaxRate(); if (shippingCostNoTax != null && shippingCostNoTax.compareTo(BigDecimal.ZERO) > 0 && shippingTaxRate != null) { shippingTax = shippingCostNoTax.multiply(shippingTaxRate).setScale(PRICE_SCALE, ROUNDING_MODE); } else { shippingCostNoTax = BigDecimal.ZERO; shippingTax = BigDecimal.ZERO; } log.info("Final shipping cost calculated: NoTax={}, Tax={}", shippingCostNoTax, shippingTax); }
                catch (Exception e) { log.error("Error calculating FINAL shipping cost for {}: {}", userIdentifierForLog, e.getMessage(), e); model.addAttribute("checkoutError", "Nepodařilo se vypočítat cenu dopravy. Zkontrolujte adresu v profilu."); prepareModelForError(model, principal, checkoutForm, orderCurrency); return "pokladna"; }
            } else { log.info("Free shipping applied for order based on coupon {}.", validatedCoupon.getCode()); shippingCostNoTax = BigDecimal.ZERO; shippingTax = BigDecimal.ZERO; }
            CreateOrderRequest orderRequest = new CreateOrderRequest(); orderRequest.setCustomerId(customer.getId()); orderRequest.setUseCustomerAddresses(true); orderRequest.setPaymentMethod(checkoutForm.getPaymentMethod()); orderRequest.setCustomerNote(checkoutForm.getCustomerNote()); orderRequest.setCouponCode(validatedCoupon != null ? validatedCoupon.getCode() : null); orderRequest.setCurrency(orderCurrency); orderRequest.setShippingCostNoTax(shippingCostNoTax); orderRequest.setShippingTax(shippingTax);
            List<CartItemDto> orderItemsDto = sessionCart.getItemsList().stream().map(this::mapCartItemToDto).collect(Collectors.toList()); orderRequest.setItems(orderItemsDto);
            log.info("Attempting to create order with request: {}", orderRequest); Order createdOrder = orderService.createOrder(orderRequest); log.info("Order {} successfully created with currency {} for {}: {}", createdOrder.getOrderCode(), orderCurrency, (isGuest ? "guest" : "user"), userIdentifierForLog);
            sessionCart.clearCart(); log.info("Session cart cleared for {}", userIdentifierForLog); redirectAttributes.addFlashAttribute("orderSuccess", "Vaše objednávka č. " + createdOrder.getOrderCode() + " byla úspěšně přijata. Děkujeme!");
            return isGuest ? "redirect:/dekujeme?orderCode=" + createdOrder.getOrderCode() : "redirect:/muj-ucet/objednavky"; // Redirect logged-in user to their orders
        } catch (CustomerService.EmailRegisteredException e) { log.warn("Guest checkout failed for {}: {}", userIdentifierForLog, e.getMessage()); bindingResult.rejectValue("email", "email.registered", e.getMessage()); prepareModelForError(model, principal, checkoutForm, orderCurrency); return "pokladna"; }
        catch (Exception e) { log.error("Failed to process checkout for {}: {}", userIdentifierForLog, e.getMessage(), e); model.addAttribute("checkoutError", "Při zpracování objednávky nastala neočekávaná chyba."); model.addAttribute("checkoutErrorDetail", e.getMessage()); prepareModelForError(model, principal, checkoutForm, orderCurrency); return "pokladna"; }
    }

    // Endpoint pro AJAX výpočet dopravy (Beze změny)
    @PostMapping("/calculate-shipping")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<ShippingCalculationResponseDto> calculateShippingAjax(@RequestBody ShippingAddressDto addressDto) {
        String currentCurrency = currencyService.getSelectedCurrency(); String currencySymbol = EURO_CURRENCY.equals(currentCurrency) ? "€" : "Kč";
        log.info("AJAX request to calculate shipping for address: {} in currency {}", addressDto, currentCurrency);
        if (addressDto == null || !StringUtils.hasText(addressDto.getStreet()) || !StringUtils.hasText(addressDto.getCity()) || !StringUtils.hasText(addressDto.getZipCode()) || !StringUtils.hasText(addressDto.getCountry())) { return ResponseEntity.badRequest().body(new ShippingCalculationResponseDto(null, null, null, null, null, "Chybí povinné údaje adresy (ulice, město, PSČ, země).", currencySymbol)); }
        Order tempOrder = new Order(); tempOrder.setDeliveryStreet(addressDto.getStreet()); tempOrder.setDeliveryCity(addressDto.getCity()); tempOrder.setDeliveryZipCode(addressDto.getZipCode()); tempOrder.setDeliveryCountry(addressDto.getCountry()); tempOrder.setCurrency(currentCurrency);
        BigDecimal shippingCostNoTax = BigDecimal.ZERO; BigDecimal shippingTax = BigDecimal.ZERO; BigDecimal totalPrice; BigDecimal totalVatWithShipping;
        Map<BigDecimal, BigDecimal> vatBreakdown = sessionCart.calculateVatBreakdown(currentCurrency); BigDecimal totalItemVat = sessionCart.calculateTotalVatAmount(currentCurrency);
        Coupon currentCoupon = sessionCart.getAppliedCoupon();
        try {
            if (currentCoupon == null || !currentCoupon.isFreeShipping()) {
                shippingCostNoTax = shippingService.calculateShippingCost(tempOrder, currentCurrency); BigDecimal shippingTaxRate = shippingService.getShippingTaxRate();
                if (shippingCostNoTax != null && shippingCostNoTax.compareTo(BigDecimal.ZERO) > 0 && shippingTaxRate != null) { shippingTax = shippingCostNoTax.multiply(shippingTaxRate).setScale(PRICE_SCALE, ROUNDING_MODE); } else { shippingCostNoTax = BigDecimal.ZERO; shippingTax = BigDecimal.ZERO; }
            } else { log.info("AJAX Shipping Calc: Free shipping applied."); shippingCostNoTax = BigDecimal.ZERO; shippingTax = BigDecimal.ZERO; }
            BigDecimal priceBeforeShipping = sessionCart.calculateTotalPriceBeforeShipping(currentCurrency);
            totalPrice = priceBeforeShipping.add(shippingCostNoTax).add(shippingTax).setScale(PRICE_SCALE, ROUNDING_MODE);
            totalVatWithShipping = totalItemVat.add(shippingTax).setScale(PRICE_SCALE, ROUNDING_MODE);
            log.info("AJAX Shipping Calc Result: costNoTax={}, tax={}, total={}, totalVatWithShipping={}", shippingCostNoTax, shippingTax, totalPrice, totalVatWithShipping);
            ShippingCalculationResponseDto response = new ShippingCalculationResponseDto(shippingCostNoTax, shippingTax, totalPrice, vatBreakdown, totalVatWithShipping, null, currencySymbol);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during AJAX shipping calculation for address {}: {}", addressDto, e.getMessage(), e); String errorMessage = "Výpočet dopravy selhal. Zkontrolujte prosím správnost zadané adresy.";
            ShippingCalculationResponseDto errorResponse = new ShippingCalculationResponseDto(new BigDecimal("-1"), BigDecimal.ZERO, null, vatBreakdown, totalItemVat, errorMessage, currencySymbol);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }


    // --- Pomocné privátní metody ---

    /**
     * Připraví model pro znovuzobrazení stránky pokladny po chybě (např. validační).
     * Zajistí, že formulářové DTO (s chybami) a data zákazníka (pokud je přihlášen) jsou v modelu.
     * Přepočítá souhrn košíku a dopravy na základě spolehlivých dat.
     */
    private void prepareModelForError(Model model, Principal principal, CheckoutFormDataDto checkoutFormWithError, String currency) {
        log.debug("Preparing model for rendering checkout page after form error. Currency: {}", currency);
        Customer customer = null;
        if (principal != null) {
            try { customer = customerService.getCustomerByEmail(principal.getName()).orElse(null); }
            catch (Exception e) { log.error("Error fetching customer during prepareModelForError", e); }
        }
        model.addAttribute("checkoutForm", checkoutFormWithError); // Ponecháme DTO s chybami z formuláře
        model.addAttribute("customer", customer);
        model.addAttribute("cart", sessionCart);
        model.addAttribute("allowedPaymentMethods", ALLOWED_PAYMENT_METHODS);
        model.addAttribute("currency", currency);
        model.addAttribute("currencySymbol", EURO_CURRENCY.equals(currency) ? "€" : "Kč");
        // Přepočítáme souhrn, ale pro dopravu použijeme spolehlivá data
        prepareCheckoutSummaryModel(model, customer, checkoutFormWithError, currency);
    }

    /**
     * Připraví data pro souhrn košíku a dopravy v modelu.
     * *** UPRAVENO: Pro výpočet dopravy používá prioritně data z Customer entity (pokud existuje),
     * jinak data z DTO (pro hosty nebo jako fallback). ***
     */
    private void prepareCheckoutSummaryModel(Model model, Customer customer, CheckoutFormDataDto checkoutForm, String currentCurrency) {
        log.debug("Preparing checkout summary model data for currency: {}", currentCurrency);

        BigDecimal subtotal = sessionCart.calculateSubtotal(currentCurrency);
        Coupon validatedCoupon = validateAndGetCoupon(sessionCart, customer, subtotal, currentCurrency); // Pass customer for usage check
        BigDecimal couponDiscount = (validatedCoupon != null) ? sessionCart.calculateDiscountAmount(currentCurrency) : BigDecimal.ZERO;
        BigDecimal totalItemVat = sessionCart.calculateTotalVatAmount(currentCurrency);
        Map<BigDecimal, BigDecimal> vatBreakdown = sessionCart.calculateVatBreakdown(currentCurrency);

        BigDecimal shippingCostNoTax = BigDecimal.ZERO;
        BigDecimal shippingTax = BigDecimal.ZERO;
        String shippingError = null;

        // --- Získání adresy pro výpočet dopravy ---
        Order tempOrderForShipping;
        if (customer != null) {
            // Pro přihlášeného uživatele VŽDY použijeme data z jeho entity
            tempOrderForShipping = createTemporaryOrderForShipping(customer, currentCurrency);
            log.debug("Using Customer entity address for shipping calculation check.");
        } else {
            // Pro hosta použijeme data z DTO
            tempOrderForShipping = createTemporaryOrderForShippingFromDto(checkoutForm, currentCurrency);
            log.debug("Using CheckoutForm DTO address for shipping calculation check.");
        }
        // ---------------------------------------------

        // Zkontrolujeme dostupnost adresy ze spolehlivého zdroje
        if (isShippingAddressAvailable(tempOrderForShipping)) {
            if (validatedCoupon == null || !validatedCoupon.isFreeShipping()) {
                try {
                    // Pro výpočet použijeme stejný spolehlivý zdroj adresy
                    shippingCostNoTax = shippingService.calculateShippingCost(tempOrderForShipping, currentCurrency);
                    BigDecimal shippingTaxRate = shippingService.getShippingTaxRate();
                    if (shippingCostNoTax != null && shippingCostNoTax.compareTo(BigDecimal.ZERO) > 0 && shippingTaxRate != null) {
                        shippingTax = shippingCostNoTax.multiply(shippingTaxRate).setScale(PRICE_SCALE, ROUNDING_MODE);
                    } else {
                        shippingCostNoTax = BigDecimal.ZERO; shippingTax = BigDecimal.ZERO;
                    }
                } catch (Exception e) {
                    log.error("Error calculating shipping cost during checkout display/prepareModel: {}", e.getMessage());
                    shippingError = "Nepodařilo se vypočítat cenu dopravy.";
                    shippingCostNoTax = new BigDecimal("-1"); // Indikace chyby
                    shippingTax = BigDecimal.ZERO;
                }
            } else {
                log.info("Free shipping applied for user {} due to coupon.", customer != null ? customer.getEmail() : "GUEST");
                shippingCostNoTax = BigDecimal.ZERO; shippingTax = BigDecimal.ZERO;
            }
        } else {
            // Adresa chybí ve spolehlivém zdroji
            shippingError = "Cena dopravy bude vypočtena po zadání/načtení adresy.";
            if (customer != null) { // Pokud je přihlášen a nemá adresu v DB
                shippingError = "Doplňte dodací adresu v profilu pro výpočet dopravy.";
            }
            // Nastavíme 0, pokud adresa chybí, aby se nezobrazovala záporná cena
            shippingCostNoTax = BigDecimal.ZERO;
            shippingTax = BigDecimal.ZERO;
        }

        BigDecimal totalPriceBeforeShipping = sessionCart.calculateTotalPriceBeforeShipping(currentCurrency);
        BigDecimal finalTotalPrice = totalPriceBeforeShipping
                .add(shippingCostNoTax.compareTo(BigDecimal.ZERO) >= 0 ? shippingCostNoTax : BigDecimal.ZERO) // Přičteme dopravu jen pokud je platná (>=0)
                .add(shippingTax)
                .setScale(PRICE_SCALE, ROUNDING_MODE);

        model.addAttribute("subtotal", subtotal);
        model.addAttribute("validatedCoupon", validatedCoupon);
        model.addAttribute("couponDiscount", couponDiscount);
        model.addAttribute("totalVat", totalItemVat);
        model.addAttribute("vatBreakdown", vatBreakdown);
        model.addAttribute("shippingCostNoTax", shippingCostNoTax); // Může být 0 nebo -1 při chybě
        model.addAttribute("shippingTax", shippingTax);
        model.addAttribute("totalPrice", finalTotalPrice);
        if(shippingError != null) { model.addAttribute("shippingError", shippingError); }

        log.debug("Checkout summary model prepared: subtotal={}, discount={}, itemVat={}, shippingCostNoTax={}, shippingTax={}, totalPrice={}, shippingError='{}'",
                subtotal, couponDiscount, totalItemVat, shippingCostNoTax, shippingTax, finalTotalPrice, shippingError);
    }

    // Ostatní pomocné metody (beze změny)
    private Order createTemporaryOrderForShipping(Customer customer, String currency) { if (customer == null) return null; Order tempOrder = new Order(); tempOrder.setCustomer(customer); tempOrder.setCurrency(currency); tempOrder.setDeliveryStreet(customer.getDeliveryStreet()); tempOrder.setDeliveryCity(customer.getDeliveryCity()); tempOrder.setDeliveryZipCode(customer.getDeliveryZipCode()); tempOrder.setDeliveryCountry(customer.getDeliveryCountry()); return tempOrder; }
    private Order createTemporaryOrderForShippingFromDto(CheckoutFormDataDto dto, String currency) { if (dto == null) return null; Order tempOrder = new Order(); tempOrder.setCurrency(currency); if(dto.isUseInvoiceAddressAsDelivery()){ tempOrder.setDeliveryStreet(dto.getInvoiceStreet()); tempOrder.setDeliveryCity(dto.getInvoiceCity()); tempOrder.setDeliveryZipCode(dto.getInvoiceZipCode()); tempOrder.setDeliveryCountry(dto.getInvoiceCountry()); } else { tempOrder.setDeliveryStreet(dto.getDeliveryStreet()); tempOrder.setDeliveryCity(dto.getDeliveryCity()); tempOrder.setDeliveryZipCode(dto.getDeliveryZipCode()); tempOrder.setDeliveryCountry(dto.getDeliveryCountry()); } return tempOrder; }
    private Coupon validateAndGetCoupon(Cart cart, Customer customer, BigDecimal subtotal, String currency) { String couponCode = cart.getAppliedCouponCode(); if (!StringUtils.hasText(couponCode)) { if(cart.getAppliedCoupon() != null) cart.removeCoupon(); return null; } Optional<Coupon> couponOpt = couponService.findByCode(couponCode); if (couponOpt.isEmpty()) { cart.removeCoupon(); return null; } Coupon coupon = couponOpt.get(); boolean isValid = couponService.isCouponGenerallyValid(coupon) && couponService.checkMinimumOrderValue(coupon, subtotal, currency); if (isValid && customer != null && !customer.isGuest()) { isValid = couponService.checkCustomerUsageLimit(customer, coupon); } if (isValid) { if(!coupon.equals(cart.getAppliedCoupon())) { cart.applyCoupon(coupon, couponCode); } return coupon; } else { cart.removeCoupon(); return null; } }
    private CartItemDto mapCartItemToDto(CartItem cartItem) { CartItemDto dto = new CartItemDto(); dto.setProductId(cartItem.getProductId()); dto.setQuantity(cartItem.getQuantity()); dto.setCustom(cartItem.isCustom()); dto.setSelectedDesignId(cartItem.getSelectedDesignId()); dto.setSelectedGlazeId(cartItem.getSelectedGlazeId()); dto.setSelectedRoofColorId(cartItem.getSelectedRoofColorId()); dto.setCustomDimensions(cartItem.getCustomDimensions()); dto.setCustomGlaze(cartItem.getCustomGlaze()); dto.setCustomRoofColor(cartItem.getCustomRoofColor()); dto.setCustomRoofOverstep(cartItem.getCustomRoofOverstep()); dto.setCustomDesign(cartItem.getCustomDesign()); dto.setCustomHasDivider(cartItem.isCustomHasDivider()); dto.setCustomHasGutter(cartItem.isCustomHasGutter()); dto.setCustomHasGardenShed(cartItem.isCustomHasGardenShed()); dto.setSelectedAddons(cartItem.getSelectedAddons()); return dto; }
    private void validateGuestData(CheckoutFormDataDto dto, BindingResult bindingResult) { if (!StringUtils.hasText(dto.getEmail())) bindingResult.rejectValue("email", "NotBlank", "Email je povinný."); else if (dto.getEmail().length() > 255 || !dto.getEmail().contains("@")) bindingResult.rejectValue("email", "Email", "Zadejte platný email."); if (!StringUtils.hasText(dto.getFirstName())) bindingResult.rejectValue("firstName", "NotBlank", "Křestní jméno je povinné."); if (!StringUtils.hasText(dto.getLastName())) bindingResult.rejectValue("lastName", "NotBlank", "Příjmení je povinné."); if (!StringUtils.hasText(dto.getPhone())) bindingResult.rejectValue("phone", "NotBlank", "Telefonní číslo je povinné."); if (!StringUtils.hasText(dto.getInvoiceStreet())) bindingResult.rejectValue("invoiceStreet", "NotBlank", "Ulice (fakturační) je povinná."); if (!StringUtils.hasText(dto.getInvoiceCity())) bindingResult.rejectValue("invoiceCity", "NotBlank", "Město (fakturační) je povinné."); if (!StringUtils.hasText(dto.getInvoiceZipCode())) bindingResult.rejectValue("invoiceZipCode", "NotBlank", "PSČ (fakturační) je povinné."); if (!StringUtils.hasText(dto.getInvoiceCountry())) bindingResult.rejectValue("invoiceCountry", "NotBlank", "Země (fakturační) je povinná."); }
    private void validateDeliveryAddress(CheckoutFormDataDto dto, BindingResult bindingResult) { if (!StringUtils.hasText(dto.getDeliveryFirstName())) bindingResult.rejectValue("deliveryFirstName", "NotBlank", "Křestní jméno (dodací) je povinné."); if (!StringUtils.hasText(dto.getDeliveryLastName())) bindingResult.rejectValue("deliveryLastName", "NotBlank", "Příjmení (dodací) je povinné."); if (!StringUtils.hasText(dto.getDeliveryStreet())) bindingResult.rejectValue("deliveryStreet", "NotBlank", "Ulice (dodací) je povinná."); if (!StringUtils.hasText(dto.getDeliveryCity())) bindingResult.rejectValue("deliveryCity", "NotBlank", "Město (dodací) je povinné."); if (!StringUtils.hasText(dto.getDeliveryZipCode())) bindingResult.rejectValue("deliveryZipCode", "NotBlank", "PSČ (dodací) je povinné."); if (!StringUtils.hasText(dto.getDeliveryCountry())) bindingResult.rejectValue("deliveryCountry", "NotBlank", "Země (dodací) je povinná."); if (!StringUtils.hasText(dto.getDeliveryPhone())) bindingResult.rejectValue("deliveryPhone", "NotBlank", "Telefon (dodací) je povinný."); }
    private boolean isShippingAddressAvailable(Order tempOrder) { return tempOrder != null && StringUtils.hasText(tempOrder.getDeliveryStreet()) && StringUtils.hasText(tempOrder.getDeliveryCity()) && StringUtils.hasText(tempOrder.getDeliveryZipCode()) && StringUtils.hasText(tempOrder.getDeliveryCountry()); }

}