package vn.thosandeal.bot.service.pricing.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LazadaPreviewRateLimiterTest {

    @Test
    @DisplayName("First acquire does not sleep; second acquire within interval is paced")
    void testRateLimiterPacing() {
        // 1 second interval for test speed
        LazadaPreviewRateLimiter limiter = new LazadaPreviewRateLimiter(1);

        long start1 = System.currentTimeMillis();
        limiter.acquire();
        long elapsed1 = System.currentTimeMillis() - start1;
        // First request should not sleep
        assertThat(elapsed1).isLessThan(200);

        // Immediate second acquire should be paced to approximately 1000ms
        long start2 = System.currentTimeMillis();
        limiter.acquire();
        long elapsed2 = System.currentTimeMillis() - start2;
        assertThat(elapsed2).isGreaterThanOrEqualTo(800);
    }
}
