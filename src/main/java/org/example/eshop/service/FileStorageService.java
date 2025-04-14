// src/main/java/org/example/eshop/service/FileStorageService.java
package org.example.eshop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    // Cesta k adresáři pro upload - načteme z application.properties
    @Value("${eshop.upload.dir:${user.dir}/uploads}") // Výchozí hodnota, pokud není v properties
    private String uploadDir;

    // Základní URL cesta, pod kterou budou obrázky dostupné (pro DB)
    @Value("${eshop.upload.url.base:/uploads}")
    private String baseUrlPath;

    /**
     * Uloží nahraný soubor do specifického podadresáře a vrátí relativní URL.
     *
     * @param file MultipartFile k uložení.
     * @param subDirectory Podadresář v rámci uploadDir (např. "products", "categories").
     * @return Relativní URL k uloženému souboru (např. "/uploads/products/xyz.jpg").
     * @throws IOException Pokud nastane chyba při ukládání.
     * @throws IllegalArgumentException Pokud soubor nebo podadresář nejsou platné.
     */
    public String storeFile(MultipartFile file, String subDirectory) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store empty file.");
        }
        if (subDirectory == null || subDirectory.isBlank()) {
            throw new IllegalArgumentException("Subdirectory cannot be empty.");
        }

        // Vytvoření cílového adresáře (včetně podadresáře), pokud neexistuje
        Path targetLocation = Paths.get(uploadDir).resolve(subDirectory).normalize();
        log.debug("Target upload directory: {}", targetLocation);
        try {
            Files.createDirectories(targetLocation);
        } catch (IOException ex) {
            log.error("Could not create the directory where the uploaded files will be stored: {}", targetLocation, ex);
            throw new IOException("Could not create storage directory.", ex);
        }

        // Vytvoření unikátního názvu souboru (zachování přípony)
        String originalFilename = file.getOriginalFilename();
        String fileExtension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        // Odstranění potenciálně nebezpečných znaků z přípony
        fileExtension = fileExtension.replaceAll("[^.a-zA-Z0-9]", "");

        String uniqueFilename = UUID.randomUUID() + fileExtension;
        Path destinationFile = targetLocation.resolve(uniqueFilename).normalize();

        // Zabezpečení proti Path Traversal
        if (!destinationFile.getParent().equals(targetLocation)) {
            throw new FileSystemException("Cannot store file outside target directory: " + originalFilename);
        }

        // Uložení souboru
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("Successfully stored file: {} in directory: {}", uniqueFilename, targetLocation);
        } catch (IOException ex) {
            log.error("Could not store file {}: {}", uniqueFilename, ex.getMessage(), ex);
            throw new IOException("Failed to store file " + uniqueFilename, ex);
        }

        // Vrácení relativní URL cesty
        String relativeUrl = Paths.get(baseUrlPath).resolve(subDirectory).resolve(uniqueFilename)
                .normalize().toString().replace("\\", "/"); // Zajistí lomítka pro URL
        log.debug("Generated relative URL for stored file: {}", relativeUrl);
        return relativeUrl;
    }

    // Metoda pro smazání souboru (pokud bude potřeba implementovat)
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank() || !fileUrl.startsWith(baseUrlPath)) {
            log.warn("Invalid file URL provided for deletion: {}", fileUrl);
            return;
        }
        try {
            // Odstranění baseUrlPath a převedení na systémovou cestu
            String relativePath = fileUrl.substring(baseUrlPath.length());
            Path filePath = Paths.get(uploadDir).resolve(relativePath.substring(1)).normalize(); // substring(1) pro odstranění úvodního lomítka

            // Bezpečnostní kontrola, abychom nemazali mimo uploadDir
            if (!filePath.startsWith(Paths.get(uploadDir).normalize())) {
                log.error("Attempted to delete file outside the upload directory: {}", filePath);
                return;
            }

            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Successfully deleted file: {}", filePath);
            } else {
                log.warn("File not found for deletion: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Could not delete file URL {}: {}", fileUrl, e.getMessage());
            // Neodhazujeme zde výjimku, logování stačí
        }
    }
}