package com.findback.module3.service;

import com.findback.module3.domain.*;
import com.findback.module3.ports.CrossModulePorts.*;
import com.findback.module3.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;


@Service
public class ClaimReviewService {

    private final ClaimRepository claimRepo;
    private final ItemCustodyStateRepository custodyStateRepo;
    private final AdminReviewRepository reviewRepo;
    private final CustodyEventRepository custodyEventRepo;
    private final ReportLookupPort reportPort;
    private final DoveEvaluationPort dovePort;
    private final OperationsPort opsPort;

    public ClaimReviewService(ClaimRepository claimRepo,
                              ItemCustodyStateRepository custodyStateRepo,
                              AdminReviewRepository reviewRepo,
                              CustodyEventRepository custodyEventRepo,
                              ReportLookupPort reportPort,
                              DoveEvaluationPort dovePort,
                              OperationsPort opsPort) {
        this.claimRepo = claimRepo;
        this.custodyStateRepo = custodyStateRepo;
        this.reviewRepo = reviewRepo;
        this.custodyEventRepo = custodyEventRepo;
        this.reportPort = reportPort;
        this.dovePort = dovePort;
        this.opsPort = opsPort;
    }

    public List<Claim> getAllClaims() {
        return claimRepo.findAll();
    }

    @Transactional
    public void withdrawClaim(Long claimId, Long studentId) {
    Claim claim = claimRepo.findById(claimId)
        .orElseThrow(() -> new RuntimeException("Claim not found"));
        
    if (!claim.getClaimantId().equals(studentId)) {
        throw new RuntimeException("Unauthorized: You cannot withdraw someone else's claim.");
    }
    if (claim.getStatus().name().equals("APPROVED")) {
        throw new RuntimeException("Cannot withdraw a claim that has already been approved.");
    }
    
    claimRepo.delete(claim);
}

