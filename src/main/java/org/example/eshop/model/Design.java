package org.example.eshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.math.BigDecimal; // <-- Přidat import
import java.util.Set;
import java.util.Objects;

@Entity
@Getter
@Setter
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Table(name = "design", indexes = { // Přidána anotace @Table
        @Index(name = "idx_design_name", columnList = "name", unique = true)
})
public class Design {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToMany(mappedBy = "availableDesigns", fetch = FetchType.LAZY)
    private Set<Product> products;

    // --- NOVÁ POLE ---
    @Column(length = 1000)
    private String imageUrl; // URL obrázku vzorku

    @Column(precision = 10, scale = 2)
    private BigDecimal priceSurchargeCZK; // Příplatek v CZK (může být null)

    @Column(precision = 10, scale = 2)
    private BigDecimal priceSurchargeEUR; // Příplatek v EUR (může být null)
    // --- KONEC NOVÝCH POLÍ ---


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Design design = (Design) o;
        return id != null && id.equals(design.id);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : getClass().hashCode();
    }
}