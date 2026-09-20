package vn.thosandeal.bot.service.pricing.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class LazadaBuyNowClient {

    private static final Logger log = LoggerFactory.getLogger(LazadaBuyNowClient.class);
    private static final String DEFAULT_SHIPPING_URL =
            "https://checkout.lazada.vn/shipping?spm=a2o4n.pdp_revamp.main_page.bottom_bar_main_button";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String userAgent;
    private final long minRequestIntervalMs;
    private final AtomicLong lastRequestTime = new AtomicLong(0);

    public LazadaBuyNowClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${pricing.lazada.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36}") String userAgent,
            @Value("${pricing.lazada.requests-per-second:1}") double requestsPerSecond) {
        this.objectMapper = objectMapper;
        this.userAgent = userAgent;
        this.minRequestIntervalMs = requestsPerSecond > 0 ? (long) (1000.0 / requestsPerSecond) : 1000L;
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                .build();
    }

    /**
     * Executes Buy Now checkout preview request for a given itemId and skuId.
     *
     * @param itemId       product itemId
     * @param skuId        variant skuId
     * @param cookieHeader session cookie string
     * @return HTML response text containing window.__initData__
     */
    public String requestCheckoutPreview(String itemId, String skuId, String cookieHeader) {
        enforceRateLimit();

        try {
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("itemId", itemId);
            itemMap.put("skuId", skuId);
            itemMap.put("quantity", 1);
            itemMap.put("attributes", null);

            Map<String, Object> buyParams = Collections.singletonMap("items", Collections.singletonList(itemMap));
            String buyParamsJson = objectMapper.writeValueAsString(buyParams);

            log.debug("Calling Lazada checkout preview for itemId={} skuId={}", itemId, skuId);

            return webClient.post()
                    .uri(DEFAULT_SHIPPING_URL)
                    .header(HttpHeaders.USER_AGENT, userAgent)
                    .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    .header(HttpHeaders.ACCEPT_LANGUAGE, "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("sec-ch-ua", "\"Chromium\";v=\"128\", \"Not;A=Brand\";v=\"24\", \"Google Chrome\";v=\"128\"")
                    .header("sec-ch-ua-mobile", "?0")
                    .header("sec-ch-ua-platform", "\"Windows\"")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Sec-Fetch-Site", "same-site")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-User", "?1")
                    .header("Sec-Fetch-Dest", "document")
                    .header(HttpHeaders.REFERER, "https://www.lazada.vn/")
                    .header("Origin", "https://www.lazada.vn")
                    .header(HttpHeaders.COOKIE, cookieHeader != null ? cookieHeader : "")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("buyParams", buyParamsJson))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(500))
                            .filter(ex -> {
                                if (ex instanceof WebClientResponseException wce) {
                                    int status = wce.getStatusCode().value();
                                    return status == 429 || (status >= 500 && status < 600);
                                }
                                return true; // Retry network/timeout errors
                            }))
                    .block();

        } catch (WebClientResponseException e) {
            log.warn("Lazada checkout preview HTTP error {} for skuId={}: {}", e.getStatusCode(), skuId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("Lazada checkout preview request failed for skuId={}: {}", skuId, e.getMessage());
            throw new RuntimeException("Checkout preview request failed: " + e.getMessage(), e);
        }
    }

    private void enforceRateLimit() {
        long now = System.currentTimeMillis();
        long prev = lastRequestTime.get();
        long diff = now - prev;
        if (diff < minRequestIntervalMs) {
            try {
                Thread.sleep(minRequestIntervalMs - diff);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTime.set(System.currentTimeMillis());
    }
}
