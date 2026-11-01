package com.findback.module3.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "admin_review")
public class AdminReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long adminReviewId;

    @Column(nullable = false)
    private Long claimId;

    @Column(nullable = false)
    private Long reviewerId;

    @Column(nullable = false, length = 32)
    private String decision;

    @Column(nullable = false, length = 32)
    private String previousStatus;

    @Column(nullable = false, length = 32)
    private String newStatus;

    @Column(nullable = false)
    private String reason;

    private String physicalCheckResult;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime reviewedAt = OffsetDateTime.now();

    public AdminReview() {}

    public AdminReview(Long claimId, Long reviewerId, String decision, String previousStatus,
                       String newStatus, String reason, String physicalCheckResult) {
        this.claimId = claimId;
        this.reviewerId = reviewerId;
        this.decision = decision;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.physicalCheckResult = physicalCheckResult;
    }

    public Long getAdminReviewId() { return adminReviewId; }
    public Long getClaimId() { return claimId; }
    public Long getReviewerId() { return reviewerId; }
    public String getDecision() { return decision; }
    public String getPreviousStatus() { return previousStatus; }
    public String getNewStatus() { return newStatus; }
    public String getReason() { return reason; }
    public String getPhysicalCheckResult() { return physicalCheckResult; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
}
