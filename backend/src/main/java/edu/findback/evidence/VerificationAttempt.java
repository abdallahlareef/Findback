package edu.findback.evidence;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="m2_verification_attempts")
public class VerificationAttempt {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false, updatable=false) Long claimId;
    @Column(nullable=false, updatable=false) Long claimantVersionId;
    @Column(updatable=false) Long finderVersionId;
    @Column(nullable=false, updatable=false) String serialOutcome;
    @Column(nullable=false, updatable=false) String markOutcome;
    @Column(nullable=false, updatable=false) String accessoryOutcome;
    @Column(nullable=false, updatable=false) int distinctMatches;
    @Column(nullable=false, updatable=false) boolean criticalConflict;
    @Column(nullable=false, updatable=false) String strength;
    @Column(updatable=false) Long reviewerId;
    @Column(length=500, updatable=false) String reason;
    @Column(nullable=false, updatable=false) Instant createdAt=Instant.now();
    protected VerificationAttempt() {}
}
