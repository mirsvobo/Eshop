package org.example.eshop.repository;

import org.example.eshop.model.RoofColor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoofColorRepository extends JpaRepository<RoofColor, Long> {
    Optional<RoofColor> findByNameIgnoreCase(String name);
}