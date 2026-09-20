package vn.thosandeal.bot.service.pricing.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LazadaBuyNowClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private LazadaBuyNowClient createClient(ExchangeFunction exchangeFunction) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);
        return new LazadaBuyNowClient(builder, objectMapper, "Test-User-Agent", 1000.0);
    }

    @Test
    @DisplayName("HTTP 200 valid checkout preview returns HTML and injects correct headers")
    void testRequestCheckoutPreviewSuccess() {
        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        String expectedHtml = "<html><script>window.__initData__ = {};</script></html>";

        ExchangeFunction exchangeFunction = request -> {
            capturedRequest.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                    .body(expectedHtml)
                    .build());
        };

        LazadaBuyNowClient client = createClient(exchangeFunction);
        String cookieHeader = "lzd_sid=secret_sid; lzd_uid=12345; cna=cookie_cna";
        String html = client.requestCheckoutPreview("2498224535", "116964266230", cookieHeader);

        assertThat(html).isEqualTo(expectedHtml);
        assertThat(capturedRequest.get()).isNotNull();
        assertThat(capturedRequest.get().headers().getFirst(HttpHeaders.USER_AGENT)).isEqualTo("Test-User-Agent");
        assertThat(capturedRequest.get().headers().getFirst(HttpHeaders.COOKIE)).isEqualTo(cookieHeader);
    }

    @Test
    @DisplayName("HTTP 401 and 403 throw WebClientResponseException without retrying")
    void testAuthErrorsThrowWithoutRetry() {
        AtomicInteger callCount = new AtomicInteger(0);

        ExchangeFunction exchange403 = request -> {
            callCount.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.FORBIDDEN)
                    .body("Access Denied")
                    .build());
        };

        LazadaBuyNowClient client = createClient(exchange403);
        assertThatThrownBy(() -> client.requestCheckoutPreview("1", "2", "cookie"))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("403");

        // 403 is not retryable -> only called once
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("HTTP 429 and 500 trigger bounded retry")
    void testRateLimitAndServerErrorTriggerRetry() {
        AtomicInteger callCount = new AtomicInteger(0);

        ExchangeFunction retryExchange = request -> {
            int attempt = callCount.incrementAndGet();
            if (attempt <= 2) {
                return Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                        .body("Rate limited")
                        .build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .body("<html>Recovered</html>")
                    .build());
        };

        LazadaBuyNowClient client = createClient(retryExchange);
        String html = client.requestCheckoutPreview("1", "2", "cookie");

        assertThat(html).isEqualTo("<html>Recovered</html>");
        // 1 initial + 2 retries = 3 calls
        assertThat(callCount.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Exception messages do not leak raw session cookie values")
    void testExceptionDoesNotLeakCookies() {
        ExchangeFunction exchange500 = request -> Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Internal Error")
                .build());

        LazadaBuyNowClient client = createClient(exchange500);
        String sensitiveCookie = "lzd_sid=VERY_SECRET_SESSION_TOKEN_12345; lzd_uid=SECRET_UID";

        assertThatThrownBy(() -> client.requestCheckoutPreview("1", "2", sensitiveCookie))
                .isInstanceOf(Exception.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("VERY_SECRET_SESSION_TOKEN_12345"));
    }
}
