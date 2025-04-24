package org.example.eshop.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.example.eshop.admin.service.AddonsService;
import org.example.eshop.model.*;
import org.example.eshop.repository.DesignRepository;
import org.example.eshop.repository.GlazeRepository;
import org.example.eshop.repository.RoofColorRepository;
import org.example.eshop.repository.TaxRateRepository;
import org.example.eshop.service.*;
import org.example.eshop.config.PriceConstants;
import org.example.eshop.dto.AddonDto;
import org.example.eshop.dto.CartItemDto;
import org.example.eshop.dto.CustomPriceRequestDto;
import org.example.eshop.dto.CustomPriceResponseDto;
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
import java.math.RoundingMode; // Import pro RoundingMode
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/kosik")
public class CartController implements PriceConstants {

    private static final Logger log = LoggerFactory.getLogger(CartController.class);

    @Autowired private Cart sessionCart; // Session-scoped cart bean
    @Autowired private ProductService productService;
    @Autowired private AddonsService addonsService; // Použito pro načtení addonů podle ID
    @Autowired private CouponService couponService;
    @Autowired private CustomerService customerService;
    @Autowired private DesignRepository designRepository;
    @Autowired private GlazeRepository glazeRepository;
    @Autowired private RoofColorRepository roofColorRepository;
    @Autowired private TaxRateService taxRateService;
    @Autowired private CurrencyService currencyService;
    @Autowired private TaxRateRepository taxRateRepository; // Použito pro načtení TaxRate

