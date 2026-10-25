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
        String claimantId,
        String foundItemId,
        String provenance,
        String serialSuffix,
        String privateMark,
        String accessoryDescription,
        String reason
    ) {}

    public record SupplementRequest(
        String claimantId,
        String foundItemId,
        String requestId
    ) {}

    public record VersionResponse(
        Long id,
        String reportId,
        int versionNumber,
        String provenance,
        Instant createdAt
    ) {}

    private final EvidenceVersionService service;

    public EvidenceVersionController(EvidenceVersionService service) {
        this.service = service;
    }

    @PostMapping("/baseline")
    public VersionResponse saveBaseline(@RequestBody VersionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }

        EvidenceVersionEntity evidence = new EvidenceVersionEntity(
            requireText(request.reportId(), "reportId"),
            1,
            parseProvenance(request.provenance()),
            request.serialSuffix(),
            request.privateMark(),
            request.accessoryDescription(),
            null
        );
        evidence.setClaimantId(requireText(request.claimantId(), "claimantId"));
        evidence.setFoundItemId(requireText(request.foundItemId(), "foundItemId"));

        return response(service.saveBaseline(evidence));
    }

    @PostMapping("/{reportId}/supplement-requests")
    public VersionResponse requestSupplement(
        @PathVariable String reportId,
        @RequestBody SupplementRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }

        String requestId = requireText(request.requestId(), "requestId");

        EvidenceVersionEntity latest = service.requestSupplement(
            reportId,
            requireText(request.claimantId(), "claimantId"),
            requireText(request.foundItemId(), "foundItemId"),
            requestId
        );

        return response(latest);
    }

    @PostMapping("/{reportId}/corrections")
    public VersionResponse addCorrection(
        @PathVariable String reportId,
        @RequestBody VersionRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }

        EvidenceVersionEntity correction = new EvidenceVersionEntity(
            reportId,
            0,
            EvidenceVersionEntity.Provenance.CLAIMANT,
            request.serialSuffix(),
            request.privateMark(),
            request.accessoryDescription(),
            requireText(request.reason(), "reason")
        );

        return response(service.appendCorrection(
            reportId,
            correction,
            request.reason()
        ));
    }

    @GetMapping("/{reportId}/versions")
    public List<VersionResponse> listVersions(@PathVariable String reportId) {
        return service.getVersions(reportId)
            .stream()
            .map(this::response)
            .toList();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> conflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(exception.getMessage());
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }

    private EvidenceVersionEntity.Provenance parseProvenance(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Provenance is required.");
        }

        try {
            return EvidenceVersionEntity.Provenance.valueOf(
                value.trim().toUpperCase()
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                "Provenance must be CLAIMANT, FINDER or STAFF_CORRECTION."
            );
        }
    }

    private VersionResponse response(EvidenceVersionEntity entity) {
        return new VersionResponse(
            entity.getId(),
            entity.getReportId(),
            entity.getVersionNumber(),
            entity.getProvenance().name(),
            entity.getCreatedAt()
        );
    }
}