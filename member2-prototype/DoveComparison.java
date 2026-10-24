import java.util.Locale;

public class DoveComparison {

    enum Outcome {
        MATCH,
        UNKNOWN,
        CONTRADICTION
    }

    static Outcome compareSerialSuffix(String owner, String finder) {
        if (owner == null || finder == null) {
            return Outcome.UNKNOWN;
        }

        // For this prototype, letter case and surrounding spaces do not matter.
        String ownerValue = owner.trim().toUpperCase(Locale.ROOT);
        String finderValue = finder.trim().toUpperCase(Locale.ROOT);

        // Both values must describe the same four-character serial suffix.
        if (!ownerValue.matches("[A-Z0-9]{4}")
                || !finderValue.matches("[A-Z0-9]{4}")) {
            return Outcome.UNKNOWN;
        }

        if (ownerValue.equals(finderValue)) {
            return Outcome.MATCH;
        }

        return Outcome.CONTRADICTION;
    }
static void check(String name, String owner, String finder,
                  Outcome expected) {
    Outcome actual = compareSerialSuffix(owner, finder);

    if (actual != expected) {
        throw new AssertionError(
                name + ": expected " + expected + ", got " + actual);
    }

    System.out.println("PASS: " + name);
}
    public static void main(String[] args) {
    check("Equal suffixes", "A82F", "A82F", Outcome.MATCH);
    check("Different suffixes", "A82F", "B91K", Outcome.CONTRADICTION);
    check("Missing owner value", null, "A82F", Outcome.UNKNOWN);
    check("Missing finder value", "A82F", null, Outcome.UNKNOWN);
    check("Both values missing", null, null, Outcome.UNKNOWN);
    check("Empty value", "A82F", "", Outcome.UNKNOWN);
    check("Spaces only", "   ", "A82F", Outcome.UNKNOWN);
    check("Formatting differences", " a82f ", "A82F", Outcome.MATCH);
    check("Too short", "A82", "A82F", Outcome.UNKNOWN);
    check("Too long", "A82F9", "A82F", Outcome.UNKNOWN);
    check("Meaningful punctuation", "A-2F", "A82F", Outcome.UNKNOWN);
    check("Matching invalid values", "???", "???", Outcome.UNKNOWN);

    System.out.println("All 12 tests passed.");
}
}