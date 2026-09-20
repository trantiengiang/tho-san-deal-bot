package vn.thosandeal.bot.service.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.thosandeal.bot.enums.OutboxStatus;
import vn.thosandeal.bot.repository.NotificationOutboxRepository;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxDeliveryServiceTest {

    @Mock
    private NotificationOutboxRepository repository;

    private NotificationOutboxDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new NotificationOutboxDeliveryService(repository);
    }

    @Test
    @DisplayName("markSent updates status to SENT with timestamp and null error")
    void markSent_Success() {
        when(repository.updateDeliveryResult(eq(101L), eq(OutboxStatus.SENT), any(Instant.class), isNull()))
                .thenReturn(1);

        service.markSent(101L);

        verify(repository).updateDeliveryResult(eq(101L), eq(OutboxStatus.SENT), any(Instant.class), isNull());
    }

    @Test
    @DisplayName("markPermanentFailure updates status to FAILED with null timestamp and error message")
    void markPermanentFailure_Success() {
        when(repository.updateDeliveryResult(eq(102L), eq(OutboxStatus.FAILED), isNull(), eq("Bad Request 400")))
                .thenReturn(1);

        service.markPermanentFailure(102L, "Bad Request 400");

        verify(repository).updateDeliveryResult(eq(102L), eq(OutboxStatus.FAILED), isNull(), eq("Bad Request 400"));
    }

    @Test
    @DisplayName("markRetryableFailure updates status to PENDING with null timestamp and error message")
    void markRetryableFailure_Success() {
        when(repository.updateDeliveryResult(eq(103L), eq(OutboxStatus.PENDING), isNull(), eq("Gateway Timeout 504")))
                .thenReturn(1);

        service.markRetryableFailure(103L, "Gateway Timeout 504");

        verify(repository).updateDeliveryResult(eq(103L), eq(OutboxStatus.PENDING), isNull(), eq("Gateway Timeout 504"));
    }

    @Test
    @DisplayName("zero rows affected logs warning and does not crash")
    void rowsAffectedZero_DoesNotCrash() {
        when(repository.updateDeliveryResult(eq(999L), eq(OutboxStatus.SENT), any(Instant.class), isNull()))
                .thenReturn(0);

        service.markSent(999L);

        verify(repository).updateDeliveryResult(eq(999L), eq(OutboxStatus.SENT), any(Instant.class), isNull());
    }
}
