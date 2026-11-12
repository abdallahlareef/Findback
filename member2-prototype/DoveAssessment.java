import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DoveAssessment {

    record Clue(
            String groupId,
            DoveComparison.Outcome outcome,
            boolean usefulPrivateClue,
            boolean criticalIdentifier) {
    }

    record Assessment(
            int distinctMatches,
            EvidenceStrength.Summary summary) {
    }

    static Assessment assess(List<Clue> clues) {
        Set<String> conflictingGroups = new HashSet<>();
        boolean contradiction = false;
        boolean criticalContradiction = false;

        for (Clue clue : clues) {
            if (clue.groupId() == null || clue.groupId().isBlank()
                    || clue.outcome() == null) {
                throw new IllegalArgumentException(
                        "Each clue needs a group ID and outcome.");
            }

            if (clue.outcome() == DoveComparison.Outcome.CONTRADICTION) {
                conflictingGroups.add(clue.groupId());
                contradiction = true;

                if (clue.criticalIdentifier()) {
                    criticalContradiction = true;
                }
            }
        }

        List<String> usefulMatches = new ArrayList<>();

        for (Clue clue : clues) {
            if (clue.outcome() == DoveComparison.Outcome.MATCH
                    && clue.usefulPrivateClue()
                    && !conflictingGroups.contains(clue.groupId())) {
                usefulMatches.add(clue.groupId());
            }
        }

        int count = DistinctClueCounter.countDistinct(usefulMatches);

        return new Assessment(
                count,
                EvidenceStrength.summarize(
                        count, contradiction, criticalContradiction));
    }

    static void check(
            String name,
            List<Clue> clues,
            int expectedCount,
            EvidenceStrength.Summary expectedSummary) {

        Assessment actual = assess(clues);

        if (actual.distinctMatches() != expectedCount
                || actual.summary() != expectedSummary) {
            throw new AssertionError(name + ": unexpected " + actual);
        }

        System.out.println("PASS: " + name);
    }

    public static void main(String[] args) {
        var match = DoveComparison.Outcome.MATCH;
        var unknown = DoveComparison.Outcome.UNKNOWN;
        var conflict = DoveComparison.Outcome.CONTRADICTION;

        check("Sticker details count once", List.of(
                new Clue("sticker_1", match, true, false),
                new Clue("sticker_1", match, true, false),
                new Clue("sticker_1", match, true, false)
        ), 1, EvidenceStrength.Summary.WEAK);

        check("Public details do not increase strength", List.of(
                new Clue("serial", match, true, true),
                new Clue("public_colour", match, false, false),
                new Clue("public_brand", match, false, false)
        ), 1, EvidenceStrength.Summary.WEAK);

        check("Unknown does not count", List.of(
                new Clue("engraving", unknown, true, false)
        ), 0, EvidenceStrength.Summary.WEAK);

        check("Three useful distinct matches", List.of(
                new Clue("serial", match, true, true),
                new Clue("sticker_1", match, true, false),
                new Clue("engraving", match, true, false)
        ), 3, EvidenceStrength.Summary.STRONG);

        check("Conflicting sticker is excluded", List.of(
                new Clue("sticker_1", match, true, false),
                new Clue("sticker_1", conflict, true, false),
                new Clue("serial", match, true, true)
        ), 1, EvidenceStrength.Summary.REVIEW_REQUIRED);

        check("Serial conflict requires hold", List.of(
                new Clue("serial", conflict, true, true),
                new Clue("engraving", match, true, false)
        ), 1, EvidenceStrength.Summary.ON_HOLD);

        System.out.println("All 6 assessment tests passed.");
    }
}