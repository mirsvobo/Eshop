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
    boolean existsBySlugIgnoreCase(String newSlug);
    boolean existsBySlugIgnoreCaseAndIdNot(String newSlug, Long id);
    Optional<Object> findBySlugIgnoreCaseAndIdNot(String newSlug, Long id); // Zůstává z původního kódu

    @Query("SELECT p FROM Product p WHERE p.active = true AND lower(p.slug) = lower(:slug)")
    @EntityGraph(attributePaths = {
            "images", "availableDesigns", "availableGlazes",
            "availableRoofColors", "availableAddons", "configurator", "discounts",
            "availableTaxRates"
    })
    Optional<Product> findActiveBySlugWithDetails(@Param("slug") String slug);

    @Query("SELECT p FROM Product p WHERE p.id = :id")
    @EntityGraph(attributePaths = {
            "images", "availableDesigns", "availableGlazes",
            "availableRoofColors", "availableAddons", "configurator", "discounts",
            "availableTaxRates"
    })
    Optional<Product> findByIdWithDetails(@Param("id") Long id);

    @EntityGraph(attributePaths = {"images", "discounts", "availableTaxRates"})
    Page<Product> findByActiveTrue(Pageable pageable);

    @EntityGraph(attributePaths = {"images", "discounts", "availableTaxRates"})
    List<Product> findAllByActiveTrue();

    @EntityGraph(attributePaths = {"images", "discounts", "availableTaxRates"})
    Page<Product> findByActiveTrueAndCustomisableFalse(Pageable pageable);

}