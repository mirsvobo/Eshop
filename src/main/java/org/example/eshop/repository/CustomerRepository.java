package org.example.eshop.repository;

import org.example.eshop.model.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
// import org.springframework.data.jpa.repository.JpaSpecificationExecutor; // Pro pokročilé filtrování
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> /*, JpaSpecificationExecutor<Customer> */ {

    Optional<Customer> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    // --- Metody pro CMS ---

    /**
     * Najde zákazníky, jejichž email obsahuje daný fragment (case-insensitive).
     */
    Page<Customer> findByEmailContainingIgnoreCase(String emailFragment, Pageable pageable);

    /**
     * Najde zákazníky, jejichž jméno nebo příjmení obsahuje daný fragment (case-insensitive).
     */
    Page<Customer> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(String nameFragment, String nameFragmentAgain, Pageable pageable);

    /**
     * Najde zákazníky podle stavu účtu (aktivní/neaktivní).
     */
    Page<Customer> findByEnabled(boolean enabled, Pageable pageable);

    /**
     * Najde zákazníky podle kombinace emailu a stavu účtu.
     */
    Page<Customer> findByEmailContainingIgnoreCaseAndEnabled(String emailFragment, boolean enabled, Pageable pageable);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // TODO: Přidat další metody pro filtrování podle telefonu, jména firmy atd.
    // Page<Customer> findByInvoiceCompanyNameContainingIgnoreCase(String companyNameFragment, Pageable pageable);
    // Page<Customer> findByPhoneContaining(String phoneFragment, Pageable pageable);
}