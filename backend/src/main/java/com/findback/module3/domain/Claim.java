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
    }
    public RiskTier getRiskTier() { return riskTier; }
    public void setRiskTier(RiskTier riskTier) { this.riskTier = riskTier; }
    public String getEscalationReason() { return escalationReason; }
    public void setEscalationReason(String escalationReason) { this.escalationReason = escalationReason; }
    public boolean isInternalReviewFlag() { return internalReviewFlag; }
    public void setInternalReviewFlag(boolean internalReviewFlag) { this.internalReviewFlag = internalReviewFlag; }
    public String getFlagReason() { return flagReason; }
    public void setFlagReason(String flagReason) { this.flagReason = flagReason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
