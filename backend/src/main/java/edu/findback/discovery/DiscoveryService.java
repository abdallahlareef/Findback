package edu.findback.discovery;

import edu.findback.report.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class DiscoveryService {

    private final ReportRepository reportRepository;

    public DiscoveryService(
            ReportRepository reportRepository
    ) {
        this.reportRepository = reportRepository;
    }

    @Transactional(readOnly = true)
    public List<CandidateMatchResponse> findCandidates(
            Long lostReportId
    ) {

        Report lostReport = reportRepository
                .findById(lostReportId)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Report not found"
                        )
                );

        if (lostReport.getType() != ReportType.LOST) {
            throw new IllegalArgumentException(
                    "Candidate discovery requires a LOST report"
            );
        }

        List<Report> foundReports =
                reportRepository.findByTypeAndStatus(
                        ReportType.FOUND,
                        ReportStatus.ACTIVE
                );

        return foundReports
                .stream()
                .map(foundReport -> {

                    double score =
                            calculateScore(
                                    lostReport,
                                    foundReport
                            );

                    return CandidateMatchResponse.from(
                            foundReport,
                            score
                    );
                })
                .sorted(
                        Comparator.comparingDouble(
                                CandidateMatchResponse::score
                        ).reversed()
                )
                .toList();
    }

    private double calculateScore(
            Report lost,
            Report found
    ) {

        double weightedScore = 0.0;
        double availableWeight = 0.0;

        // Category = 0.30
        weightedScore +=
                0.30 * exactSimilarity(
                        lost.getCategory(),
                        found.getCategory()
                );
        availableWeight += 0.30;

        // Brand / model = 0.20
        if (
                hasValue(lost.getBrand()) ||
                hasValue(lost.getModel())
        ) {

            double brandModelScore =
                    calculateBrandModelSimilarity(
                            lost,
                            found
                    );

            weightedScore +=
                    0.20 * brandModelScore;

            availableWeight += 0.20;
        }

        // Colour = 0.15
        weightedScore +=
                0.15 * exactSimilarity(
                        lost.getColour(),
                        found.getColour()
                );

        availableWeight += 0.15;

        // Location = 0.15
        weightedScore +=
                0.15 * exactSimilarity(
                        lost.getCampusZone(),
                        found.getCampusZone()
                );

        availableWeight += 0.15;

        // Date/time = 0.10
        if (
                lost.getEventDate() != null &&
                found.getEventDate() != null
        ) {

            double dateScore =
                    lost.getEventDate()
                            .equals(found.getEventDate())
                            ? 1.0
                            : 0.0;

            weightedScore +=
                    0.10 * dateScore;

            availableWeight += 0.10;
        }

        // Safe description keywords = 0.10
        if (
                hasValue(lost.getSafeDescription()) &&
                hasValue(found.getSafeDescription())
        ) {

            double descriptionScore =
                    keywordSimilarity(
                            lost.getSafeDescription(),
                            found.getSafeDescription()
                    );

            weightedScore +=
                    0.10 * descriptionScore;

            availableWeight += 0.10;
        }

        if (availableWeight == 0.0) {
            return 0.0;
        }

        double normalized =
                weightedScore / availableWeight;

        return Math.round(
                normalized * 10000.0
        ) / 100.0;
    }

    private double calculateBrandModelSimilarity(
            Report lost,
            Report found
    ) {

        boolean sameBrand =
                hasValue(lost.getBrand()) &&
                hasValue(found.getBrand()) &&
                lost.getBrand()
                        .equalsIgnoreCase(
                                found.getBrand()
                        );

        boolean sameModel =
                hasValue(lost.getModel()) &&
                hasValue(found.getModel()) &&
                lost.getModel()
                        .equalsIgnoreCase(
                                found.getModel()
                        );

        if (sameBrand && sameModel) {
            return 1.0;
        }

        if (sameBrand) {
            return 0.5;
        }

        return 0.0;
    }

    private double exactSimilarity(
            String first,
            String second
    ) {

        if (
                !hasValue(first) ||
                !hasValue(second)
        ) {
            return 0.0;
        }

        return first.trim()
                .equalsIgnoreCase(
                        second.trim()
                )
                ? 1.0
                : 0.0;
    }

    private double keywordSimilarity(
            String first,
            String second
    ) {

        String[] firstWords =
                first.toLowerCase()
                        .split("\\s+");

        String secondText =
                second.toLowerCase();

        int matches = 0;

        for (String word : firstWords) {

            if (
                    word.length() > 2 &&
                    secondText.contains(word)
            ) {
                matches++;
            }
        }

        if (firstWords.length == 0) {
            return 0.0;
        }

        return (double) matches /
                firstWords.length;
    }

    private boolean hasValue(
            String value
    ) {
        return value != null &&
                !value.isBlank();
    }
}