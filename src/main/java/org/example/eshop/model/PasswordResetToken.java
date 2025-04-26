package org.example.eshop.model; // Uprav dle své struktury

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class PasswordResetToken {

    private static final int EXPIRATION_HOURS = 1; // Platnost tokenu (např. 1 hodina)

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @OneToOne(targetEntity = Customer.class, fetch = FetchType.EAGER)
    @JoinColumn(nullable = false, name = "customer_id")
    private Customer customer;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    public PasswordResetToken() {
        this.expiryDate = calculateExpiryDate();
    }

    public PasswordResetToken(String token, Customer customer) {
        this.token = token;
        this.customer = customer;
        this.expiryDate = calculateExpiryDate();
    }

    private LocalDateTime calculateExpiryDate() {
        return LocalDateTime.now().plusHours(EXPIRATION_HOURS);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryDate);
    }
}