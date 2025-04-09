package org.example.eshop.repository;

import org.example.eshop.model.Addon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AddonsRepository extends JpaRepository<Addon, Long> {

    Optional<Addon> findByNameIgnoreCase(String name);

}