    @Transactional
    public ItemCustodyState recordCustodyIntake(Long staffId, Long foundReportId,
                                                String storageRef, String conditionNotes) {
        ReportSummaryDto foundReport = reportPort.getReportById(foundReportId);
        if (!"FOUND".equalsIgnoreCase(foundReport.type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Custody applies only to FOUND reports.");
        }
        if (foundReport.authorId().equals(staffId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff cannot intake their own found report.");
        }

        ItemCustodyState state = custodyStateRepo.findById(foundReportId)
                .orElseGet(() -> {
                    ItemCustodyState s = new ItemCustodyState();
                    s.setFoundReportId(foundReportId);
                    return s;
                });

        state.setCustodyStatus(CustodyStatus.IN_CUSTODY);
        state.setStorageReference(storageRef);
        state.setReceivingOfficerId(staffId);
        custodyStateRepo.save(state);

        custodyEventRepo.save(new CustodyEvent(foundReportId, staffId,
                "RECEIVED_INTO_CUSTODY", storageRef, conditionNotes, true));

        opsPort.recordAuditEvent(staffId, "CUSTODY_INTAKE", "FOUND-" + foundReportId, "IN_CUSTODY");
        return state;
    }

    @Transactional
    public Claim createClaim(Long claimantId, Long lostReportId, Long foundReportId) {
        ReportSummaryDto lostReport = reportPort.getReportById(lostReportId);
        ReportSummaryDto foundReport = reportPort.getReportById(foundReportId);

        if (!lostReport.authorId().equals(claimantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You must own the lost report to submit a claim.");
        }
        if (!lostReport.baselineEvidenceSaved()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Baseline private evidence is required first (FR-17).");
        }
        if (claimRepo.existsActiveClaimForPair(claimantId, foundReportId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An active claim already exists for this item (FR-06).");
        }

        Claim claim = new Claim();
        claim.setClaimantId(claimantId);
        claim.setLostReportId(lostReportId);
        claim.setFoundReportId(foundReportId);
        claim.setRiskTier(RiskTier.fromCategory(foundReport.category()));
        claim.setStatus(ClaimStatus.UNDER_REVIEW);
        claim = claimRepo.save(claim);

        DoveSummaryDto dove = dovePort.evaluateOrGetSummary(claim.getClaimId(), lostReportId, foundReportId);
        if (dove.hasCriticalIdentifierConflict()) {
            claim.setStatus(ClaimStatus.ON_HOLD);
            claim.setInternalReviewFlag(true);
            claim.setFlagReason("Critical identifier conflict detected by DOVE.");
            claimRepo.save(claim);
        }

        opsPort.recordAuditEvent(claimantId, "CLAIM_CREATED", "CLAIM-" + claim.getClaimId(), claim.getStatus().name());
        return claim;
    }

    @Transactional
    public Claim recordStaffDecision(Long reviewerId, Long claimId, String action,
                                     String reason, String physicalCheckNotes) {
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A recorded decision reason is mandatory (FR-12).");
        }

        Claim claim = claimRepo.findById(claimId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found."));

        ReportSummaryDto foundReport = reportPort.getReportById(claim.getFoundReportId());

        if (reviewerId.equals(claim.getClaimantId()) || reviewerId.equals(foundReport.authorId())) {
            opsPort.recordAuditEvent(reviewerId, "UNAUTHORIZED_SELF_REVIEW_ATTEMPT", "CLAIM-" + claimId, "DENIED");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Self-review of your own report or claim is prohibited.");
        }

        ClaimStatus prevStatus = claim.getStatus();
        ClaimStatus nextStatus;

        switch (action.toUpperCase()) {
            case "APPROVE" -> {
                if (prevStatus != ClaimStatus.UNDER_REVIEW) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Only UNDER_REVIEW claims can be approved.");
                }
                ItemCustodyState custody = custodyStateRepo.findByIdForUpdate(claim.getFoundReportId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                                "Item custody not recorded yet. Confirm physical custody before approval (T09)."));

                if (custody.getCustodyStatus() != CustodyStatus.IN_CUSTODY) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Item is not available for reservation (Current state: " + custody.getCustodyStatus() + ").");
                }
                if (custody.isDisputeBlocked()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Item is blocked by an unresolved dispute.");
                }

                custody.setCustodyStatus(CustodyStatus.RESERVED);
                custody.setReservedByClaimId(claim.getClaimId());
                custodyStateRepo.save(custody);

                nextStatus = ClaimStatus.APPROVED;
                opsPort.sendSafeNotification(claim.getClaimantId(),
                        "Your claim #" + claimId + " has been approved. Present your handover code at the Security Desk.");
            }
            case "HOLD" -> {
                if (prevStatus == ClaimStatus.APPROVED) {
                    releaseReservationInternal(claim.getFoundReportId(), claim.getClaimId(), true);
                    opsPort.revokeActiveHandoverCredential(claimId, "Claim placed On Hold by staff: " + reason);
                }
                nextStatus = ClaimStatus.ON_HOLD;
                opsPort.sendSafeNotification(claim.getClaimantId(),
                        "Your claim #" + claimId + " requires additional review or information.");
            }
            case "REJECT" -> {
                if (prevStatus == ClaimStatus.APPROVED) {
                    releaseReservationInternal(claim.getFoundReportId(), claim.getClaimId(), false);
                    opsPort.revokeActiveHandoverCredential(claimId, "Approval revoked and claim rejected.");
                }
                nextStatus = ClaimStatus.REJECTED;
                opsPort.sendSafeNotification(claim.getClaimantId(),
                        "Your claim #" + claimId + " was not approved. You may request staff reconsideration if you have further proof.");
            }
            case "RECONSIDER" -> {
                if (prevStatus != ClaimStatus.ON_HOLD && prevStatus != ClaimStatus.REJECTED) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only ON_HOLD or REJECTED claims can return to UNDER_REVIEW.");
                }
                nextStatus = ClaimStatus.UNDER_REVIEW;
                opsPort.sendSafeNotification(claim.getClaimantId(), "Your claim #" + claimId + " has been reopened for staff review.");
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported review decision: " + action);
        }

        claim.setStatus(nextStatus);
        claimRepo.save(claim);

        reviewRepo.save(new AdminReview(claimId, reviewerId, action.toUpperCase(),
                prevStatus.name(), nextStatus.name(), reason, physicalCheckNotes));

        opsPort.recordAuditEvent(reviewerId, "CLAIM_DECISION_" + action.toUpperCase(),
                "CLAIM-" + claimId, prevStatus + " -> " + nextStatus);

        return claim;
    }

    @Transactional
    public Claim escalateRiskTier(Long staffId, Long claimId, RiskTier newTier, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escalation reason is required (FR-10).");
        }
        Claim claim = claimRepo.findById(claimId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found."));
        claim.setRiskTier(newTier);
        claim.setEscalationReason(reason);
        opsPort.recordAuditEvent(staffId, "RISK_ESCALATED", "CLAIM-" + claimId, newTier.name());
        return claimRepo.save(claim);
    }

    private void releaseReservationInternal(Long foundReportId, Long claimId, boolean flagDispute) {
        ItemCustodyState custody = custodyStateRepo.findByIdForUpdate(foundReportId).orElseThrow();
        if (claimId.equals(custody.getReservedByClaimId())) {
            custody.setCustodyStatus(CustodyStatus.IN_CUSTODY);
            custody.setReservedByClaimId(null);
        }
        if (flagDispute) {
            custody.setDisputeBlocked(true);
        }
        custodyStateRepo.save(custody);
    }
}
