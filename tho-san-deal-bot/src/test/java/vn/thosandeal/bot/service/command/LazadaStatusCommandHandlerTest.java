package vn.thosandeal.bot.service.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.thosandeal.bot.config.TelegramProperties;
import vn.thosandeal.bot.dto.telegram.TelegramChat;
import vn.thosandeal.bot.dto.telegram.TelegramMessage;
import vn.thosandeal.bot.dto.telegram.TelegramUserDto;
import vn.thosandeal.bot.entity.LazadaSession;
import vn.thosandeal.bot.enums.CommandType;
import vn.thosandeal.bot.enums.LazadaSessionStatus;
import vn.thosandeal.bot.parser.ParsedCommand;
import vn.thosandeal.bot.service.pricing.session.LazadaSessionService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LazadaStatusCommandHandlerTest {

    private LazadaSessionService sessionService;
    private TelegramProperties telegramProperties;
    private LazadaStatusCommandHandler handler;

    @BeforeEach
    void setUp() {
        sessionService = mock(LazadaSessionService.class);
        telegramProperties = new TelegramProperties("token", "secret", "-100", List.of(12345L));
        handler = new LazadaStatusCommandHandler(sessionService, telegramProperties, 3, 5, 15);
    }

    @Test
    @DisplayName("Non-whitelisted user receives unauthorized message")
    void testUnauthorizedUser() {
        TelegramUserDto unauthorizedUser = new TelegramUserDto(99999L, false, "attacker", null, null);
        TelegramChat chat = new TelegramChat(1L, "private", null, null, null, null);
        TelegramMessage msg = new TelegramMessage(1L, unauthorizedUser, chat, 0L, "/lazada_status");

        String reply = handler.handle(msg, ParsedCommand.lazadaStatus());
        assertThat(reply).contains("Bạn không có quyền xem trạng thái hệ thống");
    }

    @Test
    @DisplayName("Authorized user with active session sees status report without sensitive cookies")
    void testAuthorizedUserWithActiveSession() {
        TelegramUserDto authorizedUser = new TelegramUserDto(12345L, false, "admin", null, null);
        TelegramChat chat = new TelegramChat(1L, "private", null, null, null, null);
        TelegramMessage msg = new TelegramMessage(1L, authorizedUser, chat, 0L, "/lazada_status");

        LazadaSession session = new LazadaSession();
        session.setStatus(LazadaSessionStatus.ACTIVE);
        session.setAccountId("200047533124");
        session.setLastValidatedAt(Instant.now());
        session.setLastSuccessfulPreviewAt(Instant.now());
        session.setEncryptedPayload("v1:super_secret_encrypted_payload");

        when(sessionService.getLatestSession()).thenReturn(Optional.of(session));

        String reply = handler.handle(msg, ParsedCommand.lazadaStatus());
        assertThat(reply).contains("TRẠNG THÁI PHIÊN LAZADA");
        assertThat(reply).contains("ACTIVE");
        assertThat(reply).contains("200047533124");
        assertThat(reply).contains("Deep preview budget:</b> 3/cycle");
        assertThat(reply).contains("Preview interval:</b> 5s");
        assertThat(reply).contains("Exact price TTL:</b> 15m");
        assertThat(reply).doesNotContain("super_secret_encrypted_payload");
    }

    @Test
    @DisplayName("Authorized user with challenged session sees challenge and cooldown info")
    void testAuthorizedUserWithChallengedSession() {
        TelegramUserDto authorizedUser = new TelegramUserDto(12345L, false, "admin", null, null);
        TelegramChat chat = new TelegramChat(1L, "private", null, null, null, null);
        TelegramMessage msg = new TelegramMessage(1L, authorizedUser, chat, 0L, "/lazada_status");

        LazadaSession session = new LazadaSession();
        session.setStatus(LazadaSessionStatus.CHALLENGED);
        session.setAccountId("200047533124");
        session.setChallengeDetectedAt(Instant.now());
        session.setCooldownUntil(Instant.now().plusSeconds(1800));
        session.setLastErrorSummary("Hard security challenge: CAPTCHA_REQUIRED");

        when(sessionService.getLatestSession()).thenReturn(Optional.of(session));

        String reply = handler.handle(msg, ParsedCommand.lazadaStatus());
        assertThat(reply).contains("CHALLENGED");
        assertThat(reply).contains("Thử thách phát hiện");
        assertThat(reply).contains("Thời gian chờ (Cooldown until)");
        assertThat(reply).contains("CAPTCHA_REQUIRED");
    }
}
