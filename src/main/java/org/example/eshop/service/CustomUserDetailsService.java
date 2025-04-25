package org.example.eshop.service; // Nebo např. org.example.eshop.security

import org.example.eshop.model.Customer;
import org.example.eshop.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Service // Označí tuto třídu jako Spring Bean
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(CustomUserDetailsService.class);

    @Autowired
    private CustomerRepository customerRepository;

    @Override
    @Transactional(readOnly = true) // Transakce pro případné LAZY načítání rolí
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("Attempting to load user by email: {}", email);

        // Najdeme zákazníka podle emailu (ignorujeme velikost písmen)
        Customer customer = customerRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> {
                    log.warn("User not found with email: {}", email);
                    return new UsernameNotFoundException("User not found with email: " + email);
                });

        // Zkontrolujeme, zda je účet aktivní
        if (!customer.isEnabled()) {
            log.warn("User account is disabled for email: {}", email);
            throw new UsernameNotFoundException("User account is disabled: " + email);
        }

        log.debug("User found: {}, Enabled: {}, Roles: {}", customer.getEmail(), true, customer.getRoles());

        // Převedeme role zákazníka (Set<String>) na GrantedAuthority pro Spring Security
        Collection<? extends GrantedAuthority> authorities = mapRolesToAuthorities(customer.getRoles());

        // Vrátíme objekt UserDetails, který Spring Security používá pro autentizaci
        // Použijeme vestavěnou implementaci org.springframework.security.core.userdetails.User
        return new User(customer.getEmail(), // username
                customer.getPassword(), // heslo (již hashované v DB)
                customer.isEnabled(), // enabled
                true, // accountNonExpired
                true, // credentialsNonExpired
                true, // accountNonLocked
                authorities); // role/oprávnění
    }

    // Pomocná metoda pro převod Set<String> rolí na Collection<GrantedAuthority>
    private Collection<? extends GrantedAuthority> mapRolesToAuthorities(Set<String> roles) {
        return roles.stream()
                .map(SimpleGrantedAuthority::new) // Vytvoří SimpleGrantedAuthority pro každý String role
                .collect(Collectors.toList());
    }
}