package com.findback.module3.domain;

import java.util.List;
import java.util.Locale;
import java.util.Map;

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
            "PEN", "PENCIL", "JACKET", "HOODIE", "SCARF", "CAP", "HAT", "MUG", "LUNCHBOX", "CLOTHING", "CLOTHES");

    /**
     * The fixed categories offered by the report form's dropdown, with their exact tier. Mirrors
     * ITEM_CATEGORIES in frontend/src/features/claims/riskLevels.ts (RiskTierTest checks every entry).
     */
    static final Map<String, RiskTier> FORM_CATEGORIES = Map.ofEntries(
            Map.entry("PHONE", HIGH), Map.entry("LAPTOP", HIGH), Map.entry("TABLET", HIGH),
            Map.entry("WALLET OR PURSE", HIGH), Map.entry("ID OR STUDENT CARD", HIGH),
            Map.entry("BANK OR PAYMENT CARD", HIGH), Map.entry("PASSPORT", HIGH),
            Map.entry("EARBUDS OR HEADPHONES", MEDIUM), Map.entry("CALCULATOR", MEDIUM), Map.entry("WATCH", MEDIUM),
            Map.entry("CAMERA", MEDIUM), Map.entry("CHARGER OR POWER BANK", MEDIUM), Map.entry("KEYS", MEDIUM),
            Map.entry("GLASSES", MEDIUM), Map.entry("BAG OR BACKPACK", MEDIUM),
            Map.entry("WATER BOTTLE", LOW), Map.entry("UMBRELLA", LOW), Map.entry("NOTEBOOK OR BOOK", LOW),
            Map.entry("STATIONERY", LOW), Map.entry("CLOTHING", LOW), Map.entry("LUNCH BOX", LOW));

    /**
     * Baseline tier implied by a category: exact for the dropdown categories, keyword-based for "Other"
     * free text and older reports; unknown categories are MEDIUM.
     */
    public static RiskTier fromCategory(String category) {
        if (category == null || category.isBlank()) return MEDIUM;
        String words = category.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim();
        RiskTier known = FORM_CATEGORIES.get(words);
        if (known != null) return known;
        String text = " " + words + " ";
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
