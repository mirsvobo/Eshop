package org.example.eshop.repository;

import org.example.eshop.model.Discount;
import org.example.eshop.model.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DiscountRepository extends JpaRepository<Discount, Long> {
    @EntityGraph(value = "Order.fetchFullDetail")
    Optional<Order> findFullDetailById(Long id);

    Optional<Discount> findWithProductsById(Long id);
}
