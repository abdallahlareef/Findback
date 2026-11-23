package com.findback.module3.domain;

import java.util.List;
import java.util.Locale;

/**
 * FR-10 item risk. Declared from lowest to highest, so {@link #compareTo} orders by risk.
 *
 * <p>The category keywords are mirrored in frontend/src/features/claims/riskLevels.ts, which
 * suggests a level on the report form. Keep both lists in sync.
 */
public enum RiskTier {
    LOW, MEDIUM, HIGH;

    // Checked first: "earphone" and "headphone" contain "PHONE" but are medium-risk audio items.
    private static final List<String> MEDIUM_AUDIO = List.of("EARBUD", "EARPHONE", "HEADPHONE", "HEADSET", "AIRPOD");
    private static final List<String> HIGH_PARTS = List.of("PHONE", "MOBILE", "LAPTOP", "COMPUTER", "MACBOOK",
            "TABLET", "IPAD", "WALLET", "PURSE", "PASSPORT", "PAYMENT", "CREDIT", "DEBIT");
    private static final List<String> HIGH_WORDS = List.of("CARD");
    private static final List<String> MEDIUM_PARTS = List.of("CALCULATOR", "WATCH", "ACCESSORY", "CAMERA",
            "SPEAKER", "POWERBANK", "POWER BANK", "CHARGER");
    private static final List<String> LOW_WORDS = List.of("BOTTLE", "UMBRELLA", "NOTEBOOK", "STATIONERY", "BOOK",
            "PEN", "PENCIL", "JACKET", "HOODIE", "SCARF", "CAP", "HAT", "MUG", "LUNCHBOX");

    /** Baseline tier implied by a free-text category; unknown categories are MEDIUM. */
    public static RiskTier fromCategory(String category) {
        if (category == null || category.isBlank()) return MEDIUM;
        String text = " " + category.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim() + " ";
        if (containsAny(text, MEDIUM_AUDIO)) return MEDIUM;
        if (containsAny(text, HIGH_PARTS) || hasWord(text, HIGH_WORDS)) return HIGH;
        if (containsAny(text, MEDIUM_PARTS)) return MEDIUM;
        if (hasWord(text, LOW_WORDS)) return LOW;
        return MEDIUM;
    }

    /** Parses a reporter-selected level; null or unrecognised values are ignored (null). */
    public static RiskTier parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The highest of the given tiers, ignoring nulls; MEDIUM if none are given. */
    public static RiskTier highest(RiskTier... tiers) {
        RiskTier result = null;
        for (RiskTier tier : tiers) {
            if (tier != null && (result == null || tier.compareTo(result) > 0)) result = tier;
        }
        return result == null ? MEDIUM : result;
    }

    private static boolean containsAny(String text, List<String> parts) {
        return parts.stream().anyMatch(text::contains);
    }

    private static boolean hasWord(String text, List<String> words) {
        return words.stream().anyMatch(w -> text.contains(" " + w + " ") || text.contains(" " + w + "S "));
    }
}
