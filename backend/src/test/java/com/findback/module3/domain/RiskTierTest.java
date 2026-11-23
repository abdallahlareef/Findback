package com.findback.module3.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RiskTierTest {

    @ParameterizedTest
    @CsvSource({
            // Slide examples and the original category codes
            "Umbrella, LOW", "Water bottle, LOW", "Notebook, LOW", "STATIONERY, LOW",
            "Earbuds, MEDIUM", "Calculator, MEDIUM", "Watch, MEDIUM", "ACCESSORY, MEDIUM",
            "Laptop, HIGH", "Smartphone, HIGH", "Wallet, HIGH", "PHONE, HIGH", "ID_CARD, HIGH", "PAYMENT_CARD, HIGH",
            // Free-text variants students actually type
            "iPhone 13, HIGH", "Mobile, HIGH", "MacBook Air, HIGH", "Notebook computer, HIGH", "Student ID card, HIGH",
            "Wireless earphones, MEDIUM", "Headphones, MEDIUM", "AirPods, MEDIUM", "Smartwatch, MEDIUM",
            "Books, LOW", "Pen, LOW",
            // Must not be fooled by substrings
            "Cardigan, MEDIUM", "Pendrive, MEDIUM",
            // Unknown or vague categories stay at the safe default
            "Electronics, MEDIUM", "Bag, MEDIUM"
    })
    void categoryBaseline(String category, RiskTier expected) {
        assertEquals(expected, RiskTier.fromCategory(category));
    }

    /** Every report-form dropdown category (frontend ITEM_CATEGORIES) with the tier the form shows. */
    @ParameterizedTest
    @CsvSource({
            "Phone, HIGH", "Laptop, HIGH", "Tablet, HIGH", "Wallet or purse, HIGH", "ID or student card, HIGH",
            "Bank or payment card, HIGH", "Passport, HIGH",
            "Earbuds or headphones, MEDIUM", "Calculator, MEDIUM", "Watch, MEDIUM", "Camera, MEDIUM",
            "Charger or power bank, MEDIUM", "Keys, MEDIUM", "Glasses, MEDIUM", "Bag or backpack, MEDIUM",
            "Water bottle, LOW", "Umbrella, LOW", "Notebook or book, LOW", "Stationery, LOW", "Clothing, LOW", "Lunch box, LOW"
    })
    void dropdownCategoriesHaveFixedTiers(String category, RiskTier expected) {
        assertEquals(expected, RiskTier.fromCategory(category));
        assertEquals(expected, RiskTier.FORM_CATEGORIES.get(category.toUpperCase()), "missing from FORM_CATEGORIES: " + category);
    }

    @Test
    void blankCategoryIsMedium() {
        assertEquals(RiskTier.MEDIUM, RiskTier.fromCategory(" "));
        assertEquals(RiskTier.MEDIUM, RiskTier.fromCategory(null));
    }

    @Test
    void highestIgnoresMissingValues() {
        assertEquals(RiskTier.HIGH, RiskTier.highest(RiskTier.LOW, null, RiskTier.HIGH, RiskTier.MEDIUM));
        assertEquals(RiskTier.LOW, RiskTier.highest(RiskTier.LOW, null));
        assertEquals(RiskTier.MEDIUM, RiskTier.highest(null, null));
    }

    @Test
    void parseAcceptsAnyCaseAndIgnoresJunk() {
        assertEquals(RiskTier.HIGH, RiskTier.parse(" high "));
        assertNull(RiskTier.parse("EXTREME"));
        assertNull(RiskTier.parse(null));
    }
}
