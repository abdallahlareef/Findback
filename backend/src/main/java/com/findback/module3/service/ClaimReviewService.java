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
    /** admin_review.reason, physical_check_result and custody_event.condition_notes are 255 characters. */
    public static final int NOTE_MAX_LENGTH = 255;
    /** Leaves room for the "MEDIUM -> HIGH: " prefix stored in the decision record. */
    public static final int RISK_REASON_MAX_LENGTH = 200;
    public static final int RECONSIDERATION_MAX_LENGTH = 1000;

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
                    claim.isReconsiderationPending(), claim.getReconsiderationReason(), claim.getReconsiderationRequestedAt(),
                    claim.getCreatedAt(), claim.getUpdatedAt()));
        }
        // Claimants' reconsideration requests first, so staff see the "message" straight away.
        queue.sort(Comparator.comparing((ReviewCaseView c) -> !c.reconsiderationPending()).thenComparing(ReviewCaseView::claimId));
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

    /**
     * A claimant asks staff to look again at a REJECTED claim because they have new proof. Allowed
     * once per claim; it is the only way back after a rejection (a new claim for the same item is
     * refused). Staff see the request flagged at the top of the review queue.
     */
    @Transactional
    public Claim requestReconsideration(Long claimId, Long claimantId, String reason) {
        String why = requiredText(reason, RECONSIDERATION_MAX_LENGTH,
                "Explain what new proof you have so staff can decide whether to reopen the claim.");
        Claim claim = findClaim(claimId);
        if (!claim.getClaimantId().equals(claimantId)) {
            throw error(HttpStatus.FORBIDDEN, "You can only request reconsideration of your own claim.");
        }
        if (claim.getStatus() != REJECTED) {
            throw error(HttpStatus.CONFLICT, "Only rejected claims can be reconsidered (current status: " + claim.getStatus() + ").");
        }
        if (claim.getReconsiderationRequestedAt() != null) {
            throw error(HttpStatus.CONFLICT, "You have already requested reconsideration for this claim.");
        }
        if (itemReturned(claim.getFoundReportId())) {
            throw error(HttpStatus.CONFLICT, "This item has already been returned to its verified owner.");
        }
        claim.requestReconsideration(why);
        claimRepo.save(claim);
        opsPort.recordAuditEvent(claimantId, "RECONSIDERATION_REQUESTED", "CLAIM-" + claimId, "PENDING");
        return claim;
    }

    /** Whether the claimant may still ask for reconsideration (shown as a button in their claim list). */
    public boolean reconsiderationAvailable(Claim claim) {
        return claim.getStatus() == REJECTED
                && claim.getReconsiderationRequestedAt() == null
                && !itemReturned(claim.getFoundReportId());
    }

    private boolean itemReturned(Long foundReportId) {
        return custodyStateRepo.findById(foundReportId)
                .map(s -> s.getCustodyStatus() == CustodyStatus.RETURNED)
                .orElse(false);
    }

    /** Case-history search list: every claim except those the reviewer is involved in (T09). */
    @Transactional(readOnly = true)
    public List<CaseHistoryView.CaseSummary> getHistoryCases(Long staffId) {
        List<Claim> claims = claimRepo.findAll();
        Set<Long> itemsClaimedByReviewer = claims.stream()
                .filter(c -> staffId.equals(c.getClaimantId()))
                .map(Claim::getFoundReportId)
                .collect(Collectors.toSet());
        Map<Long, ReportSummaryDto> foundReports = new HashMap<>();
        List<CaseHistoryView.CaseSummary> result = new ArrayList<>();
        for (Claim claim : claims) {
            if (itemsClaimedByReviewer.contains(claim.getFoundReportId())) continue;
            ReportSummaryDto found = foundReports.computeIfAbsent(claim.getFoundReportId(), reportPort::getReportById);
            if (staffId.equals(found.authorId())) continue;
            result.add(new CaseHistoryView.CaseSummary(claim.getClaimId(), directoryPort.displayName(claim.getClaimantId()),
                    claim.getFoundReportId(), found.title(), claim.getStatus(), claim.getRiskTier(),
                    claim.getCreatedAt(), claim.getUpdatedAt()));
        }
        result.sort(Comparator.comparing(CaseHistoryView.CaseSummary::updatedAt).reversed());
        return result;
    }

    /** FR-12 audit view: who did what to this claim and its item, when, and why. */
    @Transactional(readOnly = true)
    public CaseHistoryView getCaseHistory(Long staffId, Long claimId) {
        Claim claim = findClaim(claimId);
        ReportSummaryDto found = reportPort.getReportById(claim.getFoundReportId());
        assertNotInvolved(staffId, claim, found, "CLAIM-" + claimId);

        Map<Long, String> names = new HashMap<>();
        Function<Long, String> name = id -> id == null ? "System" : names.computeIfAbsent(id, directoryPort::displayName);
        String claimantName = name.apply(claim.getClaimantId());

        List<CaseHistoryView.Event> events = new ArrayList<>();
        events.add(new CaseHistoryView.Event(claim.getCreatedAt(), "CLAIM", "CLAIM_SUBMITTED", claimantName,
                "Claim submitted for found item #" + claim.getFoundReportId(), null));
        for (AdminReview review : reviewRepo.findByClaimIdOrderByReviewedAtDesc(claimId)) {
            String change = review.getPreviousStatus().equals(review.getNewStatus())
                    ? "Status unchanged (" + review.getNewStatus() + ")"
                    : review.getPreviousStatus() + " → " + review.getNewStatus();
            String details = review.getPhysicalCheckResult() == null || review.getPhysicalCheckResult().isBlank()
                    ? review.getReason()
                    : review.getReason() + " | Physical check: " + review.getPhysicalCheckResult();
            events.add(new CaseHistoryView.Event(review.getReviewedAt(), "DECISION", review.getDecision(),
                    name.apply(review.getReviewerId()), change, details));
        }
        for (CustodyEvent event : custodyEventRepo.findByFoundReportIdOrderByRecordedAtDesc(claim.getFoundReportId())) {
            events.add(new CaseHistoryView.Event(event.getRecordedAt(), "CUSTODY", event.getEventType(),
                    name.apply(event.getStaffId()), "Item #" + event.getFoundReportId() + " · storage " + event.getStorageReference(),
                    event.getConditionNotes()));
        }
        if (claim.getReconsiderationRequestedAt() != null) {
            events.add(new CaseHistoryView.Event(claim.getReconsiderationRequestedAt(), "CLAIM", "RECONSIDERATION_REQUESTED",
                    claimantName, "Claimant asked staff to reconsider", claim.getReconsiderationReason()));
        }
        if (claim.getStatus() == WITHDRAWN) {
            events.add(new CaseHistoryView.Event(claim.getUpdatedAt(), "CLAIM", "CLAIM_WITHDRAWN", claimantName,
                    "Claim withdrawn by the claimant", null));
        }
        events.sort(Comparator.comparing(CaseHistoryView.Event::at));

        return new CaseHistoryView(claim.getClaimId(), claim.getClaimantId(), claimantName, claim.getLostReportId(),
                claim.getFoundReportId(), found.title(), claim.getStatus(), claim.getRiskTier(), events);
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
        boolean rejectedBefore = claimRepo.findByClaimantIdAndFoundReportId(claimantId, foundReportId).stream()
                .anyMatch(c -> c.getStatus() == REJECTED);
        if (rejectedBefore) {
            throw error(HttpStatus.CONFLICT, "Your earlier claim for this item was not approved. Open it under My claims "
                    + "and use Request reconsideration instead of submitting a new claim.");
        }

        Claim claim = new Claim();
        claim.setClaimantId(claimantId);
        claim.setLostReportId(lostReportId);
        claim.setFoundReportId(foundReportId);
        // FR-10: neither party can lower the tier below what either report or either category implies.
        claim.setRiskTier(RiskTier.highest(
                RiskTier.fromCategory(foundReport.category()), RiskTier.fromCategory(lostReport.category()),
                RiskTier.parse(foundReport.riskLevel()), RiskTier.parse(lostReport.riskLevel())));
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
        String physicalNotes = optionalText(physicalCheckNotes, NOTE_MAX_LENGTH);
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
                if (itemReturned(claim.getFoundReportId())) {
                    throw error(HttpStatus.CONFLICT, "This item has already been returned to its verified owner.");
                }
                if (claim.isReconsiderationPending()) {
                    claim.resolveReconsideration("ACCEPTED");
                }
                nextStatus = UNDER_REVIEW;
                opsPort.sendSafeNotification(claim.getClaimantId(), "Your claim #" + claimId + " has been reopened for staff review.");
            }
            case "DECLINE_RECONSIDERATION" -> {
                if (prevStatus != REJECTED || !claim.isReconsiderationPending()) {
                    throw error(HttpStatus.CONFLICT, "There is no pending reconsideration request on this claim.");
                }
                claim.resolveReconsideration("DECLINED");
                nextStatus = REJECTED;
                opsPort.sendSafeNotification(claim.getClaimantId(), "Staff reviewed your reconsideration request for claim #"
                        + claimId + ". The original decision stands.");
            }
            default -> throw error(HttpStatus.BAD_REQUEST, "Unsupported review decision: " + action);
        }

        claim.setStatus(nextStatus);
        claimRepo.save(claim);

        reviewRepo.save(new AdminReview(claimId, reviewerId, decision,
                prevStatus.name(), nextStatus.name(), why, physicalNotes));

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
        String why = requiredText(reason, RISK_REASON_MAX_LENGTH, "Escalation reason is required (FR-10).");
        if (newTier == null) {
            throw error(HttpStatus.BAD_REQUEST, "Choose a risk tier (LOW, MEDIUM or HIGH).");
        }
        Claim claim = findClaim(claimId);
        assertNotInvolved(staffId, claim, reportPort.getReportById(claim.getFoundReportId()), "CLAIM-" + claimId);
        if (CLOSED.contains(claim.getStatus())) {
            throw error(HttpStatus.CONFLICT, "The risk tier of a closed claim cannot be changed (current status: "
                    + claim.getStatus() + ").");
        }
        RiskTier previous = claim.getRiskTier();
        // Raise-only: lowering a tier would let a claim skip the checks its item category requires.
        if (newTier.compareTo(previous) <= 0) {
            throw error(HttpStatus.CONFLICT, "The risk tier can only be raised (currently " + previous + ").");
        }
        claim.setRiskTier(newTier);
        claim.setEscalationReason(why);
        reviewRepo.save(new AdminReview(claimId, staffId, "RISK_RAISED", claim.getStatus().name(), claim.getStatus().name(),
                previous + " -> " + newTier + ": " + why, null));
        opsPort.recordAuditEvent(staffId, "RISK_ESCALATED", "CLAIM-" + claimId, previous + " -> " + newTier);
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
        return requiredText(value, NOTE_MAX_LENGTH, message);
    }

    private static String requiredText(String value, int maxLength, String message) {
        if (value == null || value.isBlank()) throw error(HttpStatus.BAD_REQUEST, message);
        String text = value.trim();
        if (text.length() > maxLength) {
            throw error(HttpStatus.BAD_REQUEST, "Text must be at most " + maxLength + " characters (got " + text.length() + ").");
        }
        return text;
    }

    private static String optionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        return requiredText(value, maxLength, "");
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
