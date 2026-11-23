package com.findback.module3.service;

import com.findback.module3.domain.ClaimStatus;
import com.findback.module3.domain.RiskTier;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Staff-only timeline of one claim (FR-12): decisions with their reasons, custody events for the
 * item, and claimant actions. Never contains private evidence values.
 */
public record CaseHistoryView(
        Long claimId,
        Long claimantId,
        String claimantName,
        Long lostReportId,
        Long foundReportId,
        String itemTitle,
        ClaimStatus status,
        RiskTier riskTier,
        List<Event> events) {

    /**
     * @param source CLAIM (claimant actions), DECISION (staff decisions) or CUSTODY (item custody log)
     */
    public record Event(OffsetDateTime at, String source, String type, String actorName, String summary, String details) {}

    /** One row of the case-history search list. */
    public record CaseSummary(Long claimId, String claimantName, Long foundReportId, String itemTitle,
                              ClaimStatus status, RiskTier riskTier, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
}
