import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DistinctClueCounter {

    static int countDistinct(List<String> usefulMatchedGroupIds) {
        Set<String> uniqueGroups = new HashSet<>();

        for (String groupId : usefulMatchedGroupIds) {
            if (groupId == null || groupId.isBlank()) {
                throw new IllegalArgumentException(
                        "Every matched clue must have a group ID.");
            }

            uniqueGroups.add(groupId);
        }

        return uniqueGroups.size();
    }

    static void check(
            String name,
            List<String> groups,
            int expected) {

        int actual = countDistinct(groups);

        if (actual != expected) {
            throw new AssertionError(
                    name + ": expected " + expected + ", got " + actual);
        }

        System.out.println("PASS: " + name);
    }

    public static void main(String[] args) {
        check("No useful matches",
                List.of(), 0);

        check("One serial clue",
                List.of("serial_suffix"), 1);

        check("Three details about one sticker",
                List.of("sticker_1", "sticker_1", "sticker_1"), 1);

        check("Sticker and serial",
                List.of("sticker_1", "sticker_1", "serial_suffix"), 2);

        check("Three distinct clues",
                List.of("sticker_1", "serial_suffix", "engraving_1"), 3);

        check("Repeated clues do not inflate count",
                List.of(
                        "sticker_1", "serial_suffix", "engraving_1",
                        "sticker_1", "serial_suffix"), 3);

        System.out.println("All 6 clue-counting tests passed.");
    }
}