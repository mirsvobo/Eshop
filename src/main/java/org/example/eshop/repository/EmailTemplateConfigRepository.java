package org.example.eshop.repository;

import org.example.eshop.model.EmailTemplateConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailTemplateConfigRepository extends JpaRepository<EmailTemplateConfig, Long> {

    /**
     * Najde konfiguraci emailu podle kódu stavu objednávky (case-insensitive).
     *
     * @param stateCode Kód stavu (např. "SHIPPED").
     * @return Optional obsahující konfiguraci, pokud byla nalezena.
     */
    Optional<EmailTemplateConfig> findByStateCodeIgnoreCase(String stateCode);
}