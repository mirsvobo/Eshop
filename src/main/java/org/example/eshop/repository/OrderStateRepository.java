package org.example.eshop.repository;

import org.example.eshop.model.OrderState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderStateRepository extends JpaRepository<OrderState, Long> {

    /**
     * Najde stav objednávky podle jeho kódu (bez ohledu na velikost písmen).
     *
     * @param code Kód stavu (např. "NEW", "SHIPPED").
     * @return Optional obsahující OrderState, pokud existuje.
     */
    Optional<OrderState> findByCodeIgnoreCase(String code);

    /**
     * Vrátí všechny stavy seřazené podle pole displayOrder.
     *
     * @return Seřazený seznam stavů.
     */
    List<OrderState> findAllByOrderByDisplayOrderAsc();
}