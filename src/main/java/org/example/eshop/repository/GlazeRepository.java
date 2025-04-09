package org.example.eshop.repository;

import org.example.eshop.model.Glaze;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GlazeRepository extends JpaRepository<Glaze, Long> {
    Optional<Glaze> findByNameIgnoreCase(String name);
}