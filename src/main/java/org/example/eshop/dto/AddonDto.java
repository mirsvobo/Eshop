package org.example.eshop.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable; // <-- PŘIDAT IMPORT

/**
 * DTO pro doplněk vybraný k položce objednávky.
 * Nyní obsahuje i název pro zobrazení v košíku.
 */
@Getter
@Setter
public class AddonDto implements Serializable { // <-- PŘIDAT IMPLEMENTS

    // Přidat serialVersionUID pro dobrou praxi
    private static final long serialVersionUID = 1L;

    @NotNull(message = "Addon ID is required.")
    private Long addonId;

    @Min(value = 1, message = "Addon quantity must be at least 1.")
    private int quantity = 1;

    private String addonName; // Název doplňku pro zobrazení v košíku

    // Konstruktory, pokud by byly potřeba
    public AddonDto() {}

    public AddonDto(Long addonId, int quantity) {
        this.addonId = addonId;
        this.quantity = quantity;
    }
}