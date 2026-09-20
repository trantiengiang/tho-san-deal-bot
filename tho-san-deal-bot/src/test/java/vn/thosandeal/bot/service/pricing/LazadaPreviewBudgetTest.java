package vn.thosandeal.bot.service.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LazadaPreviewBudgetTest {

    @Test
    @DisplayName("Global request budget allows exactly maxPreviewsPerCycle across all requests in cycle")
    void testGlobalPreviewBudgetAcrossMultipleItems() {
        // Global limit = 3
        LazadaPreviewBudget budget = new LazadaPreviewBudget(3);
        budget.startCycle();

        int grantedSlots = 0;
        int deniedSlots = 0;

        // 10 items, 10 SKUs each = 100 requests
        for (int item = 1; item <= 10; item++) {
            for (int sku = 1; sku <= 10; sku++) {
                if (budget.tryAcquireSlot()) {
                    grantedSlots++;
                } else {
                    deniedSlots++;
                }
            }
        }

        assertThat(grantedSlots).isEqualTo(3);
        assertThat(deniedSlots).isEqualTo(97);
        assertThat(budget.getRemainingSlots()).isEqualTo(0);

        // Next cycle resets budget
        budget.startCycle();
        assertThat(budget.getRemainingSlots()).isEqualTo(3);
        assertThat(budget.tryAcquireSlot()).isTrue();
    }
}
