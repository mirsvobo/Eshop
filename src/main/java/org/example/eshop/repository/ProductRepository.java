package org.example.eshop.repository;

import org.example.eshop.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySlugIgnoreCase(String slug);

    // Původní zůstává
    Optional<Product> findByActiveTrueAndSlugIgnoreCase(String slug);
    Page<Product> findByActiveTrue(Pageable pageable);
    List<Product> findByActiveTrue();
    boolean existsBySlugIgnoreCase(String newSlug);

    // --- NOVÉ METODY s EntityGraph ---

    /**
     * Najde aktivní produkty se základními detaily pro seznam (obrázky, daň, slevy).
     */
    @EntityGraph(attributePaths = {"images", "taxRate", "discounts"})
    Page<Product> findByActiveTrueOrderBySlugAsc(Pageable pageable);

    /**
     * Najde aktivní produkty se základními detaily pro seznam (obrázky, daň, slevy) - verze pro List.
     */
    @EntityGraph(attributePaths = {"images", "taxRate", "discounts"})
    List<Product> findAllByActiveTrue();

    /**
     * Najde aktivní produkt podle slugu se všemi potřebnými detaily pro frontend zobrazení.
     */
    // --- PŘIDÁNA ANOTACE @Query a @Param ---
    @Query("SELECT p FROM Product p WHERE p.active = true AND lower(p.slug) = lower(:slug)")
    @EntityGraph(attributePaths = { // EntityGraph zůstává
            "taxRate", "images", "availableDesigns", "availableGlazes",
            "availableRoofColors", "availableAddons", "configurator", "discounts"
    })
    Optional<Product> findActiveBySlugWithDetails(@Param("slug") String slug); // Přidáno @Param

    /**
     * Najde produkt podle ID se všemi potřebnými detaily pro admin formulář.
     * Načítá i neaktivní produkty.
     */
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    @EntityGraph(attributePaths = {
            "taxRate", "images", "availableDesigns", "availableGlazes",
            "availableRoofColors", "availableAddons", "configurator", "discounts"
    })
    Optional<Product> findByIdWithDetails(@Param("id") Long id);
}