package edu.findback.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateReportRequest(

        @NotNull(message = "Report type is required")
        ReportType type,

        @NotBlank(message = "Category is required")
        @Size(max = 100)
        String category,

        @Size(max = 100)
        String brand,

        @Size(max = 100)
        String model,

        @NotBlank(message = "Colour is required")
        @Size(max = 50)
        String colour,

        @NotBlank(message = "Campus zone is required")
        @Size(max = 100)
        String campusZone,

        @NotNull(message = "Date is required")
        LocalDate eventDate,

        LocalTime timeFrom,

        LocalTime timeTo,

        @Size(max = 500)
        String safeDescription,

        @Pattern(regexp = "(?i)LOW|MEDIUM|HIGH", message = "Risk level must be LOW, MEDIUM or HIGH")
        String riskLevel

) {
    /** Kept for callers that predate the risk level field. */
    public CreateReportRequest(ReportType type, String category, String brand, String model, String colour,
                               String campusZone, LocalDate eventDate, LocalTime timeFrom, LocalTime timeTo,
                               String safeDescription) {
        this(type, category, brand, model, colour, campusZone, eventDate, timeFrom, timeTo, safeDescription, null);
    }
}