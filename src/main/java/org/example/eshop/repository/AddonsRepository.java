package org.example.eshop.repository;

import org.example.eshop.model.Addon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddonsRepository extends JpaRepository<Addon, Long> {

    // Návratový typ je správně Optional<Addon>
    Optional<Addon> findByNameIgnoreCase(String name);

    // *** OPRAVENÝ NÁVRATOVÝ TYP ***
    Optional<Addon> findBySkuIgnoreCase(String sku);

    // Metoda pro AddonsService.getAllActiveAddons() - pokud ještě neexistuje
    List<Addon> findByActiveTrueOrderByNameAsc();

}