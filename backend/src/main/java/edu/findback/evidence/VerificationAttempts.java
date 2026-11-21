package edu.findback.evidence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface VerificationAttempts extends JpaRepository<VerificationAttempt,Long> {
    Optional<VerificationAttempt> findFirstByClaimIdOrderByIdDesc(Long claim);
    List<VerificationAttempt> findByClaimIdOrderByIdAsc(Long claim);
}
