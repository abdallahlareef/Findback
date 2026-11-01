package com.findback.module3.repository;

import com.findback.module3.domain.Claim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ClaimRepository extends JpaRepository<Claim, Long> {

    // Custom query added to fix the "No property 'existsActiveClaimForPair' found" startup crash
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Claim c WHERE c.lostReportId = ?1 AND c.foundReportId = ?2")
    boolean existsActiveClaimForPair(Long lostReportId, Long foundReportId);

    // Standard derived query used by your JUnit Mockito tests
    boolean existsByLostReportIdAndClaimantId(Long lostReportId, Long claimantId);
}