package org.example.eshop.repository;

import org.example.eshop.model.Discount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DiscountRepository extends JpaRepository<Discount, Long> {

    @EntityGraph(attributePaths = {"products"}) // Načti rovnou i produkty
    @Query("SELECT d FROM Discount d WHERE d.active = true " +
            "AND (d.validFrom IS NULL OR d.validFrom <= :now) " +
            "AND (d.validTo IS NULL OR d.validTo >= :now)")
    List<Discount> findAllActiveAndPotentiallyApplicable(@Param("now") LocalDateTime now);

    Optional<Discount> findWithProductsById(Long id);
}
