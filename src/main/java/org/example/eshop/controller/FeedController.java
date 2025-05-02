package org.example.eshop.controller;

import org.example.eshop.service.FeedGenerationService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType; // <-- Ujistěte se, že tento import existuje
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.nio.charset.StandardCharsets;

@Controller
public class FeedController {

    @Autowired
    private FeedGenerationService feedGenerationService;

    @GetMapping(value = "/robots.txt", produces = "text/plain")
    @ResponseBody
    public String getRobotsTxt() {
        return feedGenerationService.generateRobotsTxt();
    }

    // Metoda pro sitemap.xml již 'produces' má, ponecháme ji
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody // @ResponseBody zde může zůstat, pokud vracíte String a spoléháte na konverzi
    public ResponseEntity<byte[]> getSitemapXml() {
        String xmlContent = feedGenerationService.generateSitemapXml();
        // Používáme vaši pomocnou metodu pro konzistenci
        return getResponseEntity(xmlContent);
    }

    // --- ZAČÁTEK ÚPRAVY ---
    @GetMapping(value = "/google_feed.xml", produces = MediaType.APPLICATION_XML_VALUE) // Přidáno 'produces'
    // @ResponseBody // Odstraněno, pokud vracíme ResponseEntity
    public ResponseEntity<byte[]> getGoogleMerchantFeed() {
        // Předpokládáme generování pro CZK jako výchozí
        String xmlContent = feedGenerationService.generateGoogleMerchantFeed("CZK");
        return getResponseEntity(xmlContent);
    }

    @GetMapping(value = "/heureka_feed.xml", produces = MediaType.APPLICATION_XML_VALUE) // Přidáno 'produces'
    // @ResponseBody // Odstraněno, pokud vracíme ResponseEntity
    public ResponseEntity<byte[]> getHeurekaFeed() {
        // Předpokládáme generování pro CZK jako výchozí
        String xmlContent = feedGenerationService.generateHeurekaFeed("CZK");
        return getResponseEntity(xmlContent);
    }
    // --- KONEC ÚPRAVY ---

    // Pomocná metoda pro sestavení ResponseEntity (zůstává stejná)
    @NotNull
    private ResponseEntity<byte[]> getResponseEntity(String xmlContent) {
        byte[] body = xmlContent.getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML); // Nastavení Content-Type
        headers.setContentLength(body.length);

        return new ResponseEntity<>(body, headers, org.springframework.http.HttpStatus.OK);
    }
}