package org.example.eshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
public class OrderItemAddon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Vazba na nadřazenou položku objednávky
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    // Odkaz na původní Addon (může být null, pokud byl smazán z katalogu)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "addon_id")
    private Addon addon;

    // --- Data doplňku v době objednávky (HISTORIE) ---
    @Column(nullable = false)
    private String addonName; // Název doplňku

    // Cena za JEDEN kus doplňku bez DPH v době objednávky
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal addonPriceWithoutTax;

    @Column(nullable = false)
    private Integer quantity = 1; // Počet kusů doplňku (obvykle 1)

    // Celková cena za tento typ doplňku (addonPriceWithoutTax * quantity)
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPriceWithoutTax;

    // Poznámka: Daň pro doplněk se předpokládá stejná jako pro hlavní položku (OrderItem).
    // Celková cena položky (OrderItem) již zahrnuje cenu všech jejích doplňků.
}