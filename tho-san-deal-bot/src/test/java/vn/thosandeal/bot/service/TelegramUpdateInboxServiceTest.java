package vn.thosandeal.bot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.thosandeal.bot.dto.telegram.TelegramUpdate;
import vn.thosandeal.bot.repository.TelegramUpdateInboxRepository;
import vn.thosandeal.bot.service.telegram.TelegramUpdateInboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramUpdateInboxServiceTest {

    @Mock
    private TelegramUpdateInboxRepository inboxRepository;

    private TelegramUpdateInboxService inboxService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        inboxService = new TelegramUpdateInboxService(inboxRepository, objectMapper);
    }

    @Test
    void shouldReturnTrueForNewUpdate() {
        TelegramUpdate update = new TelegramUpdate(12345L, null);
        when(inboxRepository.insertIfAbsent(eq(12345L), any(), any(Instant.class)))
                .thenReturn(1); // 1 row inserted = new

        boolean result = inboxService.persistIfNew(update);

        assertThat(result).isTrue();
        verify(inboxRepository).insertIfAbsent(eq(12345L), any(), any(Instant.class));
    }

    @Test
    void shouldReturnFalseForDuplicateUpdate() {
        TelegramUpdate update = new TelegramUpdate(12345L, null);
        when(inboxRepository.insertIfAbsent(eq(12345L), any(), any(Instant.class)))
                .thenReturn(0); // 0 rows = duplicate (ON CONFLICT DO NOTHING)

        boolean result = inboxService.persistIfNew(update);

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseForNullUpdateId() {
        TelegramUpdate update = new TelegramUpdate(null, null);

        boolean result = inboxService.persistIfNew(update);

        assertThat(result).isFalse();
        verify(inboxRepository, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    void shouldCallInsertOnlyOnce() {
        // Each call to persistIfNew should call insertIfAbsent exactly once
        TelegramUpdate update = new TelegramUpdate(99L, null);
        when(inboxRepository.insertIfAbsent(any(), any(), any())).thenReturn(1);

        inboxService.persistIfNew(update);

        verify(inboxRepository, times(1)).insertIfAbsent(any(), any(), any());
    }
}
