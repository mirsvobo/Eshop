package org.example.eshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.Set;

@Setter
@Getter
@Entity
public class Addon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Použít Long místo long

    @Column(nullable = false, unique = true) private String name;
    @Lob @Column(columnDefinition = "TEXT") private String description;

    // Ceny doplňku (bez DPH)
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal priceCZK;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal priceEUR;

    @Column(unique = true, length=100) private String sku;
    @Column(nullable = false) private boolean active = true;

    @ManyToMany(mappedBy = "availableAddons", fetch = FetchType.LAZY)
    private Set<Product> products;
}