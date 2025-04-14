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

    // --- Stávající metody (beze změny) ---
    Optional<Product> findBySlugIgnoreCase(String slug);
    // findByActiveTrueAndSlugIgnoreCase - nahrazeno níže findActiveBySlugWithDetails
    // findByActiveTrue(Pageable) - nahrazeno níže
    // findByActiveTrue() - nahrazeno níže
    boolean existsBySlugIgnoreCase(String newSlug);

    // --- Optimalizované metody ---

    /**
     * Najde aktivní produkty se základními detaily pro stránkovaný seznam (obrázky, daň, slevy).
     * Používá standardní název metody s EntityGraph.
     */
     // Přepisujeme standardní metodu
    @EntityGraph(attributePaths = {"images", "taxRate", "discounts"})
    Page<Product> findByActiveTrue(Pageable pageable); // <-- PŮVODNÍ NÁZEV, PŘIDÁN @EntityGraph

    /**
     * Najde všechny aktivní produkty se základními detaily pro seznam (obrázky, daň, slevy).
     * Používá standardní název metody s EntityGraph.
     */
    @EntityGraph(attributePaths = {"images", "taxRate", "discounts"})
    List<Product> findAllByActiveTrue(); // <-- PŮVODNÍ NÁZEV, PŘIDÁN @EntityGraph


    /**
     * Najde aktivní produkt podle slugu se všemi potřebnými detaily pro frontend zobrazení.
     * Používá explicitní @Query a @EntityGraph.
     */
    @Query("SELECT p FROM Product p WHERE p.active = true AND lower(p.slug) = lower(:slug)")
    @EntityGraph(attributePaths = {
            "taxRate", "images", "availableDesigns", "availableGlazes",
            "availableRoofColors", "availableAddons", "configurator", "discounts"
    })
    Optional<Product> findActiveBySlugWithDetails(@Param("slug") String slug);


    /**
     * Najde produkt podle ID se všemi potřebnými detaily pro admin formulář.
     * Načítá i neaktivní produkty.
     * Používá explicitní @Query a @EntityGraph.
     */
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    @EntityGraph(attributePaths = {
            "taxRate", "images", "availableDesigns", "availableGlazes",
            "availableRoofColors", "availableAddons", "configurator", "discounts"
    })
    Optional<Product> findByIdWithDetails(@Param("id") Long id);

}