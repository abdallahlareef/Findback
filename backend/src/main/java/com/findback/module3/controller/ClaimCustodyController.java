package com.findback.module3.controller;

import com.findback.module3.domain.*;
import com.findback.module3.service.CaseHistoryView;
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

    /**
     * Claimant-facing view: no risk tier, internal flags or DOVE results. The reconsideration
     * status is the claimant's own request (NONE, PENDING, ACCEPTED or DECLINED).
     */
    public record SafeClaim(Long claimId, Long lostReportId, Long foundReportId, ClaimStatus status,
                            OffsetDateTime createdAt, OffsetDateTime updatedAt, boolean withdrawable,
                            String reconsiderationStatus, boolean canRequestReconsideration) {
        static SafeClaim from(Claim c, boolean canRequestReconsideration) {
            return new SafeClaim(c.getClaimId(), c.getLostReportId(), c.getFoundReportId(), c.getStatus(),
                    c.getCreatedAt(), c.getUpdatedAt(), ClaimReviewService.WITHDRAWABLE.contains(c.getStatus()),
                    c.getReconsiderationStatus(), canRequestReconsideration);
        }
    }

    private SafeClaim safe(Claim claim) {
        return SafeClaim.from(claim, service.reconsiderationAvailable(claim));
    }

    public record CreateClaimRequest(Long lostReportId, Long foundReportId) {}
    public record ReconsiderationRequest(String reason) {}
    public record CustodyIntakeRequest(String storageReference, String conditionNotes) {}
    public record StaffDecisionRequest(String action, String reason, String physicalCheckNotes) {}
    public record RiskEscalationRequest(RiskTier riskTier, String reason) {}
    public record DisputeResolutionRequest(String resolution) {}
    public record HandoverRequest(String handoverCode, String identityCheckNotes) {}

    @GetMapping("/mine")
    public List<SafeClaim> myClaims() {
        return service.getClaimsForClaimant(access.current().getId()).stream().map(this::safe).toList();
    }

    @PostMapping
    public SafeClaim submitClaim(@RequestBody CreateClaimRequest req) {
        return safe(service.createClaim(access.current().getId(), req.lostReportId(), req.foundReportId()));
    }

    /** FR-18 withdrawal of the caller's own claim. */
    @DeleteMapping("/{id}")
    public SafeClaim withdrawClaim(@PathVariable("id") Long id) {
        return safe(service.withdrawClaim(id, access.current().getId()));
    }

    /** Claimant asks staff to look again at their rejected claim (once per claim). */
    @PostMapping("/{id}/reconsideration")
    public SafeClaim requestReconsideration(@PathVariable("id") Long id, @RequestBody ReconsiderationRequest req) {
        return safe(service.requestReconsideration(id, access.current().getId(), req.reason()));
    }

    /** Used by Member 2's Evidence Workspace to list claims for staff. */
    @GetMapping("/staff/claims")
    public List<SafeClaim> listAllClaims() {
        access.staff();
        return service.getAllClaims().stream().map(c -> SafeClaim.from(c, false)).toList();
    }

    /** Case-history search list, excluding cases the caller is involved in (T09). */
    @GetMapping("/staff/history")
    public List<CaseHistoryView.CaseSummary> historyCases() {
        return service.getHistoryCases(access.staff().getId());
    }

    /** FR-12 timeline of one claim: decisions and reasons, custody events, claimant actions. */
    @GetMapping("/staff/claims/{claimId}/history")
    public CaseHistoryView caseHistory(@PathVariable Long claimId) {
        return service.getCaseHistory(access.staff().getId(), claimId);
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

    /** Release the reserved item to the verified owner at the Security Desk. */
    @PostMapping("/staff/claims/{claimId}/handover")
    public Claim completeHandover(@PathVariable Long claimId,
                                  @RequestBody HandoverRequest req) {
        return service.completeHandover(access.staff().getId(), claimId, req.handoverCode(), req.identityCheckNotes());
    }

    @PatchMapping("/staff/claims/{claimId}/risk")
    public Claim escalateRisk(@PathVariable Long claimId,
                              @RequestBody RiskEscalationRequest req) {
        return service.escalateRiskTier(access.staff().getId(), claimId, req.riskTier(), req.reason());
    }
}
