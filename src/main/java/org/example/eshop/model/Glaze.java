package org.example.eshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Set;

@Entity
@Getter
@Setter
public class Glaze {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name; // Např. "Ořech", "Bezbarvý lak"

    @Column(length = 255)
    private String description;

    @Column(length = 1000)
    private String imageUrl; // URL obrázku vzorku

    // Příplatky za tuto lazuru (mohou být nulové)
    @Column(precision = 10, scale = 2)
    private BigDecimal priceSurchargeCZK;

    @Column(precision = 10, scale = 2)
    private BigDecimal priceSurchargeEUR;

    @Column(nullable = false)
    private boolean active = true;

    // Produkty, které mohou mít tuto lazuru
    @ManyToMany(mappedBy = "availableGlazes", fetch = FetchType.LAZY)
    private Set<Product> products;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Glaze glaze = (Glaze) o;
        return id != null && id.equals(glaze.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}