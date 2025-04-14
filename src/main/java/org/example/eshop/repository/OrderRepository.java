// src/main/java/org/example/eshop/repository/OrderRepository.java
package org.example.eshop.repository;

import org.example.eshop.model.Coupon;
import org.example.eshop.model.Customer;
import org.example.eshop.model.Order;
import org.example.eshop.model.OrderState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query; // <-- PŘIDAT IMPORT
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    @Query(value = "SELECT o.order_code FROM customer_orders o WHERE o.order_code REGEXP '^[0-9]+$' ORDER BY CAST(o.order_code AS UNSIGNED) DESC LIMIT 1", nativeQuery = true)
    Optional<String> findMaxNumericOrderCode();
    // --- KONEC NOVÉ METODY ---

    long countByCustomerIdAndAppliedCouponId(Long id, Long id1); // Zachovat, pokud se používá jinde

    // --- Metody používané v OrderService a pro základní funkce (zachovat) ---
    Optional<Order> findByOrderCode(String orderCode);
    List<Order> findByCustomerOrderByOrderDateDesc(Customer customer);
    List<Order> findByCustomerIdOrderByOrderDateDesc(Long customerId);

    // Najde objednávky podle stavu (přes objekt OrderState)
    Page<Order> findByStateOfOrder(OrderState state, Pageable pageable);
    // Najde objednávky podle kódu stavu (case-insensitive)
    Page<Order> findByStateOfOrder_CodeIgnoreCase(String stateCode, Pageable pageable);
    // Najde objednávky podle data vytvoření
    Page<Order> findByOrderDateBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
    // Najde objednávky podle zákazníka (email obsahuje...)
    Page<Order> findByCustomer_EmailContainingIgnoreCase(String emailFragment, Pageable pageable);
    // Najde objednávky podle kombinace emailu a stavu objednávky (podle ID stavu)
    Page<Order> findByCustomer_EmailContainingIgnoreCaseAndStateOfOrder_Id(String emailFragment, Long stateId, Pageable pageable);

    // Metoda pro počítání objednávek ve stavu
    long countByStateOfOrderId(Long stateId);
    Optional<Order> findBySfProformaInvoiceIdOrSfTaxDocumentIdOrSfFinalInvoiceId(Long sfInvoiceId, Long sfInvoiceId1, Long sfInvoiceId2);

    long countByOrderDateBetween(LocalDateTime start, LocalDateTime end);

    long countByPaymentStatusIgnoreCase(String paymentStatus);

    long countByStateOfOrder_CodeIgnoreCase(String statusCode);

}