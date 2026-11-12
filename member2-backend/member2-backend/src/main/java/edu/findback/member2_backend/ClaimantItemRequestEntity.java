package edu.findback.member2_backend;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
    name = "claimant_item_requests",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_claimant_item_request_id",
        columnNames = {"claimant_id", "found_item_id", "request_id"}
    )
)
public class ClaimantItemRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claimant_id", nullable = false, length = 120)
    private String claimantId;

    @Column(name = "found_item_id", nullable = false, length = 120)
    private String foundItemId;

    @Column(name = "request_id", nullable = false, length = 120)
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ClaimantItemRequestEntity() {
        // Required by JPA.
    }

    public ClaimantItemRequestEntity(
        String claimantId,
        String foundItemId,
        String requestId
    ) {
        this.claimantId = claimantId;
        this.foundItemId = foundItemId;
        this.requestId = requestId;
        this.createdAt = Instant.now();
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

    public String getRequestId() {
        return requestId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}