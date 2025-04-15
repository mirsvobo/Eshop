package org.example.eshop.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.example.eshop.admin.service.AddonsService;
import org.example.eshop.model.*;
import org.example.eshop.repository.DesignRepository;
import org.example.eshop.repository.GlazeRepository;
import org.example.eshop.repository.RoofColorRepository;
import org.example.eshop.service.*;
import org.example.eshop.config.PriceConstants;
import org.example.eshop.dto.AddonDto;
import org.example.eshop.dto.CartItemDto;
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
    @Autowired private AddonsService addonsService;
    @Autowired private CouponService couponService;
    @Autowired private CustomerService customerService;
    @Autowired private DesignRepository designRepository;
    @Autowired private GlazeRepository glazeRepository;
    @Autowired private RoofColorRepository roofColorRepository;
    @Autowired private TaxRateService taxRateService; // Assuming you have this service
    @Autowired private CurrencyService currencyService; // <-- Injected CurrencyService

    // --------------------------------------------------------------------
    // View Cart Method - Updated to use CurrencyService
    // --------------------------------------------------------------------
    @GetMapping
    public String viewCart(Model model, Principal principal) {
        log.info("--- viewCart START --- Cart instance hash: {}", this.sessionCart.hashCode());
        if (log.isDebugEnabled()) {
            log.debug("Cart items before display: {}", this.sessionCart.getItems());
        }

        log.debug("Displaying cart page. Cart has {} items.", this.sessionCart.getItemCount());
        model.addAttribute("cart", this.sessionCart); // Add the cart bean itself

        // --- Get current currency and symbol using CurrencyService ---
        String currentCurrency = currencyService.getSelectedCurrency(); // Get from injected service
        String currencySymbol = EURO_CURRENCY.equals(currentCurrency) ? "€" : "Kč"; // Determine symbol
        // -------------------------------------------------------------

        log.debug("Current currency set to: {}", currentCurrency);
        model.addAttribute("currentCurrency", currentCurrency); // Pass currency code to model
        model.addAttribute("currencySymbol", currencySymbol); // Pass currency symbol to model

        if (this.sessionCart.hasItems()) {
            // Calculate Subtotal (using current currency)
            BigDecimal subtotal = this.sessionCart.calculateSubtotal(currentCurrency);
            model.addAttribute("subtotal", subtotal);
            log.debug("Cart subtotal ({}): {}", currentCurrency, subtotal);

            // Validate Coupon and Calculate Discount (using current currency)
            BigDecimal couponDiscountAmount = this.sessionCart.calculateDiscountAmount(currentCurrency);
            Coupon validatedCoupon = null;
            if (this.sessionCart.getAppliedCoupon() != null) {
                Customer customer = (principal != null) ? customerService.getCustomerByEmail(principal.getName()).orElse(null) : null;
                Coupon currentCoupon = this.sessionCart.getAppliedCoupon();
                boolean stillValid = couponService.isCouponGenerallyValid(currentCoupon) &&
                        couponService.checkMinimumOrderValue(currentCoupon, subtotal, currentCurrency) &&
                        couponService.checkCustomerUsageLimit(customer, currentCoupon);

                if (stillValid) {
                    validatedCoupon = currentCoupon;
                    if (validatedCoupon.isFreeShipping()) {
                        model.addAttribute("freeShippingApplied", true);
                        log.debug("Free shipping applied by coupon '{}'", validatedCoupon.getCode());
                    }
                } else {
                    log.warn("Previously applied coupon '{}' is no longer valid for the current cart. Removing.", currentCoupon.getCode());
                    this.sessionCart.removeCoupon();
                    model.addAttribute("couponMessage", "Dříve použitý kupón '" + currentCoupon.getCode() + "' již není platný pro aktuální obsah košíku.");
                }
            }
            model.addAttribute("validatedCoupon", validatedCoupon);
            model.addAttribute("couponDiscountAmount", couponDiscountAmount);
            log.debug("Validated coupon: {}, Discount amount ({}): {}",
                    (validatedCoupon != null ? validatedCoupon.getCode() : "None"), currentCurrency, couponDiscountAmount);

            // Calculate VAT (using current currency)
            BigDecimal totalVat = this.sessionCart.calculateTotalVatAmount(currentCurrency);
            Map<BigDecimal, BigDecimal> vatBreakdown = this.sessionCart.calculateVatBreakdown(currentCurrency);
            model.addAttribute("totalVat", totalVat);
            model.addAttribute("vatBreakdown", vatBreakdown);
            log.debug("Total VAT ({}): {}", currentCurrency, totalVat);
            log.debug("VAT Breakdown ({}): {}", currentCurrency, vatBreakdown);

            // Calculate Total Price (Before Shipping, using current currency)
            BigDecimal totalPriceBeforeShipping = this.sessionCart.calculateTotalPriceBeforeShipping(currentCurrency);
            model.addAttribute("totalPriceBeforeShipping", totalPriceBeforeShipping);
            log.debug("Total Price Before Shipping ({}): {}", currentCurrency, totalPriceBeforeShipping);

            // Calculate Total Price Without Tax After Discount (using current currency)
            BigDecimal totalPriceWithoutTaxAfterDiscount = this.sessionCart.calculateTotalPriceWithoutTaxAfterDiscount(currentCurrency);
            model.addAttribute("totalPriceWithoutTaxAfterDiscount", totalPriceWithoutTaxAfterDiscount);
            log.debug("Total Price Without Tax After Discount ({}): {}", currentCurrency, totalPriceWithoutTaxAfterDiscount);

        } else {
            // Cart is empty, set defaults
            model.addAttribute("subtotal", BigDecimal.ZERO);
            model.addAttribute("couponDiscountAmount", BigDecimal.ZERO);
            model.addAttribute("validatedCoupon", null);
            model.addAttribute("totalVat", BigDecimal.ZERO);
            model.addAttribute("vatBreakdown", Collections.emptyMap());
            model.addAttribute("totalPriceBeforeShipping", BigDecimal.ZERO);
            model.addAttribute("totalPriceWithoutTaxAfterDiscount", BigDecimal.ZERO);
        }

        log.info("--- viewCart END --- Model attributes set for cart.");
        return "kosik"; // Return the name of the cart view template
    }

    // --- Kompletní metoda addToCart ---
    @PostMapping("/pridat")
    public String addToCart(@ModelAttribute("cartItemForm") @Valid CartItemDto cartItemDto,
                            BindingResult bindingResult,
                            RedirectAttributes redirectAttributes) {

        log.info("--- addToCart START (Attribute Select Version) --- Cart instance hash: {}", this.sessionCart.hashCode());

        String productSlugForRedirect = null;
        if (cartItemDto.getProductId() != null) {
            productSlugForRedirect = productService.getProductById(cartItemDto.getProductId())
                    .map(Product::getSlug)
                    .orElse(null);
        }

        // Základní validace DTO
        if (bindingResult.hasErrors()) {
            String errors = bindingResult.getAllErrors().stream()
                    .map(e -> ((e instanceof FieldError fe) ? fe.getField() + ": " : "") + e.getDefaultMessage())
                    .collect(Collectors.joining("; "));
            log.warn("Validation errors adding to cart (from @Valid): {}", errors);
            redirectAttributes.addFlashAttribute("cartError", "Chyba validace: Zkontrolujte zadané údaje.");
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.cartItemForm", bindingResult);
            redirectAttributes.addFlashAttribute("cartItemForm", cartItemDto);
            return productSlugForRedirect != null ? "redirect:/produkt/" + productSlugForRedirect : "redirect:/produkty";
        }

        log.info("Processing addToCart request for product ID: {}", cartItemDto.getProductId());
        log.debug("CartItemDto details: isCustom={}, quantity={}", cartItemDto.isCustom(), cartItemDto.getQuantity());
        log.debug("Selected IDs: Design={}, Glaze={}, RoofColor={}", cartItemDto.getSelectedDesignId(), cartItemDto.getSelectedGlazeId(), cartItemDto.getSelectedRoofColorId());

        try {
            // Načtení produktu
            Product product = productService.getProductById(cartItemDto.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produkt nenalezen: " + cartItemDto.getProductId()));

            if (productSlugForRedirect == null) productSlugForRedirect = product.getSlug();

            if (!product.isActive()) {
                log.warn("Attempted to add inactive product ID: {}", product.getId());
                redirectAttributes.addFlashAttribute("cartError", "Produkt '" + product.getName() + "' není momentálně dostupný.");
                return "redirect:/produkt/" + productSlugForRedirect;
            }

            // Načtení vybraných atributů podle ID (Nyní POVINNÉ pro oba typy)
            if (cartItemDto.getSelectedDesignId() == null || cartItemDto.getSelectedGlazeId() == null || cartItemDto.getSelectedRoofColorId() == null) {
                log.error("Attribute selection missing for product ID: {}", cartItemDto.getProductId());
                redirectAttributes.addFlashAttribute("cartError", "Musíte vybrat design, lazuru i barvu střechy.");
                redirectAttributes.addFlashAttribute("cartItemForm", cartItemDto);
                return productSlugForRedirect != null ? "redirect:/produkt/" + productSlugForRedirect : "redirect:/produkty";
            }

            Design selectedDesign = designRepository.findById(cartItemDto.getSelectedDesignId())
                    .orElseThrow(() -> new EntityNotFoundException("Design nenalezen: " + cartItemDto.getSelectedDesignId()));
            Glaze selectedGlaze = glazeRepository.findById(cartItemDto.getSelectedGlazeId())
                    .orElseThrow(() -> new EntityNotFoundException("Lazura nenalezena: " + cartItemDto.getSelectedGlazeId()));
            RoofColor selectedRoofColor = roofColorRepository.findById(cartItemDto.getSelectedRoofColorId())
                    .orElseThrow(() -> new EntityNotFoundException("Barva střechy nenalezena: " + cartItemDto.getSelectedRoofColorId()));

            // --- Vytvoření CartItem ---
            CartItem cartItem = new CartItem();
            cartItem.setProductId(product.getId());
            cartItem.setProductName(product.getName());
            cartItem.setProductSlug(product.getSlug());
            // OPRAVENÝ KÓD v CartController.java (metoda addToCart)
            List<Image> orderedImages = product.getImagesOrdered(); // Použijeme novou metodu vracející seřazený List
            cartItem.setImageUrl(!orderedImages.isEmpty() ? orderedImages.getFirst().getUrl() : "/images/placeholder.png");
            cartItem.setQuantity(cartItemDto.getQuantity());
            cartItem.setCustom(cartItemDto.isCustom());

            // Uložení ID a JMEN atributů do CartItem
            cartItem.setSelectedDesignId(selectedDesign.getId());
            cartItem.setSelectedDesignName(selectedDesign.getName());
            cartItem.setSelectedGlazeId(selectedGlaze.getId());
            cartItem.setSelectedGlazeName(selectedGlaze.getName());
            cartItem.setSelectedRoofColorId(selectedRoofColor.getId());
            cartItem.setSelectedRoofColorName(selectedRoofColor.getName());

            // Získání Tax Rate
            TaxRate taxRate = product.getTaxRate();
            cartItem.setTaxRatePercent(taxRate != null && taxRate.getRate() != null ? taxRate.getRate().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2));
            log.debug("Applied Tax Rate {}% for product ID {}", cartItem.getTaxRatePercent().multiply(BigDecimal.valueOf(100)), product.getId());

            // --- Výpočet ceny ---
            BigDecimal baseUnitPriceCZK;
            BigDecimal baseUnitPriceEUR;
            BigDecimal attributeSurchargeCZK = BigDecimal.ZERO;
            BigDecimal attributeSurchargeEUR = BigDecimal.ZERO;

            // Příplatky za atributy
            if (selectedDesign.getPriceSurchargeCZK() != null) attributeSurchargeCZK = attributeSurchargeCZK.add(selectedDesign.getPriceSurchargeCZK());
            if (selectedGlaze.getPriceSurchargeCZK() != null) attributeSurchargeCZK = attributeSurchargeCZK.add(selectedGlaze.getPriceSurchargeCZK());
            if (selectedRoofColor.getPriceSurchargeCZK() != null) attributeSurchargeCZK = attributeSurchargeCZK.add(selectedRoofColor.getPriceSurchargeCZK());
            if (selectedDesign.getPriceSurchargeEUR() != null) attributeSurchargeEUR = attributeSurchargeEUR.add(selectedDesign.getPriceSurchargeEUR());
            if (selectedGlaze.getPriceSurchargeEUR() != null) attributeSurchargeEUR = attributeSurchargeEUR.add(selectedGlaze.getPriceSurchargeEUR());
            if (selectedRoofColor.getPriceSurchargeEUR() != null) attributeSurchargeEUR = attributeSurchargeEUR.add(selectedRoofColor.getPriceSurchargeEUR());
            log.debug("Calculated attribute surcharges: CZK={}, EUR={}", attributeSurchargeCZK, attributeSurchargeEUR);

            if (cartItemDto.isCustom()) {
                // Custom produkt - cena z productService
                if (product.getConfigurator() == null) {
                    throw new IllegalStateException("Produkt '" + product.getName() + "' je konfigurovatelný, ale chybí data konfigurátoru.");
                }
                cartItem.setCustomDimensions(cartItemDto.getCustomDimensions());
                cartItem.setCustomRoofOverstep(cartItemDto.getCustomRoofOverstep());
                cartItem.setCustomHasDivider(cartItemDto.isCustomHasDivider());
                cartItem.setCustomHasGutter(cartItemDto.isCustomHasGutter());
                cartItem.setCustomHasGardenShed(cartItemDto.isCustomHasGardenShed());

                baseUnitPriceCZK = productService.calculateDynamicProductPrice(product, cartItem.getCustomDimensions(), null, cartItem.isCustomHasDivider(), cartItem.isCustomHasGutter(), cartItem.isCustomHasGardenShed(), "CZK");
                baseUnitPriceEUR = productService.calculateDynamicProductPrice(product, cartItem.getCustomDimensions(), null, cartItem.isCustomHasDivider(), cartItem.isCustomHasGutter(), cartItem.isCustomHasGardenShed(), "EUR");
                log.debug("Calculated dynamic base price for custom product: CZK={}, EUR={}", baseUnitPriceCZK, baseUnitPriceEUR);

            } else {
                // Standardní produkt - cena z produktu
                baseUnitPriceCZK = product.getBasePriceCZK() != null ? product.getBasePriceCZK() : BigDecimal.ZERO;
                baseUnitPriceEUR = product.getBasePriceEUR() != null ? product.getBasePriceEUR() : BigDecimal.ZERO;
                cartItem.setLength(product.getLength());
                cartItem.setWidth(product.getWidth());
                cartItem.setHeight(product.getHeight());
                log.debug("Using base price for standard product: CZK={}, EUR={}", baseUnitPriceCZK, baseUnitPriceEUR);
            }

            // Zpracování Addonů (jen pro custom)
            BigDecimal addonsPriceCZK = BigDecimal.ZERO;
            BigDecimal addonsPriceEUR = BigDecimal.ZERO;
            if (cartItemDto.isCustom() && !CollectionUtils.isEmpty(cartItemDto.getSelectedAddons())) {
                List<AddonDto> validRequestedAddons = cartItemDto.getSelectedAddons().stream()
                        .filter(dto -> dto != null && dto.getAddonId() != null && dto.getQuantity() > 0)
                        .collect(Collectors.toList());
                if (!validRequestedAddons.isEmpty()) {
                    Set<Long> requestedAddonIds = validRequestedAddons.stream().map(AddonDto::getAddonId).collect(Collectors.toSet());
                    Map<Long, Addon> validDbAddons = addonsService.findAddonsByIds(requestedAddonIds).stream().filter(Addon::isActive).collect(Collectors.toMap(Addon::getId, a -> a));
                    Set<Long> allowedAddonIds = product.getAvailableAddons() != null ? product.getAvailableAddons().stream().map(Addon::getId).collect(Collectors.toSet()) : Collections.emptySet();
                    List<AddonDto> processedAddons = new ArrayList<>();
                    for (AddonDto reqAddon : validRequestedAddons) {
                        Addon dbAddon = validDbAddons.get(reqAddon.getAddonId());
                        if (dbAddon != null && allowedAddonIds.contains(dbAddon.getId())) {
                            reqAddon.setAddonName(dbAddon.getName());
                            processedAddons.add(reqAddon);
                            if (dbAddon.getPriceCZK() != null) addonsPriceCZK = addonsPriceCZK.add(dbAddon.getPriceCZK().multiply(BigDecimal.valueOf(reqAddon.getQuantity())));
                            if (dbAddon.getPriceEUR() != null) addonsPriceEUR = addonsPriceEUR.add(dbAddon.getPriceEUR().multiply(BigDecimal.valueOf(reqAddon.getQuantity())));
                        }
                    }
                    cartItem.setSelectedAddons(processedAddons);
                    log.debug("Total addon prices calculated: CZK={}, EUR={}", addonsPriceCZK, addonsPriceEUR);
                } else { cartItem.setSelectedAddons(Collections.emptyList()); }
            } else { cartItem.setSelectedAddons(Collections.emptyList()); }


            // Finální jednotková cena = základní + příplatky atributů + cena addonů
            cartItem.setUnitPriceCZK(baseUnitPriceCZK.add(attributeSurchargeCZK).add(addonsPriceCZK).setScale(PRICE_SCALE, ROUNDING_MODE));
            cartItem.setUnitPriceEUR(baseUnitPriceEUR.add(attributeSurchargeEUR).add(addonsPriceEUR).setScale(PRICE_SCALE, ROUNDING_MODE));
            log.debug("Final unit prices calculated (incl. attribute surcharges and addons): CZK={}, EUR={}", cartItem.getUnitPriceCZK(), cartItem.getUnitPriceEUR());

            // --- Sestavení variantInfo a ID položky košíku ---
            cartItem.setVariantInfo(buildVariantInfoString(cartItem, product, selectedDesign, selectedGlaze, selectedRoofColor));
            String generatedCartItemId = CartItem.generateCartItemId(
                    cartItem.getProductId(), cartItem.isCustom(),
                    cartItem.getSelectedDesignId(), cartItem.getSelectedDesignName(),
                    cartItem.getSelectedGlazeId(), cartItem.getSelectedGlazeName(),
                    cartItem.getSelectedRoofColorId(), cartItem.getSelectedRoofColorName(),
                    cartItem.getCustomDimensions(), null, null, // Nepoužité custom Stringy
                    cartItem.getCustomRoofOverstep(), null, // Nepoužitý customDesign String
                    cartItem.isCustomHasDivider(), cartItem.isCustomHasGutter(),
                    cartItem.isCustomHasGardenShed(),
                    cartItem.getSelectedAddons()
            );
            cartItem.setCartItemId(generatedCartItemId);
            log.debug("Generated Cart Item ID: {}", generatedCartItemId);

            // Přidání do košíku
            this.sessionCart.addItem(cartItem);
            log.info("--- addToCart END --- Item added/updated. Cart instance hash: {}, Current cart items count: {}", this.sessionCart.hashCode(), this.sessionCart.getItemCount());
            redirectAttributes.addFlashAttribute("cartSuccess", "Produkt '" + product.getName() + "' byl přidán do košíku.");

        } catch (ResponseStatusException | EntityNotFoundException e) {
            log.warn("Error adding item to cart (Product ID: {}): {}", cartItemDto.getProductId(), e.getMessage());
            redirectAttributes.addFlashAttribute("cartError", "Chyba: " + e.getMessage());
            return productSlugForRedirect != null ? "redirect:/produkt/" + productSlugForRedirect : "redirect:/produkty";
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Configuration or state error adding item (Product ID: {}): {}", cartItemDto.getProductId(), e.getMessage());
            redirectAttributes.addFlashAttribute("cartError", "Nepodařilo se přidat produkt do košíku: " + e.getMessage());
            return productSlugForRedirect != null ? "redirect:/produkt/" + productSlugForRedirect : "redirect:/produkty";
        } catch (Exception e) {
            log.error("Unexpected error adding item to cart (Product ID: {}): {}", cartItemDto.getProductId(), e.getMessage(), e);
            redirectAttributes.addFlashAttribute("cartError", "Neočekávaná chyba při přidávání produktu do košíku.");
            return productSlugForRedirect != null ? "redirect:/produkt/" + productSlugForRedirect : "redirect:/produkty";
        }

        return "redirect:/kosik";
    }

    // --- Kompletní pomocná metoda buildVariantInfoString ---
    /**
     * Sestaví detailní popis varianty produktu pro CartItem.
     * Používá '|' jako oddělovač pro snadné formátování v Thymeleaf.
     */
    private String buildVariantInfoString(CartItem cartItem, Product product, Design design, Glaze glaze, RoofColor roofColor) {
        if (cartItem == null) return "";
        StringBuilder variantSb = new StringBuilder();

        if (cartItem.isCustom()) {
            variantSb.append("Na míru");
            // Rozměry z customDimensions
            if (cartItem.getCustomDimensions() != null && !cartItem.getCustomDimensions().isEmpty()) {
                variantSb.append("|Rozměry (DxHxV): ")
                        .append(cartItem.getCustomDimensions().get("length") != null ? cartItem.getCustomDimensions().get("length").stripTrailingZeros().toPlainString() : "?")
                        .append("x")
                        .append(cartItem.getCustomDimensions().get("width") != null ? cartItem.getCustomDimensions().get("width").stripTrailingZeros().toPlainString() : "?")
                        .append("x")
                        .append(cartItem.getCustomDimensions().get("height") != null ? cartItem.getCustomDimensions().get("height").stripTrailingZeros().toPlainString() : "?")
                        .append(" cm");
            }
            // Vybrané atributy
            if (design != null) variantSb.append("|Design: ").append(design.getName());
            if (glaze != null) variantSb.append("|Lazura: ").append(glaze.getName());
            if (roofColor != null) variantSb.append("|Střecha: ").append(roofColor.getName());
            // Ostatní custom volby
            if (StringUtils.hasText(cartItem.getCustomRoofOverstep())) variantSb.append("|Přesah: ").append(cartItem.getCustomRoofOverstep());
            if (cartItem.isCustomHasDivider()) variantSb.append("|Příčka: Ano");
            if (cartItem.isCustomHasGutter()) variantSb.append("|Okap: Ano");
            if (cartItem.isCustomHasGardenShed()) variantSb.append("|Zahr. domek: Ano");

        } else { // Standardní produkt
            // **** PŘIDÁNÍ ROZMĚRŮ PRO STANDARDNÍ PRODUKT ****
            if (cartItem.getLength() != null || cartItem.getWidth() != null || cartItem.getHeight() != null) {
                variantSb.append("Rozměry (DxHxV): ")
                        .append(cartItem.getLength() != null ? cartItem.getLength().stripTrailingZeros().toPlainString() : "?")
                        .append("x")
                        .append(cartItem.getWidth() != null ? cartItem.getWidth().stripTrailingZeros().toPlainString() : "?")
                        .append("x")
                        .append(cartItem.getHeight() != null ? cartItem.getHeight().stripTrailingZeros().toPlainString() : "?")
                        .append(" cm");
            }
            // ************************************************
            // Vybrané atributy (zůstává)
            if (design != null) { if (!variantSb.isEmpty()) variantSb.append(" | "); variantSb.append("Design: ").append(design.getName()); }
            if (glaze != null) { if (!variantSb.isEmpty()) variantSb.append(" | "); variantSb.append("Lazura: ").append(glaze.getName()); }
            if (roofColor != null) { if (!variantSb.isEmpty()) variantSb.append(" | "); variantSb.append("Střecha: ").append(roofColor.getName()); }
        }

        String result = variantSb.toString().trim();
        log.debug("Built variant info string for CartItem (ProductID: {}): '{}'", cartItem.getProductId(), result);
        return result;
    }


    // --------------------------------------------------------------------
    // Update Quantity Method
    // --------------------------------------------------------------------
    @PostMapping("/aktualizovat")
    public String updateQuantity(@RequestParam String cartItemId,
                                 @RequestParam @Min(0) int quantity,
                                 RedirectAttributes redirectAttributes) {
        log.info("--- updateQuantity START --- Cart instance hash: {}", this.sessionCart.hashCode());
        log.info("Request to update quantity for cart item ID: {} to {}", cartItemId, quantity);
        try {
            this.sessionCart.updateQuantity(cartItemId, quantity);
            if (quantity > 0) {
                redirectAttributes.addFlashAttribute("cartSuccess", "Množství bylo aktualizováno.");
            } else {
                redirectAttributes.addFlashAttribute("cartSuccess", "Položka byla odebrána z košíku.");
            }
            log.info("--- updateQuantity END --- Success. Cart instance hash: {}", this.sessionCart.hashCode());
            if (log.isDebugEnabled()) {
                log.debug("Cart items after update: {}", this.sessionCart.getItems());
            }
        } catch (Exception e) {
            log.error("--- updateQuantity ERROR --- Cart instance hash: {}", this.sessionCart.hashCode(), e);
            redirectAttributes.addFlashAttribute("cartError", "Chyba při aktualizaci množství.");
        }
        return "redirect:/kosik";
    }

    // --------------------------------------------------------------------
    // Remove Item Method
    // --------------------------------------------------------------------
    @PostMapping("/odebrat")
    public String removeItem(@RequestParam String cartItemId,
                             RedirectAttributes redirectAttributes) {
        log.info("--- removeItem START --- Cart instance hash: {}", this.sessionCart.hashCode());
        log.info("Request to remove cart item ID: {}", cartItemId);
        try {
            this.sessionCart.removeItem(cartItemId);
            redirectAttributes.addFlashAttribute("cartSuccess", "Položka byla odebrána z košíku.");
            log.info("--- removeItem END --- Success. Cart instance hash: {}", this.sessionCart.hashCode());
            if (log.isDebugEnabled()) {
                log.debug("Cart items after remove: {}", this.sessionCart.getItems());
            }
        } catch (Exception e) {
            log.error("--- removeItem ERROR --- Cart instance hash: {}", this.sessionCart.hashCode(), e);
            redirectAttributes.addFlashAttribute("cartError", "Chyba při odebírání položky.");
        }
        return "redirect:/kosik";
    }

    // --------------------------------------------------------------------
    // Apply Coupon Method
    // --------------------------------------------------------------------
    @PostMapping("/pouzit-kupon")
    public String applyCoupon(@RequestParam String couponCode,
                              RedirectAttributes ra,
                              Principal principal) {
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
            log.warn("Coupon code '{}' is not generally valid (inactive or expired).", trimmedCode);
            return "redirect:/kosik";
        }

        // Get current currency from service
        String currency = currencyService.getSelectedCurrency();
        BigDecimal subtotal = this.sessionCart.calculateSubtotal(currency);

        if (!couponService.checkMinimumOrderValue(coupon, subtotal, currency)) {
            this.sessionCart.setAttemptedCouponCode(trimmedCode);
            String minValStr = couponService.getMinimumValueString(coupon, currency); // Use helper method
            ra.addFlashAttribute("couponMessage", "Pro použití kupónu '" + trimmedCode + "' je nutná minimální hodnota objednávky " + minValStr + ".");
            log.warn("Coupon '{}' requires minimum order value {}, current subtotal is {} {}.", trimmedCode, minValStr, subtotal, currency);
            return "redirect:/kosik";
        }

        Customer customer = null;
        if (principal != null) {
            customer = customerService.getCustomerByEmail(principal.getName()).orElse(null);
            if (customer != null && !couponService.checkCustomerUsageLimit(customer, coupon)) {
                this.sessionCart.setAttemptedCouponCode(trimmedCode);
                ra.addFlashAttribute("couponMessage", "Kupón '" + trimmedCode + "' jste již použil(a) maximální počet krát.");
                log.warn("Customer '{}' reached usage limit for coupon '{}'.", principal.getName(), trimmedCode);
                return "redirect:/kosik";
            }
        } else {
            if (coupon.getUsageLimitPerCustomer() != null && coupon.getUsageLimitPerCustomer() > 0) {
                log.warn("Applying customer-limited coupon '{}' to anonymous user. Limit check needed during checkout.", trimmedCode);
            }
        }

        this.sessionCart.applyCoupon(coupon, trimmedCode);
        ra.addFlashAttribute("couponSuccess", "Kupón '" + trimmedCode + "' byl úspěšně použit.");
        log.info("--- applyCoupon END --- Coupon '{}' successfully applied. Cart instance hash: {}", trimmedCode, this.sessionCart.hashCode());

        return "redirect:/kosik";
    }

    // --------------------------------------------------------------------
    // Remove Coupon Method
    // --------------------------------------------------------------------
    @PostMapping("/odebrat-kupon")
    public String removeCoupon(RedirectAttributes ra) {
        log.info("--- removeCoupon START --- Cart instance hash: {}", this.sessionCart.hashCode());
        this.sessionCart.removeCoupon();
        ra.addFlashAttribute("cartSuccess", "Slevový kupón byl odebrán.");
        log.info("--- removeCoupon END --- Success. Cart instance hash: {}", this.sessionCart.hashCode());
        return "redirect:/kosik";
    }
}
