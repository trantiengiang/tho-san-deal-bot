package vn.thosandeal.bot.service.pricing.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Map;

@Component
public class CheckoutInitDataParser {

    private static final Logger log = LoggerFactory.getLogger(CheckoutInitDataParser.class);
    private static final String INIT_DATA_MARKER = "window.__initData__";

    private final ObjectMapper objectMapper;

    public CheckoutInitDataParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses the HTML response from https://checkout.lazada.vn/shipping.
     *
     * @param html             raw HTML response
     * @param requestedItemId  expected product itemId
     * @param requestedSkuId   expected variant skuId
     * @return CheckoutParsedResult with semantic status and prices
     */
    public CheckoutParsedResult parse(String html, String requestedItemId, String requestedSkuId) {
        if (html == null || html.isBlank()) {
            return CheckoutParsedResult.error(CheckoutParsedResult.Status.INVALID_PREVIEW_RESPONSE, "Empty response HTML");
        }

        // A. Detect hard security pages (CAPTCHA / WAF / Challenge) first
        CheckoutParsedResult.Status hardSecurityStatus = detectHardSecurity(html);
        if (hardSecurityStatus != null) {
            return CheckoutParsedResult.error(hardSecurityStatus, "Hard security challenge: " + hardSecurityStatus);
        }

        // B. Attempt to locate window.__initData__ script
        Document doc = Jsoup.parse(html);
        Elements scripts = doc.getElementsByTag("script");
        String targetScriptContent = null;

        for (Element script : scripts) {
            String content = script.data();
            if (content == null || content.isEmpty()) {
                content = script.html();
            }
            if (content != null && content.contains(INIT_DATA_MARKER)) {
                targetScriptContent = content;
                break;
            }
        }

        if (targetScriptContent == null && html.contains(INIT_DATA_MARKER)) {
            // Fallback if Jsoup didn't isolate the script tag
            targetScriptContent = html;
        }

        // C. If initData script is found, parse and inspect structured data FIRST
        if (targetScriptContent != null) {
            String jsonPayload;
            try {
                jsonPayload = extractInitDataJson(targetScriptContent);
            } catch (Exception e) {
                log.warn("Failed to extract initData JSON from script: {}", e.getMessage());
                jsonPayload = null;
            }

            if (jsonPayload != null && !jsonPayload.isBlank()) {
                JsonNode rootNode;
                try {
                    rootNode = objectMapper.readTree(jsonPayload);
                } catch (Exception e) {
                    log.warn("Jackson failed to parse initData JSON: {}", e.getMessage());
                    rootNode = null;
                }

                if (rootNode != null) {
                    return parseInitDataRoot(rootNode, requestedItemId, requestedSkuId);
                }
            }
        }

        // D. Only when initData is absent or structurally unusable, inspect HTML login markers
        if (isLoginPage(html)) {
            return CheckoutParsedResult.error(CheckoutParsedResult.Status.SESSION_EXPIRED,
                    "Session expired / redirected to login page (initData absent)");
        }

        return CheckoutParsedResult.error(
                CheckoutParsedResult.Status.INVALID_PREVIEW_RESPONSE,
                "Missing or unparseable window.__initData__ in response");
    }

