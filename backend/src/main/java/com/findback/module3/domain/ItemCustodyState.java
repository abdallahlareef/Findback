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
    public void setDisputeBlocked(boolean disputeBlocked) { this.disputeBlocked = disputeBlocked; }
}
