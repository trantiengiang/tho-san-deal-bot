package vn.thosandeal.bot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.thosandeal.bot.enums.LazadaSessionStatus;

import java.time.Instant;

@Entity
@Table(name = "lazada_session")
@Getter
@Setter
@NoArgsConstructor
public class LazadaSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", length = 100)
    private String accountId;

    @Column(name = "encrypted_payload", nullable = false, columnDefinition = "TEXT")
    private String encryptedPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LazadaSessionStatus status = LazadaSessionStatus.ACTIVE;

    @Column(name = "last_validated_at")
    private Instant lastValidatedAt;

    @Column(name = "last_successful_preview_at")
    private Instant lastSuccessfulPreviewAt;

    @Column(name = "last_error_summary", length = 1000)
    private String lastErrorSummary;

    @Column(name = "admin_notified_at")
    private Instant adminNotifiedAt;

    @Column(name = "challenge_detected_at")
    private Instant challengeDetectedAt;

    @Column(name = "cooldown_until")
    private Instant cooldownUntil;

    @Column(name = "challenge_generation", nullable = false)
    private Long challengeGeneration = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
