package edu.findback.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ReportResponse(

        Long id,
        Long authorId,
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

        LocalDateTime createdAt,
        LocalDateTime updatedAt

) {

    public static ReportResponse from(Report report) {

        return new ReportResponse(
                report.getId(),
                report.getAuthor().getId(),
                report.getType(),

                report.getCategory(),
                report.getBrand(),
                report.getModel(),
                report.getColour(),

                report.getCampusZone(),

                report.getEventDate(),
                report.getTimeFrom(),
                report.getTimeTo(),

                report.getSafeDescription(),
                report.getStatus(),

                report.getCreatedAt(),
                report.getUpdatedAt()
        );
    }
}
