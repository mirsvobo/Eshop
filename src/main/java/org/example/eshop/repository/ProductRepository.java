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

    boolean existsBySlugIgnoreCase(String newSlug);

    @Query("SELECT p FROM Product p WHERE p.active = true AND lower(p.slug) = lower(:slug)")
    @EntityGraph(attributePaths = {
            "images", "availableDesigns", "availableGlazes",
            "availableRoofColors", "availableAddons", "configurator", "discounts",
            "availableTaxRates" // <-- PŘIDÁNO availableTaxRates
    })
    Optional<Product> findActiveBySlugWithDetails(@Param("slug") String slug);


    // findByIdWithDetails - přidáme availableTaxRates
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    @EntityGraph(attributePaths = {
            "images", "availableDesigns", "availableGlazes",
            "availableRoofColors", "availableAddons", "configurator", "discounts",
            "availableTaxRates" // <-- PŘIDÁNO availableTaxRates
    })
    Optional<Product> findByIdWithDetails(@Param("id") Long id);

    boolean existsBySlugIgnoreCaseAndIdNot(String newSlug, Long id);

    // findByActiveTrue(Pageable) - OPRAVENO: odstraněn "taxRate" z EntityGraph
    @EntityGraph(attributePaths = {"images", /*"taxRate",*/ "discounts", "availableTaxRates"})
    // <-- Odstraněno "taxRate"
    Page<Product> findByActiveTrue(Pageable pageable);

    // findAllByActiveTrue() - OPRAVENO: odstraněn "taxRate" z EntityGraph
    @EntityGraph(attributePaths = {"images", /*"taxRate",*/ "discounts", "availableTaxRates"})
    // <-- Odstraněno "taxRate"
    List<Product> findAllByActiveTrue();

    Optional<Object> findBySlugIgnoreCaseAndIdNot(String newSlug, Long id);
}

