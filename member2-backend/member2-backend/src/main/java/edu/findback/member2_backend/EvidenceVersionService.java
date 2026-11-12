package edu.findback.member2_backend;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EvidenceVersionService {

    private final EvidenceVersionRepository versionRepository;
    private final ClaimantItemLimitRepository limitRepository;
    private final ClaimantItemRequestRepository requestRepository;

    public EvidenceVersionService(
        EvidenceVersionRepository versionRepository,
        ClaimantItemLimitRepository limitRepository,
        ClaimantItemRequestRepository requestRepository
    ) {
        this.versionRepository = versionRepository;
        this.limitRepository = limitRepository;
        this.requestRepository = requestRepository;
    }

    @Transactional
    public EvidenceVersionEntity saveBaseline(EvidenceVersionEntity evidence) {
        if (evidence == null) {
            throw new IllegalArgumentException("Evidence is required.");
        }

        String reportId = requireText(evidence.getReportId(), "reportId");

        if (versionRepository.existsByReportId(reportId)) {
            throw new IllegalStateException("A baseline already exists for this report.");
        }

        String claimantId = requireText(evidence.getClaimantId(), "claimantId");
        String foundItemId = requireText(evidence.getFoundItemId(), "foundItemId");

        ClaimantItemLimitEntity limit = limitRepository
            .findByClaimantIdAndFoundItemId(claimantId, foundItemId)
            .orElseGet(() -> new ClaimantItemLimitEntity(claimantId, foundItemId));

        limit.recordSubmission();
        limitRepository.save(limit);

        evidence.setVersionNumber(1);
        return versionRepository.save(evidence);
    }

    @Transactional
    public EvidenceVersionEntity requestSupplement(
        String reportId,
        String claimantId,
        String foundItemId,
        String requestId
    ) {
        String safeReportId = requireText(reportId, "reportId");
        String safeClaimantId = requireText(claimantId, "claimantId");
        String safeFoundItemId = requireText(foundItemId, "foundItemId");
        String safeRequestId = requestId == null || requestId.isBlank()
            ? UUID.randomUUID().toString()
            : requireText(requestId, "requestId");

        if (requestRepository
            .findByClaimantIdAndFoundItemIdAndRequestId(
                safeClaimantId,
                safeFoundItemId,
                safeRequestId
            )
            .isPresent()) {
            throw new IllegalStateException("This supplement request was already used.");
        }

        ClaimantItemLimitEntity limit = limitRepository
            .findByClaimantIdAndFoundItemId(safeClaimantId, safeFoundItemId)
            .orElseThrow(() -> new IllegalStateException(
                "Submit the initial evidence before requesting a supplement."
            ));

        limit.requestSupplement(safeRequestId);
        limitRepository.save(limit);

        requestRepository.save(new ClaimantItemRequestEntity(
            safeClaimantId,
            safeFoundItemId,
            safeRequestId
        ));

        return versionRepository.findFirstByReportIdOrderByVersionNumberDesc(safeReportId)
            .orElseThrow(() -> new IllegalStateException("Evidence report was not found."));
    }

    @Transactional
    public EvidenceVersionEntity appendCorrection(
        String reportId,
        EvidenceVersionEntity correction,
        String reason
    ) {
        String safeReportId = requireText(reportId, "reportId");

        if (correction == null) {
            throw new IllegalArgumentException("Correction evidence is required.");
        }

        String safeReason = requireText(reason, "reason");
        EvidenceVersionEntity latest = versionRepository
            .findFirstByReportIdOrderByVersionNumberDesc(safeReportId)
            .orElseThrow(() -> new IllegalStateException(
                "Create a baseline before adding a correction."
            ));

        String claimantId = requireText(latest.getClaimantId(), "claimantId");
        String foundItemId = requireText(latest.getFoundItemId(), "foundItemId");

        ClaimantItemLimitEntity limit = limitRepository
            .findByClaimantIdAndFoundItemId(claimantId, foundItemId)
            .orElseThrow(() -> new IllegalStateException("Submission limit record was not found."));

        if (!limit.isSupplementPending()) {
            throw new IllegalStateException("Request a supplement before submitting one.");
        }

        correction.setReportId(safeReportId);
        correction.setClaimantId(claimantId);
        correction.setFoundItemId(foundItemId);
        correction.setVersionNumber(latest.getVersionNumber() + 1);
        correction.setCorrectionReason(safeReason);
        correction.setProvenance(EvidenceVersionEntity.Provenance.CLAIMANT);

        limit.recordSubmission();
        limitRepository.save(limit);

        return versionRepository.save(correction);
    }

    @Transactional(readOnly = true)
    public List<EvidenceVersionEntity> getVersions(String reportId) {
        return versionRepository.findByReportIdOrderByVersionNumberAsc(
            requireText(reportId, "reportId")
        );
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }
}