    // --------------------------------------------------------------------
    // View Cart Method (bez změny)
    // --------------------------------------------------------------------
    @GetMapping
    public String viewCart(Model model, Principal principal) {
        log.info("--- viewCart START --- Cart instance hash: {}", this.sessionCart.hashCode());
        if (log.isDebugEnabled()) { log.debug("Cart items before display: {}", this.sessionCart.getItems()); }
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
                if (stillValid) { validatedCoupon = currentCoupon; if (validatedCoupon.isFreeShipping()) { model.addAttribute("freeShippingApplied", true); log.debug("Free shipping applied by coupon '{}'", validatedCoupon.getCode()); } }
                else { log.warn("Previously applied coupon '{}' is no longer valid. Removing.", currentCoupon.getCode()); this.sessionCart.removeCoupon(); model.addAttribute("couponMessage", "Dříve použitý kupón '" + currentCoupon.getCode() + "' již není platný."); }
            }
            model.addAttribute("validatedCoupon", validatedCoupon); model.addAttribute("couponDiscountAmount", couponDiscountAmount);
            log.debug("Validated coupon: {}, Discount amount ({}): {}", (validatedCoupon != null ? validatedCoupon.getCode() : "None"), currentCurrency, couponDiscountAmount);
            BigDecimal totalVat = this.sessionCart.calculateTotalVatAmount(currentCurrency); Map<BigDecimal, BigDecimal> vatBreakdown = this.sessionCart.calculateVatBreakdown(currentCurrency);
            model.addAttribute("totalVat", totalVat); model.addAttribute("vatBreakdown", vatBreakdown);
            log.debug("Total VAT ({}): {}", currentCurrency, totalVat); log.debug("VAT Breakdown ({}): {}", currentCurrency, vatBreakdown);
            BigDecimal totalPriceBeforeShipping = this.sessionCart.calculateTotalPriceBeforeShipping(currentCurrency); model.addAttribute("totalPriceBeforeShipping", totalPriceBeforeShipping);
            log.debug("Total Price Before Shipping ({}): {}", currentCurrency, totalPriceBeforeShipping);
            BigDecimal totalPriceWithoutTaxAfterDiscount = this.sessionCart.calculateTotalPriceWithoutTaxAfterDiscount(currentCurrency); model.addAttribute("totalPriceWithoutTaxAfterDiscount", totalPriceWithoutTaxAfterDiscount);
            log.debug("Total Price Without Tax After Discount ({}): {}", currentCurrency, totalPriceWithoutTaxAfterDiscount);
        } else {
            model.addAttribute("subtotal", BigDecimal.ZERO); model.addAttribute("couponDiscountAmount", BigDecimal.ZERO); model.addAttribute("validatedCoupon", null);
            model.addAttribute("totalVat", BigDecimal.ZERO); model.addAttribute("vatBreakdown", Collections.emptyMap()); model.addAttribute("totalPriceBeforeShipping", BigDecimal.ZERO);
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
                            BindingResult bindingResult,
                            // ==== ZMĚNA: Přidán @RequestParam pro isCustom ====
                            @RequestParam(name = "isCustom", defaultValue = "false") boolean isCustomParam,
                            // =================================================
                            RedirectAttributes redirectAttributes) {

        log.info("--- addToCart START --- Cart hash: {}", this.sessionCart.hashCode());
        String productSlugForRedirect = null;
        if (cartItemDto.getProductId() != null) {
            productSlugForRedirect = productService.getProductById(cartItemDto.getProductId()).map(Product::getSlug).orElse(null);
        }

        // Logování přijatých dat
        log.debug("Received CartItemDto raw data: {}", cartItemDto);
        log.info("Received CartItemDto (before processing): productId={}, quantity={}, isCustom(DTO)={}, designId={}, glazeId={}, roofColorId={}, taxRateId={}, customDimensions={}, addonIds={}",
                cartItemDto.getProductId(), cartItemDto.getQuantity(), cartItemDto.isCustom(), // Z DTO
                cartItemDto.getSelectedDesignId(), cartItemDto.getSelectedGlazeId(), cartItemDto.getSelectedRoofColorId(),
                cartItemDto.getSelectedTaxRateId(), cartItemDto.getCustomDimensions(), cartItemDto.getSelectedAddonIds());
        // ==== ZMĚNA: Logování hodnoty z @RequestParam ====
        log.info("Received isCustom DIRECTLY via @RequestParam: {}", isCustomParam);
        // =============================================

        if (bindingResult.hasErrors()) {
            // ... (zpracování validačních chyb DTO - beze změny) ...
            String errors = bindingResult.getAllErrors().stream().map(e -> ((e instanceof FieldError fe) ? fe.getField() + ": " : "") + e.getDefaultMessage()).collect(Collectors.joining("; "));
            log.warn("Validation errors adding to cart (from @Valid): {}", errors);
            redirectAttributes.addFlashAttribute("cartError", "Chyba validace: Zkontrolujte zadané údaje.");
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.cartItemDto", bindingResult);
            redirectAttributes.addFlashAttribute("cartItemDto", cartItemDto);
            return productSlugForRedirect != null ? "redirect:/produkt/" + productSlugForRedirect : "redirect:/produkty";
        }

        try {
            // Načtení entit (beze změny)
            Product product = productService.getProductById(cartItemDto.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produkt nenalezen: " + cartItemDto.getProductId()));
            if (productSlugForRedirect == null) productSlugForRedirect = product.getSlug();
            if (!product.isActive()) { /* ... */ return "redirect:/produkt/" + productSlugForRedirect; }
            TaxRate selectedTaxRate = taxRateRepository.findById(cartItemDto.getSelectedTaxRateId())
                    .orElseThrow(() -> new EntityNotFoundException("Daňová sazba nenalezena: " + cartItemDto.getSelectedTaxRateId()));
            Set<TaxRate> availableRates = product.getAvailableTaxRates();
            if (availableRates == null || !availableRates.contains(selectedTaxRate)) { /* ... */ return productSlugForRedirect != null ? "redirect:/produkt/" + productSlugForRedirect : "redirect:/produkty"; }
            Design selectedDesign = designRepository.findById(cartItemDto.getSelectedDesignId())
                    .orElseThrow(() -> new EntityNotFoundException("Design nenalezen: " + cartItemDto.getSelectedDesignId()));
            Glaze selectedGlaze = glazeRepository.findById(cartItemDto.getSelectedGlazeId())
                    .orElseThrow(() -> new EntityNotFoundException("Lazura nenalezena: " + cartItemDto.getSelectedGlazeId()));
            RoofColor selectedRoofColor = roofColorRepository.findById(cartItemDto.getSelectedRoofColorId())
                    .orElseThrow(() -> new EntityNotFoundException("Barva střechy nenalezena: " + cartItemDto.getSelectedRoofColorId()));

            // Vytvoření CartItem
            CartItem cartItem = new CartItem();
            cartItem.setProductId(product.getId());
            cartItem.setProductName(product.getName());
            cartItem.setProductSlug(product.getSlug());
            cartItem.setImageUrl(!product.getImagesOrdered().isEmpty() ? product.getImagesOrdered().getFirst().getUrl() : "/images/placeholder.png");
            cartItem.setQuantity(cartItemDto.getQuantity());

            // ==== ZMĚNA: Nastavení isCustom z @RequestParam ====
            cartItem.setCustom(isCustomParam);
            log.info("Processing Cart Item: isCustom set FROM PARAM = {}", cartItem.isCustom());
            // ===============================================

            // Uložení ostatních dat (beze změny)
            cartItem.setSelectedDesignId(selectedDesign.getId()); cartItem.setSelectedDesignName(selectedDesign.getName());
            cartItem.setSelectedGlazeId(selectedGlaze.getId()); cartItem.setSelectedGlazeName(selectedGlaze.getName());
            cartItem.setSelectedRoofColorId(selectedRoofColor.getId()); cartItem.setSelectedRoofColorName(selectedRoofColor.getName());
            cartItem.setSelectedTaxRateId(selectedTaxRate.getId()); cartItem.setSelectedTaxRateValue(selectedTaxRate.getRate());
            cartItem.setSelectedIsReverseCharge(selectedTaxRate.isReverseCharge());
            log.debug("Applied Tax Rate {}% (RC: {})", selectedTaxRate.getRate().multiply(BigDecimal.valueOf(100)).setScale(2), selectedTaxRate.isReverseCharge());

            // Výpočet ceny a nastavení atributů
            List<AddonDto> processedAddons = new ArrayList<>();
            BigDecimal unitPriceCZK;
            BigDecimal unitPriceEUR;

            // ==== ZMĚNA: Podmínka používá cartItem.isCustom() (které bylo nastaveno z isCustomParam) ====
            if (cartItem.isCustom()) {
                log.info("Processing CUSTOM product path for product ID: {}", product.getId());
                // ... (zpracování custom produktu - kód zůstává stejný, používá cartItemDto pro addony a rozměry) ...
                if (product.getConfigurator() == null) { throw new IllegalStateException("Produkt '" + product.getName() + "' je konfigurovatelný, ale chybí data konfigurátoru."); }
                Map<String, BigDecimal> dimensionsMap = cartItemDto.getCustomDimensions();
                if (dimensionsMap == null || dimensionsMap.get("length") == null || dimensionsMap.get("width") == null || dimensionsMap.get("height") == null) { throw new IllegalArgumentException("Chybí kompletní rozměry v mapě customDimensions."); }
                BigDecimal lengthCm = dimensionsMap.get("length"); BigDecimal widthCm = dimensionsMap.get("width"); BigDecimal heightCm = dimensionsMap.get("height");
                CustomPriceRequestDto priceRequest = new CustomPriceRequestDto();
                priceRequest.setProductId(product.getId()); priceRequest.setCustomDimensions(dimensionsMap);
                priceRequest.setSelectedDesignId(cartItemDto.getSelectedDesignId()); priceRequest.setSelectedGlazeId(cartItemDto.getSelectedGlazeId()); priceRequest.setSelectedRoofColorId(cartItemDto.getSelectedRoofColorId());
                priceRequest.setSelectedAddonIds(cartItemDto.getSelectedAddonIds() != null ? cartItemDto.getSelectedAddonIds() : Collections.emptyList());
                CustomPriceResponseDto priceResponse = productService.calculateDetailedCustomPrice(priceRequest);
                if (StringUtils.hasText(priceResponse.getErrorMessage())) { /* ... chyba ... */ return "redirect:/produkt/" + product.getSlug(); }
                unitPriceCZK = priceResponse.getTotalPriceCZK(); unitPriceEUR = priceResponse.getTotalPriceEUR();
                cartItem.setLength(lengthCm); cartItem.setWidth(widthCm); cartItem.setHeight(heightCm); cartItem.setCustomDimensions(dimensionsMap);
                List<Long> selectedAddonIds = cartItemDto.getSelectedAddonIds();
                if (selectedAddonIds != null && !selectedAddonIds.isEmpty()) {
                    Set<Long> addonIdSet = new HashSet<>(selectedAddonIds);
                    Map<Long, Addon> validDbAddons = addonsService.findAddonsByIds(addonIdSet).stream().filter(Addon::isActive).collect(Collectors.toMap(Addon::getId, a -> a));
                    Set<Long> allowedAddonIds = Optional.ofNullable(product.getAvailableAddons()).orElse(Collections.emptySet()).stream().map(Addon::getId).collect(Collectors.toSet());
                    for (Long addonId : selectedAddonIds) {
                        Addon dbAddon = validDbAddons.get(addonId);
                        if (dbAddon != null && allowedAddonIds.contains(addonId)) {
                            AddonDto addonDto = new AddonDto(); addonDto.setAddonId(dbAddon.getId()); addonDto.setAddonName(dbAddon.getName()); addonDto.setQuantity(1);
                            processedAddons.add(addonDto);
                        } else { log.warn("Requested addon ID {} is invalid/inactive/not allowed for product {}. Skipping.", addonId, product.getId()); }
                    }
                }
                cartItem.setSelectedAddons(processedAddons);
            } else {
                log.info("Processing STANDARD product path for product ID: {}", product.getId());
                // ... (zpracování standard produktu - kód zůstává stejný) ...
                BigDecimal baseUnitPriceCZK = product.getBasePriceCZK() != null ? product.getBasePriceCZK() : BigDecimal.ZERO;
                BigDecimal baseUnitPriceEUR = product.getBasePriceEUR() != null ? product.getBasePriceEUR() : BigDecimal.ZERO;
                BigDecimal attributeSurchargeCZK = BigDecimal.ZERO; BigDecimal attributeSurchargeEUR = BigDecimal.ZERO;
                if (selectedDesign.getPriceSurchargeCZK() != null) attributeSurchargeCZK = attributeSurchargeCZK.add(selectedDesign.getPriceSurchargeCZK());
                if (selectedGlaze.getPriceSurchargeCZK() != null) attributeSurchargeCZK = attributeSurchargeCZK.add(selectedGlaze.getPriceSurchargeCZK());
                if (selectedRoofColor.getPriceSurchargeCZK() != null) attributeSurchargeCZK = attributeSurchargeCZK.add(selectedRoofColor.getPriceSurchargeCZK());
                if (selectedDesign.getPriceSurchargeEUR() != null) attributeSurchargeEUR = attributeSurchargeEUR.add(selectedDesign.getPriceSurchargeEUR());
                if (selectedGlaze.getPriceSurchargeEUR() != null) attributeSurchargeEUR = attributeSurchargeEUR.add(selectedGlaze.getPriceSurchargeEUR());
                if (selectedRoofColor.getPriceSurchargeEUR() != null) attributeSurchargeEUR = attributeSurchargeEUR.add(selectedRoofColor.getPriceSurchargeEUR());
                unitPriceCZK = baseUnitPriceCZK.add(attributeSurchargeCZK); unitPriceEUR = baseUnitPriceEUR.add(attributeSurchargeEUR);
                cartItem.setLength(product.getLength()); cartItem.setWidth(product.getWidth()); cartItem.setHeight(product.getHeight());
                cartItem.setSelectedAddons(Collections.emptyList()); cartItem.setCustomDimensions(null);
            }

            // Nastavení finálních cen (beze změny)
            cartItem.setUnitPriceCZK(unitPriceCZK.setScale(PRICE_SCALE, ROUNDING_MODE));
            cartItem.setUnitPriceEUR(unitPriceEUR.setScale(PRICE_SCALE, ROUNDING_MODE));
            log.debug("Final unit prices set for CartItem: CZK={}, EUR={}", cartItem.getUnitPriceCZK(), cartItem.getUnitPriceEUR());

            // Sestavení variantInfo (beze změny)
            String variantInfo = buildVariantInfoString(cartItem, selectedDesign, selectedGlaze, selectedRoofColor);
            cartItem.setVariantInfo(variantInfo);
            log.debug("Built variant info: {}", variantInfo);

            // Generování ID položky (beze změny)
            String generatedCartItemId = CartItem.generateCartItemId(
                    cartItem.getProductId(), cartItem.isCustom(),
                    cartItem.getSelectedDesignId(), cartItem.getSelectedDesignName(),
                    cartItem.getSelectedGlazeId(), cartItem.getSelectedGlazeName(),
                    cartItem.getSelectedRoofColorId(), cartItem.getSelectedRoofColorName(),
                    cartItem.getCustomDimensions(), cartItem.getSelectedTaxRateId(),
                    cartItemDto.getCustomRoofOverstep(), cartItemDto.isCustomHasDivider(),
                    cartItemDto.isCustomHasGutter(), cartItemDto.isCustomHasGardenShed(),
                    cartItem.getSelectedAddons());
            cartItem.setCartItemId(generatedCartItemId);
            log.debug("Generated Cart Item ID: {}", generatedCartItemId);

            // Přidání do košíku (beze změny)
            this.sessionCart.addItem(cartItem);
            log.info("--- addToCart END --- Item added/updated. Cart instance hash: {}, Items count: {}", this.sessionCart.hashCode(), this.sessionCart.getItemCount());
            redirectAttributes.addFlashAttribute("cartSuccess", "Produkt '" + product.getName() + "' byl přidán do košíku.");

        } catch (ResponseStatusException | EntityNotFoundException e) {
            // ... (zpracování chyb - beze změny) ...
            log.warn("Error adding item to cart (Product ID: {}): {}", cartItemDto.getProductId(), e.getMessage());
            redirectAttributes.addFlashAttribute("cartError", "Chyba: " + e.getMessage());
            return productSlugForRedirect != null ? "redirect:/produkt/" + productSlugForRedirect : "redirect:/produkty";
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Configuration or state error adding item (Product ID: {}): {}", cartItemDto.getProductId(), e.getMessage());
            redirectAttributes.addFlashAttribute("cartError", "Nepodařilo se přidat produkt do košíku: " + e.getMessage());
            redirectAttributes.addFlashAttribute("cartItemDto", cartItemDto);
            return productSlugForRedirect != null ? "redirect:/produkt/" + productSlugForRedirect : "redirect:/produkty";
        } catch (Exception e) {
            log.error("Unexpected error adding item to cart (Product ID: {}): {}", cartItemDto.getProductId(), e.getMessage(), e);
            redirectAttributes.addFlashAttribute("cartError", "Neočekávaná chyba při přidávání produktu do košíku.");
            return productSlugForRedirect != null ? "redirect:/produkt/" + productSlugForRedirect : "redirect:/produkty";
        }

        return "redirect:/kosik";
    }

    // Metoda buildVariantInfoString (beze změny)
    private String buildVariantInfoString(CartItem item, Design design, Glaze glaze, RoofColor roofColor) {
        // ... (kód metody zůstává stejný) ...
        if (item == null) return "";
        StringBuilder variantSb = new StringBuilder();
        if (item.isCustom()) {
            variantSb.append("Na míru");
            Map<String, BigDecimal> dims = item.getCustomDimensions();
            if (dims != null && !dims.isEmpty() && dims.containsKey("length") && dims.containsKey("width") && dims.containsKey("height")) {
                variantSb.append(" | Rozměry (DxHxV): ").append(dims.get("length") != null ? dims.get("length").stripTrailingZeros().toPlainString() : "?").append("x").append(dims.get("width") != null ? dims.get("width").stripTrailingZeros().toPlainString() : "?").append("x").append(dims.get("height") != null ? dims.get("height").stripTrailingZeros().toPlainString() : "?").append(" cm");
            }
        } else {
            if (item.getLength() != null && item.getWidth() != null && item.getHeight() != null) {
                variantSb.append("Rozměry (DxHxV): ").append(item.getLength().stripTrailingZeros().toPlainString()).append("x").append(item.getWidth().stripTrailingZeros().toPlainString()).append("x").append(item.getHeight().stripTrailingZeros().toPlainString()).append(" cm");
            }
        }
        if (design != null) { if (!variantSb.isEmpty() && variantSb.charAt(variantSb.length() - 1) != ' ') variantSb.append(" | "); variantSb.append("Design: ").append(design.getName()); }
        if (glaze != null) { if (!variantSb.isEmpty() && variantSb.charAt(variantSb.length() - 1) != ' ') variantSb.append(" | "); variantSb.append("Lazura: ").append(glaze.getName()); }
        if (roofColor != null) { if (!variantSb.isEmpty() && variantSb.charAt(variantSb.length() - 1) != ' ') variantSb.append(" | "); variantSb.append("Střecha: ").append(roofColor.getName()); }
        if (item.isCustom() && !CollectionUtils.isEmpty(item.getSelectedAddons())) {
            String addonNames = item.getSelectedAddons().stream().map(AddonDto::getAddonName).filter(StringUtils::hasText).collect(Collectors.joining(", "));
            if (StringUtils.hasText(addonNames)) { if (!variantSb.isEmpty() && variantSb.charAt(variantSb.length() - 1) != ' ') variantSb.append(" | "); variantSb.append("Doplňky: ").append(addonNames); }
        }
        String result = variantSb.toString().trim();
        result = result.replaceAll("\\s+\\|\\s+", " | ");
        return result;
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
        if (!StringUtils.hasText(couponCode)) { ra.addFlashAttribute("couponMessage", "Zadejte prosím kód kupónu."); return "redirect:/kosik"; }
        String trimmedCode = couponCode.trim();
        log.info("Attempting to apply coupon code: {}", trimmedCode);
        Optional<Coupon> couponOpt = couponService.findByCode(trimmedCode);
        if (couponOpt.isEmpty()) { this.sessionCart.setAttemptedCouponCode(trimmedCode); ra.addFlashAttribute("couponMessage", "Kód kupónu '" + trimmedCode + "' neexistuje."); log.warn("Coupon code '{}' not found.", trimmedCode); return "redirect:/kosik"; }
        Coupon coupon = couponOpt.get();
        if (!couponService.isCouponGenerallyValid(coupon)) { this.sessionCart.setAttemptedCouponCode(trimmedCode); ra.addFlashAttribute("couponMessage", "Kupón '" + trimmedCode + "' není aktivní nebo vypršela jeho platnost."); log.warn("Coupon code '{}' is not generally valid.", trimmedCode); return "redirect:/kosik"; }
        String currency = currencyService.getSelectedCurrency(); BigDecimal subtotal = this.sessionCart.calculateSubtotal(currency);
        if (!couponService.checkMinimumOrderValue(coupon, subtotal, currency)) { this.sessionCart.setAttemptedCouponCode(trimmedCode); String minValStr = couponService.getMinimumValueString(coupon, currency); ra.addFlashAttribute("couponMessage", "Pro použití kupónu '" + trimmedCode + "' je nutná minimální hodnota objednávky " + minValStr + "."); log.warn("Coupon '{}' minimum order value not met.", trimmedCode); return "redirect:/kosik"; }
        Customer customer = (principal != null) ? customerService.getCustomerByEmail(principal.getName()).orElse(null) : null;
        if (customer != null && !customer.isGuest() && !couponService.checkCustomerUsageLimit(customer, coupon)) { this.sessionCart.setAttemptedCouponCode(trimmedCode); ra.addFlashAttribute("couponMessage", "Kupón '" + trimmedCode + "' jste již použil(a) maximální počet krát."); log.warn("Customer '{}' reached usage limit for coupon '{}'.", principal.getName(), trimmedCode); return "redirect:/kosik"; }
        this.sessionCart.applyCoupon(coupon, trimmedCode); ra.addFlashAttribute("couponSuccess", "Kupón '" + trimmedCode + "' byl úspěšně použit.");
        log.info("--- applyCoupon END --- Coupon '{}' successfully applied. Cart hash: {}", trimmedCode, this.sessionCart.hashCode());
        return "redirect:/kosik";
    }

    @PostMapping("/odebrat-kupon")
    public String removeCoupon(RedirectAttributes ra) {
        log.info("--- removeCoupon START --- Cart instance hash: {}", this.sessionCart.hashCode());
        this.sessionCart.removeCoupon(); ra.addFlashAttribute("cartSuccess", "Slevový kupón byl odebrán.");
        log.info("--- removeCoupon END --- Success. Cart instance hash: {}", this.sessionCart.hashCode());
        return "redirect:/kosik";
    }
} // Konec třídy CartController