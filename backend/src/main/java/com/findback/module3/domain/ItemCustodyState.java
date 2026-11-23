package com.findback.module3.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "item_custody_state")
public class ItemCustodyState {
    @Id
    private Long foundReportId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CustodyStatus custodyStatus = CustodyStatus.REPORTED;

    private String storageReference;
    private Long receivingOfficerId;
    private Long reservedByClaimId;
    private boolean disputeBlocked = false;

    /** Who raised the current dispute (put an approved claim on hold), when and why. Cleared on resolution. */
    private Long disputeRaisedById;
    private OffsetDateTime disputeRaisedAt;
    @Column(length = 255)
    private String disputeReason;

    @Column(nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    private Long version;

    public Long getFoundReportId() { return foundReportId; }
    public void setFoundReportId(Long foundReportId) { this.foundReportId = foundReportId; }
    public CustodyStatus getCustodyStatus() { return custodyStatus; }
    public void setCustodyStatus(CustodyStatus custodyStatus) {
        this.custodyStatus = custodyStatus;
        this.updatedAt = OffsetDateTime.now();
    }
    public String getStorageReference() { return storageReference; }
    public void setStorageReference(String storageReference) { this.storageReference = storageReference; }
    public Long getReceivingOfficerId() { return receivingOfficerId; }
    public void setReceivingOfficerId(Long receivingOfficerId) { this.receivingOfficerId = receivingOfficerId; }
    public Long getReservedByClaimId() { return reservedByClaimId; }
    public void setReservedByClaimId(Long reservedByClaimId) { this.reservedByClaimId = reservedByClaimId; }
    public boolean isDisputeBlocked() { return disputeBlocked; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public Long getDisputeRaisedById() { return disputeRaisedById; }
    public OffsetDateTime getDisputeRaisedAt() { return disputeRaisedAt; }
    public String getDisputeReason() { return disputeReason; }

    /** Blocks the item from being reserved until a different staff member resolves the dispute. */
    public void raiseDispute(Long staffId, String reason) {
        this.disputeBlocked = true;
        this.disputeRaisedById = staffId;
        this.disputeRaisedAt = OffsetDateTime.now();
        this.disputeReason = reason;
        this.updatedAt = this.disputeRaisedAt;
    }

    public void clearDispute() {
        this.disputeBlocked = false;
        this.disputeRaisedById = null;
        this.disputeRaisedAt = null;
        this.disputeReason = null;
        this.updatedAt = OffsetDateTime.now();
    }
    public void setDisputeBlocked(boolean disputeBlocked) { this.disputeBlocked = disputeBlocked; }
}
