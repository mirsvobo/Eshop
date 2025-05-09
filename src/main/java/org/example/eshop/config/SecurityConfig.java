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
     * UPRAVENO: Přidány konkrétní domény pro Google Analytics a CCM do connect-src.
     * @return String s CSP direktivami.
     */
    private String getCspDirectives() {
        return "default-src 'self'; " +

                "script-src 'self' " +
                "https://cdn.jsdelivr.net " +
                "https://code.jquery.com " +
                "https://unpkg.com " +
                "https://www.googletagmanager.com " +
                "https://*.google-analytics.com " + // Povolení pro gtag.js a starší analytics.js
                "https://*.analytics.google.com " +  // Novější endpointy pro GA4
                "https://region1.analytics.google.com " + // << DŮLEŽITÉ PŘIDAT TOTO
                "https://*.googleadservices.com " +
                "https://googleads.g.doubleclick.net " +
                "https://www.google.com " +
                "https://c.imedia.cz " +
                "https://c.seznam.cz " +
                "https://*.heureka.cz " +
                "https://*.heureka.sk " +
                "https://cdn.heureka.group " + // <-- PŘIDÁNO PRO HEUREKA SDK

                "https://im9.cz " +
                "'unsafe-inline'; " + // unsafe-inline pro script-src je obecně nedoporučeno, ale může být potřeba kvůli GTM nebo inline skriptům. Zvažte použití nonce nebo hash, pokud je to možné.

                "style-src 'self' 'unsafe-inline' " + // 'unsafe-inline' pro styly je často potřeba
                "https://cdn.jsdelivr.net " +
                "https://fonts.googleapis.com " +
                "https://www.googletagmanager.com; " +

                "img-src 'self' data: " + // data: pro inline obrázky (např. SVG v CSS)
                "https://*.google-analytics.com " +
                "https://*.analytics.google.com " +
                "https://*.googletagmanager.com " +
                "https://storage.googleapis.com " + // Pro obrázky z GCS
                "https://*.google.com " +
                "https://*.google.cz " +
                "https://*.google.es " +
                "https://*.googleadservices.com " +
                "https://stats.g.doubleclick.net " +
                "https://googleads.g.doubleclick.net " +
                "https://fonts.gstatic.com " + // Pro Google Fonts
                "https://*.seznam.cz " +
                "https://*.heureka.cz " +
                "https://*.heureka.sk " +
                "https://im9.cz; " +

                "font-src 'self' data: " +
                "https://cdn.jsdelivr.net " +
                "https://fonts.gstatic.com; " +

                "connect-src 'self' " + // Povolení spojení na vlastní doménu
                "https://region1.analytics.google.com " + // << DŮLEŽITÉ PŘIDAT TOTO
                "https://*.google-analytics.com " +       // Obecnější pro GA
                "https://*.analytics.google.com " +       // Obecnější pro GA
                "https://stats.g.doubleclick.net " +      // Pro Google Ads / GA
                "https://www.google.com " +             // Pro CCM a jiné Google služby
                "https://*.google.com " +               // Obecnější pro Google
                "https://google.com " +
                "https://*.google.cz " +
                "https://*.google.es " +
                "https://*.googleadservices.com " +
                "https://*.googlesyndication.com " +    // Pro Google Ads
                "https://*.seznam.cz " +                // Pro Sklik
                "https://*.heureka.cz " +               // Pro Heureku
                "https://*.heureka.sk; " +              // Pro Heureku

                "frame-src 'self' " + // Odkud lze vkládat iframy
                "https://*.google.com " +               // Pro reCAPTCHA, Google Consent Mode dialogy
                "https://td.doubleclick.net " +         // Pro Google Ads
                "https://www.googletagmanager.com; " + // Pro GTM preview

                "object-src 'none'; " + // Zakázat <object>, <embed>, <applet>
                "base-uri 'self'; " + // Omezit URL, které mohou být použity v <base> tagu
                "form-action 'self'; " + // Odkud lze odesílat formuláře
                "upgrade-insecure-requests; " + // Přesměrovat HTTP na HTTPS
                "block-all-mixed-content;"; // Blokovat smíšený aktivní obsah
    }
}