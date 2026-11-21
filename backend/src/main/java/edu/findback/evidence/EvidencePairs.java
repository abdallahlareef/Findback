package edu.findback.evidence;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EvidencePairs extends JpaRepository<EvidencePair, Long> {
    Optional<EvidencePair> findByClaimantIdAndFoundReportId(Long claimant, Long found);
}
