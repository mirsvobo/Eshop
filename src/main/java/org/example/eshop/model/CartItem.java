package org.example.eshop.model;

import lombok.Getter;
import lombok.Setter;
import org.example.eshop.config.PriceConstants; // Assuming PriceConstants defines scale and rounding
import org.example.eshop.dto.AddonDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode; // Import RoundingMode
import java.util.*;

@Getter
@Setter
public class CartItem implements Serializable, PriceConstants { // Implement PriceConstants if needed for scale/rounding
    @Serial
    private static final long serialVersionUID = 2L; // Zvýšena verze kvůli změně
    private static final Logger log = LoggerFactory.getLogger(CartItem.class);

    private String cartItemId;
    private Long productId;
    private String productName;
    private String productSlug;
    private String imageUrl;
    private int quantity;
    private boolean isCustom;

    private BigDecimal length;  // Délka
    private BigDecimal width;   // Hloubka (šířka)
    private BigDecimal height;

    // --- Atributy ID (pro standardní i custom) ---
    private Long selectedDesignId;
    private Long selectedGlazeId;
    private Long selectedRoofColorId;

    // --- PŘIDÁNA POLE PRO JMÉNA (pro zobrazení/historii) ---
    private String selectedDesignName;
    private String selectedGlazeName;
    private String selectedRoofColorName;
    // -----------------------------------------------------

    // --- Custom Konfigurace ---
    private Map<String, BigDecimal> customDimensions;
    // ODEBRÁNO: private String customGlaze;
    // ODEBRÁNO: private String customRoofColor;
    private String customRoofOverstep;
    // ODEBRÁNO: private String customDesign;
    private boolean customHasDivider;
    private boolean customHasGutter;
    private boolean customHasGardenShed;

    private List<AddonDto> selectedAddons;

    // Ceny
    private BigDecimal unitPriceCZK;
    private BigDecimal unitPriceEUR;
    private BigDecimal taxRatePercent;

    // **** PŘIDÁNO POLE PRO DETAILNÍ POPIS ****
    private String variantInfo;
    // ****************************************

    public CartItem() {}

    // --- Methods for Price Calculation ---

    /**
     * Calculates the total price for this line item (unit price * quantity) without VAT.
     * @param currency The currency code ("CZK" or "EUR").
     * @return Total price for the line without VAT, or BigDecimal.ZERO if price is missing.
     */
    public BigDecimal getTotalLinePriceWithoutTax(String currency) {
        BigDecimal unitPrice = EURO_CURRENCY.equals(currency) ? unitPriceEUR : unitPriceCZK;
        if (unitPrice == null || quantity <= 0) {
            log.warn("Cannot calculate total line price without tax. Unit price for {} is null or quantity is zero for cartItemId: {}", currency, cartItemId);
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(PRICE_SCALE, ROUNDING_MODE);
    }

    /**
     * Calculates the VAT amount for this line item.
     * Assumes taxRatePercent stores the rate as a decimal (e.g., 0.21).
     * Includes DEBUG logging.
     * @param currency The currency code ("CZK" or "EUR").
     * @return VAT amount for the line, or BigDecimal.ZERO if price or tax rate is missing/zero.
     */
    // @Override // <-- ODSTRANĚNO
    public BigDecimal getVatAmount(String currency) {
        BigDecimal linePriceWithoutTax = getTotalLinePriceWithoutTax(currency);

        log.trace("DEBUG_VAT (CartItem {}): Calculating VAT. linePriceWithoutTax={}, stored taxRatePercent={}",
                this.cartItemId, linePriceWithoutTax, this.taxRatePercent);

        // Return ZERO if rate is null/zero or base price is zero
        if (taxRatePercent == null || taxRatePercent.compareTo(BigDecimal.ZERO) <= 0 || linePriceWithoutTax.compareTo(BigDecimal.ZERO) == 0) {
            log.trace("DEBUG_VAT (CartItem {}): Returning ZERO VAT due to zero rate/price.", this.cartItemId);
            return BigDecimal.ZERO;
        }

        // Násobíme přímo, protože taxRatePercent je již 0.21 (ne 21.00)
        BigDecimal calculatedVat = linePriceWithoutTax.multiply(taxRatePercent).setScale(PRICE_SCALE, ROUNDING_MODE);

        log.trace("DEBUG_VAT (CartItem {}): Calculated VAT amount = {}", this.cartItemId, calculatedVat);

        return calculatedVat;
    }

    /**
     * Calculates the total price for this line item (unit price * quantity) including VAT.
     * @param currency The currency code ("CZK" or "EUR").
     * @return Total price for the line including VAT.
     */
    public BigDecimal getTotalLinePriceWithTax(String currency) {
        return getTotalLinePriceWithoutTax(currency).add(getVatAmount(currency));
    }


    // --- Static method for generating ID (remains the same) ---
    public static String generateCartItemId(Long productId, boolean isCustom,
                                            Long selectedDesignId, String selectedDesignName,
                                            Long selectedGlazeId, String selectedGlazeName,
                                            Long selectedRoofColorId, String selectedRoofColorName,
                                            Map<String, BigDecimal> customDimensions,
                                            String customGlaze, String customRoofColor,
                                            String customRoofOverstep, String customDesignAttr,
                                            boolean customHasDivider, boolean customHasGutter,
                                            boolean customHasGardenShed, List<AddonDto> selectedAddons) {

        StringBuilder sb = new StringBuilder("P").append(productId);
        if (isCustom) {
            sb.append("-C");
            if (customDimensions != null && !customDimensions.isEmpty()) {
                List<String> sortedKeys = customDimensions.keySet().stream().sorted().toList();
                sb.append("-DIMS[");
                for (String key : sortedKeys) {
                    BigDecimal value = customDimensions.get(key);
                    sb.append(key).append("=").append(value != null ? value.stripTrailingZeros().toPlainString() : "null").append(";");
                }
                sb.append("]");
            }
            if (customGlaze != null) sb.append("-G").append(customGlaze.hashCode());
            if (customRoofColor != null) sb.append("-RC").append(customRoofColor.hashCode());
            if (customRoofOverstep != null) sb.append("-RO").append(customRoofOverstep.hashCode());
            if (customDesignAttr != null) sb.append("-CD").append(customDesignAttr.hashCode());
            if (customHasDivider) sb.append("-Di");
            if (customHasGutter) sb.append("-Gu");
            if (customHasGardenShed) sb.append("-Sh");
            if (selectedAddons != null && !selectedAddons.isEmpty()) {
                List<AddonDto> sortedAddons = selectedAddons.stream()
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(AddonDto::getAddonId, Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList();
                sb.append("-ADNS[");
                for (AddonDto addon : sortedAddons) {
                    sb.append(addon.getAddonId()).append("x").append(addon.getQuantity()).append(";");
                }
                sb.append("]");
            }
        } else {
            sb.append("-S");
            if (selectedDesignId != null) sb.append("-D").append(selectedDesignId);
            if (selectedGlazeId != null) sb.append("-G").append(selectedGlazeId);
            if (selectedRoofColorId != null) sb.append("-RC").append(selectedRoofColorId);
        }
        return sb.toString();
    }

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

    @Override
    public String toString() {
        // Můžete upravit, jaké informace chcete v toString vidět
        return "CartItem{" +
                "cartItemId='" + cartItemId + '\'' +
                ", productId=" + productId +
                ", quantity=" + quantity +
                ", taxRatePercent=" + taxRatePercent +
                '}';
    }
}
