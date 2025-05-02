package org.example.eshop.controller;

import org.example.eshop.service.FeedGenerationService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> getSitemapXml() {
        String xmlContent = feedGenerationService.generateSitemapXml();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(xmlContent.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping(value = "/google_feed.xml")
    public ResponseEntity<byte[]> getGoogleMerchantFeed() {
        // Předpokládáme generování pro CZK jako výchozí
        String xmlContent = feedGenerationService.generateGoogleMerchantFeed("CZK");
        return getResponseEntity(xmlContent);
    }

    @GetMapping(value = "/heureka_feed.xml") // Odebráno produces = MediaType.APPLICATION_XML_VALUE
    // @ResponseBody // Odebráno
    public ResponseEntity<byte[]> getHeurekaFeed() {
        // Předpokládáme generování pro CZK jako výchozí
        String xmlContent = feedGenerationService.generateHeurekaFeed("CZK");
        return getResponseEntity(xmlContent);
    }

    @NotNull
    private ResponseEntity<byte[]> getResponseEntity(String xmlContent) {
        byte[] body = xmlContent.getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML); // <-- Explicitní nastavení Content-Type
        headers.setContentLength(body.length);

        return new ResponseEntity<>(body, headers, org.springframework.http.HttpStatus.OK);
    }
}