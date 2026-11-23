package com.findback.module3.service;

import com.findback.module3.domain.ClaimStatus;
import com.findback.module3.domain.CustodyStatus;
import com.findback.module3.domain.RiskTier;

import java.time.OffsetDateTime;
import java.util.List;

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
        String disputeRaisedByName,
        String disputeReason,
        OffsetDateTime disputeRaisedAt,
        /** False for the staff member who raised the dispute: a different person must resolve it. */
        boolean canResolveDispute,
        DoveView doveSummary,
        long competingActiveClaimsCount,
        boolean reconsiderationPending,
        String reconsiderationReason,
        OffsetDateTime reconsiderationRequestedAt,
        Long firstApproverId,
        String firstApproverName,
        OffsetDateTime firstApprovedAt,
        /** Why the viewing reviewer could not approve right now; empty when approval is possible (FR-10). */
        List<String> approvalBlockers,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public record DoveView(String strengthLabel, int matchCount, int unknownCount, int contradictionCount,
                           boolean hasCriticalConflict, int submissionsUsed, boolean pendingSupplement,
                           boolean identifierMatched) {}
}
