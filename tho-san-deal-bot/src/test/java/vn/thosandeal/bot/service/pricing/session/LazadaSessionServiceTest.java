package vn.thosandeal.bot.service.pricing.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import vn.thosandeal.bot.entity.LazadaSession;
import vn.thosandeal.bot.enums.LazadaSessionStatus;
import vn.thosandeal.bot.repository.LazadaSessionRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LazadaSessionServiceTest {

    private LazadaSessionRepository sessionRepository;
    private SessionCryptoService cryptoService;
    private ObjectMapper objectMapper;
    private LazadaSessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(LazadaSessionRepository.class);
        when(sessionRepository.save(any(LazadaSession.class))).thenAnswer(i -> {
            LazadaSession s = i.getArgument(0);
            if (s.getId() == null) s.setId(1L);
            return s;
        });
        byte[] key32 = new byte[32];
        new SecureRandom().nextBytes(key32);
        cryptoService = new SessionCryptoService(key32);
        objectMapper = new ObjectMapper();
        sessionService = new LazadaSessionService(sessionRepository, cryptoService, objectMapper);
    }

    @Test
    @DisplayName("Import session with cookieString parses cookies correctly")
    void testImportSessionWithCookieString(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("session_string.json");
        Files.writeString(file, """
                {
                    "version": 1,
                    "cookieString": "lzd_sid=sid_123; lzd_uid=uid_456; cna=cna_789; _tb_token_=tb_abc; extra=xyz"
                }
                """);

        LazadaSession session = sessionService.importSessionFromFile(file);
        assertThat(session).isNotNull();
        assertThat(session.getAccountId()).isEqualTo("uid_456");
        assertThat(session.getStatus()).isEqualTo(LazadaSessionStatus.ACTIVE);

        when(sessionRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.of(session));
        Optional<String> headerOpt = sessionService.getActiveSessionCookieHeader();
        assertThat(headerOpt).isPresent();
        assertThat(headerOpt.get()).contains("lzd_sid=sid_123");
        assertThat(headerOpt.get()).contains("_tb_token_=tb_abc");
    }

    @Test
    @DisplayName("Valid session import encrypts payload and saves with ACTIVE status")
    void testValidSessionImport(@TempDir Path tempDir) throws IOException {
        String validJson = """
                {
                    "version": 1,
                    "cookies": {
                        "lzd_sid": "mock_sid_value_123",
                        "lzd_uid": "200047533124",
                        "cna": "mock_cna_value_456"
                    }
                }
                """;
        Path sessionFile = tempDir.resolve("lazada-session.json");
        Files.writeString(sessionFile, validJson);

        when(sessionRepository.save(any(LazadaSession.class))).thenAnswer(i -> {
            LazadaSession s = i.getArgument(0);
            s.setId(1L);
            return s;
        });

        LazadaSession saved = sessionService.importSessionFromFile(sessionFile);

        assertThat(saved.getStatus()).isEqualTo(LazadaSessionStatus.ACTIVE);
        assertThat(saved.getAccountId()).isEqualTo("200047533124");
        assertThat(saved.getEncryptedPayload()).startsWith("v1:");

        // Verify encrypted payload decrypts back to original content
        String decrypted = cryptoService.decrypt(saved.getEncryptedPayload());
        assertThat(decrypted).contains("mock_sid_value_123");

        ArgumentCaptor<LazadaSession> captor = ArgumentCaptor.forClass(LazadaSession.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getEncryptedPayload()).doesNotContain("mock_sid_value_123");
    }

    @Test
    @DisplayName("Missing session file throws IllegalArgumentException")
    void testMissingFileThrows() {
        Path nonExistent = Path.of("non_existent_dir", "missing-session.json");
        assertThatThrownBy(() -> sessionService.importSessionFromFile(nonExistent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session file does not exist");
    }

    @Test
    @DisplayName("Invalid JSON throws exception")
    void testInvalidJsonThrows(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("bad.json");
        Files.writeString(file, "{ this is not json }");

        assertThatThrownBy(() -> sessionService.importSessionFromFile(file))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Unsupported version or missing version is rejected")
    void testUnsupportedVersionRejected(@TempDir Path tempDir) throws IOException {
        Path fileVersion2 = tempDir.resolve("v2.json");
        Files.writeString(fileVersion2, """
                {
                    "version": 2,
                    "cookies": { "lzd_sid": "a", "lzd_uid": "b", "cna": "c" }
                }
                """);

        assertThatThrownBy(() -> sessionService.importSessionFromFile(fileVersion2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported session payload version: 2");

        Path fileNoVersion = tempDir.resolve("no-v.json");
        Files.writeString(fileNoVersion, """
                {
                    "cookies": { "lzd_sid": "a", "lzd_uid": "b", "cna": "c" }
                }
                """);

        assertThatThrownBy(() -> sessionService.importSessionFromFile(fileNoVersion))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing 'version'");
    }

    @Test
    @DisplayName("Missing required cookie (lzd_sid, lzd_uid, cna) is rejected")
    void testMissingRequiredCookieRejected(@TempDir Path tempDir) throws IOException {
        Path missingSid = tempDir.resolve("missing_sid.json");
        Files.writeString(missingSid, """
                {
                    "version": 1,
                    "cookies": { "lzd_uid": "b", "cna": "c" }
                }
                """);
        assertThatThrownBy(() -> sessionService.importSessionFromFile(missingSid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required cookie: lzd_sid");

        Path missingUid = tempDir.resolve("missing_uid.json");
        Files.writeString(missingUid, """
                {
                    "version": 1,
                    "cookies": { "lzd_sid": "a", "cna": "c" }
                }
                """);
        assertThatThrownBy(() -> sessionService.importSessionFromFile(missingUid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required cookie: lzd_uid");

        Path missingCna = tempDir.resolve("missing_cna.json");
        Files.writeString(missingCna, """
                {
                    "version": 1,
                    "cookies": { "lzd_sid": "a", "lzd_uid": "b" }
                }
                """);
        assertThatThrownBy(() -> sessionService.importSessionFromFile(missingCna))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required cookie: cna");
    }

    @Test
    @DisplayName("Empty cookie value is rejected")
    void testEmptyCookieValueRejected(@TempDir Path tempDir) throws IOException {
        Path emptySid = tempDir.resolve("empty_sid.json");
        Files.writeString(emptySid, """
                {
                    "version": 1,
                    "cookies": { "lzd_sid": "   ", "lzd_uid": "b", "cna": "c" }
                }
                """);
        assertThatThrownBy(() -> sessionService.importSessionFromFile(emptySid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Required cookie 'lzd_sid' cannot be empty");
    }

    @Test
    @DisplayName("Admin notification is triggered only once upon session expiry until reset")
    void testAdminNotificationOnceOnExpiry() {
        LazadaSession session = new LazadaSession();
        session.setId(1L);
        session.setStatus(LazadaSessionStatus.ACTIVE);
        session.setAdminNotifiedAt(null);

        when(sessionRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.of(session));

        // First expiry: should notify admin
        boolean notifyFirst = sessionService.markSessionStatus(LazadaSessionStatus.EXPIRED, "Session expired");
        assertThat(notifyFirst).isTrue();
        assertThat(session.getAdminNotifiedAt()).isNotNull();

        // Second check while still expired: should NOT notify admin again
        boolean notifySecond = sessionService.markSessionStatus(LazadaSessionStatus.EXPIRED, "Still expired");
        assertThat(notifySecond).isFalse();

        // Successful preview resets the admin notification flag
        sessionService.recordSuccessfulPreview();
        assertThat(session.getAdminNotifiedAt()).isNull();
        assertThat(session.getStatus()).isEqualTo(LazadaSessionStatus.ACTIVE);

        // Third expiry after recovery: should notify admin again
        boolean notifyThird = sessionService.markSessionStatus(LazadaSessionStatus.EXPIRED, "Expired again");
        assertThat(notifyThird).isTrue();
    }

    @Test
    @DisplayName("CAPTCHA_REQUIRED marks session CHALLENGED, sets cooldown, and notifies admin once per generation")
    void testMarkChallengeSetsChallengedAndCooldownAndNotifiesOnce() {
        LazadaSession session = new LazadaSession();
        session.setId(1L);
        session.setStatus(LazadaSessionStatus.ACTIVE);
        session.setChallengeGeneration(0L);
        session.setAdminNotifiedAt(null);

        when(sessionRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.of(session));

        // First challenge: should set CHALLENGED, increment generation, and notify
        LazadaSessionService.ChallengeResult res1 = sessionService.markChallenge(
                "CAPTCHA_REQUIRED", java.time.Duration.ofMinutes(30));
        assertThat(res1.shouldNotifyAdmin()).isTrue();
        assertThat(res1.challengeGeneration()).isEqualTo(1L);
        assertThat(session.getStatus()).isEqualTo(LazadaSessionStatus.CHALLENGED);
        assertThat(session.getCooldownUntil()).isNotNull();
        assertThat(session.getChallengeDetectedAt()).isNotNull();

        // Same challenge cycle: should NOT notify again
        LazadaSessionService.ChallengeResult res2 = sessionService.markChallenge(
                "CAPTCHA_REQUIRED", java.time.Duration.ofMinutes(30));
        assertThat(res2.shouldNotifyAdmin()).isFalse();
        assertThat(res2.challengeGeneration()).isEqualTo(1L);

        // Recovery to ACTIVE resets admin notification
        sessionService.recordSuccessfulPreview();
        assertThat(session.getStatus()).isEqualTo(LazadaSessionStatus.ACTIVE);
        assertThat(session.getAdminNotifiedAt()).isNull();

        // Subsequent future challenge: increments generation and notifies again
        LazadaSessionService.ChallengeResult res3 = sessionService.markChallenge(
                "CAPTCHA_REQUIRED", java.time.Duration.ofMinutes(30));
        assertThat(res3.shouldNotifyAdmin()).isTrue();
        assertThat(res3.challengeGeneration()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Concurrency-safe tryBeginProbe delegates to atomic repository lock")
    void testTryBeginProbeConcurrency() {
        Instant now = Instant.now();
        when(sessionRepository.claimProbeLock(1L, now, LazadaSessionStatus.CHALLENGED, LazadaSessionStatus.PROBING))
                .thenReturn(1);

        boolean claimed = sessionService.tryBeginProbe(1L, now);
        assertThat(claimed).isTrue();

        when(sessionRepository.claimProbeLock(1L, now, LazadaSessionStatus.CHALLENGED, LazadaSessionStatus.PROBING))
                .thenReturn(0);

        boolean secondClaim = sessionService.tryBeginProbe(1L, now);
        assertThat(secondClaim).isFalse();
    }
}
