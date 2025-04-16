package org.example.eshop.model;

import lombok.Getter;
import lombok.Setter;
import org.example.eshop.config.PriceConstants;
import org.example.eshop.dto.AddonDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Getter
@Setter
public class CartItem implements Serializable, PriceConstants {
    @Serial
    private static final long serialVersionUID = 3L; // Zvýšena verze kvůli změně DPH
    private static final Logger log = LoggerFactory.getLogger(CartItem.class);

    private String cartItemId;
    private Long productId;
    private String productName;
    private String productSlug;
    private String imageUrl;
    private int quantity;
    private boolean isCustom;

    private BigDecimal length;
    private BigDecimal width;
    private BigDecimal height;

    private Long selectedDesignId;
    private Long selectedGlazeId;
    private Long selectedRoofColorId;

    private String selectedDesignName;
    private String selectedGlazeName;
    private String selectedRoofColorName;

    // *** ZMĚNA: Pole pro DPH ***
    private Long selectedTaxRateId;       // ID vybrané sazby z formuláře
    private BigDecimal selectedTaxRateValue;  // Hodnota (např. 0.21) vybrané sazby
    private boolean selectedIsReverseCharge; // Příznak RC vybrané sazby
    // Původní: private BigDecimal taxRatePercent;

    private Map<String, BigDecimal> customDimensions;
    private String customRoofOverstep;
    private boolean customHasDivider;
    private boolean customHasGutter;
    private boolean customHasGardenShed;

    private List<AddonDto> selectedAddons;

    private BigDecimal unitPriceCZK;
    private BigDecimal unitPriceEUR;

    private String variantInfo;

    public CartItem() {}

    public BigDecimal getTotalLinePriceWithoutTax(String currency) {
        BigDecimal unitPrice = EURO_CURRENCY.equals(currency) ? unitPriceEUR : unitPriceCZK;
        if (unitPrice == null || quantity <= 0) {
            log.warn("Cannot calculate total line price without tax. Unit price for {} is null or quantity is zero for cartItemId: {}", currency, cartItemId);
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(PRICE_SCALE, ROUNDING_MODE);
    }

    // *** UPRAVENO: Používá selectedTaxRateValue ***
    public BigDecimal getVatAmount(String currency) {
        BigDecimal linePriceWithoutTax = getTotalLinePriceWithoutTax(currency);
        BigDecimal effectiveTaxRate = selectedTaxRateValue; // Použijeme uloženou hodnotu

        log.trace("DEBUG_VAT (CartItem {}): Calculating VAT. linePriceWithoutTax={}, selectedTaxRateValue={}",
                this.cartItemId, linePriceWithoutTax, effectiveTaxRate);

        if (effectiveTaxRate == null || effectiveTaxRate.compareTo(BigDecimal.ZERO) <= 0 || linePriceWithoutTax.compareTo(BigDecimal.ZERO) == 0) {
            log.trace("DEBUG_VAT (CartItem {}): Returning ZERO VAT due to zero rate/price.", this.cartItemId);
            return BigDecimal.ZERO;
        }

        BigDecimal calculatedVat = linePriceWithoutTax.multiply(effectiveTaxRate).setScale(PRICE_SCALE, ROUNDING_MODE);
        log.trace("DEBUG_VAT (CartItem {}): Calculated VAT amount = {}", this.cartItemId, calculatedVat);
        return calculatedVat;
    }

    public BigDecimal getTotalLinePriceWithTax(String currency) {
        return getTotalLinePriceWithoutTax(currency).add(getVatAmount(currency));
    }

    // *** UPRAVENO: Nepoužívá taxRatePercent, ale selectedTaxRateId ***
    public static String generateCartItemId(Long productId, boolean isCustom,
                                            Long selectedDesignId, String selectedDesignName,
                                            Long selectedGlazeId, String selectedGlazeName,
                                            Long selectedRoofColorId, String selectedRoofColorName,
                                            Map<String, BigDecimal> customDimensions,
                                            Long selectedTaxRateId, // <-- NOVÝ PARAMETR
                                            String customRoofOverstep,
                                            boolean customHasDivider, boolean customHasGutter,
                                            boolean customHasGardenShed, List<AddonDto> selectedAddons) {

        StringBuilder sb = new StringBuilder("P").append(productId);
        // Přidáme ID vybrané sazby DPH
        if (selectedTaxRateId != null) {
            sb.append("-T").append(selectedTaxRateId);
        } else {
            sb.append("-Tnull"); // Pokud by sazba nebyla vybrána (nemělo by nastat)
        }

        if (isCustom) {
            sb.append("-C");
            // Dimenze
            if (customDimensions != null && !customDimensions.isEmpty()) {
                List<String> sortedKeys = customDimensions.keySet().stream().sorted().toList();
                sb.append("-DIMS[");
                for (String key : sortedKeys) {
                    BigDecimal value = customDimensions.get(key);
                    sb.append(key).append("=").append(value != null ? value.stripTrailingZeros().toPlainString() : "null").append(";");
                }
                sb.append("]");
            }
            // Atributy (použijeme ID)
            if (selectedDesignId != null) sb.append("-D").append(selectedDesignId);
            if (selectedGlazeId != null) sb.append("-G").append(selectedGlazeId);
            if (selectedRoofColorId != null) sb.append("-RC").append(selectedRoofColorId);
            // Ostatní custom prvky
            if (customRoofOverstep != null) sb.append("-RO").append(customRoofOverstep.hashCode());
            if (customHasDivider) sb.append("-Di");
            if (customHasGutter) sb.append("-Gu");
            if (customHasGardenShed) sb.append("-Sh");
            // Addony
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
        } else { // Standardní produkt
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
        return "CartItem{" +
                "cartItemId='" + cartItemId + '\'' +
                ", productId=" + productId +
                ", quantity=" + quantity +
                ", selectedTaxRateId=" + selectedTaxRateId + // Přidáno pro info
                '}';
    }
}