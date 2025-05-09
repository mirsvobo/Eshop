package org.example.eshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CacheConcurrencyStrategy; // Přidáno pro cache

@Getter
@Setter
@Entity
@jakarta.persistence.Cacheable // JPA standardní anotace pro cachování
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE) // Specifické pro Hibernate
@Table(name = "image", indexes = { // Název tabulky by měl být "image"
        @Index(name = "idx_image_product_display_order", columnList = "product_id, displayOrder, id")
})
public class Image {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(length = 255)
    private String altText;

    @Column(length = 255)
    private String titleText;

    @Column(name = "display_order") // Explicitní název sloupce, pokud se liší
    private Integer displayOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false) // product_id by neměl být null
    private Product product;
}