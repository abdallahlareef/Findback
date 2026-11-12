package edu.findback.discovery;

import edu.findback.report.Report;

public record CandidateMatchResponse(
        Long reportId,
        String category,
        String brand,
        String model,
        String colour,
        String campusZone,
        double score
) {

    public static CandidateMatchResponse from(
            Report report,
            double score
    ) {
        return new CandidateMatchResponse(
                report.getId(),
                report.getCategory(),
                report.getBrand(),
                report.getModel(),
                report.getColour(),
                report.getCampusZone(),
                score
        );
    }
}