package edu.findback.report;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import edu.findback.discovery.CandidateMatchResponse;
import edu.findback.discovery.DiscoveryService;

import java.util.List;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

        private final ReportService reportService;
        private final DiscoveryService discoveryService;

        public ReportController(
                        ReportService reportService,
                        DiscoveryService discoveryService) {
                this.reportService = reportService;
                this.discoveryService = discoveryService;
        }

        @PostMapping
        public ResponseEntity<ReportResponse> createReport(
                        Authentication authentication,
                        @Valid @RequestBody CreateReportRequest request) {

                ReportResponse response = reportService.create(
                                authentication.getName(),
                                request);

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(response);
        }

        @GetMapping("/mine")
        public List<ReportResponse> getMyReports(
                        Authentication authentication) {

                return reportService.getMyReports(
                                authentication.getName());
        }

        @GetMapping("/{lostReportId}/candidates")
        public List<CandidateMatchResponse> getCandidates(
                        @PathVariable Long lostReportId,
                        Authentication authentication) {

                return discoveryService
                                .findCandidates(lostReportId, authentication.getName());
        }

        @GetMapping("/search")
        public List<SafeReportSummaryResponse> search(
                        @RequestParam(required = false) ReportType type,
                        @RequestParam(required = false) String category,
                        @RequestParam(required = false) String campusZone,
                        @RequestParam(required = false) LocalDate eventDate,
                        @RequestParam(required = false) ReportStatus status) {
                return reportService.search(type, category, campusZone, eventDate, status);
        }
}
