package com.findback.module3.repository;

import com.findback.module3.domain.AdminReview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AdminReviewRepository extends JpaRepository<AdminReview, Long> {
    List<AdminReview> findByClaimIdOrderByReviewedAtDesc(Long claimId);
}
