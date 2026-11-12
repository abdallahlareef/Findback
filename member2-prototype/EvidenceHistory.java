import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EvidenceHistory {

    record Version(
            int number,
            Instant submittedAt,
            String reason,
            Map<String, String> values) {

        Version {
            // Keep a protected copy that cannot be edited.
            values = Map.copyOf(values);
        }
    }

    private final List<Version> versions = new ArrayList<>();

    Version saveBaseline(Map<String, String> values) {
        if (!versions.isEmpty()) {
            throw new IllegalStateException("Baseline already exists.");
        }

        Version baseline = new Version(
                1, Instant.now(), "Initial submission", values);

        versions.add(baseline);
        return baseline;
    }

    Version addCorrection(Map<String, String> values, String reason) {
        if (versions.isEmpty()) {
            throw new IllegalStateException("Save a baseline first.");
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "A correction needs a reason.");
        }

        Version correction = new Version(
                versions.size() + 1, Instant.now(), reason, values);

        versions.add(correction);
        return correction;
    }

    List<Version> history() {
        return List.copyOf(versions);
    }

    static void check(String name, boolean passed) {
        if (!passed) {
            throw new AssertionError(name);
        }
        System.out.println("PASS: " + name);
    }

    public static void main(String[] args) {
        EvidenceHistory history = new EvidenceHistory();

        history.saveBaseline(Map.of("serial_suffix", "A82F"));

        history.addCorrection(
                Map.of("serial_suffix", "A82E"),
                "Corrected a typing mistake");

        List<Version> saved = history.history();

        check("Both versions remain", saved.size() == 2);

        check("Original value is preserved",
                saved.get(0).values().get("serial_suffix").equals("A82F"));

        check("Correction contains new value",
                saved.get(1).values().get("serial_suffix").equals("A82E"));

        check("Version numbers increase",
                saved.get(0).number() == 1
                        && saved.get(1).number() == 2);

        boolean editBlocked = false;
        try {
            saved.get(0).values().put("serial_suffix", "XXXX");
        } catch (UnsupportedOperationException expected) {
            editBlocked = true;
        }
        check("Original values cannot be edited", editBlocked);

        boolean replacementBlocked = false;
        try {
            history.saveBaseline(Map.of("serial_suffix", "XXXX"));
        } catch (IllegalStateException expected) {
            replacementBlocked = true;
        }
        check("Baseline cannot be replaced", replacementBlocked);

        System.out.println("All 6 history tests passed.");
    }
}