package com.findback.module3.repository;

import com.findback.module3.domain.ItemCustodyState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ItemCustodyStateRepository extends JpaRepository<ItemCustodyState, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ItemCustodyState s WHERE s.foundReportId = :foundReportId")
    Optional<ItemCustodyState> findByIdForUpdate(@Param("foundReportId") Long foundReportId);
}
