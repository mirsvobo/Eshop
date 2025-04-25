package org.example.eshop.repository; // Nebo jiný vhodný balíček

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.example.eshop.model.Customer;
import org.example.eshop.model.Order;
import org.example.eshop.model.OrderState;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

public class OrderSpecifications {

    /**
     * Vytvoří specifikaci pro filtrování podle emailu zákazníka (case-insensitive).
     *
     * @param email Email nebo jeho část.
     * @return Specification nebo null, pokud je email prázdný.
     */
    public static Specification<Order> customerEmailContains(String email) {
        return StringUtils.hasText(email) ? (root, query, cb) -> {
            // Potřebujeme join na Customer entitu
            Join<Order, Customer> customerJoin = root.join("customer", JoinType.INNER);
            // Hledání case-insensitive
            return cb.like(cb.lower(customerJoin.get("email")), "%" + email.toLowerCase() + "%");
        } : null; // Vrátí null, pokud není email zadán
    }

    /**
     * Vytvoří specifikaci pro filtrování podle ID stavu objednávky.
     *
     * @param stateId ID stavu.
     * @return Specification nebo null, pokud stateId je null.
     */
    public static Specification<Order> hasStateId(Long stateId) {
        return stateId != null ? (root, query, cb) -> {
            // Potřebujeme join na OrderState entitu
            Join<Order, OrderState> stateJoin = root.join("stateOfOrder", JoinType.INNER);
            return cb.equal(stateJoin.get("id"), stateId);
        } : null;
    }

    /**
     * Vytvoří specifikaci pro filtrování podle stavu platby (case-insensitive).
     *
     * @param paymentStatus Stav platby.
     * @return Specification nebo null, pokud je paymentStatus prázdný.
     */
    public static Specification<Order> hasPaymentStatus(String paymentStatus) {
        return StringUtils.hasText(paymentStatus) ? (root, query, cb) ->
                cb.equal(cb.lower(root.get("paymentStatus")), paymentStatus.toLowerCase()) : null;
    }

    /**
     * Vytvoří specifikaci pro filtrování objednávek od zadaného data a času (včetně).
     *
     * @param from Počáteční datum a čas.
     * @return Specification nebo null, pokud from je null.
     */
    public static Specification<Order> orderDateFrom(LocalDateTime from) {
        return from != null ? (root, query, cb) ->
                cb.greaterThanOrEqualTo(root.get("orderDate"), from) : null;
    }

    /**
     * Vytvoří specifikaci pro filtrování objednávek do zadaného data a času (včetně).
     *
     * @param to Konečné datum a čas.
     * @return Specification nebo null, pokud to je null.
     */
    public static Specification<Order> orderDateTo(LocalDateTime to) {
        return to != null ? (root, query, cb) ->
                cb.lessThanOrEqualTo(root.get("orderDate"), to) : null;
    }
}