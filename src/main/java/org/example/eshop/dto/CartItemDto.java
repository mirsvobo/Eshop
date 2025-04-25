package org.example.eshop.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@ToString // Přidáno pro snadnější logování obsahu DTO
public class CartItemDto {

    @NotNull(message = "Product ID is required.")
    private Long productId;

    @Min(value = 1, message = "Quantity must be at least 1.")
    private int quantity = 1;

    // Toto pole je klíčové! Název musí přesně odpovídat name atributu v HTML inputu.
    private boolean isCustom = false;

    @NotNull(message = "Design musí být vybrán.")
    private Long selectedDesignId;

    @NotNull(message = "Lazura musí být vybrána.")
    private Long selectedGlazeId;

    @NotNull(message = "Barva střechy musí být vybrána.")
    private Long selectedRoofColorId;

    @NotNull(message = "Daňová sazba musí být vybrána.")
    private Long selectedTaxRateId;

    // --- Atributy pro custom produkt ---
    // Klíče v mapě musí být "length", "width", "height"
    // Validace této mapy může být komplexní, zvažte vlastní validátor, pokud je potřeba
    private Map<String, BigDecimal> customDimensions;

    private String customRoofOverstep; // Není validováno zde, může být null
    private boolean customHasDivider = false;
    private boolean customHasGutter = false;
    private boolean customHasGardenShed = false;

    // Seznam ID vybraných addonů
    private List<Long> selectedAddonIds;
}