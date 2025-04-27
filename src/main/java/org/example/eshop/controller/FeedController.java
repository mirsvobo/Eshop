package org.example.eshop.controller;

import org.example.eshop.service.FeedGenerationService;
import org.springframework.beans.factory.annotation.Autowired;
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

    @GetMapping(value = "/google_feed.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> getGoogleMerchantFeed() {
        // Předpokládáme generování pro CZK jako výchozí
        String xmlContent = feedGenerationService.generateGoogleMerchantFeed("CZK");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(xmlContent.getBytes(StandardCharsets.UTF_8));
    }

    // Můžete přidat variantu pro EUR feed, např. /google_feed_eur.xml

    @GetMapping(value = "/heureka_feed.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> getHeurekaFeed() {
        // Předpokládáme generování pro CZK jako výchozí
        String xmlContent = feedGenerationService.generateHeurekaFeed("CZK");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(xmlContent.getBytes(StandardCharsets.UTF_8));
    }
    // Můžete přidat variantu pro EUR feed, např. /heureka_feed_eur.xml

}