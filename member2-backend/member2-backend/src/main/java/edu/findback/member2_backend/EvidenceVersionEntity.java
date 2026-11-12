package edu.findback.member2_backend;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "evidence_version",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_evidence_report_version",
        columnNames = {"report_id", "version_number"}
    )
)
public class EvidenceVersionEntity {

    public enum Provenance {
        CLAIMANT,
        FINDER,
        STAFF_CORRECTION
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false, length = 100)
    private String reportId;

    @Column(name = "claimant_id", nullable = false, length = 120)
    private String claimantId;

    @Column(name = "found_item_id", nullable = false, length = 120)
    private String foundItemId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Provenance provenance;

    @Column(name = "serial_suffix", length = 4)
    private String serialSuffix;

    @Column(name = "private_mark", length = 500)
    private String privateMark;

    @Column(name = "accessory_description", length = 500)
    private String accessoryDescription;

    @Column(name = "correction_reason", length = 500)
    private String correctionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EvidenceVersionEntity() {
        // Required by JPA.
    }

    public EvidenceVersionEntity(
        String reportId,
        int versionNumber,
        Provenance provenance,
        String serialSuffix,
        String privateMark,
        String accessoryDescription,
        String correctionReason
    ) {
        this.reportId = reportId;
        this.versionNumber = versionNumber;
        this.provenance = provenance;
        this.serialSuffix = serialSuffix;
        this.privateMark = privateMark;
        this.accessoryDescription = accessoryDescription;
        this.correctionReason = correctionReason;
    }

    @PrePersist
    void setCreatedAtBeforeSaving() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getClaimantId() {
        return claimantId;
    }

    public void setClaimantId(String claimantId) {
        this.claimantId = claimantId;
    }

    public String getFoundItemId() {
        return foundItemId;
    }

    public void setFoundItemId(String foundItemId) {
        this.foundItemId = foundItemId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public Provenance getProvenance() {
        return provenance;
    }

    public void setProvenance(Provenance provenance) {
        this.provenance = provenance;
    }

    public String getSerialSuffix() {
        return serialSuffix;
    }

    public void setSerialSuffix(String serialSuffix) {
        this.serialSuffix = serialSuffix;
    }

    public String getPrivateMark() {
        return privateMark;
    }

    public void setPrivateMark(String privateMark) {
        this.privateMark = privateMark;
    }

    public String getAccessoryDescription() {
        return accessoryDescription;
    }

    public void setAccessoryDescription(String accessoryDescription) {
        this.accessoryDescription = accessoryDescription;
    }

    public String getCorrectionReason() {
        return correctionReason;
    }

    public void setCorrectionReason(String correctionReason) {
        this.correctionReason = correctionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}