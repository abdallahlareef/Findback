package edu.findback.evidence;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record EvidenceData(String serialSuffix, String privateMark, String accessoryDescription) {
    public EvidenceData {
        serialSuffix = clean(serialSuffix, 4);
        if (serialSuffix != null) {
            serialSuffix = serialSuffix.toUpperCase(Locale.ROOT);
            if (!serialSuffix.matches("[A-Z0-9]{4}")) bad("Serial suffix must contain exactly four letters or digits.");
        }
        privateMark = clean(privateMark, 500);
        accessoryDescription = clean(accessoryDescription, 500);
    }
    static String clean(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > max) bad("Evidence field exceeds its allowed length.");
        return result;
    }
    static String reason(String value) {
        String result = clean(value, 500);
        if (result == null) bad("A reason is required.");
        return result;
    }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
