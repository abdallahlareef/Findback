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

    public static void main(String[] args) {
        System.out.println("Same suffix: "
                + compareSerialSuffix("A82F", "A82F"));

        System.out.println("Missing suffix: "
                + compareSerialSuffix("A82F", ""));

        System.out.println("Different suffix: "
                + compareSerialSuffix("A82F", "B91K"));

        System.out.println("Formatting difference: "
                + compareSerialSuffix(" a82f ", "A82F"));
    }
}