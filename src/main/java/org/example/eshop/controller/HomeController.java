package org.example.eshop.controller;// Vlož nebo nahraď v src/main/java/org/example/eshop/controller/HomeController.java
// Přidej importy, pokud chybí:
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import org.example.eshop.config.PriceConstants;
import org.example.eshop.model.Product;
import org.example.eshop.service.CurrencyService;
import org.example.eshop.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;


@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    private final ProductService productService;
    private final CurrencyService currencyService;
    private final ObjectMapper objectMapper; // Přidána závislost

    @Autowired
    public HomeController(ProductService productService, CurrencyService currencyService, ObjectMapper objectMapper) { // Přidán ObjectMapper
        this.productService = productService;
        this.currencyService = currencyService;
        this.objectMapper = objectMapper; // Inicializace ObjectMapperu
    }

    @GetMapping("/")
    public String home(Model model, HttpServletRequest request) { // Přidán HttpServletRequest
        log.info("Accessing home page");
        String currentCurrency = currencyService.getSelectedCurrency();
        model.addAttribute("currentGlobalCurrency", currentCurrency); // Pro layout

        List<String> productJsonLdList = new ArrayList<>(); // Seznam pro JSON-LD stringy

        try {
            // Načtení produktů
            List<Product> featuredProducts = productService.getAllActiveProducts().stream()
                    .filter(p -> !p.isCustomisable()) // Pouze standardní produkty
                    .limit(4)
                    .collect(Collectors.toList());

            model.addAttribute("featuredProducts", featuredProducts);

            // Výpočet cen a generování JSON-LD pro každý produkt
            Map<Long, Map<String, Object>> featuredProductPrices = new HashMap<>();
            String baseUrl = request.getScheme() + "://" + request.getServerName() + (request.getServerPort() == 80 || request.getServerPort() == 443 ? "" : ":" + request.getServerPort()) + request.getContextPath();
            log.debug("Base URL for JSON-LD image paths: {}", baseUrl);

            for (Product product : featuredProducts) {
                if (product == null || product.getId() == null) continue;

                Map<String, Object> priceInfo = Collections.emptyMap();
                BigDecimal finalPriceForSchema = BigDecimal.ZERO;

                // Výpočet ceny
                try {
                    priceInfo = productService.calculateFinalProductPrice(product, currentCurrency);
                    featuredProductPrices.put(product.getId(), priceInfo);
                    finalPriceForSchema = (BigDecimal) priceInfo.getOrDefault("discountedPrice", priceInfo.get("originalPrice"));
                    if(finalPriceForSchema == null) finalPriceForSchema = BigDecimal.ZERO;
                } catch (Exception priceEx) {
                    log.error("Error calculating price for featured product ID {}: {}", product.getId(), priceEx.getMessage());
                    featuredProductPrices.put(product.getId(), Collections.emptyMap());
                }

                // Generování JSON-LD pro tento produkt
                try {
                    Map<String, Object> productJsonLd = new LinkedHashMap<>();
                    productJsonLd.put("@context", "https://schema.org");
                    productJsonLd.put("@type", "Product");
                    productJsonLd.put("name", product.getName());
                    productJsonLd.put("description", StringUtils.hasText(product.getShortDescription()) ? product.getShortDescription() : abbreviate(product.getDescription())); // Použijeme zkrácený popis
                    productJsonLd.put("sku", "STD-" + product.getId()); // SKU pro standardní produkt
                    productJsonLd.put("url", baseUrl + "/produkt/" + product.getSlug()); // Absolutní URL

                    // Obrázek (první z galerie)
                    if (!product.getImagesOrdered().isEmpty()) {
                        String imageUrl = product.getImagesOrdered().get(0).getUrl();
                        if (imageUrl != null && !imageUrl.toLowerCase().startsWith("http")) {
                            imageUrl = baseUrl + (imageUrl.startsWith("/") ? imageUrl : "/" + imageUrl);
                        }
                        if(imageUrl != null) {
                            productJsonLd.put("image", imageUrl);
                        }
                    }

                    // Brand
                    Map<String, Object> brandMap = Map.of("@type", "Brand", "name", "Dřevníky Kolář");
                    productJsonLd.put("brand", brandMap);

                    // Offer
                    Map<String, Object> offerMap = new LinkedHashMap<>();
                    offerMap.put("@type", "Offer");
                    offerMap.put("url", baseUrl + "/produkt/" + product.getSlug()); // URL nabídky = URL produktu
                    offerMap.put("priceCurrency", currentCurrency);
                    offerMap.put("price", finalPriceForSchema.setScale(PriceConstants.PRICE_SCALE, PriceConstants.ROUNDING_MODE));
                    offerMap.put("availability", "https://schema.org/InStock"); // Předpokládáme dostupnost
                    offerMap.put("itemCondition", "https://schema.org/NewCondition");

                    Map<String, Object> priceSpecMap = new LinkedHashMap<>();
                    priceSpecMap.put("@type", "UnitPriceSpecification");
                    priceSpecMap.put("price", finalPriceForSchema.setScale(PriceConstants.PRICE_SCALE, PriceConstants.ROUNDING_MODE));
                    priceSpecMap.put("priceCurrency", currentCurrency);
                    priceSpecMap.put("valueAddedTaxIncluded", false);
                    offerMap.put("priceSpecification", priceSpecMap);

                    productJsonLd.put("offers", offerMap);

                    // Převod na JSON string a přidání do seznamu
                    productJsonLdList.add(objectMapper.writeValueAsString(productJsonLd));

                } catch (Exception jsonEx) {
                    log.error("Error generating JSON-LD for featured product ID {}: {}", product.getId(), jsonEx.getMessage());
                    // Nepřidáme nic do seznamu, pokud generování selže
                }
            }
            model.addAttribute("featuredProductPrices", featuredProductPrices);
            model.addAttribute("productJsonLdList", productJsonLdList); // Přidání seznamu JSON stringů do modelu
            log.debug("Generated {} JSON-LD strings for featured products.", productJsonLdList.size());

        } catch (Exception e) {
            log.error("Error fetching featured products for homepage: {}", e.getMessage(), e);
            model.addAttribute("featuredProducts", Collections.emptyList());
            model.addAttribute("featuredProductPrices", Collections.emptyMap());
            model.addAttribute("productJsonLdList", Collections.emptyList()); // Prázdný seznam při chybě
        }

        // Log pro kontrolu výpisu do konzole
        if(!productJsonLdList.isEmpty()){
            log.debug("--- První JSON-LD pro index.html ---\n{}\n--- Konec JSON-LD ---", productJsonLdList.get(0));
        }


        return "index";
    }
    @GetMapping("/gdpr")
    public String showGdprPage() {
        log.debug("Zobrazuji stránku GDPR.");
        return "gdpr"; // Vrátí název šablony gdpr.html
    }

    @GetMapping("/obchodni-podminky")
    public String showVopPage() {
        log.debug("Zobrazuji stránku Všeobecné obchodní podmínky.");
        return "obchodni-podminky"; // Vrátí název šablony obchodni-podminky.html
    }

    // Přidej tuto pomocnou metodu do HomeController, pokud tam ještě není
    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        int maxLength = 160; // Běžná délka pro meta description
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}