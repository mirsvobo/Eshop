// src/main/java/org/example/eshop/model/CartItem.java
package org.example.eshop.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString; // Přidáno pro logování
import org.example.eshop.config.PriceConstants;
import org.example.eshop.dto.AddonDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils; // Pro CollectionUtils.isEmpty
import org.springframework.util.StringUtils;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Getter
@Setter
@ToString // Přidáno pro lepší logování
public class CartItem implements Serializable, PriceConstants {
    @Serial
    private static final long serialVersionUID = 4L; // Zvýšena verze znovu
    private static final Logger log = LoggerFactory.getLogger(CartItem.class);

    private String cartItemId;          // Unikátní ID položky v košíku (závislé na konfiguraci)
    private Long productId;             // ID produktu
    private String productName;         // Název produktu (pro zobrazení)
    private String productSlug;         // Slug produktu (pro odkaz)
    private String imageUrl;            // URL hlavního obrázku
    private int quantity;               // Počet kusů
    private boolean isCustom;           // Je produkt konfigurovaný na míru?

    // Rozměry (pro custom i standardní - pro standardní se berou z Product)
    private BigDecimal length;
    private BigDecimal width;
    private BigDecimal height;

    // ID vybraných atributů
    private Long selectedDesignId;
    private Long selectedGlazeId;
    private Long selectedRoofColorId;

    // Názvy vybraných atributů (pro zobrazení)
    private String selectedDesignName;
    private String selectedGlazeName;
    private String selectedRoofColorName;

    // Vybraná sazba DPH
    private Long selectedTaxRateId;
    private BigDecimal selectedTaxRateValue;
    private boolean selectedIsReverseCharge;

    // Specifické pro custom
    private Map<String, BigDecimal> customDimensions; // Mapa rozměrů pro custom
    private String customRoofOverstep;
    private boolean customHasDivider;
    private boolean customHasGutter;
    private boolean customHasGardenShed;
    private List<AddonDto> selectedAddons; // Seznam vybraných doplňků

    // Jednotkové ceny (bez DPH)
    private BigDecimal unitPriceCZK;
    private BigDecimal unitPriceEUR;

    // Popis varianty (generovaný text)
    private String variantInfo;

    // Konstruktor
    public CartItem() {
        // Inicializace kolekcí pro jistotu
        this.selectedAddons = new ArrayList<>();
        this.customDimensions = new HashMap<>();
    }

    /**
     * Vypočítá celkovou cenu řádku bez DPH.
     * @param currency Měna ("CZK" nebo "EUR").
     * @return Celková cena řádku bez DPH.
     */
    public BigDecimal getTotalLinePriceWithoutTax(String currency) {
        BigDecimal unitPrice = EURO_CURRENCY.equals(currency) ? unitPriceEUR : unitPriceCZK;
        // Přidána kontrola, zda unitPrice není null
        if (unitPrice == null) {
            log.error("Cannot calculate total line price: Unit price for currency '{}' is NULL for cart item ID '{}'", currency, cartItemId);
            return BigDecimal.ZERO; // Nebo vyhodit výjimku?
        }
        if (quantity <= 0) {
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(PRICE_SCALE, ROUNDING_MODE);
    }

    /**
     * Vypočítá výši DPH pro tento řádek.
     * @param currency Měna ("CZK" nebo "EUR").
     * @return Výše DPH.
     */
    public BigDecimal getVatAmount(String currency) {
        BigDecimal linePriceWithoutTax = getTotalLinePriceWithoutTax(currency);
        BigDecimal effectiveTaxRate = selectedTaxRateValue; // Použijeme uloženou hodnotu

        if (effectiveTaxRate == null || effectiveTaxRate.compareTo(BigDecimal.ZERO) <= 0 || linePriceWithoutTax.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal calculatedVat = linePriceWithoutTax.multiply(effectiveTaxRate).setScale(PRICE_SCALE, ROUNDING_MODE);
        return calculatedVat;
    }

    /**
     * Vypočítá celkovou cenu řádku včetně DPH.
     * @param currency Měna ("CZK" nebo "EUR").
     * @return Celková cena řádku s DPH.
     */
    public BigDecimal getTotalLinePriceWithTax(String currency) {
        return getTotalLinePriceWithoutTax(currency).add(getVatAmount(currency));
    }

    /**
     * Generuje unikátní ID pro položku košíku na základě její konfigurace.
     * Zahrnuje všechny relevantní atributy a doplňky.
     * @return String reprezentující unikátní ID položky.
     */
    public static String generateCartItemId(Long productId, boolean isCustom,
                                            Long selectedDesignId, String selectedDesignName,
                                            Long selectedGlazeId, String selectedGlazeName,
                                            Long selectedRoofColorId, String selectedRoofColorName,
                                            Map<String, BigDecimal> customDimensions,
                                            Long selectedTaxRateId,
                                            String customRoofOverstep,
                                            boolean customHasDivider, boolean customHasGutter,
                                            boolean customHasGardenShed, List<AddonDto> selectedAddons) {

        StringBuilder sb = new StringBuilder("P").append(productId);
        sb.append("-T").append(selectedTaxRateId != null ? selectedTaxRateId : "null");

        if (isCustom) {
            sb.append("-C");
            // Dimenze (seřazené podle klíče pro konzistenci)
            if (customDimensions != null && !customDimensions.isEmpty()) {
                sb.append("-DIMS[");
                customDimensions.entrySet().stream()
                        .filter(entry -> entry.getValue() != null)
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> sb.append(entry.getKey())
                                .append("=")
                                .append(entry.getValue().stripTrailingZeros().toPlainString()) // Používáme stripTrailingZeros
                                .append(";"));
                sb.append("]");
            }
            // Atributy (ID)
            if (selectedDesignId != null) sb.append("-D").append(selectedDesignId);
            if (selectedGlazeId != null) sb.append("-G").append(selectedGlazeId);
            if (selectedRoofColorId != null) sb.append("-RC").append(selectedRoofColorId);
            // Ostatní custom prvky
            if (StringUtils.hasText(customRoofOverstep)) sb.append("-RO").append(customRoofOverstep.hashCode());
            if (customHasDivider) sb.append("-Di");
            if (customHasGutter) sb.append("-Gu");
            if (customHasGardenShed) sb.append("-Sh");
            // Addony (seřazené podle ID)
            if (!CollectionUtils.isEmpty(selectedAddons)) {
                sb.append("-ADNS[");
                selectedAddons.stream()
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(AddonDto::getAddonId, Comparator.nullsLast(Comparator.naturalOrder())))
                        .forEach(addon -> sb.append(addon.getAddonId())
                                .append("x")
                                .append(addon.getQuantity())
                                .append(";"));
                sb.append("]");
            }
        } else { // Standardní produkt
            sb.append("-S");
            if (selectedDesignId != null) sb.append("-D").append(selectedDesignId);
            if (selectedGlazeId != null) sb.append("-G").append(selectedGlazeId);
            if (selectedRoofColorId != null) sb.append("-RC").append(selectedRoofColorId);
        }
        return sb.toString();
    }

    // --- equals a hashCode podle cartItemId ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CartItem cartItem = (CartItem) o;
        return Objects.equals(cartItemId, cartItem.cartItemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cartItemId);
    }
}