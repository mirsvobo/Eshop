// src/main/java/org/example/eshop/config/SecurityConfig.java
package org.example.eshop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authz -> authz
                        // ... stávající requestMatchers ...
                        .requestMatchers(
                                new AntPathRequestMatcher("/css/**"),
                                new AntPathRequestMatcher("/js/**"),
                                new AntPathRequestMatcher("/images/**"),
                                new AntPathRequestMatcher("/webjars/**"),
                                new AntPathRequestMatcher("/uploads/**")
                        ).permitAll()
                        .requestMatchers(
                                new AntPathRequestMatcher("/"),
                                new AntPathRequestMatcher("/produkty"),
                                new AntPathRequestMatcher("/produkt/**"),
                                new AntPathRequestMatcher("/api/product/calculate-price"),
                                new AntPathRequestMatcher("/navrhnout-na-miru"),
                                new AntPathRequestMatcher("/navrhnout-na-miru/api/calculate-price"),
                                new AntPathRequestMatcher("/kosik/**"),
                                new AntPathRequestMatcher("/webhooks/**"),
                                new AntPathRequestMatcher("/prihlaseni"),
                                new AntPathRequestMatcher("/registrace"),
                                new AntPathRequestMatcher("/nastavit-menu"),
                                new AntPathRequestMatcher("/pokladna/calculate-shipping")

                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/pokladna").permitAll()
                        .requestMatchers(HttpMethod.POST, "/pokladna/odeslat").permitAll()
                        .requestMatchers(HttpMethod.POST, "/pokladna/calculate-shipping").permitAll()

                        .requestMatchers(
                                new AntPathRequestMatcher("/muj-ucet/**")
                        ).authenticated()
                        .requestMatchers(new AntPathRequestMatcher("/admin/**")).hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/prihlaseni")
                        .permitAll()
                        .defaultSuccessUrl("/", true)
                )
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/odhlaseni"))
                        .logoutSuccessUrl("/?odhlaseno")
                        .permitAll()
                )
                // --- Konfigurace CSRF ---
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                new AntPathRequestMatcher("/webhooks/**"),
                                new AntPathRequestMatcher("/api/**")
                        )
                )
                // *** PŘIDAT TOTO PRO VYPNUTÍ HTTP BASIC ***
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }
}