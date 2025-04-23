package org.example.eshop.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern; // For pricingType validation
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.math.BigDecimal;
import java.util.Set;

@Setter
@Getter
@Entity
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Table(name = "addon", indexes = {
        @Index(name = "idx_addon_name", columnList = "name"), // unique = true removed temporarily if addons in different categories can share names
        @Index(name = "idx_addon_sku", columnList = "sku", unique = true),
        @Index(name = "idx_addon_category", columnList = "category") // Index for category
})
public class Addon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Název doplňku nesmí být prázdný.")
    @Column(nullable = false)
    private String name;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    // --- New Fields ---
    @NotBlank(message = "Kategorie doplňku musí být vyplněna.")
    @Column(nullable = false)
    private String category = "Ostatní"; // Default category

    @NotBlank(message = "Typ ceny musí být vybrán.")
    @Pattern(regexp = "^(FIXED|PER_CM_WIDTH|PER_CM_LENGTH|PER_CM_HEIGHT|PER_SQUARE_METER)$",
            message = "Neplatný typ ceny. Povolené hodnoty: FIXED, PER_CM_WIDTH, PER_CM_LENGTH, PER_CM_HEIGHT, PER_SQUARE_METER")
    @Column(nullable = false, length = 20)
    private String pricingType = "FIXED"; // Default pricing type

    // --- Pricing Fields ---
    // Used for pricingType = FIXED
    @Column(precision = 10, scale = 2)
    private BigDecimal priceCZK;
    @Column(precision = 10, scale = 2)
    private BigDecimal priceEUR;

    // Used for dimensional pricing types (PER_CM_*, PER_SQUARE_METER)
    @Column(precision = 10, scale = 4) // Higher precision for unit prices
    private BigDecimal pricePerUnitCZK;
    @Column(precision = 10, scale = 4) // Higher precision for unit prices
    private BigDecimal pricePerUnitEUR;

    // --- Other Fields ---
    @Column(unique = true, length=100)
    private String sku;

    @NotNull // Ensure active field is never null
    @Column(nullable = false)
    private boolean active = true;

    @ManyToMany(mappedBy = "availableAddons", fetch = FetchType.LAZY)
    private Set<Product> products;

    // --- Validation Logic Helper (not persisted) ---
    @AssertTrue(message = "Pro typ ceny 'FIXED' musí být vyplněna 'Cena CZK' a 'Cena EUR'.")
    public boolean isFixedPriceValid() {
        if ("FIXED".equals(pricingType)) {
            return priceCZK != null && priceEUR != null;
        }
        return true; // Not applicable otherwise
    }

    @AssertTrue(message = "Pro dimenzionální ceny musí být vyplněna 'Cena za jednotku CZK' a 'Cena za jednotku EUR'.")
    public boolean isDimensionalPriceValid() {
        if (!"FIXED".equals(pricingType)) {
            return pricePerUnitCZK != null && pricePerUnitEUR != null;
        }
        return true; // Not applicable otherwise
    }
}