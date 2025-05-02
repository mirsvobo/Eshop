package org.example.eshop.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.example.eshop.config.PriceConstants;
import org.example.eshop.dto.*;
import org.example.eshop.dto.CheckoutFormDataDto.DeliveryAddressValidation;
import org.example.eshop.dto.CheckoutFormDataDto.GuestValidation;
import org.example.eshop.model.CartItem;
import org.example.eshop.model.Coupon;
import org.example.eshop.model.Customer;
import org.example.eshop.model.Order;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/pokladna")
public class CheckoutController implements PriceConstants {

    private static final Logger log = LoggerFactory.getLogger(CheckoutController.class);
    private static final Map<String, String> ALLOWED_PAYMENT_METHODS = Map.of(
            "BANK_TRANSFER", "Bankovní převod",
            "CASH_ON_DELIVERY", "Dobírka"
            // Přidat další metody podle potřeby
    );
    @Autowired
    private Cart sessionCart;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private CouponService couponService;
    @Autowired
    @Qualifier("googleMapsShippingService")
    private ShippingService shippingService;
    @Autowired
    private CurrencyService currencyService;

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
            // Pokud máme data z předchozího pokusu (např. po chybě), načteme přihlášeného uživatele znovu pro zobrazení
            if (userDetails != null) {
                customer = customerService.getCustomerByEmail(userDetails.getUsername()).orElse(null);
                if (customer == null) {
                    log.error("Authenticated user {} not found when preparing model after error.", userDetails.getUsername());
                    return "redirect:/prihlaseni";
                }
            }
            log.debug("Using checkoutForm DTO from previous request (possibly after error).");
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
            if (fetchedRate != null && fetchedRate.compareTo(BigDecimal.ZERO) >= 0) { // Kontrola, zda sazba není záporná
                shippingTaxRate = fetchedRate;
            } else {
                log.error("Shipping tax rate from service is null or negative! Using default: {}", shippingTaxRate);
            }

            // Přidání sazby DPH do modelu pro použití v JS
            model.addAttribute("shippingTaxRate", shippingTaxRate); // Přidáváme jako BigDecimal
            log.debug("Added shippingTaxRate {} to model.", shippingTaxRate);

            // Výpočet počáteční ceny dopravy (pouze pro přihlášené s adresou nebo pokud má DTO adresu)
            boolean canCalculateInitialShipping = false;
            Order tempOrderForShipping = null;

            if (customer != null) {
                tempOrderForShipping = createTemporaryOrderForShipping(customer, currentCurrency);
                canCalculateInitialShipping = isShippingAddressAvailable(tempOrderForShipping);
                if (!canCalculateInitialShipping)
                    initialShippingError = "Doplňte adresu v profilu pro výpočet dopravy.";
            } else if (hasSufficientAddressInDto(checkoutForm)) {
                tempOrderForShipping = createTemporaryOrderForShippingFromDto(checkoutForm, currentCurrency);
                canCalculateInitialShipping = isShippingAddressAvailable(tempOrderForShipping);
                if (!canCalculateInitialShipping)
                    initialShippingError = "Vyplňte adresu pro výpočet dopravy."; // Nemělo by nastat, pokud hasSufficientAddressInDto prošlo
            } else {
                initialShippingError = "Vyplňte adresu pro výpočet dopravy.";
            }

