package com.findback.module3.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "claim")
public class Claim {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long claimId;

    @Column(nullable = false)
    private Long claimantId;

    @Column(nullable = false)
    private Long lostReportId;

    @Column(nullable = false)
    private Long foundReportId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ClaimStatus status = ClaimStatus.UNDER_REVIEW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RiskTier riskTier = RiskTier.LOW;

    private String escalationReason;
    private boolean internalReviewFlag = false;
    private String flagReason;

    /** Claimant's one-time request to reopen a REJECTED claim, with the new proof they describe. */
    @Column(length = 1000)
    private String reconsiderationReason;
    private OffsetDateTime reconsiderationRequestedAt;
    /** Null while pending; ACCEPTED (claim reopened) or DECLINED (decision stands). */
    @Column(length = 16)
    private String reconsiderationOutcome;

    /** HIGH risk two-person rule: the first of two different approving staff members (FR-10). */
    private Long firstApproverId;
    private OffsetDateTime firstApprovedAt;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    private Long version;

    public Long getClaimId() { return claimId; }
    public Long getClaimantId() { return claimantId; }
    public void setClaimantId(Long claimantId) { this.claimantId = claimantId; }
    public Long getLostReportId() { return lostReportId; }
    public void setLostReportId(Long lostReportId) { this.lostReportId = lostReportId; }
    public Long getFoundReportId() { return foundReportId; }
    public void setFoundReportId(Long foundReportId) { this.foundReportId = foundReportId; }
    public ClaimStatus getStatus() { return status; }
    public void setStatus(ClaimStatus status) {
        this.status = status;
        this.updatedAt = OffsetDateTime.now();
        // A first approval only counts while the claim stays under review; any hold, rejection,
        // withdrawal or final approval (including evidence-triggered holds) starts the two-person check over.
        if (status != ClaimStatus.UNDER_REVIEW) {
            this.firstApproverId = null;
            this.firstApprovedAt = null;
        }
    }
    public Long getFirstApproverId() { return firstApproverId; }
    public OffsetDateTime getFirstApprovedAt() { return firstApprovedAt; }
    public boolean isAwaitingSecondApproval() { return firstApproverId != null; }
    public void recordFirstApproval(Long staffId) {
        this.firstApproverId = staffId;
        this.firstApprovedAt = OffsetDateTime.now();
        this.updatedAt = this.firstApprovedAt;
    }
    public RiskTier getRiskTier() { return riskTier; }
    public void setRiskTier(RiskTier riskTier) { this.riskTier = riskTier; }
    public String getEscalationReason() { return escalationReason; }
    public void setEscalationReason(String escalationReason) { this.escalationReason = escalationReason; }
    public boolean isInternalReviewFlag() { return internalReviewFlag; }
    public void setInternalReviewFlag(boolean internalReviewFlag) { this.internalReviewFlag = internalReviewFlag; }
    public String getFlagReason() { return flagReason; }
    public void setFlagReason(String flagReason) { this.flagReason = flagReason; }
    public String getReconsiderationReason() { return reconsiderationReason; }
    public OffsetDateTime getReconsiderationRequestedAt() { return reconsiderationRequestedAt; }
    public String getReconsiderationOutcome() { return reconsiderationOutcome; }
    public boolean isReconsiderationPending() { return reconsiderationRequestedAt != null && reconsiderationOutcome == null; }
    /** NONE, PENDING, ACCEPTED or DECLINED. */
    public String getReconsiderationStatus() {
        if (reconsiderationRequestedAt == null) return "NONE";
        return reconsiderationOutcome == null ? "PENDING" : reconsiderationOutcome;
    }
    public void requestReconsideration(String reason) {
        this.reconsiderationReason = reason;
        this.reconsiderationRequestedAt = OffsetDateTime.now();
        this.reconsiderationOutcome = null;
        this.updatedAt = OffsetDateTime.now();
    }
    public void resolveReconsideration(String outcome) {
        this.reconsiderationOutcome = outcome;
        this.updatedAt = OffsetDateTime.now();
    }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
