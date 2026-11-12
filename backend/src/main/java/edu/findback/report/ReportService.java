package edu.findback.report;

import edu.findback.user.User;
import edu.findback.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    public ReportService(
            ReportRepository reportRepository,
            UserRepository userRepository
    ) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ReportResponse create(
            String authenticatedEmail,
            CreateReportRequest request
    ) {

        User user = userRepository
                .findByEmail(authenticatedEmail)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Authenticated user not found"
                        )
                );

        Report report = new Report();

        report.setAuthor(user);
        report.setType(request.type());

        report.setCategory(request.category().trim());

        report.setBrand(clean(request.brand()));
        report.setModel(clean(request.model()));

        report.setColour(request.colour().trim());
        report.setCampusZone(request.campusZone().trim());

        report.setEventDate(request.eventDate());
        report.setTimeFrom(request.timeFrom());
        report.setTimeTo(request.timeTo());

        report.setSafeDescription(
                clean(request.safeDescription())
        );

        report.setStatus(ReportStatus.ACTIVE);

        return ReportResponse.from(
                reportRepository.save(report)
        );
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> getMyReports(
            String authenticatedEmail
    ) {

        User user = userRepository
                .findByEmail(authenticatedEmail)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Authenticated user not found"
                        )
                );

        return reportRepository
                .findByAuthorIdOrderByCreatedAtDesc(
                        user.getId()
                )
                .stream()
                .map(ReportResponse::from)
                .toList();
    }

    private String clean(String value) {

        if (value == null) {
            return null;
        }

        String cleaned = value.trim();

        return cleaned.isEmpty()
                ? null
                : cleaned;
    }
}