            if (canCalculateInitialShipping) {
                try {
                    initialShippingCostNoTax = shippingService.calculateShippingCost(tempOrderForShipping, currentCurrency);
                    if (initialShippingCostNoTax == null || initialShippingCostNoTax.compareTo(BigDecimal.ZERO) < 0) {
                        initialShippingError = "Nepodařilo se vypočítat dopravu.";
                        initialShippingCostNoTax = null;
                    } else {
                        initialShippingCostNoTax = initialShippingCostNoTax.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
                        // Úspěšný výpočet
                    }
                } catch (Exception e) {
                    log.error("Error calculating initial shipping for {}: {}", userIdentifier, e.getMessage());
                    initialShippingError = "Chyba výpočtu dopravy.";
                    initialShippingCostNoTax = null;
                }
            }
        } catch (Exception e) {
            log.error("Failed to get shipping tax rate or calculate initial shipping: {}", e.getMessage(), e);
            initialShippingError = "Chyba konfigurace dopravy.";
            // Pokud selže načtení sazby, přidáme do modelu fallback hodnotu
            if (!model.containsAttribute("shippingTaxRate")) {
                model.addAttribute("shippingTaxRate", shippingTaxRate); // Přidání fallback sazby
            }
        }

        // Přidání výsledků výpočtu dopravy do modelu
        model.addAttribute("originalShippingCostNoTax", initialShippingCostNoTax);
        model.addAttribute("shippingError", initialShippingError);

        // Příprava souhrnu (již používá proměnné z modelu a předané argumenty)
        // *** ZMĚNA: Použití this. ***
        this.prepareCheckoutSummaryModel(model, customer, checkoutForm, currentCurrency, initialShippingCostNoTax, initialShippingError);

        log.info("Checkout page model prepared for {}. Currency: {}", userIdentifier, currentCurrency);
        return "pokladna";
    }

    @PostMapping("/odeslat")
    @Transactional
    public String processCheckout(
            @Validated({DefaultValidationGroup.class}) // Validujeme standardní skupinu
            @ModelAttribute("checkoutForm") CheckoutFormDataDto checkoutForm,
            BindingResult bindingResult,
            @RequestParam(required = false) BigDecimal shippingCostNoTax,
            @RequestParam(required = false) BigDecimal shippingTax,
            Model model,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        // Získáme principal bezpečně
        Principal principal = (userDetails != null) ? userDetails::getUsername : null;
        boolean isGuest = (principal == null);
        // Získáme email pro logování (z DTO pro hosta, z principal pro přihlášeného)
        String userIdentifierForLog = isGuest
                ? (checkoutForm.getEmail() != null ? checkoutForm.getEmail() : "GUEST")
                : principal.getName();
        String orderCurrency = currencyService.getSelectedCurrency();
        // --- OPRAVA: Správná deklarace applyReverseCharge ZDE ---
        boolean applyReverseCharge = checkoutForm.isApplyReverseCharge(); // Získáme hodnotu z DTO
        log.info("Processing checkout submission for {}: {} in currency: {}. Apply RC: {}", (isGuest ? "guest" : "user"), userIdentifierForLog, orderCurrency, applyReverseCharge);

        Customer customer = null;
        Validator validator = customerService.getValidator(); // Získáme instanci validátoru

        // --- Krok 1: Základní validace a validace emailu ---
        if (!isGuest) {
            checkoutForm.setEmail(principal.getName());
            log.debug("Set email in DTO from principal for logged-in user: {}", principal.getName());
        } else {
            if (validator != null) {
                ValidationUtils.invokeValidator((org.springframework.validation.Validator) validator, checkoutForm, bindingResult, GuestValidation.class);
                log.debug("Guest validation invoked. Errors after guest validation: {}", bindingResult.hasErrors());
            } else {
                log.warn("Validator not available, skipping explicit guest group validation!");
            }
        }

        // --- Krok 2: Validace dodací adresy (pokud je odlišná) ---
        if (!checkoutForm.isUseInvoiceAddressAsDelivery()) {
            if (validator != null) {
                log.debug("Validating delivery address group...");
                ValidationUtils.invokeValidator((org.springframework.validation.Validator) validator, checkoutForm, bindingResult, DeliveryAddressValidation.class);
                log.debug("Delivery address validation invoked. Errors after delivery validation: {}", bindingResult.hasErrors());
            } else {
                log.warn("Validator not available, skipping delivery address group validation!");
            }
        }

        // --- Krok 3: Validace souhlasu s podmínkami ---
        if (!checkoutForm.isAgreeTerms()) {
            bindingResult.rejectValue("agreeTerms", "TermsNotAgreed", "Musíte souhlasit s obchodními podmínkami.");
        }

        // --- Zpracování, pokud nastaly chyby validace ---
        if (bindingResult.hasErrors()) {
            log.warn("Checkout form validation failed for {}: {}", userIdentifierForLog, bindingResult.getAllErrors());
            this.prepareModelForError(model, principal, checkoutForm, orderCurrency, bindingResult);
            return "pokladna"; // Vrátíme formulář s chybami
        }

        // --- Pokračování, pokud validace prošla ---
        try {
            // Krok 4: Získání nebo vytvoření Customer entity
            if (isGuest) {
                log.debug("Getting or creating guest customer.");
                customer = customerService.getOrCreateGuestFromCheckoutData(checkoutForm);
            } else {
                log.debug("Getting logged-in customer: {}", principal.getName());
                customer = customerService.getCustomerByEmail(principal.getName())
                        .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + principal.getName()));

                if (!addressMatches(customer, checkoutForm)) {
                    log.info("Customer {} address differs or useInvoice flag changed, updating from DTO.", customer.getId());
                    customerService.updateCustomerFromDto(customer, checkoutForm);
                    customer = customerService.saveCustomer(customer);
                    log.info("Updated address data saved for customer {}.", customer.getId());
                } else {
                    log.debug("Customer {} address matches DTO, no update needed.", customer.getId());
                }
            }

            // Krok 5: Kontrola prázdného košíku
            if (!sessionCart.hasItems()) {
                log.warn("User/guest {} submitted checkout form with an empty cart.", userIdentifierForLog);
                redirectAttributes.addFlashAttribute("checkoutError", "Váš košík je prázdný. Objednávku nelze dokončit.");
                return "redirect:/kosik";
            }

            // Krok 6: Validace kupónu a ceny dopravy
            BigDecimal subtotalForCoupon = sessionCart.calculateSubtotal(orderCurrency);
            Coupon validatedCoupon = validateAndGetCoupon(sessionCart, customer, subtotalForCoupon, orderCurrency);
            boolean freeShippingApplied = (validatedCoupon != null && validatedCoupon.isFreeShipping());
            BigDecimal finalShippingCostNoTax;
            BigDecimal finalShippingTax;

            if (!freeShippingApplied) {
                if (shippingCostNoTax == null || shippingTax == null || shippingCostNoTax.compareTo(BigDecimal.ZERO) < 0 || shippingTax.compareTo(BigDecimal.ZERO) < 0) {
                    log.error("Invalid or missing shipping costs submitted (ShippingRequired=true). CostNoTax: {}, Tax: {}", shippingCostNoTax, shippingTax);
                    bindingResult.reject("shipping.cost.missing", "Cena dopravy nebyla správně vypočtena nebo odeslána. Klikněte prosím na 'Spočítat dopravu'.");
                    throw new ValidationException("Missing or invalid shipping costs.");
                }
                finalShippingCostNoTax = shippingCostNoTax.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
                finalShippingTax = shippingTax.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
                log.debug("Using valid shipping costs from form: NoTax={}, Tax={}", finalShippingCostNoTax, finalShippingTax);
            } else {
                finalShippingCostNoTax = BigDecimal.ZERO;
                finalShippingTax = BigDecimal.ZERO;
                log.info("Overriding shipping cost to ZERO due to free shipping coupon {}.", validatedCoupon.getCode());
            }

            // Krok 7: Vytvoření požadavku na objednávku
            CreateOrderRequest orderRequest = new CreateOrderRequest();
            orderRequest.setCustomerId(customer.getId());
            orderRequest.setUseCustomerAddresses(true);
            orderRequest.setPaymentMethod(checkoutForm.getPaymentMethod());
            orderRequest.setCustomerNote(checkoutForm.getCustomerNote());
            orderRequest.setCouponCode(validatedCoupon != null ? validatedCoupon.getCode() : null);
            orderRequest.setCurrency(orderCurrency);
            orderRequest.setShippingCostNoTax(finalShippingCostNoTax);
            orderRequest.setShippingTax(finalShippingTax);
            List<CartItemDto> orderItemsDto = sessionCart.getItemsList().stream().map(this::mapCartItemToDto).collect(Collectors.toList());
            orderRequest.setItems(orderItemsDto);

            // Krok 8: Vytvoření objednávky
            log.info("Attempting to create order with request: {}, Apply RC Flag: {}", orderRequest, applyReverseCharge);
            // --- OPRAVA: Předání applyReverseCharge a pouze JEDNA deklarace createdOrder ---
            Order createdOrder = orderService.createOrder(orderRequest, applyReverseCharge);
            // --- KONEC OPRAVY ---

            // Ověření ceny (zůstává)
            // Poznámka: recalculateRoundedTotalForComparison by měla být pomocná metoda pro konzistentní výpočet
            BigDecimal roundedTotalToCompare = recalculateRoundedTotalForComparison(orderCurrency, finalShippingCostNoTax, finalShippingTax);
            if (createdOrder.getTotalPrice().compareTo(roundedTotalToCompare) != 0) {
                log.warn("Mismatch between calculated rounded price ({}) and saved order price ({}) for order {}",
                        roundedTotalToCompare, createdOrder.getTotalPrice(), createdOrder.getOrderCode());
            }

            log.info("Order {} successfully created with currency {} for {}: {}", createdOrder.getOrderCode(), orderCurrency, (isGuest ? "guest" : "user"), userIdentifierForLog);

            // Krok 9: Vyčištění košíku a přesměrování
            sessionCart.clearCart();
            log.info("Session cart cleared for {}", userIdentifierForLog);
            redirectAttributes.addFlashAttribute("orderSuccess", "Vaše objednávka č. " + createdOrder.getOrderCode() + " byla úspěšně přijata. Děkujeme!");

            return "redirect:/pokladna/dekujeme?orderCode=" + createdOrder.getOrderCode();

        } catch (ValidationException | CustomerService.EmailRegisteredException e) {
            log.warn("Validation or processing error during checkout for {}: {}", userIdentifierForLog, e.getMessage());
            if (e instanceof CustomerService.EmailRegisteredException) {
                bindingResult.rejectValue("email", "email.registered", e.getMessage());
            }
            this.prepareModelForError(model, principal, checkoutForm, orderCurrency, bindingResult);
            return "pokladna";
        } catch (IllegalStateException | EntityNotFoundException e) {
            log.error("Illegal state or entity not found during checkout for {}: {}", userIdentifierForLog, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("checkoutError", "Nastala chyba při zpracování objednávky: " + e.getMessage());
            return isGuest ? "redirect:/kosik" : "redirect:/muj-ucet/objednavky";
        } catch (Exception e) {
            log.error("Unexpected error during checkout for {}: {}", userIdentifierForLog, e.getMessage(), e);
            model.addAttribute("checkoutError", "Při zpracování objednávky nastala neočekávaná chyba.");
            model.addAttribute("checkoutErrorDetail", e.getMessage());
            this.prepareModelForError(model, principal, checkoutForm, orderCurrency, bindingResult);
            return "pokladna";
        }
    }
    private BigDecimal recalculateRoundedTotalForComparison(String currency, BigDecimal shippingCostNoTax, BigDecimal shippingTax) {
        BigDecimal subtotal = sessionCart.calculateSubtotal(currency);
        BigDecimal discount = sessionCart.calculateDiscountAmount(currency);
        BigDecimal totalItemVat = sessionCart.calculateTotalVatAmount(currency);
        BigDecimal subTotalAfterDiscount = subtotal.subtract(discount);
        BigDecimal finalTotalWithoutTax = subTotalAfterDiscount.add(Optional.ofNullable(shippingCostNoTax).orElse(BigDecimal.ZERO));
        BigDecimal finalTotalTax = totalItemVat.add(Optional.ofNullable(shippingTax).orElse(BigDecimal.ZERO));
        BigDecimal originalTotalPriceWithTax = finalTotalWithoutTax.add(finalTotalTax);
        return originalTotalPriceWithTax.setScale(0, RoundingMode.DOWN);
    }
    // --- NOVÁ METODA PRO VÝPOČET DOPRAVY ---
    @PostMapping("/calculate-shipping")
    @ResponseBody // Důležité - vracíme přímo JSON, ne název šablony
    @Transactional(readOnly = true) // Obvykle stačí read-only, pokud shippingService nemění data
    public ResponseEntity<ShippingCalculationResponseDto> calculateShippingAjax(@RequestBody @Valid ShippingAddressDto addressDto, Principal principal) {
        String userIdentifier = (principal != null) ? principal.getName() : "GUEST";
        String currentCurrency = currencyService.getSelectedCurrency();
        log.info("AJAX: Calculating shipping for user {} and currency {} with address: {}", userIdentifier, currentCurrency, addressDto);

        ShippingCalculationResponseDto responseDto = new ShippingCalculationResponseDto();
        responseDto.setCurrencySymbol(EURO_CURRENCY.equals(currentCurrency) ? "€" : "Kč");
        BigDecimal shippingCostNoTax = null;
        BigDecimal shippingTax = null;
        BigDecimal shippingTaxRate = BigDecimal.ZERO;
        Coupon validatedCoupon = null; // Budeme potřebovat pro zjištění dopravy zdarma
        Customer customer = null; // Zkusíme načíst zákazníka
        boolean freeShippingApplied = false;
        BigDecimal shippingDiscountAmount = BigDecimal.ZERO;

        try {
            // Získání zákazníka (pro validaci kuponu)
            if (principal != null) {
                customer = customerService.getCustomerByEmail(principal.getName()).orElse(null);
            }
            // Validace kuponu (potřebujeme znát subtotal pro validaci min. hodnoty)
            BigDecimal subtotalForCoupon = sessionCart.calculateSubtotal(currentCurrency);
            validatedCoupon = validateAndGetCoupon(sessionCart, customer, subtotalForCoupon, currentCurrency); // Použijeme stávající pomocnou metodu
            if (validatedCoupon != null && validatedCoupon.isFreeShipping()) {
                freeShippingApplied = true;
                log.debug("AJAX: Free shipping coupon '{}' is active.", validatedCoupon.getCode());
            }


            // Vytvoření dočasného Order objektu jen pro adresu
            Order tempOrder = createTemporaryOrderForShippingFromDto(addressDto, currentCurrency);
            if (tempOrder == null || !isShippingAddressAvailable(tempOrder)) {
                throw new IllegalArgumentException("Nebyla zadána kompletní dodací adresa.");
            }

            // Výpočet původní ceny dopravy (vždy, abychom věděli výši slevy)
            shippingCostNoTax = shippingService.calculateShippingCost(tempOrder, currentCurrency);
            if (shippingCostNoTax == null || shippingCostNoTax.compareTo(BigDecimal.ZERO) < 0) {
                log.warn("Shipping service returned null or negative cost for address: {}", addressDto);
                shippingCostNoTax = null; // Signalizuje chybu
                throw new ShippingCalculationException("Nepodařilo se vypočítat cenu dopravy pro zadanou adresu.");
            }
            shippingCostNoTax = shippingCostNoTax.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
            log.debug("AJAX: Calculated original shipping cost (no tax): {}", shippingCostNoTax);


            // Výpočet DPH a finální ceny dopravy
            shippingTaxRate = Optional.ofNullable(shippingService.getShippingTaxRate()).orElse(BigDecimal.ZERO);
            if (shippingCostNoTax.compareTo(BigDecimal.ZERO) > 0 && shippingTaxRate.compareTo(BigDecimal.ZERO) <= 0) {
                log.error("Shipping tax rate missing or zero during AJAX calculation!");
                shippingTaxRate = new BigDecimal("0.21"); // Fallback
            }
            shippingTax = shippingCostNoTax.multiply(shippingTaxRate).setScale(PRICE_SCALE, RoundingMode.HALF_UP);

            // Aplikace dopravy zdarma
            BigDecimal finalShippingCostNoTax;
            BigDecimal finalShippingTax;
            if(freeShippingApplied) {
                shippingDiscountAmount = shippingCostNoTax; // Sleva je celá původní cena
                finalShippingCostNoTax = BigDecimal.ZERO;
                finalShippingTax = BigDecimal.ZERO;
                log.info("AJAX: Applying free shipping. Original cost: {}, Discount: {}", shippingCostNoTax, shippingDiscountAmount);
            } else {
                shippingDiscountAmount = BigDecimal.ZERO; // Žádná sleva na dopravu
                finalShippingCostNoTax = shippingCostNoTax;
                finalShippingTax = shippingTax;
            }


            // --- Výpočet celkové ceny objednávky (podobně jako v prepareCheckoutSummaryModel) ---
            BigDecimal subtotal = sessionCart.calculateSubtotal(currentCurrency);
            BigDecimal couponDiscount = (validatedCoupon != null && !validatedCoupon.isFreeShippingOnly())
                    ? sessionCart.calculateDiscountAmount(currentCurrency) : BigDecimal.ZERO;
            BigDecimal totalItemVat = sessionCart.calculateTotalVatAmount(currentCurrency);
            Map<BigDecimal, BigDecimal> vatBreakdown = sessionCart.calculateVatBreakdown(currentCurrency); // Pro odpověď

            BigDecimal subTotalAfterDiscount = subtotal.subtract(couponDiscount);
            BigDecimal finalTotalWithoutTax = subTotalAfterDiscount.add(finalShippingCostNoTax);
            BigDecimal finalTotalTax = totalItemVat.add(finalShippingTax);
            BigDecimal originalTotalPriceWithTax = finalTotalWithoutTax.add(finalTotalTax);

            // Zaokrouhlení celkové ceny DOLŮ na celé číslo
            BigDecimal roundedTotalPrice = originalTotalPriceWithTax.setScale(0, RoundingMode.DOWN);
            // --- Konec výpočtu celkové ceny ---

            // Naplnění DTO pro odpověď
            responseDto.setShippingCostNoTax(finalShippingCostNoTax); // Finální cena dopravy bez DPH
            responseDto.setShippingTax(finalShippingTax); // Finální DPH z dopravy
            responseDto.setTotalPrice(originalTotalPriceWithTax.setScale(PRICE_SCALE, RoundingMode.HALF_UP)); // Vracíme PŘESNOU cenu před finálním zaokrouhlením
            responseDto.setVatBreakdown(new TreeMap<>(vatBreakdown)); // Rozpis DPH ze zboží
            responseDto.setTotalVatWithShipping(finalTotalTax.setScale(PRICE_SCALE, RoundingMode.HALF_UP)); // Celkové DPH
            responseDto.setOriginalShippingCostNoTax(shippingCostNoTax); // Původní cena dopravy
            responseDto.setOriginalShippingTax(shippingTax); // Původní DPH z dopravy
            responseDto.setShippingDiscountAmount(shippingDiscountAmount); // Sleva na dopravu

            log.info("AJAX: Shipping calculation successful for user {}. Response: {}", userIdentifier, responseDto);
            return ResponseEntity.ok(responseDto);

        } catch (ShippingCalculationException | IllegalArgumentException e) {
            log.warn("AJAX: Validation/Calculation error during shipping calculation for user {}: {}", userIdentifier, e.getMessage());
            responseDto.setErrorMessage(e.getMessage());
            // Vracíme 400 Bad Request pro chyby validace adresy nebo výpočtu
            return ResponseEntity.badRequest().body(responseDto);
        } catch (Exception e) {
            log.error("AJAX: Unexpected error during shipping calculation for user {}: {}", userIdentifier, e.getMessage(), e);
            responseDto.setErrorMessage("Došlo k neočekávané chybě při výpočtu ceny dopravy.");
            // Vracíme 500 Internal Server Error pro neočekávané chyby
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseDto);
        }
    }
    /**
     * Prepares model attributes needed for rendering the checkout page, especially after a form error.
     * Includes calculation of summary values based on current cart and potential shipping costs.
     *
     * @param model                    The model to populate.
     * @param customer                 The current customer (can be null for guest).
     * @param checkoutForm             The DTO with user's submitted data.
     * @param currentCurrency          The current currency code.
     * @param initialShippingCostNoTax The initially calculated shipping cost (or null if error/not calculated).
     * @param shippingError            Error message related to shipping calculation (or null).
     */
    private void prepareCheckoutSummaryModel(Model model, Customer customer, CheckoutFormDataDto checkoutForm, String currentCurrency, BigDecimal initialShippingCostNoTax, String shippingError) {
        log.debug("Preparing checkout summary model data for currency: {}", currentCurrency);
        String currencySymbol = EURO_CURRENCY.equals(currentCurrency) ? "€" : "Kč";

        // Cart calculations (stávající kód)
        BigDecimal subtotal = sessionCart.calculateSubtotal(currentCurrency);
        Coupon validatedCoupon = validateAndGetCoupon(sessionCart, customer, subtotal, currentCurrency);
        BigDecimal couponDiscount = (validatedCoupon != null && !validatedCoupon.isFreeShippingOnly())
                ? sessionCart.calculateDiscountAmount(currentCurrency) : BigDecimal.ZERO;
        BigDecimal totalItemVat = sessionCart.calculateTotalVatAmount(currentCurrency);
        Map<BigDecimal, BigDecimal> rawVatBreakdown = sessionCart.calculateVatBreakdown(currentCurrency);
        SortedMap<BigDecimal, BigDecimal> sortedVatBreakdown = new TreeMap<>(rawVatBreakdown);
        BigDecimal totalPriceWithoutTaxAfterDiscount = subtotal.subtract(couponDiscount).max(BigDecimal.ZERO).setScale(PRICE_SCALE, ROUNDING_MODE);

        // Shipping calculations (stávající kód - výpočet originalShippingCostNoTaxForSummary, finalShippingCostNoTax, finalShippingTax, shippingDiscountAmount)
        BigDecimal originalShippingCostNoTaxForSummary = BigDecimal.ZERO;
        BigDecimal originalShippingTaxForSummary = BigDecimal.ZERO;
        BigDecimal finalShippingCostNoTax = BigDecimal.ZERO;
        BigDecimal finalShippingTax = BigDecimal.ZERO;
        BigDecimal shippingDiscountAmount = BigDecimal.ZERO;
        boolean shippingValid = (initialShippingCostNoTax != null && initialShippingCostNoTax.compareTo(BigDecimal.ZERO) >= 0 && shippingError == null);
        BigDecimal shippingTaxRate = BigDecimal.ZERO; // Initialize rate

        if (shippingValid) {
            originalShippingCostNoTaxForSummary = initialShippingCostNoTax;
            try {
                shippingTaxRate = shippingService.getShippingTaxRate();
                if (shippingTaxRate == null || shippingTaxRate.compareTo(BigDecimal.ZERO) < 0) {
                    log.error("Invalid shipping tax rate received from service: {}. Using fallback 0.21", shippingTaxRate);
                    shippingTaxRate = new BigDecimal("0.21");
                }
            } catch (Exception e) {
                log.error("Failed to get shipping tax rate: {}", e.getMessage());
                shippingTaxRate = new BigDecimal("0.21"); // Fallback on error
            }

            if (originalShippingCostNoTaxForSummary.compareTo(BigDecimal.ZERO) > 0 && shippingTaxRate.compareTo(BigDecimal.ZERO) > 0) {
                originalShippingTaxForSummary = originalShippingCostNoTaxForSummary.multiply(shippingTaxRate).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
            }

            if (validatedCoupon != null && validatedCoupon.isFreeShipping()) {
                shippingDiscountAmount = originalShippingCostNoTaxForSummary;
            } else {
                finalShippingCostNoTax = originalShippingCostNoTaxForSummary;
                finalShippingTax = originalShippingTaxForSummary;
            }
        } else {
            shippingError = shippingError != null ? shippingError : "Doprava nebyla vypočtena.";
            originalShippingCostNoTaxForSummary = null; // Indicate error/unknown
        }

        // --- Logika zaokrouhlení (zůstává stejná) ---
        BigDecimal originalTotalPrice = null; // Cena PŘED zaokrouhlením
        BigDecimal roundedTotalPrice = null; // Cena PO zaokrouhlení dolů na celé číslo
        BigDecimal roundingDifference = BigDecimal.ZERO; // Rozdíl zaokrouhlení

        if (shippingValid) { // Počítáme jen pokud je doprava validní
            // Původní celková cena (subtotal po slevě + finální doprava + DPH ze zboží + finální DPH z dopravy)
            originalTotalPrice = totalPriceWithoutTaxAfterDiscount
                    .add(finalShippingCostNoTax)
                    .add(totalItemVat)
                    .add(finalShippingTax)
                    .setScale(PRICE_SCALE, RoundingMode.HALF_UP); // Přesná cena

            // Zaokrouhlení CELKOVÉ ceny DOLŮ na celé číslo
            roundedTotalPrice = originalTotalPrice.setScale(0, RoundingMode.DOWN);

            // Výpočet rozdílu zaokrouhlení
            roundingDifference = originalTotalPrice.subtract(roundedTotalPrice).setScale(PRICE_SCALE, RoundingMode.HALF_UP);

            log.debug("Rounding calculation: originalTotalPrice={}, roundedTotalPrice={}, roundingDifference={}",
                    originalTotalPrice, roundedTotalPrice, roundingDifference);
        } else {
            log.debug("Rounding calculation skipped because shipping is invalid.");
        }
        // --- KONEC LOGIKY ZAOKROUHLENÍ ---

        BigDecimal finalTotalVatWithShipping = totalItemVat.add(finalShippingTax).setScale(PRICE_SCALE, RoundingMode.HALF_UP);

        // Add attributes to model
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("validatedCoupon", validatedCoupon);
        model.addAttribute("couponDiscount", couponDiscount);
        model.addAttribute("totalPriceWithoutTaxAfterDiscount", totalPriceWithoutTaxAfterDiscount);
        model.addAttribute("totalVat", totalItemVat); // DPH jen ze zboží pro rozpis
        model.addAttribute("vatBreakdown", sortedVatBreakdown);
        model.addAttribute("originalShippingCostNoTax", originalShippingCostNoTaxForSummary);
        model.addAttribute("originalShippingTax", originalShippingTaxForSummary);
        model.addAttribute("shippingDiscountAmount", shippingDiscountAmount);
        model.addAttribute("shippingCostNoTax", finalShippingCostNoTax);
        model.addAttribute("shippingTax", finalShippingTax);
        model.addAttribute("totalVatWithShipping", finalTotalVatWithShipping); // Celkové DPH
        // --- Přidání nových atributů pro zaokrouhlení ---
        model.addAttribute("originalTotalPrice", originalTotalPrice); // Původní cena pro zobrazení
        model.addAttribute("roundingDifference", roundingDifference); // Rozdíl
        model.addAttribute("totalPrice", roundedTotalPrice); // Finální zaokrouhlená cena k úhradě

        // Přidáme chybu dopravy, pokud existuje
        if (shippingError != null) {
            model.addAttribute("shippingError", shippingError);
        }

        log.debug("Checkout summary model prepared: subtotal={}, itemDiscount={}, itemVat={}, origShipCost={}, shipDiscount={}, finalShipCost={}, finalShipTax={}, originalTotal={}, roundingDiff={}, finalRoundedTotal={}, totalVatWithShip={}, shippingError='{}'",
                subtotal, couponDiscount, totalItemVat, originalShippingCostNoTaxForSummary, shippingDiscountAmount, finalShippingCostNoTax, finalShippingTax, originalTotalPrice, roundingDifference, roundedTotalPrice, finalTotalVatWithShipping, shippingError);
    }

    /**
     * Helper method to prepare the model when validation errors occur, ensuring necessary data is available for re-rendering the page.
     * Keeps the calculated prices (original, rounded, difference) but indicates shipping error.
     */
    private void prepareModelForError(Model model, Principal principal, CheckoutFormDataDto checkoutFormWithError, String currency, BindingResult bindingResult) {
        log.debug("Preparing model for rendering checkout page after form error. Currency: {}", currency);
        Customer customer = null;
        if (principal != null) {
            try {
                customer = customerService.getCustomerByEmail(principal.getName()).orElse(null);
            } catch (Exception e) {
                log.error("Error fetching customer {} during prepareModelForError", principal.getName(), e);
            }
        }

        model.addAttribute("checkoutForm", checkoutFormWithError); // Keep submitted data with errors
        model.addAttribute("customer", customer); // Add customer if logged in
        model.addAttribute("cart", sessionCart);
        model.addAttribute("allowedPaymentMethods", ALLOWED_PAYMENT_METHODS);
        model.addAttribute("currency", currency);
        model.addAttribute("currencySymbol", EURO_CURRENCY.equals(currency) ? "€" : "Kč");
        model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "checkoutForm", bindingResult); // Explicitly add bindingResult

        // Re-evaluate summary based on current cart state, but force shipping error state
        String shippingError = "Zkontrolujte chyby ve formuláři a znovu vypočítejte dopravu.";
        // Call prepareCheckoutSummaryModel to get all calculations (including potentially invalid shipping)
        // Pass null for shipping cost to ensure shippingError is set correctly inside prepareCheckoutSummaryModel
        prepareCheckoutSummaryModel(model, customer, checkoutFormWithError, currency, null, shippingError);

        // *** ZDE JE KLÍČOVÁ ZMĚNA: NEODSTRAŇUJEME CENY Z MODELU ***
        // Hodnoty originalTotalPrice, roundingDifference a totalPrice zůstávají v modelu
        // tak, jak byly spočítány v prepareCheckoutSummaryModel (i když mohou být null, pokud shipping nebyl validní).
        // Template a JS se postarají o správné zobrazení a deaktivaci tlačítka na základě shippingError.

        // Ensure shipping error message is present in the model
        model.addAttribute("shippingError", shippingError);

        // Add a general error message if not already present from BindingResult
        if (!bindingResult.hasErrors() && !model.containsAttribute("checkoutError")) {
            model.addAttribute("checkoutError", "Prosím, opravte chyby ve formuláři.");
        } else if (bindingResult.hasErrors() && !model.containsAttribute("checkoutError")) {
            model.addAttribute("checkoutError", "Formulář obsahuje chyby, prosím zkontrolujte zadané údaje.");
        }
        log.debug("Model prepared for error view. Shipping error state indicated.");
    }

    private boolean hasSufficientAddress(Customer customer) {
        if (customer == null) return false;
        boolean hasInvoice = StringUtils.hasText(customer.getInvoiceStreet()) && StringUtils.hasText(customer.getInvoiceCity()) && StringUtils.hasText(customer.getInvoiceZipCode()) && StringUtils.hasText(customer.getInvoiceCountry());
        if (customer.isUseInvoiceAddressAsDelivery()) {
            return hasInvoice;
        } else {
            return hasInvoice && StringUtils.hasText(customer.getDeliveryStreet()) && StringUtils.hasText(customer.getDeliveryCity()) && StringUtils.hasText(customer.getDeliveryZipCode()) && StringUtils.hasText(customer.getDeliveryCountry());
        }
    }

    // --- POMOCNÉ METODY (PŘIDANÉ) ---

    private boolean hasSufficientAddressInDto(CheckoutFormDataDto dto) {
        if (dto == null) return false;
        boolean hasInvoice = StringUtils.hasText(dto.getInvoiceStreet()) && StringUtils.hasText(dto.getInvoiceCity()) && StringUtils.hasText(dto.getInvoiceZipCode()) && StringUtils.hasText(dto.getInvoiceCountry());
        if (dto.isUseInvoiceAddressAsDelivery()) {
            return hasInvoice;
        } else {
            boolean hasDelivery = StringUtils.hasText(dto.getDeliveryStreet()) && StringUtils.hasText(dto.getDeliveryCity()) && StringUtils.hasText(dto.getDeliveryZipCode()) && StringUtils.hasText(dto.getDeliveryCountry());
            return hasInvoice && hasDelivery;
        }
    }


    // --- OPRAVENÁ Metoda prepareModelForError ---

    private Order createTemporaryOrderForShipping(Customer customer, String currency) {
        if (customer == null) return null;
        Order tempOrder = new Order();
        tempOrder.setCustomer(customer);
        tempOrder.setCurrency(currency);
        if (customer.isUseInvoiceAddressAsDelivery()) {
            tempOrder.setDeliveryStreet(customer.getInvoiceStreet());
            tempOrder.setDeliveryCity(customer.getInvoiceCity());
            tempOrder.setDeliveryZipCode(customer.getInvoiceZipCode());
            tempOrder.setDeliveryCountry(customer.getInvoiceCountry());
        } else {
            tempOrder.setDeliveryStreet(customer.getDeliveryStreet());
            tempOrder.setDeliveryCity(customer.getDeliveryCity());
            tempOrder.setDeliveryZipCode(customer.getDeliveryZipCode());
            tempOrder.setDeliveryCountry(customer.getDeliveryCountry());
        }
        return tempOrder;
    }

