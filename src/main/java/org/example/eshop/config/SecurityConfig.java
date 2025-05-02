package org.example.eshop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer; // <-- Přidat import
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
// Odebrán import HeadersConfigurer
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter; // <-- Ponechat tento import
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
                        // ... stávající requestMatchers (beze změny) ...
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
                        .requestMatchers(
                                new AntPathRequestMatcher("/muj-ucet/**")
                        ).authenticated()
                        .requestMatchers(new AntPathRequestMatcher("/admin/**")).hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/prihlaseni")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/odhlaseni"))
                        .logoutSuccessUrl("/?odhlaseno")
                        .permitAll()
                )
                // --- Konfigurace CSRF (beze změny) ---
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                new AntPathRequestMatcher("/webhooks/**"),
                                new AntPathRequestMatcher("/api/**")
                        )
                )
                // --- OPRAVENÁ Konfigurace bezpečnostních hlaviček ---
                .headers(headers -> headers
                        // X-Content-Type-Options: nosniff (obvykle defaultně zapnuto, ale pro jistotu)
                        .contentTypeOptions(Customizer.withDefaults())
                        // X-Frame-Options: SAMEORIGIN
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                        // Referrer-Policy: strict-origin-when-cross-origin
                        .referrerPolicy(policy -> policy.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // Content-Security-Policy (CSP)
                        .contentSecurityPolicy(csp -> csp.policyDirectives(getCspDirectives()))
                        // HTTP Strict Transport Security (HSTS)
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(TimeUnit.DAYS.toSeconds(365))
                        )
                        // X-XSS-Protection
                        .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        // Permissions-Policy (náhrada za deprecated permissionsPolicy())
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", "geolocation=(), microphone=(), camera=()")) // Uprav podle potřeby
                )
                // --- Konec konfigurace bezpečnostních hlaviček ---

                .httpBasic(AbstractHttpConfigurer::disable); // Vypnutí HTTP Basic (zůstává)

        return http.build();
    }

    // V souboru src/main/java/org/example/eshop/config/SecurityConfig.java

    private String getCspDirectives() {
        return "default-src 'self'; " +

                "script-src 'self' " +
                "https://cdn.jsdelivr.net " +
                "https://code.jquery.com " +
                "https://unpkg.com " +
                "https://www.googletagmanager.com " +
                "https://*.google-analytics.com " +
                "https://*.googleadservices.com " +
                "https://c.imedia.cz " +
                "https://*.heureka.cz " +
                "https://*.heureka.sk " +
                "https://im9.cz " +
                "'unsafe-inline'; " + // Zvažte nonce/hash místo 'unsafe-inline' pro vyšší bezpečnost

                "style-src 'self' 'unsafe-inline' " +
                "https://cdn.jsdelivr.net " +
                "https://fonts.googleapis.com; " +

                "img-src 'self' data: " + // Ponecháno 'data:'
                "https://*.google-analytics.com " +
                "https://*.googletagmanager.com " +
                "https://storage.googleapis.com " +
                "https://*.google.com " +
                "https://*.googleadservices.com " +
                "https://stats.g.doubleclick.net " +
                "https://*.seznam.cz " +
                "https://*.heureka.cz " +
                "https://*.heureka.sk " +
                "https://im9.cz; " + // Zvažte upřesnění https://* pokud možno

                // --- OPRAVA ZDE ---
                "font-src 'self' data: " + // Přidáno 'data:' pro povolení inline fontů
                "https://cdn.jsdelivr.net " +
                "https://fonts.gstatic.com; " +
                // --- KONEC OPRAVY ---

                "connect-src 'self' " +
                "https://*.google-analytics.com " +
                "https://*.analytics.google.com " +
                "https://stats.g.doubleclick.net " +
                "https://*.google.com " +
                "https://*.googleadservices.com " +
                "https://*.seznam.cz " +
                "https://*.heureka.cz " +
                "https://*.heureka.sk; " +

                "frame-src 'self' " +
                "https://*.google.com; " +

                "object-src 'none'; " +
                "base-uri 'self'; " +
                "form-action 'self'; " +
                "upgrade-insecure-requests; " +
                "block-all-mixed-content;";
    }}