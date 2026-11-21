package edu.findback.evidence;
import jakarta.persistence.*;
@Entity
@Table(name = "m2_evidence_pairs", uniqueConstraints = @UniqueConstraint(columnNames = {"claimant_id", "found_report_id"}))
public class EvidencePair {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "claimant_id", nullable = false) Long claimantId;
    @Column(name = "found_report_id", nullable = false) Long foundReportId;
    @Column(nullable = false) Long lostReportId;
    @Column(nullable = false) int submissions;
    @Column(nullable = false) int requests;
    String pendingRequest;
    @Version Long rowVersion;
    protected EvidencePair() {}
    EvidencePair(Long claimant, Long found, Long lost) { claimantId=claimant; foundReportId=found; lostReportId=lost; }
    String stream() { return "PAIR:" + id; }
}
