package edu.findback.evidence;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="m2_supplement_requests", uniqueConstraints=@UniqueConstraint(columnNames={"pair_id", "request_key"}))
public class SupplementRequest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(name="pair_id", nullable=false, updatable=false) Long pairId;
    @Column(name="request_key", nullable=false, updatable=false) String requestKey;
    @Column(nullable=false, updatable=false) Long actorId;
    @Column(nullable=false, length=500, updatable=false) String reason;
    @Column(nullable=false, updatable=false) Instant createdAt=Instant.now();
    protected SupplementRequest() {}
    SupplementRequest(Long pair, String key, Long actor, String reason) { pairId=pair; requestKey=key; actorId=actor; this.reason=reason; }
}
