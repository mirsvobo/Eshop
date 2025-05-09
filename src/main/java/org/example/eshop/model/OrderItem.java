package org.example.eshop.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "order_item", indexes = { // Správný název tabulky a index
        @Index(name = "idx_orderitem_order_id", columnList = "order_id")
})
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonBackReference
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    // --- Data produktu/konfigurace v době objednávky (HISTORIE) ---
    @Column(nullable = false)
    private String productName;
    @Column(length = 100)
    private String sku;
    private String variantInfo; // Textový popis vybrané kombinace
    private boolean isCustomConfigured;

    // Pole 'model' nyní ukládá VYBRANÝ design/model pro standardní produkt
    // NEBO textový název designu zadaný u custom produktu
    @Column(length = 100)
    private String model;

    @Column(length = 100)
    private String material; // Základní materiál z produktu
    @Column(precision = 10, scale = 2)
    private BigDecimal height; // Finální výška (z produktu nebo custom)
    @Column(precision = 10, scale = 2)
    private BigDecimal width;  // Finální hloubka
    @Column(precision = 10, scale = 2)
    private BigDecimal length; // Finální Šířka
    @Column(length = 100)
    private String glaze; // Finální lazura (vybraná nebo custom)
    @Column(length = 100)
    private String roofColor; // Finální barva střechy
    @Column(length = 100)
    private String roofOverstep; // Finální přesah
    // *** NOVÉ POLE: 'design' je nyní jen pro textový vstup u custom ***
    @Column(length = 100)
    private String design; // Textový design zadaný u custom produktu

    // --- Příznaky dynamických doplňků/voleb (jen pro custom) ---
    private Boolean hasDivider;
    private Boolean hasGutter;
    private Boolean hasGardenShed;

    // --- Množství a ceny ---
    @Column(nullable = false)
    private Integer count;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPriceWithoutTax;
    // *** UPRAVENO: Pole pro uloženou sazbu a příznak RC ***
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal taxRate; // Hodnota vybrané sazby
    @Column(nullable = false)
    private boolean isReverseCharge; // Příznak RC vybrané sazby

    // *** NOVÉ: Nepovinná pole pro referenci a historii ***
    @Column(nullable = true)
    private Long selectedTaxRateId; // ID vybrané TaxRate entity
    @Column(nullable = true, length = 100)
    private String selectedTaxRateName; // Název vybrané sazby pro info
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitTaxAmount;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPriceWithTax;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPriceWithoutTax;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalTaxAmount;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPriceWithTax;

    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("addonName ASC")
    private List<OrderItemAddon> selectedAddons;
}