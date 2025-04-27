package org.example.eshop.service;

import com.google.cloud.storage.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.UUID;
// Odstranili jsme importy pro java.nio.file.* kromě Paths a InvalidPathException

@Slf4j
@Service
public class FileStorageService {

    // --- PŘIDÁNO/UPRAVENO ---
    @Value("${gcs.bucket.name}") // Nová konfigurace v application.properties
    private String bucketName;

    @Autowired // Injektujte GCS Storage klienta (nakonfigurujte jako Bean)
    private Storage storage;
    // --- KONEC PŘIDÁNÍ/ÚPRAV ---

    /**
     * Uloží soubor do Google Cloud Storage.
     *
     * @param file         Soubor k nahrání.
     * @param subDirectory Podadresář v GCS bucketu (např. "products", "avatars").
     * @return Veřejnou URL k nahranému souboru v GCS.
     * @throws IOException Pokud dojde k chybě při nahrávání.
     * @throws IllegalArgumentException Pokud jsou vstupní parametry neplatné.
     */
    public String storeFile(MultipartFile file, String subDirectory) throws IOException, IllegalArgumentException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store empty file.");
        }
        // Jednoduchá sanitizace podadresáře (zabraňuje ../)
        if (!StringUtils.hasText(subDirectory) || subDirectory.contains("..")) {
            log.warn("Invalid subdirectory provided: '{}'. Using root.", subDirectory);
            subDirectory = ""; // Use root if invalid or empty
        } else {
            subDirectory = Paths.get(subDirectory).normalize().toString(); // Normalize path
            // Ensure it doesn't escape intended directory (basic check)
            if (subDirectory.startsWith("..") || Paths.get(subDirectory).isAbsolute()) {
                log.error("Potential path traversal attempt in subdirectory: '{}'. Denying operation.", subDirectory);
                throw new IllegalArgumentException("Invalid subDirectory structure.");
            }
        }


        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            throw new IllegalArgumentException("File name cannot be empty.");
        }

        // Sanitizace názvu souboru
        String sanitizedFilenameBase;
        try {
            sanitizedFilenameBase = Paths.get(originalFilename).getFileName().toString();
            sanitizedFilenameBase = sanitizedFilenameBase.replaceAll("[^a-zA-Z0-9._-]", "_");
        } catch (InvalidPathException e) {
            log.error("Invalid original filename: {}", originalFilename, e);
            sanitizedFilenameBase = "uploaded_file";
        }

        // Vytvoření unikátního názvu souboru
        String fileExtension = "";
        int lastDot = sanitizedFilenameBase.lastIndexOf(".");
        if (lastDot > 0 && lastDot < sanitizedFilenameBase.length() - 1) {
            fileExtension = sanitizedFilenameBase.substring(lastDot);
            sanitizedFilenameBase = sanitizedFilenameBase.substring(0, lastDot);
        }
        sanitizedFilenameBase = sanitizedFilenameBase.length() > 50 ? sanitizedFilenameBase.substring(0, 50) : sanitizedFilenameBase;
        sanitizedFilenameBase = sanitizedFilenameBase.replaceAll("[^a-zA-Z0-9_-]", "_");

        String uniqueFilename = UUID.randomUUID().toString() + "_" + sanitizedFilenameBase + fileExtension;

        // Sestavení cesty k objektu (blob) v GCS
        String blobPath = (!subDirectory.isEmpty() ? subDirectory + "/" : "") + uniqueFilename;
        blobPath = blobPath.replace("\\", "/"); // Zajistit lomítka

        BlobId blobId = BlobId.of(bucketName, blobPath);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(file.getContentType()) // Nastavení content type
                // Můžete přidat další metadata, např. cache control
                // .setCacheControl("public, max-age=31536000")
                .build();

        log.debug("Attempting to upload to GCS: gs://{}/{}", bucketName, blobPath);

        try (InputStream inputStream = file.getInputStream()) {
            // Nahrání souboru do GCS
            storage.create(blobInfo, inputStream);
            log.info("Successfully stored file in GCS: gs://{}/{}", bucketName, blobPath);
        } catch (StorageException e) {
            log.error("Failed to store file in GCS (gs://{}/{}): {}", bucketName, blobPath, e.getMessage(), e);
            throw new IOException("Failed to store file in GCS.", e);
        }

        // Vrácení veřejné URL (ujistěte se, že bucket má povolen veřejný přístup!)
        // Formát se může lišit dle regionu GCS, toto je nejběžnější.
        String publicUrl = String.format("https://storage.googleapis.com/%s/%s", bucketName, blobPath);
        log.debug("Generated public URL for stored file: {}", publicUrl);
        return publicUrl;
    }

    // --- Metoda deleteFile - kompletně přepsaná ---
    /**
     * Smaže soubor z Google Cloud Storage na základě jeho veřejné URL.
     *
     * @param fileUrl Veřejná URL souboru v GCS (např. https://storage.googleapis.com/bucket/...).
     */
    public void deleteFile(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            log.warn("Invalid (null or blank) file URL provided for deletion.");
            return;
        }

        // Pokusíme se extrahovat název bucketu a cestu k souboru z URL
        final String gcsHost = "storage.googleapis.com";
        String expectedPrefix = "https://" + gcsHost + "/";

        if (!fileUrl.startsWith(expectedPrefix)) {
            // Handle alternative common format storage.cloud.google.com
            final String altGcsHost = "storage.cloud.google.com";
            expectedPrefix = "https://" + altGcsHost + "/";
            if (!fileUrl.startsWith(expectedPrefix)) {
                log.warn("File URL '{}' does not match expected GCS public URL format ({} or {}). Skipping deletion.", fileUrl, gcsHost, altGcsHost);
                return;
            }
        }


        try {
            // Extrahujeme část cesty za názvem hosta a bucketu
            // Příklad: https://storage.googleapis.com/your-bucket/subdir/file.jpg -> your-bucket/subdir/file.jpg
            String pathPart = fileUrl.substring(expectedPrefix.length());
            int firstSlash = pathPart.indexOf('/');
            if (firstSlash <= 0) {
                log.error("Could not parse bucket name and blob path from URL: {}", fileUrl);
                return;
            }

            String parsedBucketName = pathPart.substring(0, firstSlash);
            String blobName = pathPart.substring(firstSlash + 1);

            // URL decode the blob name to handle special characters like spaces (%20)
            blobName = URLDecoder.decode(blobName, StandardCharsets.UTF_8);

            // Ověření, zda extrahovaný bucket odpovídá nakonfigurovanému (bezpečnostní kontrola)
            if (!parsedBucketName.equals(bucketName)) {
                log.error("Attempted to delete file from a different bucket ('{}') than configured ('{}'). URL: {}", parsedBucketName, bucketName, fileUrl);
                return;
            }

            BlobId blobId = BlobId.of(bucketName, blobName);
            log.debug("Attempting to delete GCS blob: {}", blobId);

            boolean deleted = storage.delete(blobId);

            if (deleted) {
                log.info("Successfully deleted GCS blob: {}", blobId);
            } else {
                // Soubor nemusí existovat, což není nutně chyba
                log.warn("GCS blob not found for deletion or already deleted: {}", blobId);
            }
        } catch (StorageException e) {
            log.error("StorageException during GCS file deletion for URL {}: {} (Code: {})", fileUrl, e.getMessage(), e.getCode());
            // Zde můžete přidat specifické ošetření pro různé GCS chyby, např. 404 (NotFound) není kritická
            if (e.getCode() == 404) {
                log.warn("Blob for URL {} not found in GCS (Error 404).", fileUrl);
            } else {
                // Jiné chyby mohou být vážnější
                log.error("Unhandled StorageException during deletion of {}: {}", fileUrl, e.getMessage(), e);
                // Zvažte, zda zde nevyhodit výjimku, pokud je chyba kritická
            }
        } catch (Exception e) {
            log.error("Unexpected error during GCS file deletion for URL {}: {}", fileUrl, e.getMessage(), e);
        }
    }
}