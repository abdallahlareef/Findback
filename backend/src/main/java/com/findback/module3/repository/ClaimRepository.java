package com.findback.module3.repository;

import com.findback.module3.domain.Claim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClaimRepository extends JpaRepository<Claim, Long> {

    /** Active = not REJECTED, WITHDRAWN or COMPLETED (FR-06). Callers must hold the found-report lock. */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Claim c WHERE c.claimantId = ?1 AND c.foundReportId = ?2 AND c.status NOT IN (com.findback.module3.domain.ClaimStatus.REJECTED, com.findback.module3.domain.ClaimStatus.WITHDRAWN, com.findback.module3.domain.ClaimStatus.COMPLETED)")
    boolean existsActiveClaimForPair(Long claimantId, Long foundReportId);

    List<Claim> findByClaimantIdAndFoundReportId(Long claimantId, Long foundReportId);

    List<Claim> findByFoundReportId(Long foundReportId);

    List<Claim> findByClaimantIdOrderByCreatedAtDesc(Long claimantId);

    boolean existsByLostReportIdAndClaimantId(Long lostReportId, Long claimantId);
}
