package vn.thosandeal.bot.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.thosandeal.bot.validator.LazadaUrlValidator;

import static org.assertj.core.api.Assertions.*;

class LazadaUrlValidatorTest {

    private LazadaUrlValidator validator;

    @BeforeEach
    void setUp() {
        validator = new LazadaUrlValidator();
    }

    // -----------------------------------------------------------------------
    // Valid URLs
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "valid: \"{0}\"")
    @ValueSource(strings = {
        "https://lazada.vn/products/abc",
        "https://www.lazada.vn/products/abc",
        "https://s.lazada.vn/s/abc",
        "https://lazada.vn/",
        "https://www.lazada.vn/",
        "https://s.lazada.vn/xyz?query=test"
    })
    void shouldAcceptValidLazadaUrls(String url) {
        assertThat(validator.isValid(url)).isTrue();
    }

    // -----------------------------------------------------------------------
    // Invalid URLs — wrong domain
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "invalid domain: \"{0}\"")
    @ValueSource(strings = {
        "https://evil.com",
        "https://evil.com/path",
        "https://lazada.vn.evil.com/",          // subdomain trick
        "https://notlazada.vn/products",
        "https://lazadavn.com/products",
        "https://lazada.com/products"            // .com not .vn
    })
    void shouldRejectWrongDomain(String url) {
        assertThat(validator.isValid(url)).isFalse();
    }

    // -----------------------------------------------------------------------
    // URL injection / SSRF attempts
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "URL injection: \"{0}\"")
    @ValueSource(strings = {
        "https://evil.com/?url=lazada.vn",       // query param injection
        "https://evil.com/?redirect=lazada.vn",  // redirect param
        "https://evil.com/#lazada.vn",            // fragment trick
        "http://evil.com/lazada.vn"              // path trick
    })
    void shouldRejectUrlInjectionAttempts(String url) {
        assertThat(validator.isValid(url)).isFalse();
    }

    // -----------------------------------------------------------------------
    // HTTP scheme — only HTTPS allowed
    // -----------------------------------------------------------------------

    @Test
    void shouldRejectHttpScheme() {
        assertThat(validator.isValid("http://lazada.vn/products")).isFalse();
    }

    @Test
    void shouldRejectFtpScheme() {
        assertThat(validator.isValid("ftp://lazada.vn/products")).isFalse();
    }

    @Test
    void shouldRejectJavascriptScheme() {
        assertThat(validator.isValid("javascript:alert(1)")).isFalse();
    }

    // -----------------------------------------------------------------------
    // Edge cases
    // -----------------------------------------------------------------------

    @Test
    void shouldRejectNull() {
        assertThat(validator.isValid(null)).isFalse();
    }

    @Test
    void shouldRejectBlank() {
        assertThat(validator.isValid("")).isFalse();
        assertThat(validator.isValid("   ")).isFalse();
    }

    @Test
    void shouldRejectMalformedUrl() {
        assertThat(validator.isValid("not a url at all")).isFalse();
        assertThat(validator.isValid("lazada.vn/products")).isFalse(); // missing scheme
    }

    @Test
    void shouldRejectSubdomainOfLazada() {
        // lazada.vn.evil.com — hostname is "lazada.vn.evil.com", not "lazada.vn"
        assertThat(validator.isValid("https://lazada.vn.evil.com/")).isFalse();
    }
}
