package edu.findback.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository
        extends JpaRepository<Report, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from Report r where r.id = :id")
    java.util.Optional<Report> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    List<Report> findByAuthorIdOrderByCreatedAtDesc(Long authorId);

    List<Report> findByTypeAndStatus(
            ReportType type,
            ReportStatus status
    );
}