    private CheckoutParsedResult parseInitDataRoot(JsonNode rootNode, String requestedItemId, String requestedSkuId) {
        // Check for explicit session/auth error in structured data
        String errorNodeStr = rootNode.path("errorCode").toString().toLowerCase();
        if (errorNodeStr.contains("session") || errorNodeStr.contains("login") || errorNodeStr.contains("auth")) {
            String logMsg = rootNode.path("errorCode").path("logMessage").asText("Session/auth failure in initData");
            log.warn("InitData reported fatal auth error: {}", logMsg);
            return CheckoutParsedResult.error(CheckoutParsedResult.Status.SESSION_EXPIRED, logMsg);
        }

        // Check success flag
        boolean success = rootNode.path("success").asBoolean(true);
        if (!success) {
            String logMsg = rootNode.path("errorCode").path("logMessage").asText("");
            if (logMsg.isBlank()) {
                logMsg = rootNode.path("errorCode").path("displayMessage").asText("Checkout render failed");
            }
            log.warn("Checkout initData returned success=false for skuId={}: {}", requestedSkuId, logMsg);
            return CheckoutParsedResult.error(CheckoutParsedResult.Status.INVALID_PREVIEW_RESPONSE,
                    "Checkout render failed for SKU: " + logMsg);
        }

        JsonNode dataNode = rootNode.path("module").path("data");
        if (dataNode.isMissingNode() || !dataNode.isObject()) {
            return CheckoutParsedResult.error(CheckoutParsedResult.Status.INVALID_PREVIEW_RESPONSE,
                    "module.data missing in initData");
        }

        // Match requested SKU
        JsonNode matchedItemNode = null;
        Iterator<Map.Entry<String, JsonNode>> fields = dataNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getKey().startsWith("item_")) {
                JsonNode itemFields = entry.getValue().path("fields");
                String itemId = itemFields.path("itemId").asText(null);
                String skuId = itemFields.path("sku").path("skuId").asText(null);

                if (requestedItemId.equals(itemId) && requestedSkuId.equals(skuId)) {
                    matchedItemNode = itemFields;
                    break;
                }
            }
        }

        if (matchedItemNode == null) {
            log.warn("Requested SKU {} for item {} not found in checkout initData items", requestedSkuId, requestedItemId);
            return CheckoutParsedResult.error(
                    CheckoutParsedResult.Status.SKU_MISMATCH,
                    "Requested SKU " + requestedSkuId + " not matched in returned checkout items");
        }

        // Extract product payable price (fields.discountPrice.price)
        String skuId = matchedItemNode.path("sku").path("skuId").asText();
        String skuText = matchedItemNode.path("sku").path("skuText").asText();
        String currentPriceRaw = matchedItemNode.path("price").path("currentPrice").asText(null);
        String discountPriceRaw = matchedItemNode.path("discountPrice").path("price").asText(null);

        if (discountPriceRaw == null || discountPriceRaw.isBlank()) {
            discountPriceRaw = currentPriceRaw;
        }

        if (discountPriceRaw == null || discountPriceRaw.isBlank()) {
            return CheckoutParsedResult.error(
                    CheckoutParsedResult.Status.PARSE_FAILED,
                    "Both discountPrice and currentPrice missing for SKU " + requestedSkuId);
        }

        BigDecimal productPayable = LazadaMoneyParser.parse(discountPriceRaw);
        BigDecimal currentPrice = currentPriceRaw != null ? LazadaMoneyParser.parse(currentPriceRaw) : productPayable;

        // Extract order total if present
        BigDecimal orderTotalPay = null;
        JsonNode orderTotalNode = dataNode.path("orderTotal_1").path("fields").path("payment").path("pay");
        if (!orderTotalNode.isMissingNode()) {
            try {
                orderTotalPay = LazadaMoneyParser.parse(orderTotalNode.asText());
            } catch (Exception ignored) {}
        }

        return CheckoutParsedResult.success(skuId, skuText, currentPrice, productPayable, orderTotalPay);
    }

    /**
     * Checks HTML for anti-bot / WAF challenges or Captcha pages.
     */
    private CheckoutParsedResult.Status detectHardSecurity(String html) {
        String lower = html.toLowerCase();

        // Captcha pages
        if (lower.contains("captcha") || lower.contains("nhập mã xác nhận")
                || lower.contains("xác minh bảo mật") || lower.contains("slide to verify")
                || lower.contains("punish") || lower.contains("geetest")) {
            return CheckoutParsedResult.Status.CAPTCHA_REQUIRED;
        }

        // Anti-bot / WAF challenges
        if (lower.contains("cf-chl") || (lower.contains("sec-ch-ua-platform") && lower.contains("challenge"))
                || (lower.contains("waf") && lower.contains("blocked"))
                || lower.contains("access denied") || lower.contains("403 forbidden")) {
            return CheckoutParsedResult.Status.WAF_BLOCKED;
        }

        return null;
    }

    /**
     * Checks if HTML represents an actual login page when initData is absent.
     */
    private boolean isLoginPage(String html) {
        String lower = html.toLowerCase();
        return lower.contains("/user/login")
                || lower.contains("member/login")
                || lower.contains("loginbytoken")
                || lower.contains("session expired")
                || lower.contains("phiên đăng nhập hết hạn")
                || (lower.contains("đăng nhập") && lower.contains("anonlogin"));
    }

    /**
     * Extracts the JSON string of window.__initData__ using balanced-brace algorithm.
     * Handles quoted strings, escaped quotes, nested braces, and variable whitespace safely.
     */
    public String extractInitDataJson(String scriptContent) {
        int markerIdx = scriptContent.indexOf(INIT_DATA_MARKER);
        if (markerIdx == -1) {
            return null;
        }

        // Find the first opening brace after the marker
        int startIdx = scriptContent.indexOf('{', markerIdx);
        if (startIdx == -1) {
            return null;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = startIdx; i < scriptContent.length(); i++) {
            char c = scriptContent.charAt(i);

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
                        return scriptContent.substring(startIdx, i + 1);
                    }
                }
            }
        }

        return null;
    }
}
