package org.example.eshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;
import java.util.Objects; // Import pro Objects.hash

@Entity
@Getter
@Setter
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

    // NEMÁ příplatky

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Design design = (Design) o;
        return id != null && id.equals(design.id);
    }

    @Override
    public int hashCode() {
        // Použít Objects.hash pro konzistentní hash kód založený na ID
        // nebo vrátit pevnou hodnotu, pokud je ID null (pro nové entity)
        return id != null ? Objects.hash(id) : getClass().hashCode();
    }
}