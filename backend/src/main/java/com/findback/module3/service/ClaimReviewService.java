package com.findback.module3.service;

import com.findback.module3.domain.*;
import com.findback.module3.ports.CrossModulePorts.*;
import com.findback.module3.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.findback.module3.domain.ClaimStatus.*;

@Service
public class ClaimReviewService {

    /** Claims that still count for FR-06 duplicate prevention and for competition over an item. */
    public static final Set<ClaimStatus> ACTIVE = Collections.unmodifiableSet(EnumSet.of(SUBMITTED, UNDER_REVIEW, ON_HOLD, APPROVED));
    /** A claimant may withdraw only while the claim is still waiting for a final staff decision (FR-18). */
    public static final Set<ClaimStatus> WITHDRAWABLE = Collections.unmodifiableSet(EnumSet.of(SUBMITTED, UNDER_REVIEW, ON_HOLD));
    /** Closed claims accept no further decisions; a REJECTED claim can only be reopened with RECONSIDER. */
    public static final Set<ClaimStatus> CLOSED = Collections.unmodifiableSet(EnumSet.of(REJECTED, WITHDRAWN, COMPLETED));
    private static final int STORAGE_REFERENCE_MAX_LENGTH = 100;

    private final ClaimRepository claimRepo;
    private final ItemCustodyStateRepository custodyStateRepo;
    private final AdminReviewRepository reviewRepo;
    private final CustodyEventRepository custodyEventRepo;
    private final ReportLookupPort reportPort;
    private final DoveEvaluationPort dovePort;
    private final OperationsPort opsPort;
    private final ClaimantDirectoryPort directoryPort;
    private final ReportLifecyclePort reportLifecyclePort;

    public ClaimReviewService(ClaimRepository claimRepo,
                              ItemCustodyStateRepository custodyStateRepo,
                              AdminReviewRepository reviewRepo,
                              CustodyEventRepository custodyEventRepo,
                              ReportLookupPort reportPort,
                              DoveEvaluationPort dovePort,
                              OperationsPort opsPort,
                              ClaimantDirectoryPort directoryPort,
                              ReportLifecyclePort reportLifecyclePort) {
        this.claimRepo = claimRepo;
        this.custodyStateRepo = custodyStateRepo;
        this.reviewRepo = reviewRepo;
        this.custodyEventRepo = custodyEventRepo;
        this.reportPort = reportPort;
        this.dovePort = dovePort;
        this.opsPort = opsPort;
        this.directoryPort = directoryPort;
        this.reportLifecyclePort = reportLifecyclePort;
    }

    public List<Claim> getAllClaims() {
        return claimRepo.findAll();
    }

    public List<Claim> getClaimsForClaimant(Long claimantId) {
        return claimRepo.findByClaimantIdOrderByCreatedAtDesc(claimantId);
    }

