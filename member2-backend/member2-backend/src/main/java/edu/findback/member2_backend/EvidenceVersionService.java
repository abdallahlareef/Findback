package edu.findback.member2_backend;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvidenceVersionService {

    private final EvidenceVersionRepository repository;

    public EvidenceVersionService(EvidenceVersionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public EvidenceVersionEntity saveBaseline(
            String reportId,
            EvidenceVersionEntity.Provenance provenance,
            String serialSuffix,
            String privateMark,
            String accessoryDescription) {

        validateReportId(reportId);
        validateProvenance(provenance);

        if (provenance == EvidenceVersionEntity.Provenance.STAFF_CORRECTION) {
            throw new IllegalArgumentException(
                    "The baseline must come from the claimant or finder.");
        }

        if (repository.existsByReportId(reportId)) {
            throw new IllegalStateException(
                    "This report already has a baseline.");
        }

        return repository.save(new EvidenceVersionEntity(
                reportId,
                1,
                provenance,
                normalizeSerial(serialSuffix),
                cleanText(privateMark),
                cleanText(accessoryDescription),
                null));
    }

    @Transactional
    public EvidenceVersionEntity appendCorrection(
            String reportId,
            EvidenceVersionEntity.Provenance provenance,
            String serialSuffix,
            String privateMark,
            String accessoryDescription,
            String reason) {

        validateReportId(reportId);
        validateProvenance(provenance);

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "A correction or supplement needs a reason.");
        }
        if (reason.length() > 500) {
            throw new IllegalArgumentException(
                    "The reason must be 500 characters or fewer.");
        }

        EvidenceVersionEntity previous =
                repository.findFirstByReportIdOrderByVersionNumberDesc(reportId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Save a baseline before adding a version."));

        return repository.save(new EvidenceVersionEntity(
                reportId,
                previous.getVersionNumber() + 1,
                provenance,
                normalizeSerial(serialSuffix),
                cleanText(privateMark),
                cleanText(accessoryDescription),
                reason.trim()));
    }

    private void validateReportId(String reportId) {
        if (reportId == null || reportId.isBlank()
                || reportId.length() > 100) {
            throw new IllegalArgumentException(
                    "A report ID of 1 to 100 characters is required.");
        }
    }

    private void validateProvenance(
            EvidenceVersionEntity.Provenance provenance) {
        if (provenance == null) {
            throw new IllegalArgumentException("Provenance is required.");
        }
    }

    private String normalizeSerial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9]{4}")) {
            throw new IllegalArgumentException(
                    "Serial suffix must contain exactly 4 letters or digits.");
        }

        return normalized;
    }

    private String cleanText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > 500) {
            throw new IllegalArgumentException(
                    "Evidence descriptions must be 500 characters or fewer.");
        }
        return value.trim();
    }
}