package com.findback.module3.controller;

import com.findback.module3.domain.*;
import com.findback.module3.service.ClaimReviewService;
import com.findback.module3.service.ReviewCaseView;
import edu.findback.evidence.EvidenceAccess;

import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Identity always comes from the authenticated session ({@link EvidenceAccess}); client-supplied
 * user IDs or headers are never trusted. Everything under /staff is ADMIN-only in SecurityConfig
 * and is re-checked here.
 */
@RestController
@RequestMapping("/api/v1/claims")
public class ClaimCustodyController {

    private final ClaimReviewService service;
    private final EvidenceAccess access;

    public ClaimCustodyController(ClaimReviewService service, EvidenceAccess access) {
        this.service = service;
        this.access = access;
    }

    /** Claimant-facing view: no risk tier, internal flags or DOVE results. */
    public record SafeClaim(Long claimId, Long lostReportId, Long foundReportId, ClaimStatus status,
                            OffsetDateTime createdAt, OffsetDateTime updatedAt, boolean withdrawable) {
        static SafeClaim from(Claim c) {
            return new SafeClaim(c.getClaimId(), c.getLostReportId(), c.getFoundReportId(), c.getStatus(),
                    c.getCreatedAt(), c.getUpdatedAt(), ClaimReviewService.WITHDRAWABLE.contains(c.getStatus()));
        }
    }

    public record CreateClaimRequest(Long lostReportId, Long foundReportId) {}
    public record CustodyIntakeRequest(String storageReference, String conditionNotes) {}
    public record StaffDecisionRequest(String action, String reason, String physicalCheckNotes) {}
    public record RiskEscalationRequest(RiskTier riskTier, String reason) {}
    public record DisputeResolutionRequest(String resolution) {}

    @GetMapping("/mine")
    public List<SafeClaim> myClaims() {
        return service.getClaimsForClaimant(access.current().getId()).stream().map(SafeClaim::from).toList();
    }

    @PostMapping
    public SafeClaim submitClaim(@RequestBody CreateClaimRequest req) {
        return SafeClaim.from(service.createClaim(access.current().getId(), req.lostReportId(), req.foundReportId()));
    }

    /** FR-18 withdrawal of the caller's own claim. */
    @DeleteMapping("/{id}")
    public SafeClaim withdrawClaim(@PathVariable("id") Long id) {
        return SafeClaim.from(service.withdrawClaim(id, access.current().getId()));
    }

    @GetMapping("/staff/claims")
    public List<SafeClaim> listAllClaims() {
        access.staff();
        return service.getAllClaims().stream().map(SafeClaim::from).toList();
    }

    /** Live staff review queue, excluding cases the caller is involved in (T09). */
    @GetMapping("/staff/cases")
    public List<ReviewCaseView> reviewQueue() {
        return service.getReviewQueue(access.staff().getId());
    }

    @PostMapping("/staff/custody/{foundReportId}/intake")
    public ItemCustodyState recordCustody(@PathVariable Long foundReportId,
                                          @RequestBody CustodyIntakeRequest req) {
        return service.recordCustodyIntake(access.staff().getId(), foundReportId, req.storageReference(), req.conditionNotes());
    }

    @PostMapping("/staff/custody/{foundReportId}/dispute/resolve")
    public ItemCustodyState resolveDispute(@PathVariable Long foundReportId,
                                           @RequestBody DisputeResolutionRequest req) {
        return service.resolveDispute(access.staff().getId(), foundReportId, req.resolution());
    }

    @PostMapping("/staff/claims/{claimId}/decision")
    public Claim decideClaim(@PathVariable Long claimId,
                             @RequestBody StaffDecisionRequest req) {
        return service.recordStaffDecision(access.staff().getId(), claimId, req.action(), req.reason(), req.physicalCheckNotes());
    }

    @PatchMapping("/staff/claims/{claimId}/risk")
    public Claim escalateRisk(@PathVariable Long claimId,
                              @RequestBody RiskEscalationRequest req) {
        return service.escalateRiskTier(access.staff().getId(), claimId, req.riskTier(), req.reason());
    }
}
