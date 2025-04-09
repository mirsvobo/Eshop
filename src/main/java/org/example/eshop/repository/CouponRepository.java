package org.example.eshop.repository;

import org.example.eshop.model.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional; // Import Optional

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /**
     * Najde kupón podle jeho kódu (bez ohledu na velikost písmen).
     * @param code Kód kupónu.
     * @return Optional obsahující kupón, pokud byl nalezen.
     */
    Optional<Coupon> findByCodeIgnoreCase(String code);
}