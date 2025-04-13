package org.example.eshop.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class CartItemDto {

    @NotNull(message = "Product ID is required.")
    private Long productId;

    @Min(value = 1, message = "Quantity must be at least 1.")
    private int quantity = 1;

    private boolean isCustom = false;

    // --- Atributy vybírané selectem (použijí se pro standardní i custom) ---
    @NotNull(message = "Design musí být vybrán.")
    private Long selectedDesignId;

    @NotNull(message = "Lazura musí být vybrána.")
    private Long selectedGlazeId;

    @NotNull(message = "Barva střechy musí být vybrána.")
    private Long selectedRoofColorId;

    // --- Atributy pro custom produkt (zůstávají) ---
    private Map<String, BigDecimal> customDimensions;
    // Odebráno: private String customGlaze;
    // Odebráno: private String customRoofColor;
    private String customRoofOverstep; // Ponecháme, pokud je to stále textový vstup
    // Odebráno: private String customDesign;
    private boolean customHasDivider = false;
    private boolean customHasGutter = false;
    private boolean customHasGardenShed = false;

    private List<AddonDto> selectedAddons; // Jen pro custom
}