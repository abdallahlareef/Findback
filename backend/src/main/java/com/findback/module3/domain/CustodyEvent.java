package com.findback.module3.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "custody_event")
public class CustodyEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long custodyEventId;

    @Column(nullable = false)
    private Long foundReportId;

    @Column(nullable = false)
    private Long staffId;

    @Column(nullable = false, length = 32)
    private String eventType;

    @Column(nullable = false, length = 100)
    private String storageReference;

    @Column(nullable = false)
    private String conditionNotes;

    private boolean physicalCheckPassed = true;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime recordedAt = OffsetDateTime.now();

    public CustodyEvent() {}

    public CustodyEvent(Long foundReportId, Long staffId, String eventType,
                        String storageReference, String conditionNotes, boolean physicalCheckPassed) {
        this.foundReportId = foundReportId;
        this.staffId = staffId;
        this.eventType = eventType;
        this.storageReference = storageReference;
        this.conditionNotes = conditionNotes;
        this.physicalCheckPassed = physicalCheckPassed;
    }

    public Long getCustodyEventId() { return custodyEventId; }
    public Long getFoundReportId() { return foundReportId; }
    public Long getStaffId() { return staffId; }
    public String getEventType() { return eventType; }
    public String getStorageReference() { return storageReference; }
    public String getConditionNotes() { return conditionNotes; }
    public boolean isPhysicalCheckPassed() { return physicalCheckPassed; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
}
