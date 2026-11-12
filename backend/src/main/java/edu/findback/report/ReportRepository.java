package edu.findback.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository
        extends JpaRepository<Report, Long> {

    List<Report> findByAuthorIdOrderByCreatedAtDesc(Long authorId);

    List<Report> findByTypeAndStatus(
            ReportType type,
            ReportStatus status
    );
}