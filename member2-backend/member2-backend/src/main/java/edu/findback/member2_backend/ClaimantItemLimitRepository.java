package edu.findback.member2_backend;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClaimantItemLimitRepository
        extends JpaRepository<ClaimantItemLimitEntity, Long> {

    Optional<ClaimantItemLimitEntity> findByClaimantIdAndFoundItemId(
        String claimantId,
        String foundItemId
    );
}