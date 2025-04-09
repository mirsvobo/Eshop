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

    // --- Atributy pro standardní produkt vybírané selectem (nyní ID) ---
    @NotNull(message = "Lazura musí být vybrána.") // Přidána validace
    private Long selectedGlazeId;

    @NotNull(message = "Barva střechy musí být vybrána.") // Přidána validace
    private Long selectedRoofColorId;

    @NotNull(message = "Design musí být vybrán.") // Přidána validace
    private Long selectedDesignId;

    // --- Atributy pro custom produkt (zůstávají String) ---
    private Map<String, BigDecimal> customDimensions;
    private String customGlaze;
    private String customRoofColor;
    private String customRoofOverstep;
    private String customDesign;
    private boolean customHasDivider = false;
    private boolean customHasGutter = false;
    private boolean customHasGardenShed = false;

    private List<AddonDto> selectedAddons; // Jen pro custom
}