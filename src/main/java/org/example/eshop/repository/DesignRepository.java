package org.example.eshop.repository;

import org.example.eshop.model.Design;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DesignRepository extends JpaRepository<Design, Long> {
    Optional<Design> findByNameIgnoreCase(String name);
}