package org.example.eshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Image {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000) // URL nebo cesta k souboru
    private String url;

    @Column(length = 255)
    private String altText; // Alternativní text pro SEO a přístupnost

    @Column(length = 255)
    private String titleText; // Text titulku (zobrazí se při najetí myši)

    private Integer displayOrder = 0; // Pořadí zobrazení (0=hlavní, 1, 2...)

    // Obrázek patří POUZE k produktu
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id") // Může být null? Asi by nemělo. Záleží na logice.
    private Product product;

    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "product_variant_id") // Může být null
    // private ProductVariant productVariant; // <<< SMAZÁNO
}