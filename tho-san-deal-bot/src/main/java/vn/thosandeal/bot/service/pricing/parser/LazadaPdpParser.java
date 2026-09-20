package vn.thosandeal.bot.service.pricing.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class LazadaPdpParser {

    private static final Logger log = LoggerFactory.getLogger(LazadaPdpParser.class);
    private static final String MODULE_DATA_MARKER = "var __moduleData__ =";

    private final ObjectMapper objectMapper;

    public LazadaPdpParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses the PDP HTML page once to extract all SKU variants.
     */
    public LazadaProductSnapshot parse(String html) {
        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException("PDP HTML is empty");
        }

        Document doc = Jsoup.parse(html);
        String canonicalUrl = null;
        Element canonicalLink = doc.selectFirst("link[rel=canonical]");
        if (canonicalLink != null) {
            canonicalUrl = canonicalLink.attr("href");
        }

        // Extract __moduleData__ using balanced-brace parser
        String jsonPayload = extractModuleDataJson(html);
        if (jsonPayload == null || jsonPayload.isEmpty()) {
            log.warn("Could not find or extract __moduleData__ from PDP HTML");
            return fallbackParse(doc, canonicalUrl);
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload);
            JsonNode fields = root.path("data").path("root").path("fields");
            String itemId = fields.path("primaryKey").path("itemId").asText(null);
            if (itemId == null || itemId.isBlank()) {
                itemId = fields.path("product").path("itemId").asText(null);
            }
            if (itemId == null || itemId.isBlank()) {
                com.fasterxml.jackson.databind.JsonNode skus = fields.path("productOption").path("skuBase").path("skus");
                if (skus.isArray() && !skus.isEmpty()) {
                    itemId = skus.get(0).path("itemId").asText(null);
                }
            }
            if (itemId == null || itemId.isBlank()) {
                if (canonicalUrl != null) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("-i(\\d+)").matcher(canonicalUrl);
                    if (m.find()) {
                        itemId = m.group(1);
                    }
                }
            }

            String productName = fields.path("product").path("title").asText(null);
            if (productName == null) {
                Element titleElem = doc.selectFirst("title");
                productName = titleElem != null ? titleElem.text() : "Sản phẩm Lazada";
            }

            // Default product sale price if present
            BigDecimal defaultPrice = null;
            String priceStr = fields.path("tracking").path("pdt_price").asText(null);
            if (priceStr != null) {
                try {
                    defaultPrice = LazadaMoneyParser.parse(priceStr);
                } catch (Exception ignored) {}
            }

            // 1. Build property name lookup map: "pid:vid" -> "valueName"
            Map<String, String> propValueMap = new HashMap<>();
            JsonNode propertiesNode = fields.path("productOption").path("skuBase").path("properties");
            if (propertiesNode.isArray()) {
                for (JsonNode prop : propertiesNode) {
                    String pid = prop.path("pid").asText();
                    JsonNode values = prop.path("values");
                    if (values.isArray()) {
                        for (JsonNode val : values) {
                            String vid = val.path("vid").asText();
                            String name = val.path("name").asText();
                            propValueMap.put(pid + ":" + vid, name);
                        }
                    }
                }
            }

            // 2. Build skuInfos lookup for availability and stock
            JsonNode skuInfosNode = fields.path("skuInfos");

            // 3. Extract all SKUs from skuBase.skus
            List<LazadaSkuSnapshot> skuSnapshots = new ArrayList<>();
            JsonNode skusNode = fields.path("productOption").path("skuBase").path("skus");
            if (skusNode.isArray() && skusNode.size() > 0) {
                for (JsonNode skuNode : skusNode) {
                    String skuId = skuNode.path("skuId").asText(null);
                    if (skuId == null || skuId.isBlank()) {
                        continue;
                    }

                    String propPath = skuNode.path("propPath").asText("");
                    String variantName = resolveVariantName(propPath, propValueMap);

                    // Check availability and price from skuInfos
                    boolean available = true;
                    Integer stock = null;
                    BigDecimal skuSalePrice = defaultPrice;
                    BigDecimal skuOriginalPrice = defaultPrice;

                    JsonNode skuInfo = skuInfosNode.path(skuId);
                    if (!skuInfo.isMissingNode()) {
                        boolean disabled = skuInfo.path("operation").path("disable").asBoolean(false);
                        available = !disabled;
                        if (skuInfo.has("stock")) {
                            stock = skuInfo.path("stock").asInt();
                            if (stock <= 0) {
                                available = false;
                            }
                        }

                        JsonNode priceNode = skuInfo.path("price");
                        if (!priceNode.isMissingNode()) {
                            if (priceNode.has("salePrice")) {
                                JsonNode sp = priceNode.path("salePrice");
                                if (sp.has("value") && !sp.path("value").isNull()) {
                                    try {
                                        skuSalePrice = new BigDecimal(sp.path("value").asText());
                                    } catch (Exception ignored) {}
                                }
                            }
                            if (priceNode.has("originalPrice")) {
                                JsonNode op = priceNode.path("originalPrice");
                                if (op.has("value") && !op.path("value").isNull()) {
                                    try {
                                        skuOriginalPrice = new BigDecimal(op.path("value").asText());
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                    }

                    BigDecimal finalSalePrice = skuSalePrice != null ? skuSalePrice : defaultPrice;
                    BigDecimal finalOriginalPrice = skuOriginalPrice != null ? skuOriginalPrice : finalSalePrice;

                    skuSnapshots.add(new LazadaSkuSnapshot(
                            skuId,
                            null,
                            variantName,
                            finalSalePrice,
                            finalOriginalPrice,
                            stock,
                            available
                    ));
                }
            }

            if (skuSnapshots.isEmpty()) {
                // Try from skuInfos keys directly
                if (skuInfosNode.isObject()) {
                    Iterator<String> keys = skuInfosNode.fieldNames();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        if (!"0".equals(k)) {
                            JsonNode skuInfo = skuInfosNode.path(k);
                            BigDecimal sPrice = defaultPrice;
                            BigDecimal oPrice = defaultPrice;
                            if (skuInfo.has("price")) {
                                JsonNode pn = skuInfo.path("price");
                                if (pn.has("salePrice") && pn.path("salePrice").has("value")) {
                                    try {
                                        sPrice = new BigDecimal(pn.path("salePrice").path("value").asText());
                                    } catch (Exception ignored) {}
                                }
                                if (pn.has("originalPrice") && pn.path("originalPrice").has("value")) {
                                    try {
                                        oPrice = new BigDecimal(pn.path("originalPrice").path("value").asText());
                                    } catch (Exception ignored) {}
                                }
                            }
                            BigDecimal fSale = sPrice != null ? sPrice : defaultPrice;
                            BigDecimal fOrig = oPrice != null ? oPrice : fSale;
                            skuSnapshots.add(new LazadaSkuSnapshot(
                                    k, null, "Phân loại " + k, fSale, fOrig, null, true
                            ));
                        }
                    }
                }
            }

            return new LazadaProductSnapshot(itemId, productName, canonicalUrl, skuSnapshots);

        } catch (Exception e) {
            log.error("Failed to parse PDP moduleData: {}", e.getMessage(), e);
            return fallbackParse(doc, canonicalUrl);
        }
    }

    private String resolveVariantName(String propPath, Map<String, String> propValueMap) {
        if (propPath == null || propPath.isBlank()) {
            return "Mặc định";
        }
        String[] parts = propPath.split(";");
        List<String> names = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (propValueMap.containsKey(trimmed)) {
                names.add(propValueMap.get(trimmed));
            } else {
                // Fallback: strip pid if colon present
                int colonIdx = trimmed.indexOf(':');
                names.add(colonIdx != -1 ? trimmed.substring(colonIdx + 1) : trimmed);
            }
        }
        return String.join(" / ", names);
    }

    private String extractModuleDataJson(String html) {
        int markerIdx = html.indexOf(MODULE_DATA_MARKER);
        if (markerIdx == -1) {
            return null;
        }

        int startIdx = html.indexOf('{', markerIdx);
        if (startIdx == -1) {
            return null;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = startIdx; i < html.length(); i++) {
            char c = html.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return html.substring(startIdx, i + 1);
                    }
                }
            }
        }
        return null;
    }

    private LazadaProductSnapshot fallbackParse(Document doc, String canonicalUrl) {
        Element titleElem = doc.selectFirst("title");
        String title = titleElem != null ? titleElem.text() : "Sản phẩm Lazada";
        return new LazadaProductSnapshot(null, title, canonicalUrl, Collections.emptyList());
    }
}
