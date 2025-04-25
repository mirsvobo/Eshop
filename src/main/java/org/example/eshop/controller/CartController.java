package org.example.eshop.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.example.eshop.admin.service.AddonsService;
import org.example.eshop.config.PriceConstants;
import org.example.eshop.dto.AddonDto;
import org.example.eshop.dto.CartItemDto;
import org.example.eshop.dto.CustomPriceRequestDto;
import org.example.eshop.dto.CustomPriceResponseDto;
import org.example.eshop.model.*;
import org.example.eshop.repository.DesignRepository;
import org.example.eshop.repository.GlazeRepository;
import org.example.eshop.repository.RoofColorRepository;
import org.example.eshop.repository.TaxRateRepository;
import org.example.eshop.service.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/kosik")
public class CartController implements PriceConstants {

    private static final Logger log = LoggerFactory.getLogger(CartController.class);

    @Autowired
    private Cart sessionCart; // Session-scoped cart bean
    @Autowired
    private ProductService productService;
    @Autowired
    private AddonsService addonsService; // Použito pro načtení addonů podle ID
    @Autowired
    private CouponService couponService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private DesignRepository designRepository;
    @Autowired
    private GlazeRepository glazeRepository;
    @Autowired
    private RoofColorRepository roofColorRepository;
    
    @Autowired
    private CurrencyService currencyService;
    @Autowired
    private TaxRateRepository taxRateRepository; // Použito pro načtení TaxRate

    // --------------------------------------------------------------------
    // View Cart Method (bez změny)
    // --------------------------------------------------------------------
    @GetMapping
    public String viewCart(Model model, Principal principal) {
        log.info("--- viewCart START --- Cart instance hash: {}", this.sessionCart.hashCode());
        if (log.isDebugEnabled()) {
            log.debug("Cart items before display: {}", this.sessionCart.getItems());
        }
        log.debug("Displaying cart page. Cart has {} items.", this.sessionCart.getItemCount());
        model.addAttribute("cart", this.sessionCart);

        String currentCurrency = currencyService.getSelectedCurrency();
        String currencySymbol = EURO_CURRENCY.equals(currentCurrency) ? "€" : "Kč";
        log.debug("Current currency set to: {}", currentCurrency);
        model.addAttribute("currentCurrency", currentCurrency);
        model.addAttribute("currencySymbol", currencySymbol);

        if (this.sessionCart.hasItems()) {
            BigDecimal subtotal = this.sessionCart.calculateSubtotal(currentCurrency);
            model.addAttribute("subtotal", subtotal);
            log.debug("Cart subtotal ({}): {}", currentCurrency, subtotal);
            BigDecimal couponDiscountAmount = this.sessionCart.calculateDiscountAmount(currentCurrency);
            Coupon validatedCoupon = null;
            if (this.sessionCart.getAppliedCoupon() != null) {
                Customer customer = (principal != null) ? customerService.getCustomerByEmail(principal.getName()).orElse(null) : null;
                Coupon currentCoupon = this.sessionCart.getAppliedCoupon();
                boolean stillValid = couponService.isCouponGenerallyValid(currentCoupon) && couponService.checkMinimumOrderValue(currentCoupon, subtotal, currentCurrency) && couponService.checkCustomerUsageLimit(customer, currentCoupon);
                if (stillValid) {
                    validatedCoupon = currentCoupon;
                    if (validatedCoupon.isFreeShipping()) {
                        model.addAttribute("freeShippingApplied", true);
                        log.debug("Free shipping applied by coupon '{}'", validatedCoupon.getCode());
                    }
                } else {
                    log.warn("Previously applied coupon '{}' is no longer valid. Removing.", currentCoupon.getCode());
                    this.sessionCart.removeCoupon();
                    model.addAttribute("couponMessage", "Dříve použitý kupón '" + currentCoupon.getCode() + "' již není platný.");
                }
            }
            model.addAttribute("validatedCoupon", validatedCoupon);
            model.addAttribute("couponDiscountAmount", couponDiscountAmount);
            log.debug("Validated coupon: {}, Discount amount ({}): {}", (validatedCoupon != null ? validatedCoupon.getCode() : "None"), currentCurrency, couponDiscountAmount);
            BigDecimal totalVat = this.sessionCart.calculateTotalVatAmount(currentCurrency);
            Map<BigDecimal, BigDecimal> vatBreakdown = this.sessionCart.calculateVatBreakdown(currentCurrency);
            model.addAttribute("totalVat", totalVat);
            model.addAttribute("vatBreakdown", vatBreakdown);
            log.debug("Total VAT ({}): {}", currentCurrency, totalVat);
            log.debug("VAT Breakdown ({}): {}", currentCurrency, vatBreakdown);
            BigDecimal totalPriceBeforeShipping = this.sessionCart.calculateTotalPriceBeforeShipping(currentCurrency);
            model.addAttribute("totalPriceBeforeShipping", totalPriceBeforeShipping);
            log.debug("Total Price Before Shipping ({}): {}", currentCurrency, totalPriceBeforeShipping);
            BigDecimal totalPriceWithoutTaxAfterDiscount = this.sessionCart.calculateTotalPriceWithoutTaxAfterDiscount(currentCurrency);
            model.addAttribute("totalPriceWithoutTaxAfterDiscount", totalPriceWithoutTaxAfterDiscount);
            log.debug("Total Price Without Tax After Discount ({}): {}", currentCurrency, totalPriceWithoutTaxAfterDiscount);
        } else {
            model.addAttribute("subtotal", BigDecimal.ZERO);
            model.addAttribute("couponDiscountAmount", BigDecimal.ZERO);
            model.addAttribute("validatedCoupon", null);
            model.addAttribute("totalVat", BigDecimal.ZERO);
            model.addAttribute("vatBreakdown", Collections.emptyMap());
            model.addAttribute("totalPriceBeforeShipping", BigDecimal.ZERO);
            model.addAttribute("totalPriceWithoutTaxAfterDiscount", BigDecimal.ZERO);
        }
        log.info("--- viewCart END --- Model attributes set for cart.");
        return "kosik";
    }

