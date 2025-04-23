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
    @Autowired private TaxRateRepository taxRateRepository;

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

    // V třídě CartController

    @PostMapping("/pridat")
    public String addToCart(@ModelAttribute("cartItemForm") @Valid CartItemDto cartItemDto,
                            BindingResult bindingResult,
                            RedirectAttributes redirectAttributes) {

        log.info("--- addToCart START (TaxRate Select Version) --- Cart hash: {}", this.sessionCart.hashCode());
        String productSlugForRedirect = null;
        if (cartItemDto.getProductId() != null) {
            // Zkusíme získat slug pro případné přesměrování zpět na produkt
            productSlugForRedirect = productService.getProductById(cartItemDto.getProductId())
                    .map(Product::getSlug)
                    .orElse(null);
        }

        // Základní validace DTO anotací (@NotNull, @Min atd.)
        if (bindingResult.hasErrors()) {
            String errors = bindingResult.getAllErrors().stream()
                    .map(e -> ((e instanceof FieldError fe) ? fe.getField() + ": " : "") + e.getDefaultMessage())
                    .collect(Collectors.joining("; "));
            log.warn("Validation errors adding to cart (from @Valid): {}", errors);
            // Vrátíme chyby a data zpět na formulář produktu
            redirectAttributes.addFlashAttribute("cartError", "Chyba validace: Zkontrolujte zadané údaje.");
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.cartItemForm", bindingResult);
            redirectAttributes.addFlashAttribute("cartItemForm", cartItemDto);
            return productSlugForRedirect != null ? "redirect:/produkt/" + productSlugForRedirect : "redirect:/produkty";
        }

        log.info("Processing addToCart request for product ID: {}, TaxRate ID: {}", cartItemDto.getProductId(), cartItemDto.getSelectedTaxRateId());
        log.debug("CartItemDto details: isCustom={}, quantity={}", cartItemDto.isCustom(), cartItemDto.getQuantity());
        log.debug("Selected IDs: Design={}, Glaze={}, RoofColor={}, TaxRate={}",
                cartItemDto.getSelectedDesignId(), cartItemDto.getSelectedGlazeId(),
                cartItemDto.getSelectedRoofColorId(), cartItemDto.getSelectedTaxRateId()); // Přidáno logování TaxRateID

        try {
            // Načtení produktu (ideálně s asociacemi pro validaci)
            Product product = productService.getProductById(cartItemDto.getProductId()) // Můžeš použít getProductByIdWithDetails pokud je potřeba
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produkt nenalezen: " + cartItemDto.getProductId()));

            if (productSlugForRedirect == null) productSlugForRedirect = product.getSlug(); // Získání slugu, pokud se nepodařilo dříve

            // Kontrola, zda je produkt aktivní
            if (!product.isActive()) {
                log.warn("Attempted to add inactive product ID: {}", product.getId());
                redirectAttributes.addFlashAttribute("cartError", "Produkt '" + product.getName() + "' není momentálně dostupný.");
                return "redirect:/produkt/" + productSlugForRedirect;
            }

            // --- VALIDACE VÝBĚRU ATRIBUTŮ A DPH ---

            // Načtení a validace vybrané sazby DPH (NOVÁ ČÁST)
            if (cartItemDto.getSelectedTaxRateId() == null) {
                log.error("Tax Rate ID is missing for product ID: {}", cartItemDto.getProductId());
                redirectAttributes.addFlashAttribute("cartError", "Musíte vybrat daňovou sazbu.");
                redirectAttributes.addFlashAttribute("cartItemForm", cartItemDto); // Vrátíme data pro formulář
                return productSlugForRedirect != null ? "redirect:/produkt/" + productSlugForRedirect : "redirect:/produkty";
            }
            // Potřebujeme TaxRateRepository
            TaxRate selectedTaxRate = taxRateRepository.findById(cartItemDto.getSelectedTaxRateId())
                    .orElseThrow(() -> new EntityNotFoundException("Daňová sazba nenalezena: " + cartItemDto.getSelectedTaxRateId()));

            // Ověření, zda je vybraná sazba povolena pro produkt
            // Ujisti se, že `product` má načtené `availableTaxRates` (např. přes getProductByIdWithDetails nebo lazy loading v transakci)
            java.util.Set<TaxRate> availableRates = product.getAvailableTaxRates();
            if (availableRates == null || availableRates.isEmpty()) { // Pojistka
                log.error("Product ID {} has no available tax rates configured!", product.getId());
                // Možná donačíst zde, pokud nejsi v transakci a používáš lazy loading
                // Product refreshedProduct = productService.getProductByIdWithDetails(product.getId()).orElseThrow(...);
                // availableRates = refreshedProduct.getAvailableTaxRates();
                // if (availableRates == null || availableRates.isEmpty()) { ... }
                throw new IllegalStateException("Konfigurační chyba: Produkt '" + product.getName() + "' nemá definované povolené daňové sazby.");
            }
            if (!availableRates.contains(selectedTaxRate)) {
                log.error("Selected TaxRate ID {} is not allowed for product ID {}", selectedTaxRate.getId(), product.getId());
                redirectAttributes.addFlashAttribute("cartError", "Vybraná daňová sazba není pro tento produkt povolena.");
                redirectAttributes.addFlashAttribute("cartItemForm", cartItemDto);
                return productSlugForRedirect != null ? "redirect:/produkt/" + productSlugForRedirect : "redirect:/produkty";
            }
            // --- KONEC VALIDACE DPH ---


            // Načtení vybraných atributů (Design, Glaze, RoofColor) - Kód zůstává, ale je důležitý
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
            List<Image> orderedImages = product.getImagesOrdered(); // Použijeme metodu vracející List
            cartItem.setImageUrl(!orderedImages.isEmpty() ? orderedImages.getFirst().getUrl() : "/images/placeholder.png"); // Použijeme getFirst() pro List
            cartItem.setQuantity(cartItemDto.getQuantity());
            cartItem.setCustom(cartItemDto.isCustom());

            // Uložení ID a JMEN atributů do CartItem
            cartItem.setSelectedDesignId(selectedDesign.getId());
            cartItem.setSelectedDesignName(selectedDesign.getName());
            cartItem.setSelectedGlazeId(selectedGlaze.getId());
            cartItem.setSelectedGlazeName(selectedGlaze.getName());
            cartItem.setSelectedRoofColorId(selectedRoofColor.getId());
            cartItem.setSelectedRoofColorName(selectedRoofColor.getName());

            // *** Uložení vybrané DPH sazby do CartItem (NOVÁ ČÁST) ***
            cartItem.setSelectedTaxRateId(selectedTaxRate.getId());
            cartItem.setSelectedTaxRateValue(selectedTaxRate.getRate()); // Hodnota 0.xx
            cartItem.setSelectedIsReverseCharge(selectedTaxRate.isReverseCharge()); // boolean
            log.debug("Applied Tax Rate {}% (RC: {}) for product ID {}",
                    selectedTaxRate.getRate().multiply(BigDecimal.valueOf(100)).setScale(2),
                    selectedTaxRate.isReverseCharge(), product.getId());
            // *** KONEC UKLÁDÁNÍ DPH ***

            // ODSTRANĚNÍ STARÉHO KÓDU PRO DPH:
            // TaxRate taxRate = product.getTaxRate(); // <-- ODSTRANIT
            // cartItem.setTaxRatePercent(taxRate != null && taxRate.getRate() != null ? taxRate.getRate().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2)); // <-- ODSTRANIT
            // log.debug("Applied Tax Rate {}% for product ID {}", cartItem.getTaxRatePercent().multiply(BigDecimal.valueOf(100)), product.getId()); // <-- ODSTRANIT

            // --- Výpočet ceny (logika zůstává stejná, DPH se neřeší zde) ---
            BigDecimal baseUnitPriceCZK;
            BigDecimal baseUnitPriceEUR;
            BigDecimal attributeSurchargeCZK = BigDecimal.ZERO;
            BigDecimal attributeSurchargeEUR = BigDecimal.ZERO;

            // Příplatky za atributy (kód zůstává)
            if (selectedDesign.getPriceSurchargeCZK() != null) attributeSurchargeCZK = attributeSurchargeCZK.add(selectedDesign.getPriceSurchargeCZK());
            if (selectedGlaze.getPriceSurchargeCZK() != null) attributeSurchargeCZK = attributeSurchargeCZK.add(selectedGlaze.getPriceSurchargeCZK());
            if (selectedRoofColor.getPriceSurchargeCZK() != null) attributeSurchargeCZK = attributeSurchargeCZK.add(selectedRoofColor.getPriceSurchargeCZK());
            if (selectedDesign.getPriceSurchargeEUR() != null) attributeSurchargeEUR = attributeSurchargeEUR.add(selectedDesign.getPriceSurchargeEUR());
            if (selectedGlaze.getPriceSurchargeEUR() != null) attributeSurchargeEUR = attributeSurchargeEUR.add(selectedGlaze.getPriceSurchargeEUR());
            if (selectedRoofColor.getPriceSurchargeEUR() != null) attributeSurchargeEUR = attributeSurchargeEUR.add(selectedRoofColor.getPriceSurchargeEUR());
            log.debug("Calculated attribute surcharges: CZK={}, EUR={}", attributeSurchargeCZK, attributeSurchargeEUR);

            // Výpočet základní ceny
            if (cartItemDto.isCustom()) {
                if (product.getConfigurator() == null) {
                    throw new IllegalStateException("Produkt '" + product.getName() + "' je konfigurovatelný, ale chybí data konfigurátoru.");
                }
                // Nastavení custom atributů na CartItem
                cartItem.setCustomDimensions(cartItemDto.getCustomDimensions());
                // Poznámka: Odstranili jsme nastavování customHasDivider atd. zde,
                // protože tyto by měly být reprezentovány jako AddonDto v cartItemDto.getSelectedAddons()

                // --- OPRAVENÉ VOLÁNÍ calculateDynamicProductPrice ---
                baseUnitPriceCZK = productService.calculateDynamicProductPrice(
                        product,
                        cartItem.getCustomDimensions(), // Mapa rozměrů
                        "CZK"                           // Měna
                );
                baseUnitPriceEUR = productService.calculateDynamicProductPrice(
                        product,
                        cartItem.getCustomDimensions(), // Mapa rozměrů
                        "EUR"                           // Měna
                );
                // --- KONEC OPRAVY ---
                log.debug("Calculated dynamic BASE price for custom product: CZK={}, EUR={}", baseUnitPriceCZK, baseUnitPriceEUR);

            } else {
                // Standardní produkt
                baseUnitPriceCZK = product.getBasePriceCZK() != null ? product.getBasePriceCZK() : BigDecimal.ZERO;
                baseUnitPriceEUR = product.getBasePriceEUR() != null ? product.getBasePriceEUR() : BigDecimal.ZERO;
                // Nastavení rozměrů standardního produktu na CartItem
                cartItem.setLength(product.getLength());
                cartItem.setWidth(product.getWidth());
                cartItem.setHeight(product.getHeight());
                log.debug("Using base price for standard product: CZK={}, EUR={}", baseUnitPriceCZK, baseUnitPriceEUR);
            }

            // Zpracování Addonů (kód zůstává, ale je důležitý)
            BigDecimal addonsPriceCZK = BigDecimal.ZERO;
            BigDecimal addonsPriceEUR = BigDecimal.ZERO;
            if (cartItemDto.isCustom() && !CollectionUtils.isEmpty(cartItemDto.getSelectedAddons())) {
                List<AddonDto> validRequestedAddons = cartItemDto.getSelectedAddons().stream()
                        .filter(dto -> dto != null && dto.getAddonId() != null && dto.getQuantity() > 0)
                        .collect(Collectors.toList());
                if (!validRequestedAddons.isEmpty()) {
                    Set<Long> requestedAddonIds = validRequestedAddons.stream().map(AddonDto::getAddonId).collect(Collectors.toSet());
                    // Načteme jen aktivní addony z DB
                    Map<Long, Addon> validDbAddons = addonsService.findAddonsByIds(requestedAddonIds).stream()
                            .filter(Addon::isActive)
                            .collect(Collectors.toMap(Addon::getId, a -> a));
                    // Získáme povolené addony pro daný produkt
                    Set<Long> allowedAddonIds = product.getAvailableAddons() != null
                            ? product.getAvailableAddons().stream().map(Addon::getId).collect(Collectors.toSet())
                            : Collections.emptySet();
                    List<AddonDto> processedAddons = new ArrayList<>();
                    for (AddonDto reqAddon : validRequestedAddons) {
                        Addon dbAddon = validDbAddons.get(reqAddon.getAddonId());
                        // Přidáme addon, jen pokud existuje v DB, je aktivní A je povolený pro produkt
                        if (dbAddon != null && allowedAddonIds.contains(dbAddon.getId())) {
                            reqAddon.setAddonName(dbAddon.getName()); // Uložíme název
                            // Vypočteme cenu addonu ZDE (předpokládáme FIXNÍ cenu addonu pro jednoduchost)
                            // TODO: Rozšířit pro dimenzionální ceny addonů, pokud je třeba
                            BigDecimal addonUnitPriceCzk = dbAddon.getPriceCZK() != null ? dbAddon.getPriceCZK() : BigDecimal.ZERO;
                            BigDecimal addonUnitPriceEur = dbAddon.getPriceEUR() != null ? dbAddon.getPriceEUR() : BigDecimal.ZERO;

                            addonsPriceCZK = addonsPriceCZK.add(addonUnitPriceCzk.multiply(BigDecimal.valueOf(reqAddon.getQuantity())));
                            addonsPriceEUR = addonsPriceEUR.add(addonUnitPriceEur.multiply(BigDecimal.valueOf(reqAddon.getQuantity())));

                            processedAddons.add(reqAddon); // Přidáme do seznamu zpracovaných
                        } else {
                            log.warn("Requested addon ID {} is not valid, not active, or not allowed for product ID {}. Skipping.", reqAddon.getAddonId(), product.getId());
                        }
                    }
                    cartItem.setSelectedAddons(processedAddons);
                    log.debug("Total addon prices calculated (assuming FIXED pricing): CZK={}, EUR={}", addonsPriceCZK, addonsPriceEUR);
                } else { cartItem.setSelectedAddons(Collections.emptyList()); }
            } else { cartItem.setSelectedAddons(Collections.emptyList()); }


            // Finální jednotková cena BEZ DPH = základní + příplatky atributů + cena addonů
            cartItem.setUnitPriceCZK(baseUnitPriceCZK.add(attributeSurchargeCZK).add(addonsPriceCZK).setScale(PRICE_SCALE, ROUNDING_MODE));
            cartItem.setUnitPriceEUR(baseUnitPriceEUR.add(attributeSurchargeEUR).add(addonsPriceEUR).setScale(PRICE_SCALE, ROUNDING_MODE));
            log.debug("Final unit prices calculated (incl. attribute surcharges and addons): CZK={}, EUR={}", cartItem.getUnitPriceCZK(), cartItem.getUnitPriceEUR());

            // --- Sestavení variantInfo a ID položky košíku ---
            cartItem.setVariantInfo(buildVariantInfoString(cartItem, product, selectedDesign, selectedGlaze, selectedRoofColor));

            // *** OPRAVENÉ VOLÁNÍ generateCartItemId ***
            String generatedCartItemId = CartItem.generateCartItemId(
                    cartItem.getProductId(),             // Long productId
                    cartItem.isCustom(),                 // boolean isCustom
                    cartItem.getSelectedDesignId(),      // Long selectedDesignId
                    cartItem.getSelectedDesignName(),    // String selectedDesignName
                    cartItem.getSelectedGlazeId(),       // Long selectedGlazeId
                    cartItem.getSelectedGlazeName(),     // String selectedGlazeName
                    cartItem.getSelectedRoofColorId(),   // Long selectedRoofColorId
                    cartItem.getSelectedRoofColorName(), // String selectedRoofColorName
                    cartItem.getCustomDimensions(),      // Map<String, BigDecimal> customDimensions
                    cartItem.getSelectedTaxRateId(),     // Long selectedTaxRateId  <-- NOVÝ PARAMETR
                    // --- Zde předáváme hodnoty z cartItem, které by měly reprezentovat dříve vybrané Addony ---
                    // Je třeba zajistit, aby `cartItem.getSelectedAddons()` obsahoval DTOs pro Divider, Gutter, Shed, pokud byly vybrány jako Addony
                    // Pro zjednodušení zde PŘEDPOKLÁDÁME, že tyto informace už nejsou relevantní pro ID košíku, pokud jsou řešeny genericky přes Addons
                    cartItem.getCustomRoofOverstep(),    // String customRoofOverstep (pokud zůstává)
                    false, // cartItem.isCustomHasDivider()  // Nahrazeno Addonem? Předáme false
                    false, // cartItem.isCustomHasGutter()   // Nahrazeno Addonem? Předáme false
                    false, // cartItem.isCustomHasGardenShed()// Nahrazeno Addonem? Předáme false
                    cartItem.getSelectedAddons()         // List<AddonDto> selectedAddons
            );
            cartItem.setCartItemId(generatedCartItemId);
            log.debug("Generated Cart Item ID: {}", generatedCartItemId);
            // *** KONEC OPRAVY VOLÁNÍ ***

            // Přidání do košíku
            this.sessionCart.addItem(cartItem);
            log.info("--- addToCart END --- Item added/updated. Cart instance hash: {}, Current cart items count: {}", this.sessionCart.hashCode(), this.sessionCart.getItemCount());
            redirectAttributes.addFlashAttribute("cartSuccess", "Produkt '" + product.getName() + "' byl přidán do košíku.");

        } catch (ResponseStatusException | EntityNotFoundException e) {
            // Chyby, které znamenají, že data nebyla nalezena
            log.warn("Error adding item to cart (Product ID: {}): {}", cartItemDto.getProductId(), e.getMessage());
            redirectAttributes.addFlashAttribute("cartError", "Chyba: " + e.getMessage());
            // Vrátíme na detail produktu, pokud je znám slug
            return productSlugForRedirect != null ? "redirect:/produkt/" + productSlugForRedirect : "redirect:/produkty";
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Chyby konfigurace nebo neplatného stavu
            log.warn("Configuration or state error adding item (Product ID: {}): {}", cartItemDto.getProductId(), e.getMessage());
            redirectAttributes.addFlashAttribute("cartError", "Nepodařilo se přidat produkt do košíku: " + e.getMessage());
            // Vrátíme data formuláře zpět pro případnou korekci
            redirectAttributes.addFlashAttribute("cartItemForm", cartItemDto);
            return productSlugForRedirect != null ? "redirect:/produkt/" + productSlugForRedirect : "redirect:/produkty";
        } catch (Exception e) {
            // Neočekávané chyby
            log.error("Unexpected error adding item to cart (Product ID: {}): {}", cartItemDto.getProductId(), e.getMessage(), e);
            redirectAttributes.addFlashAttribute("cartError", "Neočekávaná chyba při přidávání produktu do košíku.");
            return productSlugForRedirect != null ? "redirect:/produkt/" + productSlugForRedirect : "redirect:/produkty";
        }

        // Pokud vše proběhlo úspěšně, přesměrujeme na košík
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
