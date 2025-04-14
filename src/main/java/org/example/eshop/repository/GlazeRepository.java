package org.example.eshop.repository;

import org.example.eshop.model.Glaze;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query; // <-- Import
import org.springframework.data.repository.query.Param; // <-- Import
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface GlazeRepository extends JpaRepository<Glaze, Long> {
    Optional<Glaze> findByNameIgnoreCase(String name);

    @Query("SELECT g FROM Glaze g WHERE g.id = :id") // Explicitní dotaz
    @EntityGraph(attributePaths = {"products"})
    Optional<Glaze> findByIdWithProducts(@Param("id") Long id); // Nová metoda
}