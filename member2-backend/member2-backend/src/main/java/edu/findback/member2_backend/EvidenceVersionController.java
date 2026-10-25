package edu.findback.member2_backend;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/member2/evidence")
public class EvidenceVersionController {

    public record VersionRequest(
            String reportId,
            String provenance,
            String serialSuffix,
            String privateMark,
            String accessoryDescription,
            String reason) {
    }

    public record VersionResponse(
            Long id,
            String reportId,
            int versionNumber,
            String provenance,
            Instant createdAt) {
    }

    private final EvidenceVersionService service;
    private final EvidenceVersionRepository repository;

    public EvidenceVersionController(
            EvidenceVersionService service,
            EvidenceVersionRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @PostMapping("/baseline")
    public VersionResponse saveBaseline(
            @RequestBody VersionRequest request) {
        var saved = service.saveBaseline(
                request.reportId(),
                parseProvenance(request.provenance()),
                request.serialSuffix(),
                request.privateMark(),
                request.accessoryDescription());

        return response(saved);
    }

    @PostMapping("/{reportId}/corrections")
    public VersionResponse addCorrection(
            @PathVariable String reportId,
            @RequestBody VersionRequest request) {
        var saved = service.appendCorrection(
                reportId,
                parseProvenance(request.provenance()),
                request.serialSuffix(),
                request.privateMark(),
                request.accessoryDescription(),
                request.reason());

        return response(saved);
    }

    @GetMapping("/{reportId}/versions")
    public List<VersionResponse> listVersions(
            @PathVariable String reportId) {
        return repository.findByReportIdOrderByVersionNumberAsc(reportId)
                .stream()
                .map(this::response)
                .toList();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalidRequest(
            IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> conflict(
            IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(exception.getMessage());
    }

    private EvidenceVersionEntity.Provenance parseProvenance(
            String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Provenance is required.");
        }

        try {
            return EvidenceVersionEntity.Provenance.valueOf(
                    value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Provenance must be CLAIMANT, FINDER or STAFF_CORRECTION.");
        }
    }

    private VersionResponse response(EvidenceVersionEntity entity) {
        return new VersionResponse(
                entity.getId(),
                entity.getReportId(),
                entity.getVersionNumber(),
                entity.getProvenance().name(),
                entity.getCreatedAt());
    }
}