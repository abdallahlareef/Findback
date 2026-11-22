package com.findback.module3.repository;

import com.findback.module3.domain.CustodyEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CustodyEventRepository extends JpaRepository<CustodyEvent, Long> {
    List<CustodyEvent> findByFoundReportIdOrderByRecordedAtDesc(Long foundReportId);
}
