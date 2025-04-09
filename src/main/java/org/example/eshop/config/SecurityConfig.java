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
                        .requestMatchers(
                                new AntPathRequestMatcher("/css/**"),
                                new AntPathRequestMatcher("/js/**"),
                                new AntPathRequestMatcher("/images/**"),
                                new AntPathRequestMatcher("/webjars/**")
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
                                new AntPathRequestMatcher("/nastavit-menu")
                        ).permitAll()

                        .requestMatchers(HttpMethod.GET, "/pokladna").permitAll()
                        .requestMatchers(HttpMethod.POST, "/pokladna/odeslat").permitAll() // <-- Změněno z authenticated() na permitAll()

                        .requestMatchers(
                                new AntPathRequestMatcher("/muj-ucet/**")

                        ).authenticated()

                        .requestMatchers(new AntPathRequestMatcher("/admin/**")).hasRole("ADMIN")

                        .anyRequest().permitAll()
                )
                .formLogin(form -> form
                        .loginPage("/prihlaseni")
                        .permitAll()
                        // Přesměrování po úspěšném přihlášení - pokud uživatel šel do pokladny, vrátit ho tam?
                        // To vyžaduje implementaci AuthenticationSuccessHandler
                        .defaultSuccessUrl("/", true)
                )
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/odhlaseni"))
                        .logoutSuccessUrl("/?odhlaseno")
                        .permitAll()
                )
                // CSRF je důležité zapnout v produkci, pro vývoj může být vypnuté
                .csrf(AbstractHttpConfigurer::disable); // V PRODUKCI ZAPNOUT!


        return http.build();
    }
}