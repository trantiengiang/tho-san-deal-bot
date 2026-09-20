package vn.thosandeal.bot.service.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.thosandeal.bot.client.TelegramApiException;
import vn.thosandeal.bot.client.TelegramClient;
import vn.thosandeal.bot.dto.request.SendMessageRequest;
import vn.thosandeal.bot.entity.NotificationOutbox;
import vn.thosandeal.bot.enums.OutboxStatus;
import vn.thosandeal.bot.repository.NotificationOutboxRepository;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxWorkerTest {

    @Mock
    private NotificationOutboxRepository outboxRepository;

    @Mock
    private TelegramClient telegramClient;

    @Mock
    private NotificationOutboxDeliveryService deliveryService;

    private NotificationOutboxWorker worker;

    @BeforeEach
    void setUp() {
        worker = new NotificationOutboxWorker(outboxRepository, telegramClient, deliveryService);
    }

    @Test
    @DisplayName("HTTP call happens BEFORE markSent, no transaction around HTTP call")
    void successfulDelivery_CallsHttpThenMarksSent() {
        NotificationOutbox entry = new NotificationOutbox();
        entry.setId(1L);
        entry.setTelegramChatId("-10012345");
        entry.setMessageText("Test deal message");
        entry.setAttemptCount(0);

        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(entry));

        doNothing().when(telegramClient).sendMessage(any(SendMessageRequest.class));

        worker.processOutbox();

        InOrder inOrder = inOrder(telegramClient, deliveryService);
        inOrder.verify(telegramClient).sendMessage(any(SendMessageRequest.class));
        inOrder.verify(deliveryService).markSent(1L);
    }

    @Test
    @DisplayName("Telegram 429 rate limit triggers markRetryableFailure")
    void rateLimit429_TriggersRetryableFailure() {
        NotificationOutbox entry = new NotificationOutbox();
        entry.setId(2L);
        entry.setTelegramChatId("-10012345");
        entry.setMessageText("Rate limit deal");
        entry.setAttemptCount(1);

        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(entry));

        doThrow(TelegramApiException.fromHttpStatus(429, "Too Many Requests"))
                .when(telegramClient).sendMessage(any(SendMessageRequest.class));

        worker.processOutbox();

        verify(deliveryService).markRetryableFailure(eq(2L), contains("429"));
        verify(deliveryService, never()).markSent(any());
        verify(deliveryService, never()).markPermanentFailure(any(), any());
    }

    @Test
    @DisplayName("Permanent 4xx error marks FAILED immediately without retry")
    void permanent4xx_MarksPermanentFailure() {
        NotificationOutbox entry = new NotificationOutbox();
        entry.setId(3L);
        entry.setTelegramChatId("-10012345");
        entry.setMessageText("Bad request deal");
        entry.setAttemptCount(0);

        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(entry));

        doThrow(TelegramApiException.fromHttpStatus(400, "Bad Request: chat not found"))
                .when(telegramClient).sendMessage(any(SendMessageRequest.class));

        worker.processOutbox();

        verify(deliveryService).markPermanentFailure(eq(3L), contains("400"));
        verify(deliveryService, never()).markSent(any());
    }

    @Test
    @DisplayName("Exceeded max attempts marks FAILED without attempting HTTP call")
    void maxAttemptsExceeded_MarksFailedWithoutHttp() {
        NotificationOutbox entry = new NotificationOutbox();
        entry.setId(4L);
        entry.setTelegramChatId("-10012345");
        entry.setMessageText("Expired deal");
        entry.setAttemptCount(3); // MAX_ATTEMPTS = 3

        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(entry));

        worker.processOutbox();

        verify(telegramClient, never()).sendMessage(any());
        verify(deliveryService).markPermanentFailure(eq(4L), eq("Max attempts exceeded"));
    }

    @Test
    @DisplayName("Failure on first entry does not prevent processing subsequent entries")
    void itemFailure_ContinuesToNextItem() {
        NotificationOutbox entry1 = new NotificationOutbox();
        entry1.setId(5L);
        entry1.setTelegramChatId("-1001");
        entry1.setMessageText("Message 1");
        entry1.setAttemptCount(0);

        NotificationOutbox entry2 = new NotificationOutbox();
        entry2.setId(6L);
        entry2.setTelegramChatId("-1002");
        entry2.setMessageText("Message 2");
        entry2.setAttemptCount(0);

        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(entry1, entry2));

        // Entry 1 throws unexpected exception
        doThrow(new RuntimeException("Network down"))
                .when(telegramClient).sendMessage(org.mockito.ArgumentMatchers.argThat(
                        req -> req != null && "-1001".equals(req.chatId())));

        // Entry 2 succeeds
        doNothing().when(telegramClient).sendMessage(org.mockito.ArgumentMatchers.argThat(
                req -> req != null && "-1002".equals(req.chatId())));

        worker.processOutbox();

        verify(deliveryService).markRetryableFailure(eq(5L), contains("Network down"));
        verify(deliveryService).markSent(6L);
    }
}
