package org.example.eshop.service;

import org.example.eshop.config.PriceConstants;
import org.example.eshop.model.Image;
import org.example.eshop.model.Product;
import org.example.eshop.model.TaxRate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class FeedGenerationService implements PriceConstants {

    private static final Logger log = LoggerFactory.getLogger(FeedGenerationService.class);

    @Autowired
    private ProductService productService;

    @Autowired
    private TaxRateService taxRateService;

    @Value("${eshop.url:https://www.drevniknamiru.cz}")
    private String baseUrl;

    private static final BigDecimal DEFAULT_HEUREKA_VAT_RATE = new BigDecimal("0.21");
    private static final String HEUREKA_CATEGORY_TEXT = "Heureka.cz | Dům a zahrada | Zahrada | Zahradní nábytek a další vybavení | Dřevníky";
    private static final String HEUREKA_DELIVERY_DAYS = "30";
    private static final String HEUREKA_DELIVERY_ID = "VLASTNI_PREPRAVA";
    private static final String HEUREKA_DELIVERY_PRICE = "0";
    private static final String GOOGLE_PRODUCT_CATEGORY_ID = "5076"; // ID for Sheds, Garages & Carports
    private static final String GOOGLE_PRODUCT_TYPE_TEXT = "Dům a zahrada > Zahradní stavby > Dřevníky";
    public static final String BRAND_NAME = "Dřevníky Kolář";

    public String generateRobotsTxt() {
        StringBuilder sb = new StringBuilder();
        sb.append("User-agent: *\n\n");
        sb.append("Allow: /\n");
        sb.append("Allow: /o-nas\n");
        sb.append("Allow: /produkty\n");
        sb.append("Allow: /produkt/\n");
        sb.append("Allow: /gdpr\n");
        sb.append("Allow: /obchodni-podminky\n");
        sb.append("Allow: /images/\n");
        sb.append("Allow: /css/\n");
        sb.append("Allow: /js/\n");
        sb.append("Allow: /uploads/\n");
        sb.append("\n");
        sb.append("Disallow: /admin/\n");
        sb.append("Disallow: /muj-ucet/\n");
        sb.append("Disallow: /kosik/\n");
        sb.append("Disallow: /pokladna/\n");
        sb.append("Disallow: /prihlaseni\n");
        sb.append("Disallow: /registrace\n");
        sb.append("Disallow: /zapomenute-heslo\n");
        sb.append("Disallow: /resetovat-heslo\n");
        sb.append("Disallow: /error/\n");
        sb.append("Disallow: /api/\n");
        sb.append("Disallow: /nastavit-menu\n");
        sb.append("\n");
        sb.append("Sitemap: ").append(baseUrl).append("/sitemap.xml\n");
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String generateSitemapXml() {
        log.info("Generating sitemap.xml...");
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        appendUrl(xml, baseUrl + "/", "weekly", "1.0");
        appendUrl(xml, baseUrl + "/o-nas", "monthly", "0.7");
        appendUrl(xml, baseUrl + "/produkty", "weekly", "0.9");
        appendUrl(xml, baseUrl + "/produkt/na-miru", "monthly", "0.8");
        appendUrl(xml, baseUrl + "/obchodni-podminky", "yearly", "0.3");
        appendUrl(xml, baseUrl + "/gdpr", "yearly", "0.3");

        try {
            List<Product> products = productService.getAllActiveProducts();
            log.info("Found {} active products for sitemap.", products.size());
            for (Product product : products) {
                if (!product.isCustomisable() && product.getSlug() != null) {
                    String productUrl = baseUrl + "/produkt/" + product.getSlug();
                    appendUrl(xml, productUrl, "monthly", "0.8");
                }
            }
        } catch (Exception e) {
            log.error("Error fetching products for sitemap: {}", e.getMessage(), e);
        }

        xml.append("</urlset>");
        log.info("Sitemap.xml generated successfully.");
        return xml.toString();
    }

    // --- AKTUALIZOVANÉ Generování Google Merchant feed ---
    @Transactional(readOnly = true)
    public String generateGoogleMerchantFeed(String targetCurrency) {
        log.info("Generating Google Merchant feed for currency: {}", targetCurrency);
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<rss version=\"2.0\" xmlns:g=\"http://base.google.com/ns/1.0\">\n");
        xml.append("<channel>\n");
        xml.append("  <title>Dřevníky Kolář - Produktový feed</title>\n");
        xml.append("  <link>").append(baseUrl).append("</link>\n");
        xml.append("  <description>Feed produktů pro Google Merchant Center</description>\n\n");

        try {
            List<Product> products = productService.getAllActiveProducts();
            log.info("Found {} active products for Google feed.", products.size());
            for (Product product : products) {
                if (!product.isCustomisable() && product.isActive() && product.getSlug() != null) {
                    Map<String, Object> priceInfo = productService.calculateFinalProductPrice(product, targetCurrency);
                    BigDecimal discountedPrice = (BigDecimal) priceInfo.get("discountedPrice");
                    BigDecimal originalPrice = (BigDecimal) priceInfo.get("originalPrice");

                    BigDecimal priceNoVat = null;
                    if (discountedPrice != null && discountedPrice.compareTo(BigDecimal.ZERO) > 0) {
                        priceNoVat = discountedPrice;
                    } else if (originalPrice != null && originalPrice.compareTo(BigDecimal.ZERO) > 0) {
                        priceNoVat = originalPrice;
                    }

                    if (priceNoVat == null || priceNoVat.compareTo(BigDecimal.ZERO) <= 0) {
                        log.warn("Skipping product ID {} ('{}') in Google feed due to missing or zero price after checking discounted and original price in {}.", product.getId(), product.getName(), targetCurrency);
                        continue;
                    }

                    xml.append("  <item>\n");
                    appendXmlElement(xml, "g:id", "STD-" + product.getId(), 4);
                    appendXmlElement(xml, "g:title", product.getName(), 4);
                    appendXmlElement(xml, "g:description", product.getShortDescription() != null ? product.getShortDescription() : product.getDescription(), 4);
                    appendXmlElement(xml, "g:link", baseUrl + "/produkt/" + product.getSlug(), 4);

                    String imageUrl = product.getImagesOrdered().stream()
                            .findFirst()
                            .map(Image::getUrl)
                            .map(url -> url.startsWith("/") ? baseUrl + url : url)
                            .orElse(baseUrl + "/images/placeholder.png");
                    appendXmlElement(xml, "g:image_link", imageUrl, 4);

                    appendXmlElement(xml, "g:price", priceNoVat.setScale(PRICE_SCALE, ROUNDING_MODE) + " " + targetCurrency, 4);
                    appendXmlElement(xml, "g:brand", BRAND_NAME, 4);
                    appendXmlElement(xml, "g:condition", "new", 4);
                    appendXmlElement(xml, "g:availability", "in stock", 4);
                    appendXmlElement(xml, "g:identifier_exists", "no", 4);
                    appendXmlElement(xml, "g:product_type", GOOGLE_PRODUCT_TYPE_TEXT, 4);
                    appendXmlElement(xml, "g:google_product_category", GOOGLE_PRODUCT_CATEGORY_ID, 4);

                    // --- Přidání materiálu a rozměrů pro Google ---
                    if (product.getMaterial() != null && !product.getMaterial().isBlank()) {
                        appendXmlElement(xml, "g:material", product.getMaterial(), 4);
                    }
                    if (product.getHeight() != null) {
                        appendXmlElement(xml, "g:product_height", product.getHeight().setScale(1, RoundingMode.HALF_UP).toPlainString() + " cm", 4);
                    }
                    if (product.getLength() != null) { // length = Šířka
                        appendXmlElement(xml, "g:product_width", product.getLength().setScale(1, RoundingMode.HALF_UP).toPlainString() + " cm", 4);
                    }
                    if (product.getWidth() != null) { // width = Hloubka
                        appendXmlElement(xml, "g:product_length", product.getWidth().setScale(1, RoundingMode.HALF_UP).toPlainString() + " cm", 4);
                    }
                    // --- Konec přidání materiálu a rozměrů ---

                    xml.append("  </item>\n");
                }
            }
        } catch (Exception e) {
            log.error("Error fetching products for Google Merchant feed: {}", e.getMessage(), e);
        }

        xml.append("</channel>\n");
        xml.append("</rss>");
        log.info("Google Merchant feed generated successfully.");
        return xml.toString();
    }


    @Transactional(readOnly = true)
    public String generateHeurekaFeed(String targetCurrency) {
        log.info("Generating Heureka feed for currency: {}", targetCurrency);
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        xml.append("<SHOP>\n");

        try {
            List<Product> products = productService.getAllActiveProducts();
            log.info("Found {} active products for Heureka feed.", products.size());

            TaxRate defaultTaxRate = findDefaultTaxRate(targetCurrency);

            for (Product product : products) {
                if (!product.isCustomisable() && product.isActive() && product.getSlug() != null) {
                    Map<String, Object> priceInfo = productService.calculateFinalProductPrice(product, targetCurrency);
                    BigDecimal discountedPrice = (BigDecimal) priceInfo.get("discountedPrice");
                    BigDecimal originalPrice = (BigDecimal) priceInfo.get("originalPrice");

                    BigDecimal priceNoVat = null;
                    if (discountedPrice != null && discountedPrice.compareTo(BigDecimal.ZERO) > 0) {
                        priceNoVat = discountedPrice;
                    } else if (originalPrice != null && originalPrice.compareTo(BigDecimal.ZERO) > 0) {
                        priceNoVat = originalPrice;
                    }

                    if (priceNoVat == null || priceNoVat.compareTo(BigDecimal.ZERO) <= 0) {
                        log.warn("Skipping product ID {} ('{}') in Heureka feed due to missing or zero price after checking discounted and original price in {}.", product.getId(), product.getName(), targetCurrency);
                        continue;
                    }

                    TaxRate productTaxRate = getApplicableTaxRate(product, defaultTaxRate);
                    BigDecimal vatRateValue = productTaxRate.getRate();
                    BigDecimal priceWithVat = priceNoVat.multiply(BigDecimal.ONE.add(vatRateValue))
                            .setScale(PRICE_SCALE, ROUNDING_MODE);

                    xml.append("  <SHOPITEM>\n");
                    appendXmlElement(xml, "ITEM_ID", "STD-" + product.getId(), 4);
                    // ÚPRAVA ZDE:
                    appendXmlElement(xml, "PRODUCTNAME", BRAND_NAME + " | " + product.getName() + " | " + "STD - " + product.getId(), 4);
                    appendXmlElement(xml, "PRODUCT", BRAND_NAME + " | " + product.getName() + " - distribuce po ČR/SR", 4); // Také upraveno pro konzistenci, pokud je třeba
                    appendXmlElement(xml, "DESCRIPTION", product.getShortDescription() != null ? product.getShortDescription() : product.getDescription(), 4);
                    appendXmlElement(xml, "URL", baseUrl + "/produkt/" + product.getSlug(), 4);

                    String imageUrl = product.getImagesOrdered().stream()
                            .findFirst()
                            .map(Image::getUrl)
                            .map(url -> url.startsWith("/") ? baseUrl + url : url)
                            .orElse(baseUrl + "/images/placeholder.png");
                    appendXmlElement(xml, "IMGURL", imageUrl, 4);

                    appendXmlElement(xml, "PRICE_VAT", priceWithVat.toPlainString(), 4);
                    appendXmlElement(xml, "VAT", vatRateValue.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).toPlainString() + "%", 4);

                    // PARAMetry
                    if (product.getHeight() != null) {
                        appendParam(xml, "Výška", product.getHeight().setScale(0, RoundingMode.HALF_UP).toPlainString() + " cm", 4);
                    }
                    if (product.getLength() != null) { // length = šířka
                        appendParam(xml, "Šířka", product.getLength().setScale(0, RoundingMode.HALF_UP).toPlainString() + " cm", 4);
                    }
                    if (product.getWidth() != null) { // width = hloubka
                        appendParam(xml, "Hloubka", product.getWidth().setScale(0, RoundingMode.HALF_UP).toPlainString() + " cm", 4);
                    }
                    String material = (product.getMaterial() != null && !product.getMaterial().isBlank()) ? product.getMaterial() : "Dřevo";
                    appendParam(xml, "Materiál", material, 4);

                    appendXmlElement(xml, "MANUFACTURER", BRAND_NAME, 4); // Použití konstanty
                    appendXmlElement(xml, "CATEGORYTEXT", HEUREKA_CATEGORY_TEXT, 4);
                    appendXmlElement(xml, "DELIVERY_DATE", HEUREKA_DELIVERY_DAYS, 4);

                    // DELIVERY block
                    xml.append("    <DELIVERY>\n");
                    appendXmlElement(xml, "DELIVERY_ID", HEUREKA_DELIVERY_ID, 6);
                    appendXmlElement(xml, "DELIVERY_PRICE", HEUREKA_DELIVERY_PRICE, 6);
                    xml.append("    </DELIVERY>\n");

                    // SPECIAL_SERVICE tagy
                    appendXmlElement(xml, "SPECIAL_SERVICE", "Sestavení v ceně dopravy", 4);
                    appendXmlElement(xml, "SPECIAL_SERVICE", "Úprava rozměrů na míru", 4);
                    appendXmlElement(xml, "SPECIAL_SERVICE", "Pravidelné aktualizace o stavu objednávky", 4);
                    appendXmlElement(xml, "SPECIAL_SERVICE", "Možnost výběru vlastního barevného provedení", 4);

                    xml.append("  </SHOPITEM>\n");
                }
            }
        } catch (Exception e) {
            log.error("Error generating Heureka feed: {}", e.getMessage(), e);
        }

        xml.append("</SHOP>");
        log.info("Heureka feed generated successfully.");
        return xml.toString();
    }


    // --- Pomocné metody ---
    private void appendUrl(StringBuilder xml, String loc, String changefreq, String priority) {
        xml.append("  <url>\n");
        appendXmlElement(xml, "loc", loc, 4);
        if (changefreq != null) appendXmlElement(xml, "changefreq", changefreq, 4);
        if (priority != null) appendXmlElement(xml, "priority", priority, 4);
        appendXmlElement(xml, "lastmod", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), 4);
        xml.append("  </url>\n");
    }

    private TaxRate findDefaultTaxRate(String currency) {
        Optional<TaxRate> rateOpt = taxRateService.getAllTaxRates().stream()
                .filter(r -> r.getRate().compareTo(DEFAULT_HEUREKA_VAT_RATE) == 0)
                .findFirst();
        if (rateOpt.isPresent()) return rateOpt.get();

        Optional<TaxRate> fallbackRate = taxRateService.getAllTaxRates().stream().findFirst();
        if (fallbackRate.isPresent()) {
            log.warn("Default VAT rate ({}) not found, using first available rate: {}", DEFAULT_HEUREKA_VAT_RATE, fallbackRate.get().getRate());
            return fallbackRate.get();
        }

        log.error("CRITICAL: No tax rates found in the system!");
        TaxRate dummyRate = new TaxRate();
        dummyRate.setRate(BigDecimal.ZERO);
        dummyRate.setName("CHYBA - Není sazba");
        return dummyRate;
    }

    private TaxRate getApplicableTaxRate(Product product, TaxRate defaultRate) {
        Set<TaxRate> availableRates = product.getAvailableTaxRates();
        if (availableRates == null || availableRates.isEmpty()) {
            log.warn("Product ID {} has no available tax rates defined! Using default rate: {}", product.getId(), defaultRate.getRate());
            return defaultRate;
        }
        Optional<TaxRate> standardRate = availableRates.stream()
                .filter(r -> r.getRate().compareTo(DEFAULT_HEUREKA_VAT_RATE) == 0)
                .findFirst();

        return standardRate.orElse(availableRates.stream().findFirst().orElse(defaultRate));
    }

    private void appendXmlElement(StringBuilder xml, String tagName, Object value, int indentSpaces) {
        if (value != null) {
            String indent = " ".repeat(indentSpaces);
            xml.append(indent)
                    .append("<").append(tagName).append(">")
                    .append(escapeXml(value.toString()))
                    .append("</").append(tagName).append(">\n");
        }
    }

    private void appendParam(StringBuilder xml, String paramName, String val, int indentSpaces) {
        if (paramName != null && val != null) {
            String indent = " ".repeat(indentSpaces);
            xml.append(indent).append("<PARAM>\n");
            appendXmlElement(xml, "PARAM_NAME", paramName, indentSpaces + 2);
            appendXmlElement(xml, "VAL", val, indentSpaces + 2);
            xml.append(indent).append("</PARAM>\n");
        }
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

}