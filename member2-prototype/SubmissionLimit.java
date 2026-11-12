import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SubmissionLimit {

    record Pair(String claimantId, String foundItemId) {
    }

    private static class State {
        final Set<String> acceptedRequestIds = new HashSet<>();
        boolean supplementRequested;
    }

    private final Map<Pair, State> records = new HashMap<>();

    private State stateFor(Pair pair) {
        return records.computeIfAbsent(pair, key -> new State());
    }

    // Prototype only: production must authorize the staff member.
    synchronized void requestSupplement(Pair pair) {
        State state = stateFor(pair);
        int count = state.acceptedRequestIds.size();

        if (count == 0) {
            throw new IllegalStateException(
                    "The initial submission must come first.");
        }

        if (count >= 3) {
            throw new IllegalStateException(
                    "The ordinary submission limit has been reached.");
        }

        state.supplementRequested = true;
    }

    synchronized int submit(Pair pair, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("Request ID is required.");
        }

        State state = stateFor(pair);

        // Retrying an accepted request does not consume another submission.
        if (state.acceptedRequestIds.contains(requestId)) {
            return state.acceptedRequestIds.size();
        }

        int count = state.acceptedRequestIds.size();

        if (count >= 3) {
            throw new IllegalStateException(
                    "No more self-service submissions are allowed.");
        }

        if (count > 0 && !state.supplementRequested) {
            throw new IllegalStateException(
                    "Staff must request a supplement first.");
        }

        state.acceptedRequestIds.add(requestId);
        state.supplementRequested = false;

        return state.acceptedRequestIds.size();
    }

    static void check(String name, boolean passed) {
        if (!passed) {
            throw new AssertionError(name);
        }
        System.out.println("PASS: " + name);
    }

    static void expectBlocked(String name, Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            System.out.println("PASS: " + name);
            return;
        }

        throw new AssertionError(name + ": request was not blocked");
    }

    public static void main(String[] args) {
        SubmissionLimit limits = new SubmissionLimit();
        Pair pair = new Pair("claimant-1", "found-item-1");

        expectBlocked("Supplement request before baseline is blocked",
                () -> limits.requestSupplement(pair));

        check("Initial submission accepted",
                limits.submit(pair, "request-1") == 1);

        check("Duplicate request counts once",
                limits.submit(pair, "request-1") == 1);

        expectBlocked("Unrequested supplement is blocked",
                () -> limits.submit(pair, "request-2"));

        limits.requestSupplement(pair);

        check("First requested supplement accepted",
                limits.submit(pair, "request-2") == 2);

        expectBlocked("Staff request is consumed after use",
                () -> limits.submit(pair, "request-3"));

        limits.requestSupplement(pair);

        check("Second requested supplement accepted",
                limits.submit(pair, "request-3") == 3);

        check("Retry at the limit does not increase count",
                limits.submit(pair, "request-3") == 3);

        expectBlocked("Fourth submission is blocked",
                () -> limits.submit(pair, "request-4"));

        expectBlocked("Further ordinary supplement request is blocked",
                () -> limits.requestSupplement(pair));

        // A recreated claim still refers to the same account and item.
        Pair samePair = new Pair("claimant-1", "found-item-1");

        expectBlocked("Recreated claim cannot reset pair limit",
                () -> limits.submit(samePair, "new-claim-request"));

        Pair otherItem = new Pair("claimant-1", "found-item-2");

        check("Different item has its own limit",
                limits.submit(otherItem, "request-other-item") == 1);

        System.out.println("All 12 submission-limit tests passed.");
    }
}