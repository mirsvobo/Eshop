package org.example.eshop.service;

import com.google.api.client.util.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils; // Přidat import
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.UUID;


@Slf4j
@Service
public class FileStorageService {

    @Value("${eshop.upload.dir:${user.dir}/uploads}")
    private String uploadDir;

    @Value("${eshop.upload.url.base:/uploads}")
    private String baseUrlPath;

    public String storeFile(MultipartFile file, String subDirectory) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store empty file.");
        }
        if (!StringUtils.hasText(subDirectory)) { // Použijeme StringUtils.hasText pro robustnější kontrolu
            throw new IllegalArgumentException("Subdirectory cannot be empty.");
        }
        // Získání a sanitizace původního názvu souboru
        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            throw new IllegalArgumentException("File name cannot be empty.");
        }

        // --- OPRAVA: Získání pouze názvu souboru a základní sanitizace ---
        String sanitizedFilenameBase;
        try {
            // Toto je robustní způsob, jak získat pouze název souboru bez cesty
            sanitizedFilenameBase = Paths.get(originalFilename).getFileName().toString();
            // Další sanitizace: odstranění potenciálně problematických znaků kromě tečky, písmen, čísel, podtržítka, pomlčky
            sanitizedFilenameBase = sanitizedFilenameBase.replaceAll("[^a-zA-Z0-9._-]", "_");
        } catch (InvalidPathException e) {
            log.error("Invalid original filename received: {}", originalFilename, e);
            // Pokud je název souboru zcela neplatný, použijeme náhradní
            sanitizedFilenameBase = "uploaded_file";
        }
        // --- KONEC OPRAVY ---


        // Vytvoření cílového adresáře (včetně podadresáře), pokud neexistuje
        Path targetLocation;
        try {
            // Přidána kontrola subDirectory proti path traversal
            Path normalizedSubDir = Paths.get(subDirectory).normalize();
            if (normalizedSubDir.startsWith("..") || normalizedSubDir.isAbsolute()) {
                throw new IllegalArgumentException("Invalid subDirectory: " + subDirectory);
            }
            targetLocation = Paths.get(uploadDir).resolve(normalizedSubDir).normalize();
            log.debug("Target upload directory: {}", targetLocation);
            Files.createDirectories(targetLocation);
        } catch (IOException ex) {
            log.error("Could not create the directory where the uploaded files will be stored: {}", uploadDir + "/" + subDirectory, ex);
            throw new IOException("Could not create storage directory.", ex);
        } catch (InvalidPathException e) {
            log.error("Invalid path specified for subdirectory: {}", subDirectory, e);
            throw new IOException("Invalid storage subdirectory.", e);
        }


        // Vytvoření unikátního názvu souboru (zachování přípony)
        String fileExtension = "";
        int lastDot = sanitizedFilenameBase.lastIndexOf(".");
        if (lastDot > 0 && lastDot < sanitizedFilenameBase.length() - 1) { // Ensure dot is not first or last char
            fileExtension = sanitizedFilenameBase.substring(lastDot);
            sanitizedFilenameBase = sanitizedFilenameBase.substring(0, lastDot); // Base name without extension
        }
        // Omezení délky názvu (bez přípony) a odstranění problematických znaků
        sanitizedFilenameBase = sanitizedFilenameBase.length() > 50 ? sanitizedFilenameBase.substring(0, 50) : sanitizedFilenameBase;
        sanitizedFilenameBase = sanitizedFilenameBase.replaceAll("[^a-zA-Z0-9_-]", "_"); // Povolit podtržítka a pomlčky

        String uniqueFilename = UUID.randomUUID().toString() + "_" + sanitizedFilenameBase + fileExtension;
        Path destinationFile = targetLocation.resolve(uniqueFilename).normalize();

        // --- OPRAVA: Důsledná kontrola Path Traversal ---
        // Znovu ověříme, zda výsledná cesta stále začíná naším cílovým adresářem
        if (!destinationFile.startsWith(targetLocation)) {
            log.error("Path traversal attempt detected! Tried to save to: {}. Original filename: {}", destinationFile, originalFilename);
            throw new FileSystemException("Path traversal attempt detected. Cannot store file outside target directory: " + originalFilename);
        }
        // --- KONEC OPRAVY ---


        // Uložení souboru
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("Successfully stored file: {} in directory: {}", uniqueFilename, targetLocation);
        } catch (IOException ex) {
            log.error("Could not store file {}: {}", uniqueFilename, ex.getMessage(), ex);
            // V případě chyby smazat částečně nahraný soubor? Záleží na logice.
            // Files.deleteIfExists(destinationFile);
            throw new IOException("Failed to store file " + uniqueFilename, ex);
        }

        // Vrácení relativní URL cesty (bez změny)
        String relativeUrl = Paths.get(baseUrlPath).resolve(subDirectory).resolve(uniqueFilename)
                .normalize().toString().replace("\\", "/"); // Zajistí lomítka pro URL
        log.debug("Generated relative URL for stored file: {}", relativeUrl);
        return relativeUrl;
    }

    // Metoda deleteFile zůstává stejná...
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank() || !fileUrl.startsWith(baseUrlPath)) {
            log.warn("Invalid file URL provided for deletion: {}", fileUrl);
            return;
        }
        try {
            // Odstranění baseUrlPath a převedení na systémovou cestu
            String relativePath = fileUrl.substring(baseUrlPath.length());
            Path filePath = Paths.get(uploadDir).resolve(relativePath.startsWith("/") ? relativePath.substring(1) : relativePath).normalize();

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
        } catch (InvalidPathException e) {
            log.error("Invalid path derived from file URL for deletion: {}", fileUrl, e);
        }
    }
}