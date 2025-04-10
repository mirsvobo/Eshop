package org.example.eshop.repository;

import org.example.eshop.model.Coupon;
import org.example.eshop.model.Customer;
import org.example.eshop.model.Order;
import org.example.eshop.model.OrderState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor; // <-- PŘIDÁNO
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
// Přidáno JpaSpecificationExecutor<Order>
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    // --- Metody používané v OrderService a pro základní funkce ---

    Optional<Order> findByOrderCode(String orderCode);

    List<Order> findByCustomerOrderByOrderDateDesc(Customer customer);

    List<Order> findByCustomerIdOrderByOrderDateDesc(Long customerId);


    // --- Metody pro CMS / Reporting / Specifické kontroly ---
    // Tyto metody pro kombinované vyhledávání už NEBUDOU primárně potřeba,
    // protože budeme používat Specifications. Můžeš je ponechat pro specifické
    // případy nebo je odstranit. Zde je necháme prozatím.

    // Najde objednávky podle stavu (přes objekt OrderState)
    Page<Order> findByStateOfOrder(OrderState state, Pageable pageable);

    // Najde objednávky podle kódu stavu (case-insensitive)
    Page<Order> findByStateOfOrder_CodeIgnoreCase(String stateCode, Pageable pageable);

    // Najde objednávky podle data vytvoření
    Page<Order> findByOrderDateBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    // Najde objednávky podle zákazníka (email obsahuje...)
    Page<Order> findByCustomer_EmailContainingIgnoreCase(String emailFragment, Pageable pageable);

    // Najde objednávky podle kombinace emailu a stavu objednávky (podle ID stavu)
    // Tuto možná budeme potřebovat, pokud nebudeme chtít používat specifikaci pro jednoduché případy
    Page<Order> findByCustomer_EmailContainingIgnoreCaseAndStateOfOrder_Id(
            String emailFragment, Long stateId, Pageable pageable);


    // Metoda pro počítání objednávek ve stavu
    long countByStateOfOrderId(Long stateId);

    long countByCustomerAndAppliedCoupon(Customer customer, Coupon coupon);

    Optional<Order> findBySfProformaInvoiceIdOrSfTaxDocumentIdOrSfFinalInvoiceId(Long sfInvoiceId, Long sfInvoiceId1, Long sfInvoiceId2);

    long countByCustomerIdAndAppliedCouponId(Long id, Long id1);
}