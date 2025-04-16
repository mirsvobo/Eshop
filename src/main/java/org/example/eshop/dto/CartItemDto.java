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

    @NotNull(message = "Design musí být vybrán.")
    private Long selectedDesignId;

    @NotNull(message = "Lazura musí být vybrána.")
    private Long selectedGlazeId;

    @NotNull(message = "Barva střechy musí být vybrána.")
    private Long selectedRoofColorId;

    // *** NOVÉ POLE: ID vybrané daňové sazby ***
    @NotNull(message = "Daňová sazba musí být vybrána.")
    private Long selectedTaxRateId;

    // --- Atributy pro custom produkt (zůstávají) ---
    private Map<String, BigDecimal> customDimensions;
    private String customRoofOverstep;
    private boolean customHasDivider = false;
    private boolean customHasGutter = false;
    private boolean customHasGardenShed = false;

    private List<AddonDto> selectedAddons;
}