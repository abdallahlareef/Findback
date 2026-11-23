package com.findback.module3.service;

import com.findback.module3.domain.ClaimStatus;
import com.findback.module3.domain.CustodyStatus;
import com.findback.module3.domain.RiskTier;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Staff custody desk (FR-11): every found item and where it physically is. No private evidence. */
public final class CustodyDeskView {
    private CustodyDeskView() {}

    /** One found item on the desk list. REPORTED means it has not been received into custody yet. */
    public record Item(Long foundReportId, String category, String colour, String campusZone, LocalDate eventDate,
                       String reportStatus, String riskLevel, String finderName,
                       CustodyStatus custodyStatus, String storageReference, String receivedByName,
                       OffsetDateTime custodyUpdatedAt, boolean disputeBlocked,
                       String disputeRaisedByName, String disputeReason, Long reservedByClaimId,
                       long activeClaimsCount, long totalClaimsCount) {}

    /** Custody log and claims for one item. */
    public record ItemHistory(Long foundReportId, String category, String campusZone, String finderName,
                              CustodyStatus custodyStatus, String storageReference, boolean disputeBlocked,
                              Long reservedByClaimId, List<CaseHistoryView.Event> events, List<ClaimRef> claims) {}

    public record ClaimRef(Long claimId, String claimantName, ClaimStatus status, RiskTier riskTier, OffsetDateTime createdAt) {}
}
