package edu.findback.member2_backend;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClaimantItemRequestRepository
        extends JpaRepository<ClaimantItemRequestEntity, Long> {

    Optional<ClaimantItemRequestEntity> findByClaimantIdAndFoundItemIdAndRequestId(
        String claimantId,
        String foundItemId,
        String requestId
    );
}