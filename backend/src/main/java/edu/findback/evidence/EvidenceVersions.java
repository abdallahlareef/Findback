package edu.findback.evidence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EvidenceVersions extends JpaRepository<EvidenceVersion, Long> {
    List<EvidenceVersion> findByStreamKeyOrderByVersionNumberAsc(String key);
    Optional<EvidenceVersion> findFirstByStreamKeyOrderByVersionNumberDesc(String key);
    boolean existsByStreamKey(String key);
}
