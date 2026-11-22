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
    private final edu.findback.evidence.EvidenceAccess access;

    public ClaimCustodyController(ClaimReviewService service, edu.findback.evidence.EvidenceAccess access) {
        this.service = service;
        this.access = access;
    }
    public record SafeClaim(Long claimId, Long lostReportId, Long foundReportId, ClaimStatus status) {
        static SafeClaim from(Claim c) { return new SafeClaim(c.getClaimId(),c.getLostReportId(),c.getFoundReportId(),c.getStatus()); }
    }

    public record CreateClaimRequest(Long lostReportId, Long foundReportId) {}
    public record CustodyIntakeRequest(String storageReference, String conditionNotes) {}
    public record StaffDecisionRequest(String action, String reason, String physicalCheckNotes) {}
    public record RiskEscalationRequest(RiskTier riskTier, String reason) {}

    @GetMapping("/staff/claims")
    public List<SafeClaim> listAllClaims() {
        access.staff();
        return service.getAllClaims().stream().map(SafeClaim::from).toList();
    }

    // 1. FIXED SUBMIT METHOD (Maps exactly to /api/v1/claims)
    @PostMapping
    public SafeClaim submitClaim(
                             @RequestBody CreateClaimRequest req) {
        return SafeClaim.from(service.createClaim(access.current().getId(), req.lostReportId(), req.foundReportId()));
    }

    @PostMapping("/staff/custody/{foundReportId}/intake")
    public ItemCustodyState recordCustody(
                                          @PathVariable Long foundReportId,
                                          @RequestBody CustodyIntakeRequest req) {
        return service.recordCustodyIntake(access.staff().getId(), foundReportId, req.storageReference(), req.conditionNotes());
    }

    @PostMapping("/staff/claims/{claimId}/decision")
    public Claim decideClaim(
                             @PathVariable Long claimId,
                             @RequestBody StaffDecisionRequest req) {
        return service.recordStaffDecision(access.staff().getId(), claimId, req.action(), req.reason(), req.physicalCheckNotes());
    }

    @PatchMapping("/staff/claims/{claimId}/risk")
    public Claim escalateRisk(
                              @PathVariable Long claimId,
                              @RequestBody RiskEscalationRequest req) {
        return service.escalateRiskTier(access.staff().getId(), claimId, req.riskTier(), req.reason());
    }

    // 2. FR-18 WITHDRAWAL METHOD (Maps exactly to DELETE /api/v1/claims/{id})
    @DeleteMapping("/{id}")
    public ResponseEntity<?> withdrawClaim(
        @PathVariable("id") Long id) {
        try {
            service.withdrawClaim(id, access.current().getId());
            return ResponseEntity.ok().body("Claim withdrawn successfully (FR-18).");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