    /**
     * Live review queue for one staff member. Cases the reviewer is involved in (as claimant,
     * finder or competing claimant) are left out entirely so that their rivals' DOVE results and
     * identities are never shown to them (T09).
     */
    @Transactional
    public List<ReviewCaseView> getReviewQueue(Long staffId) {
        List<Claim> claims = claimRepo.findAll();
        Set<Long> itemsClaimedByReviewer = claims.stream()
                .filter(c -> staffId.equals(c.getClaimantId()))
                .map(Claim::getFoundReportId)
                .collect(Collectors.toSet());
        Map<Long, Long> activeClaimsPerItem = claims.stream()
                .filter(c -> ACTIVE.contains(c.getStatus()))
                .collect(Collectors.groupingBy(Claim::getFoundReportId, Collectors.counting()));
        Map<Long, ItemCustodyState> custodyByItem = custodyStateRepo
                .findAllById(claims.stream().map(Claim::getFoundReportId).distinct().toList()).stream()
                .collect(Collectors.toMap(ItemCustodyState::getFoundReportId, Function.identity()));
        Map<Long, ReportSummaryDto> foundReports = new HashMap<>();

        List<ReviewCaseView> queue = new ArrayList<>();
        for (Claim claim : claims) {
            if (claim.getStatus() == WITHDRAWN || claim.getStatus() == COMPLETED) continue;
            if (itemsClaimedByReviewer.contains(claim.getFoundReportId())) continue;
            ItemCustodyState custody = custodyByItem.get(claim.getFoundReportId());
            // Once an item is back with its owner, the auto-closed competing claims need no review.
            if (custody != null && custody.getCustodyStatus() == CustodyStatus.RETURNED) continue;
            ReportSummaryDto found = foundReports.computeIfAbsent(claim.getFoundReportId(), reportPort::getReportById);
            if (staffId.equals(found.authorId())) continue;

            DoveSummaryDto dove = dovePort.evaluateOrGetSummary(claim.getClaimId(), claim.getLostReportId(), claim.getFoundReportId());
            boolean pendingSupplement = dovePort.hasPendingSupplement(claim.getClaimId());
            long competing = activeClaimsPerItem.getOrDefault(claim.getFoundReportId(), 0L)
                    - (ACTIVE.contains(claim.getStatus()) ? 1 : 0);

            queue.add(new ReviewCaseView(
                    claim.getClaimId(), claim.getClaimantId(), directoryPort.displayName(claim.getClaimantId()),
                    claim.getLostReportId(), claim.getFoundReportId(),
                    found.title(), found.category(), found.zone(),
                    claim.getStatus(), claim.getRiskTier(), claim.getEscalationReason(),
                    claim.isInternalReviewFlag(), claim.getFlagReason(),
                    custody == null ? CustodyStatus.REPORTED : custody.getCustodyStatus(),
                    custody == null ? null : custody.getStorageReference(),
                    custody != null && custody.isDisputeBlocked(),
                    custody == null ? null : custody.getReservedByClaimId(),
                    new ReviewCaseView.DoveView(dove.strengthLabel(), dove.matchCount(), dove.unknownCount(),
                            dove.contradictionCount(), dove.hasCriticalIdentifierConflict(),
                            dove.submissionAttemptsUsed(), pendingSupplement),
                    Math.max(competing, 0L),
                    claim.getCreatedAt(), claim.getUpdatedAt()));
        }
        queue.sort(Comparator.comparing(ReviewCaseView::claimId));
        return queue;
    }

    /** FR-18: a claimant withdraws their own claim before any final staff decision. */
    @Transactional
    public Claim withdrawClaim(Long claimId, Long claimantId) {
        Claim claim = findClaim(claimId);
        if (!claim.getClaimantId().equals(claimantId)) {
            throw error(HttpStatus.FORBIDDEN, "You can only withdraw your own claim.");
        }
        ClaimStatus previous = claim.getStatus();
        if (!WITHDRAWABLE.contains(previous)) {
            throw error(HttpStatus.CONFLICT, "Only claims still awaiting a staff decision can be withdrawn (current status: "
                    + previous + ").");
        }
        claim.setStatus(WITHDRAWN);
        claimRepo.save(claim);
        opsPort.recordAuditEvent(claimantId, "CLAIM_WITHDRAWN", "CLAIM-" + claimId, previous + " -> " + WITHDRAWN);
        return claim;
    }

