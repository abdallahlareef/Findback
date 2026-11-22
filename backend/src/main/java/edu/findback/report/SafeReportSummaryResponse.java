package edu.findback.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Safe, public discovery fields only; intentionally excludes report author data. */
public record SafeReportSummaryResponse(
        Long reportId,
        ReportType type,
        String category,
        String brand,
        String model,
        String colour,
        String campusZone,
        LocalDate eventDate,
        LocalTime timeFrom,
        LocalTime timeTo,
        String safeDescription,
        ReportStatus status,
        LocalDateTime createdAt) {

    public static SafeReportSummaryResponse from(Report report) {
        return new SafeReportSummaryResponse(report.getId(), report.getType(), report.getCategory(),
                report.getBrand(), report.getModel(), report.getColour(), report.getCampusZone(),
                report.getEventDate(), report.getTimeFrom(), report.getTimeTo(),
                report.getSafeDescription(), report.getStatus(), report.getCreatedAt());
    }
}
