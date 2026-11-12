package edu.findback.member2_backend;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceVersionRepository
        extends JpaRepository<EvidenceVersionEntity, Long> {

    boolean existsByReportId(String reportId);

    List<EvidenceVersionEntity> findByReportIdOrderByVersionNumberAsc(
            String reportId);

    Optional<EvidenceVersionEntity> findFirstByReportIdOrderByVersionNumberDesc(
            String reportId);
}