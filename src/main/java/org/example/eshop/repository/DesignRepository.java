package org.example.eshop.repository;

import org.example.eshop.model.Design;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DesignRepository extends JpaRepository<Design, Long> {
    Optional<Design> findByNameIgnoreCase(String name);

    @Query("SELECT d FROM Design d WHERE d.id = :id") // Explicitní dotaz
    @EntityGraph(attributePaths = {"products"})
    Optional<Design> findByIdWithProducts(@Param("id") Long id); // Nová metoda
}