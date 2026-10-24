import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EvidenceSubmissionService {

    private final SubmissionLimit limits = new SubmissionLimit();

    private final Map<SubmissionLimit.Pair, EvidenceHistory> histories =
            new HashMap<>();

    private record Accepted(
            Map<String, String> values,
            String reason,
            EvidenceHistory.Version version) {
    }

    private final Map<SubmissionLimit.Pair, Map<String, Accepted>> requests =
            new HashMap<>();

    // Simulated staff action; real authorization comes later.
    synchronized void requestSupplement(SubmissionLimit.Pair pair) {
        limits.requestSupplement(pair);
    }

    synchronized EvidenceHistory.Version submit(
            SubmissionLimit.Pair pair,
            String requestId,
            Map<String, String> values,
            String reason) {

        // Validate before changing the counter or history.
        if (pair == null
                || pair.claimantId() == null || pair.claimantId().isBlank()
                || pair.foundItemId() == null || pair.foundItemId().isBlank()) {
            throw new IllegalArgumentException("Account and item are required.");
        }

        if (requestId == null || requestId.isBlank()
                || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Request ID and reason are required.");
        }

        Map<String, String> snapshot = Map.copyOf(values);

        Map<String, Accepted> accepted =
                requests.computeIfAbsent(pair, key -> new HashMap<>());

        Accepted previous = accepted.get(requestId);

        if (previous != null) {
            if (!previous.values().equals(snapshot)
                    || !previous.reason().equals(reason)) {
                throw new IllegalArgumentException(
                        "A retry must contain the same evidence and reason.");
            }
            return previous.version();
        }

        EvidenceHistory history =
                histories.computeIfAbsent(pair, key -> new EvidenceHistory());

        int count = limits.submit(pair, requestId);

        EvidenceHistory.Version version = count == 1
                ? history.saveBaseline(snapshot)
                : history.addCorrection(snapshot, reason);

        accepted.put(requestId, new Accepted(snapshot, reason, version));
        return version;
    }

    synchronized List<EvidenceHistory.Version> history(
            SubmissionLimit.Pair pair) {
        EvidenceHistory history = histories.get(pair);
        return history == null ? List.of() : history.history();
    }

    static void check(String name, boolean passed) {
        if (!passed) {
            throw new AssertionError(name);
        }
        System.out.println("PASS: " + name);
    }

    public static void main(String[] args) {
        EvidenceSubmissionService service = new EvidenceSubmissionService();
        var pair = new SubmissionLimit.Pair("claimant-1", "item-1");
        var original = Map.of("serial_suffix", "A82F");

        var first = service.submit(pair, "request-1", original, "Initial");

        check("Initial submission creates one version",
                service.history(pair).size() == 1);

        var retry = service.submit(pair, "request-1", original, "Initial");

        check("Retry returns the original version",
                retry.equals(first) && service.history(pair).size() == 1);

        boolean blocked = false;
        try {
            service.submit(pair, "request-2",
                    Map.of("serial_suffix", "A82E"), "Typing correction");
        } catch (IllegalStateException expected) {
            blocked = true;
        }

        check("Blocked submission creates no version",
                blocked && service.history(pair).size() == 1);

        service.requestSupplement(pair);
        service.submit(pair, "request-2",
                Map.of("serial_suffix", "A82E"), "Typing correction");

        check("Requested supplement creates a second version",
                service.history(pair).size() == 2);

        check("Original evidence remains unchanged",
                service.history(pair).get(0).values()
                        .get("serial_suffix").equals("A82F"));

                // Staff permits one more supplement.
        service.requestSupplement(pair);

        boolean changedRetryBlocked = false;

        try {
            service.submit(
                    pair,
                    "request-2",
                    Map.of("serial_suffix", "ZZ99"),
                    "Typing correction");
        } catch (IllegalArgumentException expected) {
            changedRetryBlocked = true;
        }

        check("Changed evidence with an old request ID is rejected",
                changedRetryBlocked);

        check("Rejected retry does not change history",
                service.history(pair).size() == 2
                        && service.history(pair).get(1).values()
                        .get("serial_suffix").equals("A82E"));

        // The rejected retry must not consume the staff permission.
        var third = service.submit(
                pair,
                "request-3",
                Map.of("serial_suffix", "A82E", "engraving", "TEST"),
                "Added an identifying detail");

        check("Rejected retry does not consume the supplement",
                third.number() == 3 && service.history(pair).size() == 3);

        boolean fourthBlocked = false;

        try {
            service.submit(
                    pair,
                    "request-4",
                    Map.of("serial_suffix", "B91K"),
                    "Another correction");
        } catch (IllegalStateException expected) {
            fourthBlocked = true;
        }

        check("Fourth submission creates no extra version",
                fourthBlocked && service.history(pair).size() == 3);

        System.out.println("All 9 submission-service tests passed.");
    }
}