package org.example.eshop.repository;

import org.example.eshop.model.Product;
import org.springframework.data.domain.Page; // Import Page
import org.springframework.data.domain.Pageable; // Import Pageable
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List; // Import List
import java.util.Optional; // Import Optional

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Najde produkt podle jeho unikátního slugu (bez ohledu na velikost písmen).
     * @param slug Slug produktu.
     * @return Optional obsahující produkt, pokud byl nalezen.
     */
    Optional<Product> findBySlugIgnoreCase(String slug);

    /**
     * Najde aktivní produkt podle jeho unikátního slugu (bez ohledu na velikost písmen).
     * Použitelné pro zobrazení detailu produktu v e-shopu.
     * @param slug Slug produktu.
     * @return Optional obsahující aktivní produkt, pokud byl nalezen.
     */
    Optional<Product> findByActiveTrueAndSlugIgnoreCase(String slug);

    /**
     * Vrátí stránkovaný seznam všech aktivních produktů.
     * @param pageable Informace o stránkování (číslo stránky, velikost, řazení).
     * @return Stránka s aktivními produkty.
     */
    Page<Product> findByActiveTrue(Pageable pageable);

    /**
     * Vrátí seznam všech aktivních produktů (např. pro sitemapu nebo jednoduchý výpis).
     * @return Seznam aktivních produktů.
     */
    List<Product> findByActiveTrue();

    boolean existsBySlugIgnoreCase(String newSlug);

    // Případně metody pro vyhledávání podle názvu, kategorie atd.
    // Page<Product> findByActiveTrueAndNameContainingIgnoreCase(String name, Pageable pageable);
}