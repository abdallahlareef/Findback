package edu.findback.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ReportRepository
        extends JpaRepository<Report, Long>, JpaSpecificationExecutor<Report> {

    List<Report> findByAuthorIdOrderByCreatedAtDesc(Long authorId);

    List<Report> findByTypeAndStatus(
            ReportType type,
            ReportStatus status
    );
}
