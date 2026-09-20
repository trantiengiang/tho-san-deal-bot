package vn.thosandeal.bot.service.pricing.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.thosandeal.bot.entity.LazadaSession;
import vn.thosandeal.bot.enums.LazadaSessionStatus;
import vn.thosandeal.bot.repository.LazadaSessionRepository;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class LazadaSessionService {

    private static final Logger log = LoggerFactory.getLogger(LazadaSessionService.class);

    private final LazadaSessionRepository sessionRepository;
    private final SessionCryptoService cryptoService;
    private final ObjectMapper objectMapper;

    public LazadaSessionService(
            LazadaSessionRepository sessionRepository,
            SessionCryptoService cryptoService,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
    }

    /**
     * Gets the decrypted cookie header for active session, or empty if no active session.
     */
    @Transactional(readOnly = true)
    public Optional<String> getActiveSessionCookieHeader() {
        Optional<LazadaSession> sessionOpt = sessionRepository.findFirstByOrderByIdDesc();
        if (sessionOpt.isEmpty()) {
            return Optional.empty();
        }

        LazadaSession session = sessionOpt.get();
        if (session.getStatus() != LazadaSessionStatus.ACTIVE && session.getStatus() != LazadaSessionStatus.PROBING) {
            return Optional.empty();
        }

        try {
            String decryptedJson = cryptoService.decrypt(session.getEncryptedPayload());
            LazadaSessionPayload payload = objectMapper.readValue(decryptedJson, LazadaSessionPayload.class);
            if (!payload.hasRequiredCookies()) {
                log.warn("Lazada session id={} is missing required minimal cookies", session.getId());
                return Optional.empty();
            }
            return Optional.of(payload.toCookieHeader());
        } catch (Exception e) {
            log.error("Failed to decrypt or parse Lazada session id={}: {}", session.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Records a successful checkout preview, ensuring status is ACTIVE and updating timestamps.
     */
    @Transactional
    public void recordSuccessfulPreview() {
        Optional<LazadaSession> sessionOpt = sessionRepository.findFirstByOrderByIdDesc();
        if (sessionOpt.isPresent()) {
            LazadaSession session = sessionOpt.get();
            session.setStatus(LazadaSessionStatus.ACTIVE);
            session.setLastValidatedAt(Instant.now());
            session.setLastSuccessfulPreviewAt(Instant.now());
            session.setLastErrorSummary(null);
            session.setCooldownUntil(null);
            session.setAdminNotifiedAt(null); // Reset admin notification flag so future expiry can notify again
            sessionRepository.save(session);
        }
    }

    public record ChallengeResult(boolean shouldNotifyAdmin, Long sessionId, Long challengeGeneration, Instant cooldownUntil) {}

    /**
     * Marks session as CHALLENGED, sets cooldown, and increments challenge generation if new.
     * Returns ChallengeResult indicating if admin should be notified.
     */
    @Transactional
    public ChallengeResult markChallenge(String errorSummary, java.time.Duration cooldownDuration) {
        Optional<LazadaSession> sessionOpt = sessionRepository.findFirstByOrderByIdDesc();
        if (sessionOpt.isEmpty()) {
            return new ChallengeResult(false, null, 0L, null);
        }

        LazadaSession session = sessionOpt.get();
        boolean isNewChallenge = session.getStatus() != LazadaSessionStatus.CHALLENGED;
        if (isNewChallenge) {
            session.setChallengeGeneration(session.getChallengeGeneration() + 1);
            session.setChallengeDetectedAt(Instant.now());
        }
        session.setStatus(LazadaSessionStatus.CHALLENGED);
        session.setLastValidatedAt(Instant.now());
        session.setLastErrorSummary(errorSummary);
        Instant cooldownUntil = Instant.now().plus(cooldownDuration);
        session.setCooldownUntil(cooldownUntil);

        boolean shouldNotify = false;
        if (isNewChallenge && session.getAdminNotifiedAt() == null) {
            shouldNotify = true;
            session.setAdminNotifiedAt(Instant.now());
        }

        sessionRepository.save(session);
        log.warn("Lazada session id={} marked as CHALLENGED (generation={}, shouldNotify={}): {}",
                session.getId(), session.getChallengeGeneration(), shouldNotify, errorSummary);

        return new ChallengeResult(shouldNotify, session.getId(), session.getChallengeGeneration(), cooldownUntil);
    }

    /**
     * Concurrency-safe atomic probe claim after cooldown expiry.
     * Updates status from CHALLENGED to PROBING only if cooldownUntil <= now.
     */
    @Transactional
    public boolean tryBeginProbe(Long sessionId, Instant now) {
        int affected = sessionRepository.claimProbeLock(
                sessionId, now, LazadaSessionStatus.CHALLENGED, LazadaSessionStatus.PROBING);
        if (affected == 1) {
            log.info("Lazada session id={} successfully claimed for PROBING", sessionId);
            return true;
        }
        return false;
    }

    /**
     * Marks session as EXPIRED, INVALID, or NEEDS_LOGIN and checks if admin notification is due.
     * Returns true if admin should be notified (first time notification only).
     */
    @Transactional
    public boolean markSessionStatus(LazadaSessionStatus status, String errorSummary) {
        Optional<LazadaSession> sessionOpt = sessionRepository.findFirstByOrderByIdDesc();
        if (sessionOpt.isEmpty()) {
            return false;
        }

        LazadaSession session = sessionOpt.get();
        session.setStatus(status);
        session.setLastValidatedAt(Instant.now());
        session.setLastErrorSummary(errorSummary);

        boolean shouldNotifyAdmin = false;
        if (status == LazadaSessionStatus.EXPIRED || status == LazadaSessionStatus.INVALID) {
            if (session.getAdminNotifiedAt() == null) {
                shouldNotifyAdmin = true;
                session.setAdminNotifiedAt(Instant.now());
            }
        }

        sessionRepository.save(session);
        log.warn("Lazada session id={} marked as {} (shouldNotifyAdmin={}): {}",
                session.getId(), status, shouldNotifyAdmin, errorSummary);
        return shouldNotifyAdmin;
    }

    /**
     * Local-only import utility (MandatoryFix #6).
     * Reads a local JSON file (e.g. secrets/lazada-session.json), validates, encrypts, and saves.
     * Format of JSON file:
     * {
     *   "cookies": {
     *     "lzd_sid": "...",
     *     "lzd_uid": "...",
     *     "cna": "..."
     *   },
     *   "accountId": "optional_account_id"
     * }
     */
    @Transactional
    public LazadaSession importSessionFromFile(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Session file does not exist: " + path);
        }

        try {
            String jsonContent = Files.readString(path);
            LazadaSessionPayload payload = objectMapper.readValue(jsonContent, LazadaSessionPayload.class);
            payload.validate();

            String serializedPayload = objectMapper.writeValueAsString(payload);
            String encryptedPayload = cryptoService.encrypt(serializedPayload);

            LazadaSession session = new LazadaSession();
            session.setAccountId(payload.getCookies().get("lzd_uid"));
            session.setEncryptedPayload(encryptedPayload);
            session.setStatus(LazadaSessionStatus.ACTIVE);
            session.setLastValidatedAt(Instant.now());
            session.setAdminNotifiedAt(null);

            LazadaSession saved = sessionRepository.save(session);
            log.info("Successfully imported and encrypted Lazada session id={} (cookies count={})",
                    saved.getId(), payload.getCookies().size());
            return saved;
        } catch (IllegalArgumentException e) {
            log.error("Validation failed for session import from {}: {}", path, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to import session from {}: {}", path, e.getMessage());
            throw new IllegalStateException("Failed to import session: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<LazadaSession> getLatestSession() {
        return sessionRepository.findFirstByOrderByIdDesc();
    }
}
