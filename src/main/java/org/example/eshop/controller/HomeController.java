package org.example.eshop.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
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

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class HomeController implements PriceConstants { // Přidáno PriceConstants

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    private final ProductService productService;
    private final CurrencyService currencyService;
    private final ObjectMapper objectMapper;

    @Autowired
    public HomeController(ProductService productService, CurrencyService currencyService, ObjectMapper objectMapper) {
        this.productService = productService;
        this.currencyService = currencyService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/")
    public String home(Model model, HttpServletRequest request) {
        log.info("Accessing home page - Applying price sorting");
        String currentCurrency = currencyService.getSelectedCurrency();
        model.addAttribute("currentGlobalCurrency", currentCurrency); // Pro layout

        List<String> productJsonLdList = new ArrayList<>();
        Map<Long, Map<String, Object>> featuredProductPrices = new HashMap<>();
        List<Product> featuredProducts = Collections.emptyList();

        try {
            // 1. Načtení VŠECH aktivních standardních produktů
            List<Product> allActiveStandardProducts = productService.getAllActiveProducts().stream()
                    .filter(p -> p != null && !p.isCustomisable()) // Filtrujeme standardní a non-null
                    .collect(Collectors.toList());
            log.debug("Found {} active standard products initially.", allActiveStandardProducts.size());

            // 2. Seřazení podle ceny v aktuální měně (vzestupně)
            allActiveStandardProducts.sort(Comparator.comparing(
                    p -> EURO_CURRENCY.equals(currentCurrency) ? p.getBasePriceEUR() : p.getBasePriceCZK(),
                    Comparator.nullsLast(BigDecimal::compareTo) // Produkty bez ceny dáme na konec
            ));
            log.debug("Sorted active standard products by {} price.", currentCurrency);

            // 3. Výběr prvních 4 (nebo méně, pokud jich není tolik)
            featuredProducts = allActiveStandardProducts.stream()
                    .limit(4)
                    .collect(Collectors.toList());
            log.debug("Selected top {} products for display.", featuredProducts.size());

            // 4. Výpočet finálních cen a generování JSON-LD (pouze pro vybrané produkty)
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
                    if (finalPriceForSchema == null) finalPriceForSchema = BigDecimal.ZERO;
                } catch (Exception priceEx) {
                    log.error("Error calculating price for featured product ID {}: {}", product.getId(), priceEx.getMessage());
                    featuredProductPrices.put(product.getId(), Collections.emptyMap());
                }

                // Generování JSON-LD (kód beze změny, jen je teď v cyklu pro featuredProducts)
                try {
                    Map<String, Object> productJsonLd = new LinkedHashMap<>();
                    productJsonLd.put("@context", "https://schema.org");
                    productJsonLd.put("@type", "Product");
                    productJsonLd.put("name", product.getName());
                    productJsonLd.put("description", StringUtils.hasText(product.getShortDescription()) ? product.getShortDescription() : abbreviate(product.getDescription()));
                    productJsonLd.put("sku", "STD-" + product.getId());
                    productJsonLd.put("url", baseUrl + "/produkt/" + product.getSlug());

                    if (!product.getImagesOrdered().isEmpty()) {
                        String imageUrl = product.getImagesOrdered().get(0).getUrl();
                        if (imageUrl != null && !imageUrl.toLowerCase().startsWith("http")) {
                            imageUrl = baseUrl + (imageUrl.startsWith("/") ? imageUrl : "/" + imageUrl);
                        }
                        if (imageUrl != null) {
                            productJsonLd.put("image", imageUrl);
                        }
                    }

                    Map<String, Object> brandMap = Map.of("@type", "Brand", "name", "Dřevníky Kolář");
                    productJsonLd.put("brand", brandMap);

                    Map<String, Object> offerMap = new LinkedHashMap<>();
                    offerMap.put("@type", "Offer");
                    offerMap.put("url", baseUrl + "/produkt/" + product.getSlug());
                    offerMap.put("priceCurrency", currentCurrency);
                    offerMap.put("price", finalPriceForSchema.setScale(PRICE_SCALE, ROUNDING_MODE));
                    offerMap.put("availability", "https://schema.org/InStock");
                    offerMap.put("itemCondition", "https://schema.org/NewCondition");

                    Map<String, Object> priceSpecMap = new LinkedHashMap<>();
                    priceSpecMap.put("@type", "UnitPriceSpecification");
                    priceSpecMap.put("price", finalPriceForSchema.setScale(PRICE_SCALE, ROUNDING_MODE));
                    priceSpecMap.put("priceCurrency", currentCurrency);
                    priceSpecMap.put("valueAddedTaxIncluded", false);
                    offerMap.put("priceSpecification", priceSpecMap);

                    productJsonLd.put("offers", offerMap);

                    productJsonLdList.add(objectMapper.writeValueAsString(productJsonLd));

                } catch (Exception jsonEx) {
                    log.error("Error generating JSON-LD for featured product ID {}: {}", product.getId(), jsonEx.getMessage());
                }
            }

            model.addAttribute("featuredProducts", featuredProducts); // Přidání seřazeného a omezeného seznamu
            model.addAttribute("featuredProductPrices", featuredProductPrices);
            model.addAttribute("productJsonLdList", productJsonLdList);
            log.debug("Generated {} JSON-LD strings for featured products.", productJsonLdList.size());

        } catch (Exception e) {
            log.error("Error fetching or sorting featured products for homepage: {}", e.getMessage(), e);
            model.addAttribute("featuredProducts", Collections.emptyList());
            model.addAttribute("featuredProductPrices", Collections.emptyMap());
            model.addAttribute("productJsonLdList", Collections.emptyList());
        }
        return "index";
    }

    @GetMapping("/gdpr")
    public String showGdprPage() {
        log.debug("Zobrazuji stránku GDPR.");
        return "gdpr";
    }

    @GetMapping("/obchodni-podminky")
    public String showVopPage() {
        log.debug("Zobrazuji stránku Všeobecné obchodní podmínky.");
        return "obchodni-podminky";
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        int maxLength = 160;
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}