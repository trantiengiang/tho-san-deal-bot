package vn.thosandeal.bot.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import vn.thosandeal.bot.client.TelegramClient;
import vn.thosandeal.bot.repository.NotificationOutboxRepository;
import vn.thosandeal.bot.repository.PriceCheckLogRepository;
import vn.thosandeal.bot.repository.WatchItemRepository;
import vn.thosandeal.bot.scheduler.PriceWatchScheduler;
import vn.thosandeal.bot.service.notification.NotificationOutboxDeliveryService;
import vn.thosandeal.bot.service.notification.NotificationOutboxWorker;
import vn.thosandeal.bot.service.pricing.ProductPriceProvider;
import vn.thosandeal.bot.service.watch.WatchSkuService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SchedulingConditionalTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfig.class, PriceWatchScheduler.class, NotificationOutboxWorker.class)
            .withBean(WatchItemRepository.class, () -> mock(WatchItemRepository.class))
            .withBean(PriceCheckLogRepository.class, () -> mock(PriceCheckLogRepository.class))
            .withBean(ProductPriceProvider.class, () -> mock(ProductPriceProvider.class))
            .withBean(WatchSkuService.class, () -> mock(WatchSkuService.class))
            .withBean(NotificationOutboxRepository.class, () -> mock(NotificationOutboxRepository.class))
            .withBean(TelegramClient.class, () -> mock(TelegramClient.class))
            .withBean(NotificationOutboxDeliveryService.class, () -> mock(NotificationOutboxDeliveryService.class));

    @Test
    @DisplayName("app.scheduling.enabled=false disables SchedulingConfig, PriceWatchScheduler, and NotificationOutboxWorker")
    void whenSchedulingDisabled_noSchedulerBeansLoaded() {
        contextRunner
                .withPropertyValues("app.scheduling.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SchedulingConfig.class);
                    assertThat(context).doesNotHaveBean(PriceWatchScheduler.class);
                    assertThat(context).doesNotHaveBean(NotificationOutboxWorker.class);
                });
    }

    @Test
    @DisplayName("app.scheduling.enabled=true (or default) loads all scheduler beans")
    void whenSchedulingEnabled_allSchedulerBeansLoaded() {
        contextRunner
                .withPropertyValues("app.scheduling.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(SchedulingConfig.class);
                    assertThat(context).hasSingleBean(PriceWatchScheduler.class);
                    assertThat(context).hasSingleBean(NotificationOutboxWorker.class);
                });
    }
}
