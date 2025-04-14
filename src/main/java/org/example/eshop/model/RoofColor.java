package org.example.eshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.math.BigDecimal;
import java.util.Set;

@Entity
@Getter
@Setter
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class RoofColor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name; // Např. "Antracit", "Červená"

    @Column(length = 255)
    private String description;

    @Column(length = 1000)
    private String imageUrl; // URL obrázku vzorku

    // Příplatky za tuto barvu (mohou být nulové)
    @Column(precision = 10, scale = 2)
    private BigDecimal priceSurchargeCZK;

    @Column(precision = 10, scale = 2)
    private BigDecimal priceSurchargeEUR;

    @Column(nullable = false)
    private boolean active = true;

    // Produkty, které mohou mít tuto barvu střechy
    @ManyToMany(mappedBy = "availableRoofColors", fetch = FetchType.LAZY)
    private Set<Product> products;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoofColor roofColor = (RoofColor) o;
        return id != null && id.equals(roofColor.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}