package org.example.eshop.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${eshop.upload.dir:${user.dir}/uploads}")
    private String uploadDir;

    @Value("${eshop.upload.url.base:/uploads}")
    private String baseUrlPath;

    @Override
    public void addResourceHandlers(@NotNull ResourceHandlerRegistry registry) {
        String resolvedUploadDir = Paths.get(uploadDir).toAbsolutePath().normalize().toString();
        // Zajistí, že cesta končí lomítkem, pokud není root
        if (!resolvedUploadDir.endsWith("/") && !resolvedUploadDir.endsWith("\\")) {
            resolvedUploadDir += "/";
        }
        String resourceLocation = "file:" + resolvedUploadDir;
        String urlPath = baseUrlPath.endsWith("/**") ? baseUrlPath : baseUrlPath + "/**";

        registry.addResourceHandler(urlPath)
                .addResourceLocations(resourceLocation);

        System.out.println(">>> Serving static resources from path: " + urlPath + " mapped to location: " + resourceLocation);
    }
}