package edu.findback.evidence;

import jakarta.persistence.*;
import java.time.Instant;

/** Append-only snapshots. Never returned directly from a controller. */
@Entity
@Table(name = "m2_evidence_versions", uniqueConstraints = @UniqueConstraint(columnNames = {"stream_key", "version_number"}))
public class EvidenceVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "stream_key", nullable = false, updatable = false) String streamKey;
    @Column(nullable = false, updatable = false) Long reportId;
    @Column(nullable = false, updatable = false) Long actorId;
    @Column(name = "version_number", nullable = false, updatable = false) int versionNumber;
    @Column(nullable = false, updatable = false) String provenance;
    @Column(length = 4, updatable = false) String serialSuffix;
    @Column(length = 500, updatable = false) String privateMark;
    @Column(length = 500, updatable = false) String accessoryDescription;
    @Column(length = 500, updatable = false) String reason;
    @Column(updatable = false) String requestId;
    @Column(nullable = false, updatable = false) Instant createdAt = Instant.now();
    protected EvidenceVersion() {}
    EvidenceVersion(String stream, Long report, Long actor, int number, String provenance, EvidenceData data, String reason, String requestId) {
        this.streamKey = stream; this.reportId = report; this.actorId = actor; this.versionNumber = number;
        this.provenance = provenance; this.serialSuffix = data.serialSuffix(); this.privateMark = data.privateMark();
        this.accessoryDescription = data.accessoryDescription(); this.reason = reason; this.requestId = requestId;
    }
    EvidenceData data() { return new EvidenceData(serialSuffix, privateMark, accessoryDescription); }
}
