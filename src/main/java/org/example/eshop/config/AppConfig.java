package org.example.eshop.config;

import com.google.auth.oauth2.GoogleCredentials; // Import
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions; // Import
import org.springframework.beans.factory.annotation.Value; // Import
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource; // Import
import org.springframework.web.client.RestTemplate;

import java.io.IOException; // Import
import java.io.InputStream; // Import

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    // --- PŘIDÁNO: Bean pro GCS Storage klienta ---
    @Value("${gcs.project.id}") // Načtení ID projektu z properties
    private String gcsProjectId;

    // Volitelná cesta ke credentials souboru
    @Value("${gcs.credentials.location:}") // Defaultně prázdné - použije ADC
    private Resource gcsCredentialsResource;

    @Bean
    public Storage googleCloudStorage() throws IOException {
        StorageOptions.Builder optionsBuilder = StorageOptions.newBuilder()
                .setProjectId(gcsProjectId);

        // Pokud je specifikována cesta ke credentials, použijeme je
        if (gcsCredentialsResource != null && gcsCredentialsResource.exists()) {
            try (InputStream credentialsStream = gcsCredentialsResource.getInputStream()) {
                optionsBuilder.setCredentials(GoogleCredentials.fromStream(credentialsStream));
                System.out.println(">>> Using GCS credentials from: " + gcsCredentialsResource.getURI());
            }
        } else {
            // Jinak spoléháme na Application Default Credentials (ADC)
            // https://cloud.google.com/docs/authentication/provide-credentials-adc
            optionsBuilder.setCredentials(GoogleCredentials.getApplicationDefault());
            System.out.println(">>> Using Application Default Credentials for GCS.");
        }

        return optionsBuilder.build().getService();
    }
    // --- KONEC PŘIDÁNÍ ---
}