    /** FR-11: staff record that a found item is physically held and where it is stored. */
    @Transactional
    public ItemCustodyState recordCustodyIntake(Long staffId, Long foundReportId,
                                                String storageRef, String conditionNotes) {
        String storage = required(storageRef, "A storage or locker reference is required for custody intake (FR-11).");
        if (storage.length() > STORAGE_REFERENCE_MAX_LENGTH) {
            throw error(HttpStatus.BAD_REQUEST, "Storage reference must be at most " + STORAGE_REFERENCE_MAX_LENGTH + " characters.");
        }
        String condition = required(conditionNotes, "Receiving condition notes are required for custody intake (FR-11).");

        // The found-report lock serializes intake with claim creation and with other intakes of the
        // same item, including the very first intake when no custody row exists yet (T10).
        ReportSummaryDto foundReport = reportPort.lockReportForUpdate(foundReportId);
        if (!"FOUND".equalsIgnoreCase(foundReport.type())) {
            throw error(HttpStatus.BAD_REQUEST, "Custody applies only to FOUND reports.");
        }
        assertNotInvolved(staffId, null, foundReport, "FOUND-" + foundReportId);

        Optional<ItemCustodyState> existing = custodyStateRepo.findByIdForUpdate(foundReportId);
        if (existing.isPresent()) {
            CustodyStatus current = existing.get().getCustodyStatus();
            if (current == CustodyStatus.RESERVED) {
                throw error(HttpStatus.CONFLICT, "This item is reserved for approved claim #" + existing.get().getReservedByClaimId()
                        + ". Custody cannot be re-recorded until that reservation is released (T10).");
            }
            if (current == CustodyStatus.RETURNED) {
                throw error(HttpStatus.CONFLICT, "This item has already been returned to its owner.");
            }
        }

        ItemCustodyState state = existing.orElseGet(() -> {
            ItemCustodyState s = new ItemCustodyState();
            s.setFoundReportId(foundReportId);
            return s;
        });
        boolean firstIntake = state.getCustodyStatus() != CustodyStatus.IN_CUSTODY;
        state.setCustodyStatus(CustodyStatus.IN_CUSTODY);
        state.setStorageReference(storage);
        if (firstIntake || state.getReceivingOfficerId() == null) {
            state.setReceivingOfficerId(staffId);
        }
        custodyStateRepo.save(state);

        custodyEventRepo.save(new CustodyEvent(foundReportId, staffId,
                firstIntake ? "RECEIVED_INTO_CUSTODY" : "STORAGE_RECONFIRMED", storage, condition, true));

        opsPort.recordAuditEvent(staffId, firstIntake ? "CUSTODY_INTAKE" : "CUSTODY_RECONFIRMED",
                "FOUND-" + foundReportId, "IN_CUSTODY");
        return state;
    }

    /**
     * Clears the dispute block that is set when an approved claim is put on hold, so the item can be
     * reserved again. Requires a recorded resolution and an uninvolved reviewer (FR-12, T09).
     */
    @Transactional
    public ItemCustodyState resolveDispute(Long staffId, Long foundReportId, String resolution) {
        String why = required(resolution, "A recorded resolution is required to clear a dispute (FR-12).");
        ReportSummaryDto foundReport = reportPort.lockReportForUpdate(foundReportId);
        assertNotInvolved(staffId, null, foundReport, "FOUND-" + foundReportId);

        ItemCustodyState custody = custodyStateRepo.findByIdForUpdate(foundReportId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "No custody record exists for this item."));
        if (!custody.isDisputeBlocked()) {
            throw error(HttpStatus.CONFLICT, "This item is not blocked by a dispute.");
        }
        custody.setDisputeBlocked(false);
        custodyStateRepo.save(custody);

