package org.example.eshop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder; // Pro pokročilejší konfiguraci

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // Můžeme zde přidat další konfiguraci (timeouty, interceptory atd.)
        return builder.build();
    }

}