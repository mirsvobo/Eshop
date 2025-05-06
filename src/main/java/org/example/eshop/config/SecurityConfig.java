package org.example.eshop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer; // Import je zde v pořádku
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.concurrent.TimeUnit;

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
                        // Povolení statických zdrojů a veřejných stránek
                        .requestMatchers(
                                new AntPathRequestMatcher("/css/**"),
                                new AntPathRequestMatcher("/js/**"),
                                new AntPathRequestMatcher("/images/**"),
                                new AntPathRequestMatcher("/webjars/**"),
                                new AntPathRequestMatcher("/uploads/**"),
                                new AntPathRequestMatcher("/robots.txt"),
                                new AntPathRequestMatcher("/sitemap.xml"),
                                new AntPathRequestMatcher("/google_feed.xml"),
                                new AntPathRequestMatcher("/heureka_feed.xml")
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
                                new AntPathRequestMatcher("/pokladna/calculate-shipping"),
                                new AntPathRequestMatcher("/o-nas"),
                                new AntPathRequestMatcher("/zapomenute-heslo"),
                                new AntPathRequestMatcher("/resetovat-heslo"),
                                new AntPathRequestMatcher("/gdpr"),
                                new AntPathRequestMatcher("/obchodni-podminky"),
                                new AntPathRequestMatcher("/pokladna/dekujeme"),
                                new AntPathRequestMatcher("/403"),
                                new AntPathRequestMatcher("/404"),
                                new AntPathRequestMatcher("/500")
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/pokladna").permitAll()
                        .requestMatchers(HttpMethod.POST, "/pokladna/odeslat").permitAll()
                        .requestMatchers(HttpMethod.POST, "/pokladna/calculate-shipping").permitAll()
                        // Zabezpečené části
                        .requestMatchers(
                                new AntPathRequestMatcher("/muj-ucet/**")
                        ).authenticated()
                        .requestMatchers(new AntPathRequestMatcher("/admin/**")).hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                // Přihlášení a odhlášení
                .formLogin(form -> form
                        .loginPage("/prihlaseni")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/odhlaseni"))
                        .logoutSuccessUrl("/?odhlaseno")
                        .permitAll()
                )
                // CSRF konfigurace
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                new AntPathRequestMatcher("/webhooks/**"),
                                new AntPathRequestMatcher("/api/**") // Ignorování API cest obecně
                        )
                )
                // Konfigurace bezpečnostních hlaviček
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                        .referrerPolicy(policy -> policy.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(getCspDirectives())) // Použití metody pro CSP
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(TimeUnit.DAYS.toSeconds(365)) // 1 rok
                        )
                        .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", "geolocation=(), microphone=(), camera=()"))
                )
                .httpBasic(AbstractHttpConfigurer::disable); // Vypnutí HTTP Basic

        return http.build();
    }

    /**
     * Generuje řetězec s direktivami Content Security Policy.
     * @return String s CSP direktivami.
     */
    private String getCspDirectives() {
        return "default-src 'self'; " +

                "script-src 'self' " +
                "https://cdn.jsdelivr.net " +
                "https://code.jquery.com " +
                "https://unpkg.com " +
                "https://www.googletagmanager.com " +
                "https://*.google-analytics.com " +
                "https://*.googleadservices.com " +
                "https://googleads.g.doubleclick.net " +
                "https://www.google.com " +
                "https://c.imedia.cz " +
                "https://*.heureka.cz " +
                "https://*.heureka.sk " +
                "https://im9.cz " +
                "'unsafe-inline'; " + // Zvážit odstranění

                "style-src 'self' 'unsafe-inline' " +
                "https://cdn.jsdelivr.net " +
                "https://fonts.googleapis.com " +
                "https://www.googletagmanager.com; " +

                "img-src 'self' data: " +
                "https://*.google-analytics.com " +
                "https://*.googletagmanager.com " +
                "https://storage.googleapis.com " + // Pro GCS obrázky
                "https://*.google.com " +
                "https://*.googleadservices.com " +
                "https://stats.g.doubleclick.net " +
                "https://googleads.g.doubleclick.net " +
                "https://*.google.es " +
                "https://fonts.gstatic.com " +
                "https://*.seznam.cz " +
                "https://*.heureka.cz " +
                "https://*.heureka.sk " +
                "https://im9.cz; " +

                "font-src 'self' data: " +
                "https://cdn.jsdelivr.net " +
                "https://fonts.gstatic.com; " +

                // ****** UPRAVENO ZDE: Přidána doména googlesyndication.com ******
                "connect-src 'self' " +
                "https://*.google-analytics.com " +
                "https://*.analytics.google.com " +
                "https://stats.g.doubleclick.net " +
                "https://*.google.com " +
                "https://google.com " +
                "https://*.googleadservices.com " +
                "https://*.googlesyndication.com " + // <-- PŘIDÁNO TOTO
                "https://*.seznam.cz " +
                "https://*.heureka.cz " +
                "https://*.heureka.sk; " +
                // ****** KONEC ÚPRAVY ******

                "frame-src 'self' " +
                "https://*.google.com " +
                "https://td.doubleclick.net " +
                "https://www.googletagmanager.com; " +

                "object-src 'none'; " +
                "base-uri 'self'; " +
                "form-action 'self'; " +
                "upgrade-insecure-requests; " +
                "block-all-mixed-content;";
    }
}