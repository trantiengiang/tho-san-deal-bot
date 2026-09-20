package vn.thosandeal.bot.service.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global cycle budget for deep Lazada checkout preview requests.
 * Enforces a global cap on previews across an entire scheduler cycle.
 */
@Component
public class LazadaPreviewBudget {

    private static final Logger log = LoggerFactory.getLogger(LazadaPreviewBudget.class);

    private final int maxPreviewsPerCycle;
    private final AtomicInteger remaining;

    public LazadaPreviewBudget(
            @Value("${pricing.lazada.max-previews-per-cycle:3}") int maxPreviewsPerCycle) {
        this.maxPreviewsPerCycle = Math.max(1, maxPreviewsPerCycle);
        this.remaining = new AtomicInteger(this.maxPreviewsPerCycle);
    }

    /**
     * Resets the budget for a new scheduler cycle.
     */
    public void startCycle() {
        this.remaining.set(maxPreviewsPerCycle);
        log.debug("LazadaPreviewBudget: started cycle with {} slots", maxPreviewsPerCycle);
    }

    /**
     * Atomically attempts to acquire a preview slot from the cycle budget.
     *
     * @return true if a slot was granted, false if budget exhausted
     */
    public boolean tryAcquireSlot() {
        while (true) {
            int current = remaining.get();
            if (current <= 0) {
                return false;
            }
            if (remaining.compareAndSet(current, current - 1)) {
                return true;
            }
        }
    }

    public int getRemainingSlots() {
        return Math.max(0, remaining.get());
    }

    public int getMaxPreviewsPerCycle() {
        return maxPreviewsPerCycle;
    }
}
