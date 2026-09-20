package vn.thosandeal.bot.service.pricing.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Encapsulates pacing logic for deep Lazada checkout preview requests.
 * Enforces a minimum interval between requests without sleeping before the first request.
 */
@Component
public class LazadaPreviewRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LazadaPreviewRateLimiter.class);

    private final long minIntervalMs;
    private final AtomicLong lastRequestTime = new AtomicLong(0);

    public LazadaPreviewRateLimiter(
            @Value("${pricing.lazada.min-preview-interval-seconds:5}") long minIntervalSeconds) {
        this.minIntervalMs = Math.max(1, minIntervalSeconds) * 1000L;
    }

    /**
     * Acquires permission to send a preview request.
     * Sleeps only if the elapsed time since the previous request is less than minIntervalMs.
     */
    public void acquire() {
        long prev = lastRequestTime.get();
        if (prev > 0) {
            long now = System.currentTimeMillis();
            long elapsed = now - prev;
            if (elapsed < minIntervalMs) {
                long sleepMs = minIntervalMs - elapsed;
                log.debug("LazadaPreviewRateLimiter: pacing checkout preview, sleeping {}ms", sleepMs);
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        lastRequestTime.set(System.currentTimeMillis());
    }

    public long getMinIntervalSeconds() {
        return minIntervalMs / 1000L;
    }

    public void reset() {
        lastRequestTime.set(0);
    }
}