    // ====================================================================
    // === Add to Cart Method - UPRAVENO PRO TEST S @RequestParam ===
    // ====================================================================
    @PostMapping("/pridat")
    public String addToCart(@ModelAttribute("cartItemDto") @Valid CartItemDto cartItemDto,
                            // ----- ZAČÁTEK ZMĚNY -----
                            @RequestParam(name = "isCustom", defaultValue = "false") boolean isCustomParam, // Explicitní parametr
                            // ----- KONEC ZMĚNY -----
                            BindingResult bindingResult,
                            RedirectAttributes redirectAttributes,
                            HttpServletRequest request) { // Přidán HttpServletRequest pro logování

        // Logování surových parametrů (pro budoucí debug)
        java.util.Map<String, String[]> paramMap = request.getParameterMap();
        log.debug("Raw request parameters: {}", paramMap.entrySet().stream()
                .map(e -> e.getKey() + "=" + Arrays.toString(e.getValue()))
                .collect(Collectors.joining(", ")));

        // ----- ZAČÁTEK ZMĚNY -----
        // Ručně nastavíme hodnotu 'isCustom' v DTO podle parametru
        cartItemDto.setCustom(isCustomParam);
        // ----- KONEC ZMĚNY -----

        log.info("--- addToCart START --- Cart hash: {}", this.sessionCart.hashCode());
        // Tento log teď ukáže hodnotu *po* manuálním nastavení
        log.debug("Received CartItemDto (after manual 'isCustom' set): {}", cartItemDto);

        // ---- KROK 1: Standardní validace pomocí @Valid ----
        if (bindingResult.hasErrors()) {
            String errors = bindingResult.getAllErrors().stream()
                    .map(e -> ((e instanceof FieldError fe) ? fe.getField() + ": " : "") + e.getDefaultMessage())
                    .collect(Collectors.joining("; "));
            log.warn("Binding/Validation errors adding to cart (from @Valid): {}", errors);

            String productSlugForRedirect = getProductSlugForRedirect(cartItemDto.getProductId());
            prepareRedirectWithError(redirectAttributes, cartItemDto, bindingResult, "Chyba validace: Zkontrolujte zadané údaje.");
            return productSlugForRedirect != null ? "redirect:/produkt/" + productSlugForRedirect : "redirect:/produkty";
        }

        // ---- KROK 2: Načtení produktu a manuální validace ----
        String productSlugForRedirect = null;
        Product product = null;
        TaxRate selectedTaxRate = null;
        Design selectedDesign = null;
        Glaze selectedGlaze = null;
        RoofColor selectedRoofColor = null;

        try {
            // Načtení produktu
            product = productService.getProductById(cartItemDto.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produkt nenalezen: " + cartItemDto.getProductId()));
            productSlugForRedirect = product.getSlug();

            // Kontrola aktivity produktu
            if (!product.isActive()) {
                log.warn("Attempted to add inactive product ID {} to cart.", product.getId());
                prepareRedirectWithError(redirectAttributes, cartItemDto, bindingResult, "Produkt '" + product.getName() + "' není aktivní a nelze jej přidat do košíku.");
                return "redirect:/produkt/" + productSlugForRedirect;
            }

            // Tento log nyní ukáže správnou hodnotu díky manuálnímu nastavení výše
            log.info("Processing Cart Item: isCustom = {}", cartItemDto.isCustom());

            // Načtení a validace TaxRate (povinné pro všechny)
            if (cartItemDto.getSelectedTaxRateId() == null) {
                bindingResult.rejectValue("selectedTaxRateId", "NotNull", "Daňová sazba musí být vybrána.");
                throw new IllegalArgumentException("Chybí ID vybrané daňové sazby."); // Vyhodíme výjimku, protože je to chyba
            }
            selectedTaxRate = taxRateRepository.findById(cartItemDto.getSelectedTaxRateId())
                    .orElseThrow(() -> new EntityNotFoundException("Daňová sazba nenalezena: " + cartItemDto.getSelectedTaxRateId()));
            Set<TaxRate> availableRates = product.getAvailableTaxRates();
            if (availableRates == null || availableRates.isEmpty() || !availableRates.contains(selectedTaxRate)) {
                log.error("Selected TaxRate ID {} ('{}') is not available for Product ID {}", selectedTaxRate.getId(), selectedTaxRate.getName(), product.getId());
                prepareRedirectWithError(redirectAttributes, cartItemDto, bindingResult, "Vybraná daňová sazba '" + selectedTaxRate.getName() + "' není pro produkt '" + product.getName() + "' povolena.");
                return "redirect:/produkt/" + productSlugForRedirect;
            }

            // Manuální validace ID atributů a rozměrů POUZE pokud je custom
            if (cartItemDto.isCustom()) { // Tento if teď bude fungovat správně
                boolean manualValidationError = false;
                // Kontrola ID Designu
                if (cartItemDto.getSelectedDesignId() == null) {
                    bindingResult.rejectValue("selectedDesignId", "NotNull", "Design musí být vybrán.");
                    manualValidationError = true;
                } else {
                    selectedDesign = designRepository.findById(cartItemDto.getSelectedDesignId())
                            .orElseThrow(() -> new EntityNotFoundException("Design nenalezen: ID " + cartItemDto.getSelectedDesignId()));
                }
                // Kontrola ID Glaze
                if (cartItemDto.getSelectedGlazeId() == null) {
                    bindingResult.rejectValue("selectedGlazeId", "NotNull", "Lazura musí být vybrána.");
                    manualValidationError = true;
                } else {
                    selectedGlaze = glazeRepository.findById(cartItemDto.getSelectedGlazeId())
                            .orElseThrow(() -> new EntityNotFoundException("Lazura nenalezena: ID " + cartItemDto.getSelectedGlazeId()));
                }
                // Kontrola ID RoofColor
                if (cartItemDto.getSelectedRoofColorId() == null) {
                    bindingResult.rejectValue("selectedRoofColorId", "NotNull", "Barva střechy musí být vybrána.");
                    manualValidationError = true;
                } else {
                    selectedRoofColor = roofColorRepository.findById(cartItemDto.getSelectedRoofColorId())
                            .orElseThrow(() -> new EntityNotFoundException("Barva střechy nenalezena: ID " + cartItemDto.getSelectedRoofColorId()));
                }
                // Kontrola rozměrů
                if (cartItemDto.getCustomDimensions() == null
                        || cartItemDto.getCustomDimensions().get("length") == null
                        || cartItemDto.getCustomDimensions().get("width") == null
                        || cartItemDto.getCustomDimensions().get("height") == null) {
                    bindingResult.reject("customDimensions.required", "Rozměry (délka, šířka, výška) jsou pro produkt na míru povinné.");
                    manualValidationError = true;
                }

                if (manualValidationError) {
                    log.warn("Manual validation failed for custom product attributes or dimensions.");
                    prepareRedirectWithError(redirectAttributes, cartItemDto, bindingResult, "Prosím, vyberte všechny povinné atributy a zadejte rozměry pro produkt na míru.");
                    return "redirect:/produkt/" + productSlugForRedirect;
                }
            } else {
                // Pro standardní produkt také musíme načíst atributy
                if (cartItemDto.getSelectedDesignId() != null) {
                    selectedDesign = designRepository.findById(cartItemDto.getSelectedDesignId())
                            .orElseThrow(() -> new EntityNotFoundException("Design nenalezen (Standard): ID " + cartItemDto.getSelectedDesignId()));
                } else {
                    bindingResult.rejectValue("selectedDesignId", "NotNull", "Design musí být vybrán (Standard).");
                    throw new IllegalArgumentException("Chybí ID designu pro standardní produkt.");
                }
                if (cartItemDto.getSelectedGlazeId() != null) {
                    selectedGlaze = glazeRepository.findById(cartItemDto.getSelectedGlazeId())
                            .orElseThrow(() -> new EntityNotFoundException("Lazura nenalezena (Standard): ID " + cartItemDto.getSelectedGlazeId()));
                } else {
                    bindingResult.rejectValue("selectedGlazeId", "NotNull", "Lazura musí být vybrána (Standard).");
                    throw new IllegalArgumentException("Chybí ID lazury pro standardní produkt.");
                }
                if (cartItemDto.getSelectedRoofColorId() != null) {
                    selectedRoofColor = roofColorRepository.findById(cartItemDto.getSelectedRoofColorId())
                            .orElseThrow(() -> new EntityNotFoundException("Barva střechy nenalezena (Standard): ID " + cartItemDto.getSelectedRoofColorId()));
                } else {
                    bindingResult.rejectValue("selectedRoofColorId", "NotNull", "Barva střechy musí být vybrána (Standard).");
                    throw new IllegalArgumentException("Chybí ID barvy střechy pro standardní produkt.");
                }
            }

            // ---- KROK 3: Pokračování se zpracováním položky ----

            // Vytvoření CartItem
            CartItem cartItem = new CartItem();
            cartItem.setProductId(product.getId());
            cartItem.setProductName(product.getName());
            cartItem.setProductSlug(product.getSlug());
            cartItem.setImageUrl(!product.getImagesOrdered().isEmpty() ? product.getImagesOrdered().getFirst().getUrl() : "/images/placeholder.png");
            cartItem.setQuantity(cartItemDto.getQuantity());
            cartItem.setCustom(cartItemDto.isCustom()); // <-- Použij hodnotu z DTO (nyní opravenou)

            // Nastavení načtených atributů
            cartItem.setSelectedDesignId(selectedDesign.getId());
            cartItem.setSelectedDesignName(selectedDesign.getName());
            cartItem.setSelectedGlazeId(selectedGlaze.getId());
            cartItem.setSelectedGlazeName(selectedGlaze.getName());
            cartItem.setSelectedRoofColorId(selectedRoofColor.getId());
            cartItem.setSelectedRoofColorName(selectedRoofColor.getName());
            cartItem.setSelectedTaxRateId(selectedTaxRate.getId());
            cartItem.setSelectedTaxRateValue(selectedTaxRate.getRate());
            cartItem.setSelectedIsReverseCharge(selectedTaxRate.isReverseCharge()); // Store original RC flag
            log.debug("Applied Tax Rate {}% (RC from TaxRate entity: {})", selectedTaxRate.getRate().multiply(BigDecimal.valueOf(100)).setScale(2), selectedTaxRate.isReverseCharge());

            // Výpočet ceny a nastavení dalších atributů
            List<AddonDto> processedAddons = new ArrayList<>();
            BigDecimal unitPriceCZK;
            BigDecimal unitPriceEUR;
            String currency = currencyService.getSelectedCurrency();

            if (cartItem.isCustom()) {
                // Tento blok by se měl nyní spustit pro custom produkt
                log.info("Processing CUSTOM product path for product ID: {}", product.getId());
                if (product.getConfigurator() == null) {
                    throw new IllegalStateException("Produkt '" + product.getName() + "' je konfigurovatelný, ale chybí data konfigurátoru.");
                }
                Map<String, BigDecimal> dimensionsMap = cartItemDto.getCustomDimensions();
                // Validace rozměrů už proběhla výše

                BigDecimal lengthCm = dimensionsMap.get("length");
                BigDecimal widthCm = dimensionsMap.get("width");
                BigDecimal heightCm = dimensionsMap.get("height");

                // Vytvoření requestu pro detailní výpočet ceny
                CustomPriceRequestDto priceRequest = getCustomPriceRequestDto(cartItemDto, product, dimensionsMap);
                // Zavolání servisní metody pro detailní cenu
                CustomPriceResponseDto priceResponse = productService.calculateDetailedCustomPrice(priceRequest);

                if (StringUtils.hasText(priceResponse.getErrorMessage())) {
                    log.error("Chyba při výpočtu detailní ceny pro produkt ID {}: {}", product.getId(), priceResponse.getErrorMessage());
                    prepareRedirectWithError(redirectAttributes, cartItemDto, bindingResult, "Chyba výpočtu ceny: " + priceResponse.getErrorMessage());
                    return "redirect:/produkt/" + product.getSlug();
                }

                // Získání celkové ceny z response
                unitPriceCZK = Optional.ofNullable(priceResponse.getTotalPriceCZK()).orElse(BigDecimal.ZERO);
                unitPriceEUR = Optional.ofNullable(priceResponse.getTotalPriceEUR()).orElse(BigDecimal.ZERO);

                // Nastavení rozměrů a zpracování addonů
                cartItem.setLength(lengthCm);
                cartItem.setWidth(widthCm);
                cartItem.setHeight(heightCm);
                cartItem.setCustomDimensions(dimensionsMap); // Uložíme mapu pro CartItem ID a variantInfo
                List<Long> selectedAddonIds = cartItemDto.getSelectedAddonIds();
                if (selectedAddonIds != null && !selectedAddonIds.isEmpty()) {
                    Set<Long> addonIdSet = new HashSet<>(selectedAddonIds);
                    Map<Long, Addon> validDbAddons = addonsService.findAddonsByIds(addonIdSet).stream()
                            .filter(Addon::isActive)
                            .collect(Collectors.toMap(Addon::getId, a -> a));
                    Set<Long> allowedAddonIds = Optional.ofNullable(product.getAvailableAddons()).orElse(Collections.emptySet()).stream()
                            .map(Addon::getId)
                            .collect(Collectors.toSet());

                    for (Long addonId : selectedAddonIds) {
                        Addon dbAddon = validDbAddons.get(addonId);
                        if (dbAddon != null && allowedAddonIds.contains(addonId)) {
                            AddonDto addonDto = new AddonDto();
                            addonDto.setAddonId(dbAddon.getId());
                            addonDto.setAddonName(dbAddon.getName());
                            addonDto.setQuantity(1); // Předpokládáme množství 1 pro addony přidávané zde
                            processedAddons.add(addonDto);
                        } else {
                            log.warn("Requested addon ID {} is invalid/inactive/not allowed for product {}. Skipping.", addonId, product.getId());
                        }
                    }
                } else {
                    log.debug("[processCartItem] No addons selected for custom item.");
                }
                cartItem.setSelectedAddons(processedAddons);
                // Nastavení dalších custom příznaků z DTO
                cartItem.setCustomRoofOverstep(cartItemDto.getCustomRoofOverstep());
                cartItem.setCustomHasDivider(cartItemDto.isCustomHasDivider());
                cartItem.setCustomHasGutter(cartItemDto.isCustomHasGutter());
                cartItem.setCustomHasGardenShed(cartItemDto.isCustomHasGardenShed());

            } else { // Standardní produkt
                log.error("Chyba: Produkt ID {} je custom, ale zpracovává se jako standardní!", product.getId()); // Měli bychom sem logicky dojít jen pokud selže oprava
                // Zde by byla logika pro standardní produkt, pokud by to nebyl custom
                // V tomto případě by ale měla metoda skončit chybou výše, nebo bude cena 0
                BigDecimal baseUnitPriceCZK = Optional.ofNullable(product.getBasePriceCZK()).orElse(BigDecimal.ZERO);
                BigDecimal baseUnitPriceEUR = Optional.ofNullable(product.getBasePriceEUR()).orElse(BigDecimal.ZERO);
                BigDecimal attributeSurchargeCZK = BigDecimal.ZERO
                        .add(Optional.ofNullable(selectedDesign.getPriceSurchargeCZK()).orElse(BigDecimal.ZERO))
                        .add(Optional.ofNullable(selectedGlaze.getPriceSurchargeCZK()).orElse(BigDecimal.ZERO))
                        .add(Optional.ofNullable(selectedRoofColor.getPriceSurchargeCZK()).orElse(BigDecimal.ZERO));
                BigDecimal attributeSurchargeEUR = BigDecimal.ZERO
                        .add(Optional.ofNullable(selectedDesign.getPriceSurchargeEUR()).orElse(BigDecimal.ZERO))
                        .add(Optional.ofNullable(selectedGlaze.getPriceSurchargeEUR()).orElse(BigDecimal.ZERO))
                        .add(Optional.ofNullable(selectedRoofColor.getPriceSurchargeEUR()).orElse(BigDecimal.ZERO));

                unitPriceCZK = baseUnitPriceCZK.add(attributeSurchargeCZK);
                unitPriceEUR = baseUnitPriceEUR.add(attributeSurchargeEUR);

                cartItem.setLength(product.getLength());
                cartItem.setWidth(product.getWidth());
                cartItem.setHeight(product.getHeight());
                cartItem.setSelectedAddons(Collections.emptyList());
                cartItem.setCustomDimensions(null);
                cartItem.setCustomRoofOverstep(null);
                cartItem.setCustomHasDivider(false);
                cartItem.setCustomHasGutter(false);
                cartItem.setCustomHasGardenShed(false);
            }

            // Nastavení finálních jednotkových cen (již zahrnují bázi, atributy, addony)
            cartItem.setUnitPriceCZK(unitPriceCZK.setScale(PRICE_SCALE, ROUNDING_MODE));
            cartItem.setUnitPriceEUR(unitPriceEUR.setScale(PRICE_SCALE, ROUNDING_MODE));
            log.debug("Final unit prices set for CartItem: CZK={}, EUR={}", cartItem.getUnitPriceCZK(), cartItem.getUnitPriceEUR());

            // Sestavení variantInfo
            String variantInfo = buildVariantInfoString(cartItem, selectedDesign, selectedGlaze, selectedRoofColor);
            cartItem.setVariantInfo(variantInfo);
            log.debug("Built variant info: {}", variantInfo);

            // Generování ID položky košíku
            String generatedCartItemId = CartItem.generateCartItemId(
                    cartItem.getProductId(), cartItem.isCustom(),
                    cartItem.getSelectedDesignId(), cartItem.getSelectedDesignName(),
                    cartItem.getSelectedGlazeId(), cartItem.getSelectedGlazeName(),
                    cartItem.getSelectedRoofColorId(), cartItem.getSelectedRoofColorName(),
                    cartItem.getCustomDimensions(), cartItem.getSelectedTaxRateId(),
                    cartItemDto.getCustomRoofOverstep(), cartItemDto.isCustomHasDivider(),
                    cartItemDto.isCustomHasGutter(), cartItemDto.isCustomHasGardenShed(),
                    cartItem.getSelectedAddons()
            );
            cartItem.setCartItemId(generatedCartItemId);
            log.debug("Generated Cart Item ID: {}", generatedCartItemId);

            // Přidání do košíku
            this.sessionCart.addItem(cartItem);
            log.info("--- addToCart END --- Item added/updated. Cart hash: {}, Items count: {}", this.sessionCart.hashCode(), this.sessionCart.getItemCount());
            redirectAttributes.addFlashAttribute("cartSuccess", "Produkt '" + product.getName() + "' byl přidán do košíku.");
            return "redirect:/kosik"; // Přesměrování do košíku po úspěchu

        } catch (ResponseStatusException | EntityNotFoundException e) {
            log.warn("Error adding item to cart (Product ID: {}): {}", (cartItemDto != null ? cartItemDto.getProductId() : "null"), e.getMessage());
            prepareRedirectWithError(redirectAttributes, cartItemDto, bindingResult, "Chyba: " + e.getMessage());
            // Pokud slug nebyl načten před chybou, přesměrujeme obecně
            return productSlugForRedirect != null ? "redirect:/produkt/" + productSlugForRedirect : "redirect:/produkty";
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Configuration or state error adding item (Product ID: {}): {}", (cartItemDto != null ? cartItemDto.getProductId() : "null"), e.getMessage());
            prepareRedirectWithError(redirectAttributes, cartItemDto, bindingResult, "Nepodařilo se přidat produkt do košíku: " + e.getMessage());
            return productSlugForRedirect != null ? "redirect:/produkt/" + productSlugForRedirect : "redirect:/produkty";
        } catch (Exception e) {
            log.error("Unexpected error adding item to cart (Product ID: {}): {}", (cartItemDto != null ? cartItemDto.getProductId() : "null"), e.getMessage(), e);
            prepareRedirectWithError(redirectAttributes, cartItemDto, bindingResult, "Neočekávaná chyba při přidávání produktu do košíku.");
            return productSlugForRedirect != null ? "redirect:/produkt/" + productSlugForRedirect : "redirect:/produkty";
        }
    }

    // Metody updateQuantity, removeItem, applyCoupon, removeCoupon (beze změny)
    @PostMapping("/aktualizovat")
    public String updateQuantity(@RequestParam String cartItemId, @RequestParam @Min(0) int quantity, RedirectAttributes redirectAttributes) {
        log.info("--- updateQuantity START --- Cart hash: {}", this.sessionCart.hashCode());
        log.info("Request to update quantity for cart item ID: {} to {}", cartItemId, quantity);
        try {
            this.sessionCart.updateQuantity(cartItemId, quantity);
            redirectAttributes.addFlashAttribute("cartSuccess", quantity > 0 ? "Množství bylo aktualizováno." : "Položka byla odebrána z košíku.");
            log.info("--- updateQuantity END --- Success. Cart hash: {}", this.sessionCart.hashCode());
        } catch (Exception e) {
            log.error("--- updateQuantity ERROR --- Cart hash: {}", this.sessionCart.hashCode(), e);
            redirectAttributes.addFlashAttribute("cartError", "Chyba při aktualizaci množství.");
        }
        return "redirect:/kosik";
    }

    @PostMapping("/odebrat")
    public String removeItem(@RequestParam String cartItemId, RedirectAttributes redirectAttributes) {
        log.info("--- removeItem START --- Cart instance hash: {}", this.sessionCart.hashCode());
        log.info("Request to remove cart item ID: {}", cartItemId);
        try {
            this.sessionCart.removeItem(cartItemId);
            redirectAttributes.addFlashAttribute("cartSuccess", "Položka byla odebrána z košíku.");
            log.info("--- removeItem END --- Success. Cart instance hash: {}", this.sessionCart.hashCode());
        } catch (Exception e) {
            log.error("--- removeItem ERROR --- Cart instance hash: {}", this.sessionCart.hashCode(), e);
            redirectAttributes.addFlashAttribute("cartError", "Chyba při odebírání položky.");
        }
        return "redirect:/kosik";
    }

    @PostMapping("/pouzit-kupon")
    public String applyCoupon(@RequestParam String couponCode, RedirectAttributes ra, Principal principal) {
        log.info("--- applyCoupon START --- Cart instance hash: {}", this.sessionCart.hashCode());
        if (!StringUtils.hasText(couponCode)) {
            ra.addFlashAttribute("couponMessage", "Zadejte prosím kód kupónu.");
            return "redirect:/kosik";
        }
        String trimmedCode = couponCode.trim();
        log.info("Attempting to apply coupon code: {}", trimmedCode);
        Optional<Coupon> couponOpt = couponService.findByCode(trimmedCode);
        if (couponOpt.isEmpty()) {
            this.sessionCart.setAttemptedCouponCode(trimmedCode);
            ra.addFlashAttribute("couponMessage", "Kód kupónu '" + trimmedCode + "' neexistuje.");
            log.warn("Coupon code '{}' not found.", trimmedCode);
            return "redirect:/kosik";
        }
        Coupon coupon = couponOpt.get();
        if (!couponService.isCouponGenerallyValid(coupon)) {
            this.sessionCart.setAttemptedCouponCode(trimmedCode);
            ra.addFlashAttribute("couponMessage", "Kupón '" + trimmedCode + "' není aktivní nebo vypršela jeho platnost.");
            log.warn("Coupon code '{}' is not generally valid.", trimmedCode);
            return "redirect:/kosik";
        }
        String currency = currencyService.getSelectedCurrency();
        BigDecimal subtotal = this.sessionCart.calculateSubtotal(currency);
        if (!couponService.checkMinimumOrderValue(coupon, subtotal, currency)) {
            this.sessionCart.setAttemptedCouponCode(trimmedCode);
            String minValStr = couponService.getMinimumValueString(coupon, currency);
            ra.addFlashAttribute("couponMessage", "Pro použití kupónu '" + trimmedCode + "' je nutná minimální hodnota objednávky " + minValStr + ".");
            log.warn("Coupon '{}' minimum order value not met.", trimmedCode);
            return "redirect:/kosik";
        }
        Customer customer = (principal != null) ? customerService.getCustomerByEmail(principal.getName()).orElse(null) : null;
        if (customer != null && !customer.isGuest() && !couponService.checkCustomerUsageLimit(customer, coupon)) {
            this.sessionCart.setAttemptedCouponCode(trimmedCode);
            ra.addFlashAttribute("couponMessage", "Kupón '" + trimmedCode + "' jste již použil(a) maximální počet krát.");
            log.warn("Customer '{}' reached usage limit for coupon '{}'.", principal.getName(), trimmedCode);
            return "redirect:/kosik";
        }
        this.sessionCart.applyCoupon(coupon, trimmedCode);
        ra.addFlashAttribute("couponSuccess", "Kupón '" + trimmedCode + "' byl úspěšně použit.");
        log.info("--- applyCoupon END --- Coupon '{}' successfully applied. Cart hash: {}", trimmedCode, this.sessionCart.hashCode());
        return "redirect:/kosik";
    }

    @PostMapping("/odebrat-kupon")
    public String removeCoupon(RedirectAttributes ra) {
        log.info("--- removeCoupon START --- Cart instance hash: {}", this.sessionCart.hashCode());
        this.sessionCart.removeCoupon();
        ra.addFlashAttribute("cartSuccess", "Slevový kupón byl odebrán.");
        log.info("--- removeCoupon END --- Success. Cart instance hash: {}", this.sessionCart.hashCode());
        return "redirect:/kosik";
    }
    private String getProductSlugForRedirect(Long productId) {
        if (productId == null) return null;
        try {
            // Předpokládá, že productService.getProductById() existuje a vrací Optional<Product>
            return productService.getProductById(productId).map(Product::getSlug).orElse(null);
        } catch (Exception e) {
            log.error("Error fetching product slug for redirect, ID: {}", productId, e);
            return null; // V případě chyby vrátí null
        }
    }
    /**
     * Pomocná metoda pro nastavení atributů pro přesměrování v případě chyby.
     * Zajišťuje, že formulář bude znovu zobrazen s chybovými hláškami a původními daty.
     * @param redirectAttributes Objekt pro přidání flash atributů.
     * @param dtoWithError DTO objekt s původními daty z formuláře.
     * @param bindingResult BindingResult obsahující validační chyby.
     * @param errorMessage Obecná chybová zpráva k zobrazení.
     */
    private void prepareRedirectWithError(RedirectAttributes redirectAttributes, CartItemDto dtoWithError, BindingResult bindingResult, String errorMessage) {
        redirectAttributes.addFlashAttribute("cartError", errorMessage);
        // Klíč pro BindingResult musí odpovídat tomu, jak jej Thymeleaf očekává
        redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.cartItemDto", bindingResult);
        // Přidáme i samotné DTO, aby Thymeleaf mohl předvyplnit formulář
        redirectAttributes.addFlashAttribute("cartItemDto", dtoWithError);
        log.debug("Prepared redirect attributes with error message and validation results.");
    }
    /**
     * Pomocná metoda pro sestavení popisného stringu varianty produktu.
     * @param item Položka košíku.
     * @param design Vybraný design.
     * @param glaze Vybraná lazura.
     * @param roofColor Vybraná barva střechy.
     * @return Sestavený string popisující variantu.
     */
    private String buildVariantInfoString(CartItem item, Design design, Glaze glaze, RoofColor roofColor) {
        if (item == null) return "";
        StringBuilder variantSb = new StringBuilder();

        if (item.isCustom()) {
            variantSb.append("Na míru");
            Map<String, BigDecimal> dims = item.getCustomDimensions();
            if (dims != null && !dims.isEmpty() && dims.containsKey("length") && dims.containsKey("width") && dims.containsKey("height")) {
                variantSb.append("|Rozměry (DxHxV): ").append(dims.get("length") != null ? dims.get("length").stripTrailingZeros().toPlainString() : "?").append("x").append(dims.get("width") != null ? dims.get("width").stripTrailingZeros().toPlainString() : "?").append("x").append(dims.get("height") != null ? dims.get("height").stripTrailingZeros().toPlainString() : "?").append(" cm");
            }
        }
        // Přidání atributů (pokud jsou vybrány)
        if (design != null && StringUtils.hasText(design.getName())) {
            if (!variantSb.isEmpty() && variantSb.charAt(variantSb.length() - 1) != '|') variantSb.append("|");
            variantSb.append("Design: ").append(design.getName());
        }
        if (glaze != null && StringUtils.hasText(glaze.getName())) {
            if (!variantSb.isEmpty() && variantSb.charAt(variantSb.length() - 1) != '|') variantSb.append("|");
            variantSb.append("Lazura: ").append(glaze.getName());
        }
        if (roofColor != null && StringUtils.hasText(roofColor.getName())) {
            if (!variantSb.isEmpty() && variantSb.charAt(variantSb.length() - 1) != '|') variantSb.append("|");
            variantSb.append("Střecha: ").append(roofColor.getName());
        }
        // Přidání custom příznaků a addonů (pouze pro custom)
        if (item.isCustom()) {
            if (StringUtils.hasText(item.getCustomRoofOverstep())) {
                if (!variantSb.isEmpty() && variantSb.charAt(variantSb.length() - 1) != '|') variantSb.append("|");
                variantSb.append("Přesah: ").append(item.getCustomRoofOverstep());
            }
            if (item.isCustomHasDivider()) {
                if (!variantSb.isEmpty() && variantSb.charAt(variantSb.length() - 1) != '|') variantSb.append("|");
                variantSb.append("Příčka");
            }
            if (item.isCustomHasGutter()) {
                if (!variantSb.isEmpty() && variantSb.charAt(variantSb.length() - 1) != '|') variantSb.append("|");
                variantSb.append("Okap");
            }
            if (item.isCustomHasGardenShed()) {
                if (!variantSb.isEmpty() && variantSb.charAt(variantSb.length() - 1) != '|') variantSb.append("|");
                variantSb.append("Zahr. domek");
            }
            if (!CollectionUtils.isEmpty(item.getSelectedAddons())) {
                String addonNames = item.getSelectedAddons().stream()
                        .map(AddonDto::getAddonName)
                        .filter(StringUtils::hasText)
                        .collect(Collectors.joining(", "));
                if (StringUtils.hasText(addonNames)) {
                    if (!variantSb.isEmpty() && variantSb.charAt(variantSb.length() - 1) != '|') variantSb.append("|");
                    variantSb.append("Doplňky: ").append(addonNames);
                }
            }
        }

        String result = variantSb.toString().trim();
        // Nahradí vícenásobné oddělovače jedním a odstraní případný na konci/začátku
        result = result.replaceAll("\\|+", "|").replaceAll("^\\||\\|$", "").trim();
        // Nahradí první pipe za mezeru pro lepší čitelnost, pokud existuje
        result = result.replaceFirst("\\|", " | ");
        return result;
    }
    @NotNull
    private static CustomPriceRequestDto getCustomPriceRequestDto(CartItemDto cartItemDto, Product product, Map<String, BigDecimal> dimensionsMap) {
        CustomPriceRequestDto priceRequest = new CustomPriceRequestDto();
        priceRequest.setProductId(product.getId());
        priceRequest.setCustomDimensions(dimensionsMap);
        priceRequest.setSelectedDesignId(cartItemDto.getSelectedDesignId());
        priceRequest.setSelectedGlazeId(cartItemDto.getSelectedGlazeId());
        priceRequest.setSelectedRoofColorId(cartItemDto.getSelectedRoofColorId());
        priceRequest.setSelectedAddonIds(cartItemDto.getSelectedAddonIds() != null ? cartItemDto.getSelectedAddonIds() : Collections.emptyList());
        return priceRequest;
    }
} // Konec třídy CartController