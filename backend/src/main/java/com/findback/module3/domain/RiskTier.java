package com.findback.module3.domain;

public enum RiskTier {
    LOW, MEDIUM, HIGH;

    public static RiskTier fromCategory(String category) {
        if (category == null) return MEDIUM;
        return switch (category.toUpperCase()) {
            case "BOTTLE", "UMBRELLA", "NOTEBOOK", "STATIONERY" -> LOW;
            case "EARBUDS", "CALCULATOR", "WATCH", "ACCESSORY" -> MEDIUM;
            case "PHONE", "LAPTOP", "WALLET", "ID_CARD", "PAYMENT_CARD" -> HIGH;
            default -> MEDIUM;
        };
    }
}