        custodyEventRepo.save(new CustodyEvent(foundReportId, staffId, "DISPUTE_RESOLVED",
                Objects.requireNonNullElse(custody.getStorageReference(), "UNRECORDED"), why, true));
        opsPort.recordAuditEvent(staffId, "DISPUTE_RESOLVED", "FOUND-" + foundReportId, "UNBLOCKED");
        return custody;
    }

    /** FR-06: one active claim per claimant and found item. */
    @Transactional
    public Claim createClaim(Long claimantId, Long lostReportId, Long foundReportId) {
        if (lostReportId == null || foundReportId == null) {
            throw error(HttpStatus.BAD_REQUEST, "Choose your lost report and the found report you are claiming.");
        }
        ReportSummaryDto lostReport = reportPort.getReportById(lostReportId);
        // Concurrent submissions for the same item (e.g. a double click) wait here, so the
        // duplicate check below always sees claims committed by the other request (FR-06, T10).
        ReportSummaryDto foundReport = reportPort.lockReportForUpdate(foundReportId);

        if (!"LOST".equals(lostReport.type()) || !"FOUND".equals(foundReport.type()) || foundReport.authorId().equals(claimantId)) {
            throw error(HttpStatus.BAD_REQUEST, "A claim requires your lost report and another user's found report.");
        }
        if (!lostReport.authorId().equals(claimantId)) {
            throw error(HttpStatus.FORBIDDEN, "You must own the lost report to submit a claim.");
        }
        if (!lostReport.baselineEvidenceSaved()) {
            throw error(HttpStatus.BAD_REQUEST, "Baseline private evidence is required first (FR-17).");
        }
        if (claimRepo.existsActiveClaimForPair(claimantId, foundReportId)) {
            throw error(HttpStatus.CONFLICT, "An active claim already exists for this item (FR-06).");
        }

        Claim claim = new Claim();
        claim.setClaimantId(claimantId);
        claim.setLostReportId(lostReportId);
        claim.setFoundReportId(foundReportId);
        claim.setRiskTier(RiskTier.fromCategory(foundReport.category()));
        claim.setStatus(UNDER_REVIEW);
        claim = claimRepo.save(claim);

        DoveSummaryDto dove = dovePort.evaluateOrGetSummary(claim.getClaimId(), lostReportId, foundReportId);
        if (dove.hasCriticalIdentifierConflict()) {
            claim.setStatus(ON_HOLD);
            claim.setInternalReviewFlag(true);
            claim.setFlagReason("Critical identifier conflict detected by DOVE.");
            claimRepo.save(claim);
        }

        opsPort.recordAuditEvent(claimantId, "CLAIM_CREATED", "CLAIM-" + claim.getClaimId(), claim.getStatus().name());
        return claim;
    }

    /** FR-12: every decision needs a recorded reason; approval also reserves the item (T10). */
    @Transactional
    public Claim recordStaffDecision(Long reviewerId, Long claimId, String action,
                                     String reason, String physicalCheckNotes) {
        String why = required(reason, "A recorded decision reason is mandatory (FR-12).");
        String decision = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);

        Claim claim = findClaim(claimId);
        ReportSummaryDto foundReport = reportPort.getReportById(claim.getFoundReportId());
        assertNotInvolved(reviewerId, claim, foundReport, "CLAIM-" + claimId);

        ClaimStatus prevStatus = claim.getStatus();
        ClaimStatus nextStatus;

        switch (decision) {
            case "APPROVE" -> {
                if (prevStatus != UNDER_REVIEW) {
                    throw error(HttpStatus.CONFLICT, "Only UNDER_REVIEW claims can be approved (current status: " + prevStatus + ").");
                }
                DoveSummaryDto currentDove = dovePort.evaluateOrGetSummary(claimId, claim.getLostReportId(), claim.getFoundReportId());
                if (currentDove.hasCriticalIdentifierConflict() || dovePort.hasPendingSupplement(claimId)) {
                    throw error(HttpStatus.CONFLICT, "Resolve outstanding evidence review before approval.");
                }
                ItemCustodyState custody = custodyStateRepo.findByIdForUpdate(claim.getFoundReportId())
                        .orElseThrow(() -> error(HttpStatus.CONFLICT,
                                "Item custody not recorded yet. Confirm physical custody before approval (FR-11)."));

                if (custody.getCustodyStatus() != CustodyStatus.IN_CUSTODY) {
                    throw error(HttpStatus.CONFLICT,
                            "Item is not available for reservation (current state: " + custody.getCustodyStatus() + ").");
                }
                if (custody.isDisputeBlocked()) {
                    throw error(HttpStatus.CONFLICT, "Item is blocked by an unresolved dispute.");
                }

                custody.setCustodyStatus(CustodyStatus.RESERVED);
                custody.setReservedByClaimId(claim.getClaimId());
                custodyStateRepo.save(custody);

                nextStatus = APPROVED;
                opsPort.issueHandoverCredential(claimId, claim.getClaimantId());
                opsPort.sendSafeNotification(claim.getClaimantId(),
                        "Your claim #" + claimId + " has been approved. Present your handover code at the Security Desk.");
            }
            case "HOLD" -> {
                assertOpen(prevStatus);
                if (prevStatus == APPROVED) {
                    releaseReservationInternal(claim.getFoundReportId(), claim.getClaimId(), true);
                    opsPort.revokeActiveHandoverCredential(claimId, "Claim placed On Hold by staff: " + why);
                }
                nextStatus = ON_HOLD;
                opsPort.sendSafeNotification(claim.getClaimantId(),
                        "Your claim #" + claimId + " requires additional review or information.");
            }
            case "REJECT" -> {
                assertOpen(prevStatus);
                if (prevStatus == APPROVED) {
                    releaseReservationInternal(claim.getFoundReportId(), claim.getClaimId(), false);
                    opsPort.revokeActiveHandoverCredential(claimId, "Approval revoked and claim rejected.");
                }
                nextStatus = REJECTED;
                opsPort.sendSafeNotification(claim.getClaimantId(),
                        "Your claim #" + claimId + " was not approved. You may request staff reconsideration if you have further proof.");
            }
            case "RECONSIDER" -> {
                if (prevStatus != ON_HOLD && prevStatus != REJECTED) {
                    throw error(HttpStatus.BAD_REQUEST, "Only ON_HOLD or REJECTED claims can return to UNDER_REVIEW.");
                }
                nextStatus = UNDER_REVIEW;
                opsPort.sendSafeNotification(claim.getClaimantId(), "Your claim #" + claimId + " has been reopened for staff review.");
            }
            default -> throw error(HttpStatus.BAD_REQUEST, "Unsupported review decision: " + action);
        }

        claim.setStatus(nextStatus);
        claimRepo.save(claim);

        reviewRepo.save(new AdminReview(claimId, reviewerId, decision,
                prevStatus.name(), nextStatus.name(), why, physicalCheckNotes));

        opsPort.recordAuditEvent(reviewerId, "CLAIM_DECISION_" + decision,
                "CLAIM-" + claimId, prevStatus + " -> " + nextStatus);

        return claim;
    }

    /**
     * Final step of the lifecycle: the verified owner collects the item at the desk. The claim is
     * completed, the item marked RETURNED, both reports closed, and any competing claims closed
     * (FR-11, FR-12, T09, T10).
     */
    @Transactional
    public Claim completeHandover(Long staffId, Long claimId, String handoverCode, String identityCheckNotes) {
        String code = required(handoverCode, "Enter the claimant's one-time handover code.");
        String notes = required(identityCheckNotes, "Record the identity check performed at handover (FR-12).");

        Claim claim = findClaim(claimId);
        Long foundReportId = claim.getFoundReportId();
        // Same lock order as approval and intake (found report, then custody row), so two desks
        // cannot release the same item at once.
        ReportSummaryDto foundReport = reportPort.lockReportForUpdate(foundReportId);
        assertNotInvolved(staffId, claim, foundReport, "CLAIM-" + claimId);
        if (claim.getStatus() != APPROVED) {
            throw error(HttpStatus.CONFLICT, "Only APPROVED claims can be handed over (current status: " + claim.getStatus() + ").");
        }
        ItemCustodyState custody = custodyStateRepo.findByIdForUpdate(foundReportId)
                .orElseThrow(() -> error(HttpStatus.CONFLICT, "No custody record exists for this item."));
        if (custody.getCustodyStatus() != CustodyStatus.RESERVED || !claimId.equals(custody.getReservedByClaimId())) {
            throw error(HttpStatus.CONFLICT, "The item is not reserved for this claim (custody state: " + custody.getCustodyStatus() + ").");
        }
        if (!opsPort.verifyAndConsumeHandoverCredential(claimId, code)) {
            opsPort.recordAuditEvent(staffId, "HANDOVER_CODE_REJECTED", "CLAIM-" + claimId, "DENIED");
            throw error(HttpStatus.FORBIDDEN, "The handover code is invalid, expired or already used.");
        }

        custody.setCustodyStatus(CustodyStatus.RETURNED);
        custodyStateRepo.save(custody);
        custodyEventRepo.save(new CustodyEvent(foundReportId, staffId, "RELEASED_TO_OWNER",
                Objects.requireNonNullElse(custody.getStorageReference(), "UNRECORDED"), notes, true));

        claim.setStatus(COMPLETED);
        claimRepo.save(claim);
        reviewRepo.save(new AdminReview(claimId, staffId, "HANDOVER", APPROVED.name(), COMPLETED.name(), notes, null));

        for (Claim other : claimRepo.findByFoundReportId(foundReportId)) {
            if (other.getClaimId().equals(claimId) || !ACTIVE.contains(other.getStatus())) continue;
            ClaimStatus previous = other.getStatus();
            other.setStatus(REJECTED);
            claimRepo.save(other);
            reviewRepo.save(new AdminReview(other.getClaimId(), staffId, "AUTO_CLOSED", previous.name(), REJECTED.name(),
                    "Item returned to another verified claimant (claim #" + claimId + ").", null));
            opsPort.sendSafeNotification(other.getClaimantId(), "Your claim #" + other.getClaimId()
                    + " has been closed because the item was returned to its owner after verification.");
            opsPort.recordAuditEvent(staffId, "CLAIM_AUTO_CLOSED", "CLAIM-" + other.getClaimId(), previous + " -> " + REJECTED);
        }

        reportLifecyclePort.closeReport(foundReportId);
        reportLifecyclePort.closeReport(claim.getLostReportId());

        opsPort.sendSafeNotification(claim.getClaimantId(),
                "Your item for claim #" + claimId + " has been handed over. This case is now closed.");
        opsPort.recordAuditEvent(staffId, "HANDOVER_COMPLETED", "CLAIM-" + claimId, APPROVED + " -> " + COMPLETED);
        return claim;
    }

    /** FR-10: staff change a claim's risk tier with a documented reason. */
    @Transactional
    public Claim escalateRiskTier(Long staffId, Long claimId, RiskTier newTier, String reason) {
        String why = required(reason, "Escalation reason is required (FR-10).");
        if (newTier == null) {
            throw error(HttpStatus.BAD_REQUEST, "Choose a risk tier (LOW, MEDIUM or HIGH).");
        }
        Claim claim = findClaim(claimId);
        assertNotInvolved(staffId, claim, reportPort.getReportById(claim.getFoundReportId()), "CLAIM-" + claimId);
        if (CLOSED.contains(claim.getStatus())) {
            throw error(HttpStatus.CONFLICT, "The risk tier of a closed claim cannot be changed (current status: "
                    + claim.getStatus() + ").");
        }
        claim.setRiskTier(newTier);
        claim.setEscalationReason(why);
        opsPort.recordAuditEvent(staffId, "RISK_ESCALATED", "CLAIM-" + claimId, newTier.name());
        return claimRepo.save(claim);
    }

    /**
     * T09: a reviewer may not act on a case they are part of: as the claimant, as the finder who
     * filed the found report, or as a claimant of any claim on the same found item.
     */
    private void assertNotInvolved(Long staffId, Claim claim, ReportSummaryDto foundReport, String caseRef) {
        boolean involved = (claim != null && staffId.equals(claim.getClaimantId()))
                || staffId.equals(foundReport.authorId())
                || !claimRepo.findByClaimantIdAndFoundReportId(staffId, foundReport.reportId()).isEmpty();
        if (involved) {
            opsPort.recordAuditEvent(staffId, "UNAUTHORIZED_SELF_REVIEW_ATTEMPT", caseRef, "DENIED");
            throw error(HttpStatus.FORBIDDEN, "You cannot review or act on a case you are involved in (T09).");
        }
    }

    private static void assertOpen(ClaimStatus status) {
        if (CLOSED.contains(status)) {
            String hint = status == REJECTED ? " Use RECONSIDER to reopen it first." : "";
            throw error(HttpStatus.CONFLICT, "This claim is already closed (status: " + status + ")." + hint);
        }
    }

    private Claim findClaim(Long claimId) {
        return claimRepo.findById(claimId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Claim not found."));
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw error(HttpStatus.BAD_REQUEST, message);
        return value.trim();
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
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
