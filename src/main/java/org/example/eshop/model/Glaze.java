package org.example.eshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

@Entity
@Getter
@Setter
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Table(name = "glaze", indexes = { // Přidána anotace @Table
        @Index(name = "idx_glaze_name", columnList = "name", unique = true)
})
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

    // V Glaze.java
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        // Kontrola null a třídy, ale pozor na proxy objekty Hibernate! Bezpečnější je:
        // if (o == null || !(o instanceof Glaze)) return false;
        // Nebo pokud používáš Hibernate specifické kontroly:
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Glaze glaze = (Glaze) o;
        // Porovnáváme podle ID, jen pokud není null a je nenulové (tj. entita je perzistovaná)
        return id != null && id.equals(glaze.id);
    }

    @Override
    public int hashCode() {
        // Hash kód založený na třídě, pokud není ID (pro transientní instance)
        // Nebo lépe použít konstantu, pokud ID není null
        return id != null ? Objects.hash(id) : getClass().hashCode();
        // Alternativně: return getClass().hashCode(); // Jednodušší, ale méně optimální pro Set operace PŘED uložením
    }}
