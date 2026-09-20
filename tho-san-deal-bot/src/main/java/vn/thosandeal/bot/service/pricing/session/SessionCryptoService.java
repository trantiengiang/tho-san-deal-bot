package vn.thosandeal.bot.service.pricing.session;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts and decrypts sensitive session payloads using AES-256-GCM.
 *
 * Requirements:
 * - Encryption key must be provided via LAZADA_SESSION_ENCRYPTION_KEY_BASE64.
 * - Key must decode to exactly 32 bytes (256 bits).
 * - IV is 12 random bytes generated per encryption call (never reused).
 * - Format: versioned payload "v1:<base64(12-byte-iv + ciphertext-tag)>".
 */
@Service
public class SessionCryptoService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final String VERSION_PREFIX = "v1:";

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @org.springframework.beans.factory.annotation.Autowired
    public SessionCryptoService(
            @Value("${pricing.lazada.session-encryption-key-base64:}") String keyBase64,
            @Value("${pricing.provider:mock}") String provider) {
        if (keyBase64 == null || keyBase64.trim().isEmpty()) {
            if ("lazada-auth".equalsIgnoreCase(provider)) {
                throw new IllegalStateException(
                        "LAZADA_SESSION_ENCRYPTION_KEY_BASE64 is required when pricing.provider=lazada-auth");
            }
            // For mock provider or tests without key, generate an ephemeral random 32-byte key
            byte[] ephemeral = new byte[32];
            new SecureRandom().nextBytes(ephemeral);
            this.secretKey = new SecretKeySpec(ephemeral, "AES");
            return;
        }

        byte[] decodedKey;
        try {
            decodedKey = Base64.getDecoder().decode(keyBase64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "LAZADA_SESSION_ENCRYPTION_KEY_BASE64 is not valid Base64: " + e.getMessage(), e);
        }

        if (decodedKey.length != 32) {
            throw new IllegalStateException(
                    "LAZADA_SESSION_ENCRYPTION_KEY_BASE64 must decode to exactly 32 bytes (256 bits). Got "
                            + decodedKey.length + " bytes.");
        }

        this.secretKey = new SecretKeySpec(decodedKey, "AES");
    }

    /**
     * Constructor for explicit programmatic / test instantiation.
     */
    public SessionCryptoService(byte[] raw32ByteKey) {
        if (raw32ByteKey == null || raw32ByteKey.length != 32) {
            throw new IllegalArgumentException("Key must be exactly 32 bytes");
        }
        this.secretKey = new SecretKeySpec(raw32ByteKey, "AES");
    }

    /**
     * Encrypts plaintext string into versioned payload: "v1:<base64(iv + ciphertext)>".
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return VERSION_PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt session payload", e);
        }
    }

    /**
     * Decrypts versioned payload: "v1:<base64(iv + ciphertext)>" into plaintext string.
     */
    public String decrypt(String encryptedPayload) {
        if (encryptedPayload == null) {
            return null;
        }
        if (!encryptedPayload.startsWith(VERSION_PREFIX)) {
            throw new IllegalArgumentException("Unsupported encrypted payload version: " + encryptedPayload);
        }

        String b64 = encryptedPayload.substring(VERSION_PREFIX.length());
        byte[] combined;
        try {
            combined = Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed Base64 in encrypted payload", e);
        }

        if (combined.length < GCM_IV_LENGTH) {
            throw new IllegalArgumentException("Encrypted payload too short");
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

            int cipherLength = combined.length - GCM_IV_LENGTH;
            byte[] ciphertext = new byte[cipherLength];
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, cipherLength);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt session payload", e);
        }
    }
}
