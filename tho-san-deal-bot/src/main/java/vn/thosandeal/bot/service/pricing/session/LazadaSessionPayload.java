package vn.thosandeal.bot.service.pricing.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LazadaSessionPayload {

    private Integer version;
    private Map<String, String> cookies;

    public void validate() {
        if (version == null) {
            throw new IllegalArgumentException("Session payload is missing 'version' field");
        }
        if (version != 1) {
            throw new IllegalArgumentException("Unsupported session payload version: " + version + ". Supported version: 1");
        }
        if (cookies == null || cookies.isEmpty()) {
            throw new IllegalArgumentException("Session payload is missing 'cookies' object");
        }
        String[] required = {"lzd_sid", "lzd_uid", "cna"};
        for (String key : required) {
            if (!cookies.containsKey(key)) {
                throw new IllegalArgumentException("Session payload is missing required cookie: " + key);
            }
            String val = cookies.get(key);
            if (val == null || val.trim().isEmpty()) {
                throw new IllegalArgumentException("Required cookie '" + key + "' cannot be empty");
            }
        }
    }

    public String toCookieHeader() {
        if (cookies == null || cookies.isEmpty()) {
            return "";
        }
        return cookies.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null && !e.getValue().isBlank())
                .map(e -> e.getKey().trim() + "=" + e.getValue().trim())
                .collect(Collectors.joining("; "));
    }

    public boolean hasRequiredCookies() {
        try {
            validate();
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
