package com.findback.module3.controller;

import com.findback.module3.domain.*;
import com.findback.module3.service.ClaimReviewService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/claims")
public class ClaimCustodyController {

    private final ClaimReviewService service;

    public ClaimCustodyController(ClaimReviewService service) {
        this.service = service;
    }

    public record CreateClaimRequest(Long lostReportId, Long foundReportId) {}
    public record CustodyIntakeRequest(String storageReference, String conditionNotes) {}
    public record StaffDecisionRequest(String action, String reason, String physicalCheckNotes) {}
    public record RiskEscalationRequest(RiskTier riskTier, String reason) {}

    @GetMapping("/staff/claims")
    public List<Claim> listAllClaims() {
        return service.getAllClaims();
    }

    // 1. FIXED SUBMIT METHOD (Maps exactly to /api/v1/claims)
    @PostMapping
    public Claim submitClaim(@RequestHeader(value = "X-User-Id", defaultValue = "2012") Long currentUserId,
                             @RequestBody CreateClaimRequest req) {
        return service.createClaim(currentUserId, req.lostReportId(), req.foundReportId());
    }

    @PostMapping("/staff/custody/{foundReportId}/intake")
    public ItemCustodyState recordCustody(@RequestHeader(value = "X-User-Id", defaultValue = "9001") Long staffId,
                                          @PathVariable Long foundReportId,
                                          @RequestBody CustodyIntakeRequest req) {
        return service.recordCustodyIntake(staffId, foundReportId, req.storageReference(), req.conditionNotes());
    }

    @PostMapping("/staff/claims/{claimId}/decision")
    public Claim decideClaim(@RequestHeader(value = "X-User-Id", defaultValue = "9001") Long staffId,
                             @PathVariable Long claimId,
                             @RequestBody StaffDecisionRequest req) {
        return service.recordStaffDecision(staffId, claimId, req.action(), req.reason(), req.physicalCheckNotes());
    }

    @PatchMapping("/staff/claims/{claimId}/risk")
    public Claim escalateRisk(@RequestHeader(value = "X-User-Id", defaultValue = "9001") Long staffId,
                              @PathVariable Long claimId,
                              @RequestBody RiskEscalationRequest req) {
        return service.escalateRiskTier(staffId, claimId, req.riskTier(), req.reason());
    }

    // 2. FR-18 WITHDRAWAL METHOD (Maps exactly to DELETE /api/v1/claims/{id})
    @DeleteMapping("/{id}")
    public ResponseEntity<?> withdrawClaim(
        @PathVariable("id") Long id, 
        @RequestHeader("X-User-Id") Long userId) {
        try {
            service.withdrawClaim(id, userId); 
            return ResponseEntity.ok().body("Claim withdrawn successfully (FR-18).");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}