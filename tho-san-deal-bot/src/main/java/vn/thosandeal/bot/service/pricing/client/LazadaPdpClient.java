package vn.thosandeal.bot.service.pricing.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import vn.thosandeal.bot.service.pricing.parser.LazadaPdpParser;
import vn.thosandeal.bot.service.pricing.parser.LazadaProductSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LazadaPdpClient {

    private static final Logger log = LoggerFactory.getLogger(LazadaPdpClient.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(1);

    private final WebClient webClient;
    private final LazadaPdpParser pdpParser;
    private final vn.thosandeal.bot.validator.LazadaUrlValidator urlValidator;
    private final String userAgent;

    private static final java.util.regex.Pattern ORIGIN_LINK_PATTERN = java.util.regex.Pattern.compile(
            "<link\\s+[^>]*rel=['\"]origin['\"][^>]*href=['\"](https?://[^'\"]+)['\"]",
            java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern ORIGIN_LINK_PATTERN_REV = java.util.regex.Pattern.compile(
            "<link\\s+[^>]*href=['\"](https?://[^'\"]+)['\"][^>]*rel=['\"]origin['\"]",
            java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern META_REFRESH_PATTERN = java.util.regex.Pattern.compile(
            "<meta[^>]+http-equiv=['\"]refresh['\"][^>]+content=['\"][^'\"]*url=(https?://[^'\">]+)",
            java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern JS_REDIRECT_PATTERN = java.util.regex.Pattern.compile(
            "(?:REDIRECTURL\\s*=\\s*new\\s+URL\\(|window\\.location\\.href\\s*=)\\s*['\"](https?://[^'\"]+)['\"]",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    private record CachedSnapshot(LazadaProductSnapshot snapshot, Instant expiresAt) {}
    private final Map<String, CachedSnapshot> cache = new ConcurrentHashMap<>();

    public LazadaPdpClient(
            WebClient.Builder webClientBuilder,
            LazadaPdpParser pdpParser,
            vn.thosandeal.bot.validator.LazadaUrlValidator urlValidator,
            @Value("${pricing.lazada.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36}") String userAgent) {
        this.pdpParser = pdpParser;
        this.urlValidator = urlValidator;
        this.userAgent = userAgent;
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                .build();
    }

    /**
     * Fetches and parses PDP HTML, reusing cached snapshot if available within current cycle.
     * If the returned HTML is a bridge/short link redirect page, automatically resolves and fetches the product page.
     */
    public LazadaProductSnapshot fetchProductSnapshot(String productUrl, String cookieHeader) {
        String cacheKey = productUrl != null ? productUrl.trim() : "";
        CachedSnapshot cached = cache.get(cacheKey);
        if (cached != null && Instant.now().isBefore(cached.expiresAt())) {
            log.info("Reusing cached PDP snapshot for {}", cacheKey);
            return cached.snapshot();
        }

        log.info("Fetching fresh PDP snapshot for {}", productUrl);
        String html = fetchHtml(productUrl, cookieHeader);

        String redirectUrl = extractRedirectUrlIfBridgePage(html);
        if (redirectUrl != null && urlValidator.isValid(redirectUrl)) {
            log.info("Detected Lazada bridge/short link. Resolved {} to {}", productUrl, redirectUrl);
            try {
                String resolvedHtml = fetchHtml(redirectUrl, cookieHeader);
                if (resolvedHtml != null && !resolvedHtml.isBlank()) {
                    html = resolvedHtml;
                }
            } catch (Exception e) {
                log.warn("Failed to fetch resolved PDP URL {}: {}", redirectUrl, e.getMessage());
            }
        }

        LazadaProductSnapshot snapshot = pdpParser.parse(html);
        cache.put(cacheKey, new CachedSnapshot(snapshot, Instant.now().plus(CACHE_TTL)));
        return snapshot;
    }

    private String fetchHtml(String url, String cookieHeader) {
        return webClient.get()
                .uri(url)
                .header(HttpHeaders.USER_AGENT, userAgent)
                .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("sec-ch-ua", "\"Chromium\";v=\"128\", \"Not;A=Brand\";v=\"24\", \"Google Chrome\";v=\"128\"")
                .header("sec-ch-ua-mobile", "?0")
                .header("sec-ch-ua-platform", "\"Windows\"")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Sec-Fetch-Site", "same-origin")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-User", "?1")
                .header("Sec-Fetch-Dest", "document")
                .header(HttpHeaders.COOKIE, cookieHeader != null ? cookieHeader : "")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .block();
    }

    protected String extractRedirectUrlIfBridgePage(String html) {
        if (html == null || html.contains("__moduleData__")) {
            return null;
        }

        java.util.regex.Matcher m = ORIGIN_LINK_PATTERN.matcher(html);
        if (m.find()) {
            return cleanUrl(m.group(1));
        }

        m = ORIGIN_LINK_PATTERN_REV.matcher(html);
        if (m.find()) {
            return cleanUrl(m.group(1));
        }

        m = META_REFRESH_PATTERN.matcher(html);
        if (m.find()) {
            return cleanUrl(m.group(1));
        }

        m = JS_REDIRECT_PATTERN.matcher(html);
        if (m.find()) {
            return cleanUrl(m.group(1));
        }

        return null;
    }

    private String cleanUrl(String rawUrl) {
        if (rawUrl == null) return null;
        return rawUrl.replace("&amp;", "&").trim();
    }

    public void clearCache() {
        cache.clear();
    }
}
