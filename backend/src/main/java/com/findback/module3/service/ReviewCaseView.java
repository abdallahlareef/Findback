package com.findback.module3.service;

import com.findback.module3.domain.ClaimStatus;
import com.findback.module3.domain.CustodyStatus;
import com.findback.module3.domain.RiskTier;

import java.time.OffsetDateTime;

/**
 * One row of the staff review queue. Contains DOVE counts only, never the private evidence values,
 * and is only built for reviewers who have no involvement in the case (T09).
 */
public record ReviewCaseView(
        Long claimId,
        Long claimantId,
        String claimantName,
        Long lostReportId,
        Long foundReportId,
        String itemTitle,
        String category,
        String campusZone,
        ClaimStatus status,
        RiskTier riskTier,
        String escalationReason,
        boolean internalReviewFlag,
        String flagReason,
        CustodyStatus custodyStatus,
        String storageReference,
        boolean disputeBlocked,
        Long reservedByClaimId,
        DoveView doveSummary,
        long competingActiveClaimsCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public record DoveView(String strengthLabel, int matchCount, int unknownCount, int contradictionCount,
                           boolean hasCriticalConflict, int submissionsUsed, boolean pendingSupplement) {}
}
