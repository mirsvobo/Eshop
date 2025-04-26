package org.example.eshop.repository; // Uprav dle své struktury

import org.example.eshop.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    Optional<PasswordResetToken> findByCustomerEmailIgnoreCase(String email); // Pro hledání existujícího tokenu
    void deleteByToken(String token); // Pro smazání po použití
    void deleteByExpiryDateBefore(java.time.LocalDateTime now); // Pro čištění expirovaných
}