import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class VerificationHistory {

    record Attempt(
            int sequence,
            String claimId,
            String claimantReportId,
            int claimantVersion,
            String finderReportId,
            int finderVersion,
            String comparatorVersion,
            Instant comparedAt,
            DoveComparison.Outcome serialOutcome) {
    }

    private final List<Attempt> attempts = new ArrayList<>();

    synchronized Attempt compare(
            String claimId,
            String claimantReportId,
            EvidenceHistory.Version claimant,
            String finderReportId,
            EvidenceHistory.Version finder) {

        if (claimId == null || claimId.isBlank()
                || claimantReportId == null || claimantReportId.isBlank()
                || finderReportId == null || finderReportId.isBlank()
                || claimant == null || finder == null) {
            throw new IllegalArgumentException(
                    "Claim, reports and evidence versions are required.");
        }

        DoveComparison.Outcome outcome =
                DoveComparison.compareSerialSuffix(
                        claimant.values().get("serial_suffix"),
                        finder.values().get("serial_suffix"));

        Attempt attempt = new Attempt(
                attempts.size() + 1,
                claimId,
                claimantReportId,
                claimant.number(),
                finderReportId,
                finder.number(),
                "serial-suffix-v1",
                Instant.now(),
                outcome);

        attempts.add(attempt);
        return attempt;
    }

    synchronized List<Attempt> history() {
        return List.copyOf(attempts);
    }

    static void check(String name, boolean passed) {
        if (!passed) {
            throw new AssertionError(name);
        }
        System.out.println("PASS: " + name);
    }

    public static void main(String[] args) {
        EvidenceHistory ownerHistory = new EvidenceHistory();
        EvidenceHistory finderHistory = new EvidenceHistory();

        var ownerV1 = ownerHistory.saveBaseline(
                java.util.Map.of("serial_suffix", "A82F"));

        var finderV1 = finderHistory.saveBaseline(
                java.util.Map.of("serial_suffix", "A82F"));

        VerificationHistory verification = new VerificationHistory();

        var first = verification.compare(
                "claim-1", "lost-report-1", ownerV1,
                "found-report-1", finderV1);

        check("Initial comparison matches",
                first.serialOutcome() == DoveComparison.Outcome.MATCH);

        var finderV2 = finderHistory.addCorrection(
                java.util.Map.of("serial_suffix", "B91K"),
                "Corrected observation");

        var second = verification.compare(
                "claim-1", "lost-report-1", ownerV1,
                "found-report-1", finderV2);

        check("New comparison detects contradiction",
                second.serialOutcome()
                        == DoveComparison.Outcome.CONTRADICTION);

        check("Both attempts remain",
                verification.history().size() == 2);

        check("Earlier result remains unchanged",
                verification.history().get(0).serialOutcome()
                        == DoveComparison.Outcome.MATCH);

        check("Attempts reference the correct versions",
                first.claimantVersion() == 1
                        && first.finderVersion() == 1
                        && second.claimantVersion() == 1
                        && second.finderVersion() == 2);

        check("Comparison rule version is recorded",
                second.comparatorVersion().equals("serial-suffix-v1"));

        System.out.println("All 6 verification-history tests passed.");
    }
}