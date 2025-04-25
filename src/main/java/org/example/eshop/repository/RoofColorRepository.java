package org.example.eshop.repository;

import org.example.eshop.model.RoofColor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoofColorRepository extends JpaRepository<RoofColor, Long> {
    Optional<RoofColor> findByNameIgnoreCase(String name);

    @Query("SELECT r FROM RoofColor r WHERE r.id = :id") // Explicitní dotaz
    @EntityGraph(attributePaths = {"products"})
    Optional<RoofColor> findByIdWithProducts(@Param("id") Long id); // Nová metoda
}