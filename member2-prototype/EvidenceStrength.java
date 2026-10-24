public class EvidenceStrength {

    enum Summary {
        WEAK,
        MODERATE,
        STRONG,
        REVIEW_REQUIRED,
        ON_HOLD
    }

    static Summary summarize(
            int distinctMatches,
            boolean unresolvedContradiction,
            boolean criticalContradiction) {

        if (distinctMatches < 0) {
            throw new IllegalArgumentException(
                    "Match count cannot be negative.");
        }

        // A critical conflict takes priority over all matching clues.
        if (criticalContradiction) {
            return Summary.ON_HOLD;
        }

        if (unresolvedContradiction) {
            return Summary.REVIEW_REQUIRED;
        }

        if (distinctMatches >= 3) {
            return Summary.STRONG;
        }

        if (distinctMatches == 2) {
            return Summary.MODERATE;
        }

        return Summary.WEAK;
    }

    static void check(
            String name,
            int matches,
            boolean contradiction,
            boolean critical,
            Summary expected) {

        Summary actual = summarize(matches, contradiction, critical);

        if (actual != expected) {
            throw new AssertionError(
                    name + ": expected " + expected + ", got " + actual);
        }

        System.out.println("PASS: " + name);
    }

    public static void main(String[] args) {
        check("Zero matches", 0, false, false, Summary.WEAK);
        check("One match", 1, false, false, Summary.WEAK);
        check("Two matches", 2, false, false, Summary.MODERATE);
        check("Three matches", 3, false, false, Summary.STRONG);
        check("Four matches", 4, false, false, Summary.STRONG);

        check("Contradiction overrides strong",
                3, true, false, Summary.REVIEW_REQUIRED);

        check("Critical conflict overrides strong",
                3, true, true, Summary.ON_HOLD);

        check("Critical flag alone requires hold",
                0, false, true, Summary.ON_HOLD);

        System.out.println("All 8 summary tests passed.");
    }
}