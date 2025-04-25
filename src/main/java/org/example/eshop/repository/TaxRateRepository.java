package org.example.eshop.repository;

import org.example.eshop.model.TaxRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TaxRateRepository extends JpaRepository<TaxRate, Long> {

    /**
     * Najde daňovou sazbu podle jejího názvu (bez ohledu na velikost písmen).
     *
     * @param name Název sazby.
     * @return Optional obsahující sazbu, pokud byla nalezena.
     */
    Optional<TaxRate> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String trim);
}