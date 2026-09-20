package vn.thosandeal.bot.service.pricing.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionCryptoServiceTest {

    private String generateRandomBase64Key(int lengthBytes) {
        byte[] bytes = new byte[lengthBytes];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    @DisplayName("Startup fails when AES key length is not exactly 32 bytes")
    void testInvalidAesKeyLengthFails() {
        // 16 bytes (128 bits) -> invalid
        String shortKey = generateRandomBase64Key(16);
        assertThatThrownBy(() -> new SessionCryptoService(shortKey, "lazada-auth"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must decode to exactly 32 bytes");

        // 24 bytes (192 bits) -> invalid
        String key24 = generateRandomBase64Key(24);
        assertThatThrownBy(() -> new SessionCryptoService(key24, "lazada-auth"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must decode to exactly 32 bytes");

        // 40 bytes -> invalid
        String longKey = generateRandomBase64Key(40);
        assertThatThrownBy(() -> new SessionCryptoService(longKey, "lazada-auth"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must decode to exactly 32 bytes");

        // Not base64
        assertThatThrownBy(() -> new SessionCryptoService("not-valid-base64!@#", "lazada-auth"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Startup fails when lazada-auth is active but key is empty")
    void testEmptyKeyFailsWhenLazadaAuthProvider() {
        assertThatThrownBy(() -> new SessionCryptoService("", "lazada-auth"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LAZADA_SESSION_ENCRYPTION_KEY_BASE64 is required");
    }

    @Test
    @DisplayName("Same plaintext decrypts correctly with valid 32-byte key")
    void testEncryptDecryptRoundtrip() {
        String key32 = generateRandomBase64Key(32);
        SessionCryptoService cryptoService = new SessionCryptoService(key32, "lazada-auth");

        String plaintext = "{\"version\":1,\"cookies\":{\"lzd_sid\":\"abc123xyz\",\"cna\":\"cookie_cna_val\"}}";
        String encrypted = cryptoService.encrypt(plaintext);

        assertThat(encrypted).startsWith("v1:");
        String decrypted = cryptoService.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Random IV produces different ciphertext for identical plaintext")
    void testRandomIvProducesDifferentCiphertext() {
        String key32 = generateRandomBase64Key(32);
        SessionCryptoService cryptoService = new SessionCryptoService(key32, "lazada-auth");

        String plaintext = "same-payload-to-encrypt";
        String cipher1 = cryptoService.encrypt(plaintext);
        String cipher2 = cryptoService.encrypt(plaintext);

        assertThat(cipher1).isNotEqualTo(cipher2);
        // But both decrypt back to the exact same plaintext
        assertThat(cryptoService.decrypt(cipher1)).isEqualTo(plaintext);
        assertThat(cryptoService.decrypt(cipher2)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Wrong AES key cannot decrypt ciphertext")
    void testWrongKeyCannotDecrypt() {
        String key1 = generateRandomBase64Key(32);
        String key2 = generateRandomBase64Key(32);

        SessionCryptoService crypto1 = new SessionCryptoService(key1, "lazada-auth");
        SessionCryptoService crypto2 = new SessionCryptoService(key2, "lazada-auth");

        String encrypted = crypto1.encrypt("super-secret-session-token");

        assertThatThrownBy(() -> crypto2.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to decrypt session payload");
    }
}
