package edu.findback.member2_backend;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(
    name = "claimant_item_limits",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_claimant_item_pair",
        columnNames = {"claimant_id", "found_item_id"}
    )
)
public class ClaimantItemLimitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claimant_id", nullable = false, length = 120)
    private String claimantId;

    @Column(name = "found_item_id", nullable = false, length = 120)
    private String foundItemId;

    @Column(name = "submission_count", nullable = false)
    private int submissionCount;

    @Column(name = "supplement_request_count", nullable = false)
    private int supplementRequestCount;

    @Column(name = "supplement_pending", nullable = false)
    private boolean supplementPending;

    @Column(name = "pending_request_id", length = 120)
    private String pendingRequestId;

    @Version
    private long rowVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClaimantItemLimitEntity() {
        // Required by JPA.
    }

    public ClaimantItemLimitEntity(String claimantId, String foundItemId) {
        this.claimantId = claimantId;
        this.foundItemId = foundItemId;
        this.submissionCount = 0;
        this.supplementRequestCount = 0;
        this.supplementPending = false;
        this.updatedAt = Instant.now();
    }

    public void requestSupplement(String requestId) {
        if (supplementPending) {
            throw new IllegalStateException("A supplement request is already pending.");
        }
        if (supplementRequestCount >= 2) {
            throw new IllegalStateException("The supplement request limit has been reached.");
        }

        supplementRequestCount++;
        supplementPending = true;
        pendingRequestId = requestId;
        updatedAt = Instant.now();
    }

    public void recordSubmission() {
        if (submissionCount >= 3) {
            throw new IllegalStateException("The submission limit has been reached.");
        }

        submissionCount++;
        supplementPending = false;
        pendingRequestId = null;
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getClaimantId() {
        return claimantId;
    }

    public String getFoundItemId() {
        return foundItemId;
    }

    public int getSubmissionCount() {
        return submissionCount;
    }

    public int getSupplementRequestCount() {
        return supplementRequestCount;
    }

    public boolean isSupplementPending() {
        return supplementPending;
    }

    public String getPendingRequestId() {
        return pendingRequestId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}