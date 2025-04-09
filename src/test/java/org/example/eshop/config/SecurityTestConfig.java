// src/test/java/org/example/eshop/config/SecurityTestConfig.java
package org.example.eshop.config; // Nahraď skutečným balíčkem

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@TestConfiguration
public class SecurityTestConfig {

    @Bean
    @Order(1) // Zajistí, že tato konfigurace má přednost před hlavní konfigurací v testech
    SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authz -> authz
                                // Povolíme přístup k veřejným endpointům (přihlášení, registrace, statické zdroje)
                                // Tyto jsou často potřeba i při testování jiných částí.
                                .requestMatchers(
                                        "/prihlaseni",
                                        "/registrace",
                                        "/logout", // Může být relevantní
                                        "/css/**",
                                        "/js/**",
                                        "/images/**" // Případně další statické zdroje
                                ).permitAll()
                                // Všechny ostatní požadavky *v rámci testů* povolíme.
                                // Pro testování zabezpečených endpointů použijeme @WithMockUser.
                                // Toto zjednodušuje testování logiky controllerů.
                                // Pokud bys chtěl testovat přímo pravidla autorizace,
                                // musela by být tato konfigurace specifičtější.
                                .anyRequest().permitAll()
                        // Alternativně, pokud chceš testovat i autorizaci:
                        // .anyRequest().authenticated() // nebo specifičtější pravidla
                )
                // Ponecháme formLogin pro konzistenci, i když se přihlášení často mockuje.
                // Může být užitečné, pokud některé testy potřebují přesměrování na login.
                .formLogin(form -> form
                        .loginPage("/prihlaseni").permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout") // Definuje URL pro odhlášení
                        .logoutSuccessUrl("/prihlaseni?logout") // Kam přesměrovat po úspěšném odhlášení
                        .permitAll()
                )
                // *** Důležité: Vypnutí CSRF pro testy s MockMvc ***
                // MockMvc standardně neodesílá CSRF tokeny, což by vedlo k chybám 403 u POST/PUT/DELETE.
                .csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }
}