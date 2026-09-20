package vn.thosandeal.bot.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.thosandeal.bot.entity.TelegramUserEntity;
import vn.thosandeal.bot.entity.WatchItem;
import vn.thosandeal.bot.exception.WatchItemNotFoundException;
import vn.thosandeal.bot.repository.TelegramUserRepository;
import vn.thosandeal.bot.repository.WatchItemRepository;
import vn.thosandeal.bot.service.watch.WatchService;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for remove authorization (MandatoryFix #23).
 * Uses Testcontainers PostgreSQL — real DB, not H2.
 * Tests that partial unique index is created by Flyway (MandatoryFix #32).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class RemoveAuthorizationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("thosandeal_test")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Disable Telegram API calls in integration tests
        registry.add("telegram.bot-token", () -> "test-token");
        registry.add("telegram.webhook-secret", () -> "test-secret");
        registry.add("telegram.notification-channel-id", () -> "-100test");
    }

    @Autowired
    private WatchService watchService;

    @Autowired
    private TelegramUserRepository userRepository;

    @Autowired
    private WatchItemRepository watchItemRepository;

    @Test
    void ownerCanRemoveTheirItem() {
        // Arrange
        TelegramUserEntity user = new TelegramUserEntity(1001L, "owner", "Owner", "User");
        userRepository.save(user);

        WatchItem item = new WatchItem(user,
                "https://s.lazada.vn/abc", "https://s.lazada.vn/abc",
                new BigDecimal("1500000"));
        WatchItem saved = watchItemRepository.save(item);

        // Act & Assert
        assertThatCode(() -> watchService.removeWatchItem(saved.getId(), 1001L))
                .doesNotThrowAnyException();

        // Item should be deactivated
        WatchItem after = watchItemRepository.findById(saved.getId()).orElseThrow();
        assertThat(after.isActive()).isFalse();
    }

    @Test
    void anotherUserCannotRemoveSomeonesItem() {
        // Arrange
        TelegramUserEntity owner = new TelegramUserEntity(2001L, "owner2", "Owner2", null);
        userRepository.save(owner);

        WatchItem item = new WatchItem(owner,
                "https://s.lazada.vn/xyz", "https://s.lazada.vn/xyz",
                new BigDecimal("2000000"));
        WatchItem saved = watchItemRepository.save(item);

        Long attacker = 9999L; // Different user — not the owner

        // Act & Assert
        assertThatThrownBy(() -> watchService.removeWatchItem(saved.getId(), attacker))
                .isInstanceOf(WatchItemNotFoundException.class);

        // Item must still be active
        WatchItem after = watchItemRepository.findById(saved.getId()).orElseThrow();
        assertThat(after.isActive()).isTrue();
    }

    @Test
    void duplicateWatchItemIsPreventedByPartialUniqueIndex() {
        // This test verifies the DB partial unique index created in V2 migration
        TelegramUserEntity user = new TelegramUserEntity(3001L, "dupuser", "Dup", null);
        userRepository.save(user);

        String url = "https://s.lazada.vn/dup";
        BigDecimal price = new BigDecimal("1500000");

        WatchItem first = new WatchItem(user, url, url, price);
        watchItemRepository.save(first);

        // Second identical active watch should be blocked
        WatchItem second = new WatchItem(user, url, url, price);
        assertThatThrownBy(() -> watchItemRepository.saveAndFlush(second))
                .isInstanceOf(Exception.class); // DataIntegrityViolationException or similar
    }
}