// Zbytek třídy CheckoutController...

    private Order createTemporaryOrderForShippingFromDto(ShippingAddressDto dto, String currency) {
        if (dto == null) return null;
        Order tempOrder = new Order();
        tempOrder.setCurrency(currency);
        tempOrder.setDeliveryStreet(dto.getStreet());
        tempOrder.setDeliveryCity(dto.getCity());
        tempOrder.setDeliveryZipCode(dto.getZipCode());
        tempOrder.setDeliveryCountry(dto.getCountry());
        return tempOrder;
    }

    private Order createTemporaryOrderForShippingFromDto(CheckoutFormDataDto dto, String currency) {
        if (dto == null) return null;
        Order tempOrder = new Order();
        tempOrder.setCurrency(currency);
        if (dto.isUseInvoiceAddressAsDelivery()) {
            tempOrder.setDeliveryStreet(dto.getInvoiceStreet());
            tempOrder.setDeliveryCity(dto.getInvoiceCity());
            tempOrder.setDeliveryZipCode(dto.getInvoiceZipCode());
            tempOrder.setDeliveryCountry(dto.getInvoiceCountry());
        } else {
            tempOrder.setDeliveryStreet(dto.getDeliveryStreet());
            tempOrder.setDeliveryCity(dto.getDeliveryCity());
            tempOrder.setDeliveryZipCode(dto.getDeliveryZipCode());
            tempOrder.setDeliveryCountry(dto.getDeliveryCountry());
        }
        return tempOrder;
    }

    private Coupon validateAndGetCoupon(Cart cart, Customer customer, BigDecimal subtotal, String currency) {
        String couponCode = cart.getAppliedCouponCode();
        if (!StringUtils.hasText(couponCode)) {
            if (cart.getAppliedCoupon() != null) cart.removeCoupon();
            return null;
        }
        Optional<Coupon> couponOpt = couponService.findByCode(couponCode);
        if (couponOpt.isEmpty()) {
            cart.removeCoupon();
            return null;
        }
        Coupon coupon = couponOpt.get();
        boolean isValid = couponService.isCouponGenerallyValid(coupon) && couponService.checkMinimumOrderValue(coupon, subtotal, currency);
        if (isValid && customer != null && !customer.isGuest()) {
            isValid = couponService.checkCustomerUsageLimit(customer, coupon);
        }
        if (isValid) {
            if (!Objects.equals(coupon, cart.getAppliedCoupon())) {
                cart.applyCoupon(coupon, couponCode);
            }
            return coupon;
        } else {
            cart.removeCoupon();
            return null;
        }
    }

    // V třídě CheckoutController.java
    private CartItemDto mapCartItemToDto(CartItem cartItem) {
        CartItemDto dto = new CartItemDto();
        dto.setProductId(cartItem.getProductId());
        dto.setQuantity(cartItem.getQuantity());
        dto.setCustom(cartItem.isCustom());
        dto.setSelectedDesignId(cartItem.getSelectedDesignId());
        dto.setSelectedGlazeId(cartItem.getSelectedGlazeId());
        dto.setSelectedRoofColorId(cartItem.getSelectedRoofColorId());
        dto.setSelectedTaxRateId(cartItem.getSelectedTaxRateId()); // Přidáno mapování TaxRateId
        dto.setCustomDimensions(cartItem.getCustomDimensions());
        dto.setCustomRoofOverstep(cartItem.getCustomRoofOverstep());
        dto.setCustomHasDivider(cartItem.isCustomHasDivider());
        dto.setCustomHasGutter(cartItem.isCustomHasGutter());
        dto.setCustomHasGardenShed(cartItem.isCustomHasGardenShed());

        // *** OPRAVA ZDE: Mapování List<AddonDto> na List<Long> ***
        if (cartItem.getSelectedAddons() != null) {
            List<Long> addonIds = cartItem.getSelectedAddons().stream()
                    .filter(Objects::nonNull)
                    .map(AddonDto::getAddonId) // Získáme ID z AddonDto
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            dto.setSelectedAddonIds(addonIds); // Nastavíme seznam IDček
        } else {
            dto.setSelectedAddonIds(Collections.emptyList());
        }
        // *** KONEC OPRAVY ***

        return dto;
    }

    private boolean isShippingAddressAvailable(Order tempOrder) {
        return tempOrder != null && StringUtils.hasText(tempOrder.getDeliveryStreet()) && StringUtils.hasText(tempOrder.getDeliveryCity()) && StringUtils.hasText(tempOrder.getDeliveryZipCode()) && StringUtils.hasText(tempOrder.getDeliveryCountry());
    }

    // Helper to check if address data in DTO differs from Customer entity
    private boolean addressMatches(Customer customer, CheckoutFormDataDto dto) {
        if (customer == null || dto == null) return false;
        if (!Objects.equals(customer.getFirstName(), dto.getFirstName()) || !Objects.equals(customer.getLastName(), dto.getLastName()) || !Objects.equals(customer.getPhone(), dto.getPhone())) {
            return false;
        }
        if (!Objects.equals(customer.getInvoiceCompanyName(), dto.getInvoiceCompanyName()) || !Objects.equals(customer.getInvoiceStreet(), dto.getInvoiceStreet()) || !Objects.equals(customer.getInvoiceCity(), dto.getInvoiceCity()) || !Objects.equals(customer.getInvoiceZipCode(), dto.getInvoiceZipCode()) || !Objects.equals(customer.getInvoiceCountry(), dto.getInvoiceCountry()) || !Objects.equals(customer.getInvoiceTaxId(), dto.getInvoiceTaxId()) || !Objects.equals(customer.getInvoiceVatId(), dto.getInvoiceVatId())) {
            return false;
        }
        if (customer.isUseInvoiceAddressAsDelivery() != dto.isUseInvoiceAddressAsDelivery()) {
            return false;
        }
        if (!dto.isUseInvoiceAddressAsDelivery()) {
            return Objects.equals(customer.getDeliveryCompanyName(), dto.getDeliveryCompanyName()) && Objects.equals(customer.getDeliveryFirstName(), dto.getDeliveryFirstName()) && Objects.equals(customer.getDeliveryLastName(), dto.getDeliveryLastName()) && Objects.equals(customer.getDeliveryStreet(), dto.getDeliveryStreet()) && Objects.equals(customer.getDeliveryCity(), dto.getDeliveryCity()) && Objects.equals(customer.getDeliveryZipCode(), dto.getDeliveryZipCode()) && Objects.equals(customer.getDeliveryCountry(), dto.getDeliveryCountry()) && Objects.equals(customer.getDeliveryPhone(), dto.getDeliveryPhone());
        }
        return true;
    }

    // Helper interface for default validation group
    interface DefaultValidationGroup {
    }

    // Custom exception for validation flow control
    static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }

    // Výjimka pro chybu výpočtu dopravy (zůstává)
    public static class ShippingCalculationException extends RuntimeException {
        public ShippingCalculationException(String message) {
            super(message);
        }

        public ShippingCalculationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    @GetMapping("/dekujeme") // Nebo jiná cesta, např. /objednavka/potvrzeni
    public String showOrderConfirmationPage(@RequestParam String orderCode, Model model, RedirectAttributes redirectAttributes) {
        log.info("Displaying order confirmation page for order code: {}", orderCode);
        try {
            Order order = orderService.findOrderByCode(orderCode) // Použijte metodu pro nalezení podle kódu
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Objednávka s kódem '" + orderCode + "' nenalezena."));

            model.addAttribute("order", order);
            return "objednavka-potvrzeni"; // Název nové šablony

        } catch (ResponseStatusException e) {
            log.warn("Order confirmation page requested for non-existent order code: {}", orderCode);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/"; // Přesměrování na domovskou stránku při chybě
        } catch (Exception e) {
            log.error("Error loading order confirmation page for code {}: {}", orderCode, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Při zobrazování potvrzení objednávky nastala chyba.");
            return "redirect:/";
        }
    }

} // Konec třídy CheckoutController