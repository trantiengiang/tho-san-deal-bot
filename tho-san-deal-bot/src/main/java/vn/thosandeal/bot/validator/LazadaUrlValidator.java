package vn.thosandeal.bot.validator;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Set;

/**
 * Validates Lazada URLs by safely parsing the URI and checking the exact hostname.
 *
 * <p>Security rationale (MandatoryFix URL validation):
 * Using URI.getHost() prevents bypass attacks like:
 * - https://evil.com/?url=lazada.vn (host = evil.com, rejected)
 * - https://lazada.vn.evil.com/ (host = lazada.vn.evil.com, rejected)
 * - javascript:alert(1) (no valid host, rejected)
 *
 * <p>DOES NOT call the URL — no SSRF risk.
 * Only accepts https:// scheme.
 */
@Component
public class LazadaUrlValidator {

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "lazada.vn",
            "www.lazada.vn",
            "s.lazada.vn"
    );

    /**
     * Validates that the URL is a legitimate Lazada URL.
     *
     * @param url the URL string provided by the user
     * @return true if valid Lazada URL, false otherwise
     */
    public boolean isValid(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(url.trim());
            // Must be https
            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme)) {
                return false;
            }
            // Must have a host
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            // Exact match against allowlist — no contains() check
            return ALLOWED_HOSTS.contains(host.toLowerCase());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Normalizes a valid Lazada URL by removing tracking parameters.
     * Returns the URL unchanged in Phase 1 — normalization logic can be added later.
     */
    public String normalize(String url) {
        if (url == null) return null;
        return url.trim();
    }
}
