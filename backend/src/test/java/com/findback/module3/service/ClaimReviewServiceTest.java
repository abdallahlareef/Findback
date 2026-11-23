package com.findback.module3.service;

import com.findback.module3.domain.Claim;
import com.findback.module3.domain.ClaimStatus;
import com.findback.module3.domain.CustodyStatus;
import com.findback.module3.domain.ItemCustodyState;
import com.findback.module3.domain.RiskTier;
import com.findback.module3.ports.CrossModulePorts.DoveEvaluationPort;
import com.findback.module3.ports.CrossModulePorts.DoveSummaryDto;
import com.findback.module3.ports.CrossModulePorts.OperationsPort;
import com.findback.module3.ports.CrossModulePorts.ReportLifecyclePort;
import com.findback.module3.ports.CrossModulePorts.ReportLookupPort;
import com.findback.module3.ports.CrossModulePorts.ReportSummaryDto;
import com.findback.module3.repository.AdminReviewRepository;
import com.findback.module3.repository.ClaimRepository;
import com.findback.module3.repository.CustodyEventRepository;
import com.findback.module3.repository.ItemCustodyStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ClaimReviewServiceTest {
    private static final ReportSummaryDto LOST = new ReportSummaryDto(510L, 2012L, "LOST", "EARBUDS", "Earbuds", "Library", true, null);
    private static final ReportSummaryDto FOUND = new ReportSummaryDto(610L, 99L, "FOUND", "EARBUDS", "Earbuds", "Library", true, null);
    private static final Long STAFF = 9001L;

    @Mock private ReportLookupPort reportPort;
    @Mock private OperationsPort opsPort;
    @Mock private ClaimRepository claimRepository;
    @Mock private ItemCustodyStateRepository custodyRepository;
    @Mock private CustodyEventRepository custodyEventRepository;
    @Mock private AdminReviewRepository reviewRepository;
    @Mock private ReportLifecyclePort reportLifecycle;
    @Mock private DoveEvaluationPort dovePort;

    @InjectMocks
    private ClaimReviewService claimReviewService;

    private static Claim claim(ClaimStatus status) {
        Claim claim = new Claim();
        claim.setClaimantId(2012L);
        claim.setLostReportId(510L);
        claim.setFoundReportId(610L);
        claim.setStatus(status);
        return claim;
    }

    private static void assertStatus(HttpStatus expected, ResponseStatusException exception) {
        assertEquals(expected, exception.getStatusCode());
    }

    @Test
    void shouldPreventDuplicateActiveClaims_FR06() {
        when(reportPort.getReportById(510L)).thenReturn(LOST);
        when(reportPort.lockReportForUpdate(610L)).thenReturn(FOUND);
        when(claimRepository.existsActiveClaimForPair(2012L, 610L)).thenReturn(true);

        var exception = assertThrows(ResponseStatusException.class, () -> claimReviewService.createClaim(2012L, 510L, 610L));

        assertStatus(HttpStatus.CONFLICT, exception);
        verify(claimRepository, never()).save(any());
    }

    @Test
    void shouldPreventStaffSelfReview_T09() {
        when(reportPort.getReportById(610L)).thenReturn(FOUND);
        when(claimRepository.findById(anyLong())).thenReturn(Optional.of(claim(ClaimStatus.UNDER_REVIEW)));

        var exception = assertThrows(ResponseStatusException.class,
                () -> claimReviewService.recordStaffDecision(2012L, 1L, "APPROVE", "Self approval", "Looks good"));

        assertStatus(HttpStatus.FORBIDDEN, exception);
    }

    @Test
    void staffWithCompetingClaimCannotDecide_T09() {
        when(reportPort.getReportById(610L)).thenReturn(FOUND);
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim(ClaimStatus.UNDER_REVIEW)));
        when(claimRepository.findByClaimantIdAndFoundReportId(STAFF, 610L)).thenReturn(List.of(new Claim()));

        var exception = assertThrows(ResponseStatusException.class,
                () -> claimReviewService.recordStaffDecision(STAFF, 1L, "REJECT", "Competing claimant", null));

        assertStatus(HttpStatus.FORBIDDEN, exception);
        verify(opsPort).recordAuditEvent(STAFF, "UNAUTHORIZED_SELF_REVIEW_ATTEMPT", "CLAIM-1", "DENIED");
    }

    @Test
    void involvedStaffCannotEscalateRisk_T09() {
        Claim claim = claim(ClaimStatus.UNDER_REVIEW);
        when(reportPort.getReportById(610L)).thenReturn(FOUND);
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));

        var exception = assertThrows(ResponseStatusException.class,
                () -> claimReviewService.escalateRiskTier(2012L, 1L, RiskTier.HIGH, "Change my own claim's tier"));

        assertStatus(HttpStatus.FORBIDDEN, exception);
        assertEquals(RiskTier.LOW, claim.getRiskTier());
        verify(claimRepository, never()).save(any());
    }

    @Test
    void staffCannotReviveWithdrawnClaim() {
        when(reportPort.getReportById(610L)).thenReturn(FOUND);
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim(ClaimStatus.WITHDRAWN)));

        var exception = assertThrows(ResponseStatusException.class,
                () -> claimReviewService.recordStaffDecision(STAFF, 1L, "HOLD", "Needs more proof", null));

        assertStatus(HttpStatus.CONFLICT, exception);
    }

    @Test
    void reservedItemCannotBeReIntaken_T10() {
        ItemCustodyState reserved = new ItemCustodyState();
        reserved.setFoundReportId(610L);
        reserved.setCustodyStatus(CustodyStatus.RESERVED);
        reserved.setReservedByClaimId(1L);
        when(reportPort.lockReportForUpdate(610L)).thenReturn(FOUND);
        when(custodyRepository.findByIdForUpdate(610L)).thenReturn(Optional.of(reserved));

        var exception = assertThrows(ResponseStatusException.class,
                () -> claimReviewService.recordCustodyIntake(STAFF, 610L, "LOCKER-2", "Second intake"));

        assertStatus(HttpStatus.CONFLICT, exception);
        assertEquals(CustodyStatus.RESERVED, reserved.getCustodyStatus());
        assertEquals(1L, reserved.getReservedByClaimId());
        verify(custodyRepository, never()).save(any());
        verify(custodyEventRepository, never()).save(any());
    }

    private static ItemCustodyState disputed() {
        ItemCustodyState state = new ItemCustodyState();
        state.setFoundReportId(610L);
        state.setCustodyStatus(CustodyStatus.IN_CUSTODY);
        state.setStorageReference("LOCKER-1");
        state.setDisputeBlocked(true);
        return state;
    }

    @Test
    void resolvingDisputeClearsBlockAndRecordsEvent() {
        ItemCustodyState state = disputed();
        when(reportPort.lockReportForUpdate(610L)).thenReturn(FOUND);
        when(custodyRepository.findByIdForUpdate(610L)).thenReturn(Optional.of(state));

        claimReviewService.resolveDispute(STAFF, 610L, "Competing owner withdrew after ID check");

        assertEquals(false, state.isDisputeBlocked());
        verify(custodyRepository).save(state);
        verify(custodyEventRepository).save(any());
        verify(opsPort).recordAuditEvent(STAFF, "DISPUTE_RESOLVED", "FOUND-610", "UNBLOCKED");
    }

    @Test
    void resolvingDisputeRequiresReason_FR12() {
        var exception = assertThrows(ResponseStatusException.class, () -> claimReviewService.resolveDispute(STAFF, 610L, " "));

        assertStatus(HttpStatus.BAD_REQUEST, exception);
    }

    @Test
    void finderCannotResolveDisputeOnOwnItem_T09() {
        ItemCustodyState state = disputed();
        when(reportPort.lockReportForUpdate(610L)).thenReturn(FOUND);

        var exception = assertThrows(ResponseStatusException.class,
                () -> claimReviewService.resolveDispute(99L, 610L, "Clearing my own item"));

        assertStatus(HttpStatus.FORBIDDEN, exception);
        assertEquals(true, state.isDisputeBlocked());
        verify(custodyRepository, never()).save(any());
    }

    @Test
    void resolvingUnblockedItemIsConflict() {
        ItemCustodyState state = disputed();
        state.setDisputeBlocked(false);
        when(reportPort.lockReportForUpdate(610L)).thenReturn(FOUND);
        when(custodyRepository.findByIdForUpdate(610L)).thenReturn(Optional.of(state));

        var exception = assertThrows(ResponseStatusException.class,
                () -> claimReviewService.resolveDispute(STAFF, 610L, "Nothing to resolve"));

        assertStatus(HttpStatus.CONFLICT, exception);
    }

    private static Claim claimWithId(long id, long claimantId, ClaimStatus status) {
        Claim claim = claim(status);
        claim.setClaimantId(claimantId);
        ReflectionTestUtils.setField(claim, "claimId", id);
        return claim;
    }

    private static ItemCustodyState reservedFor(long claimId) {
        ItemCustodyState state = new ItemCustodyState();
        state.setFoundReportId(610L);
        state.setCustodyStatus(CustodyStatus.RESERVED);
        state.setStorageReference("LOCKER-1");
        state.setReservedByClaimId(claimId);
        return state;
    }

    @Test
    void handoverRequiresApprovedClaim() {
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claimWithId(1L, 2012L, ClaimStatus.UNDER_REVIEW)));
        when(reportPort.lockReportForUpdate(610L)).thenReturn(FOUND);

        var exception = assertThrows(ResponseStatusException.class,
                () -> claimReviewService.completeHandover(STAFF, 1L, "CODE-1", "Student ID checked"));

        assertStatus(HttpStatus.CONFLICT, exception);
        verify(opsPort, never()).verifyAndConsumeHandoverCredential(anyLong(), any());
    }

    @Test
    void handoverRequiresIdentityCheckNotes_FR12() {
        var exception = assertThrows(ResponseStatusException.class,
                () -> claimReviewService.completeHandover(STAFF, 1L, "CODE-1", ""));

        assertStatus(HttpStatus.BAD_REQUEST, exception);
    }

    @Test
    void handoverWithInvalidCodeChangesNothing() {
        Claim claim = claimWithId(1L, 2012L, ClaimStatus.APPROVED);
        ItemCustodyState state = reservedFor(1L);
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
        when(reportPort.lockReportForUpdate(610L)).thenReturn(FOUND);
        when(custodyRepository.findByIdForUpdate(610L)).thenReturn(Optional.of(state));
        when(opsPort.verifyAndConsumeHandoverCredential(1L, "WRONG")).thenReturn(false);

        var exception = assertThrows(ResponseStatusException.class,
                () -> claimReviewService.completeHandover(STAFF, 1L, "WRONG", "Student ID checked"));

        assertStatus(HttpStatus.FORBIDDEN, exception);
        assertEquals(ClaimStatus.APPROVED, claim.getStatus());
        assertEquals(CustodyStatus.RESERVED, state.getCustodyStatus());
        verify(reportLifecycle, never()).closeReport(anyLong());
        verify(opsPort).recordAuditEvent(STAFF, "HANDOVER_CODE_REJECTED", "CLAIM-1", "DENIED");
    }

    @Test
    void handoverCompletesClaimReturnsItemAndClosesEverythingElse() {
        Claim claim = claimWithId(1L, 2012L, ClaimStatus.APPROVED);
        Claim competing = claimWithId(2L, 3000L, ClaimStatus.ON_HOLD);
        Claim alreadyWithdrawn = claimWithId(3L, 4000L, ClaimStatus.WITHDRAWN);
        ItemCustodyState state = reservedFor(1L);
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
        when(reportPort.lockReportForUpdate(610L)).thenReturn(FOUND);
        when(custodyRepository.findByIdForUpdate(610L)).thenReturn(Optional.of(state));
        when(opsPort.verifyAndConsumeHandoverCredential(1L, "CODE-1")).thenReturn(true);
        when(claimRepository.findByFoundReportId(610L)).thenReturn(List.of(claim, competing, alreadyWithdrawn));

        claimReviewService.completeHandover(STAFF, 1L, "CODE-1", "Student ID checked");

        assertEquals(ClaimStatus.COMPLETED, claim.getStatus());
        assertEquals(CustodyStatus.RETURNED, state.getCustodyStatus());
        assertEquals(ClaimStatus.REJECTED, competing.getStatus());
        assertEquals(ClaimStatus.WITHDRAWN, alreadyWithdrawn.getStatus());
        verify(reportLifecycle).closeReport(610L);
        verify(reportLifecycle).closeReport(510L);
        verify(opsPort).recordAuditEvent(STAFF, "HANDOVER_COMPLETED", "CLAIM-1", "APPROVED -> COMPLETED");
    }

    @Test
    void claimTierIsTheHighestOfBothReportsAndCategories_FR10() {
        ReportSummaryDto lowLost = new ReportSummaryDto(510L, 2012L, "LOST", "EARBUDS", "Earbuds", "Library", true, "LOW");
        ReportSummaryDto highFound = new ReportSummaryDto(610L, 99L, "FOUND", "EARBUDS", "Earbuds", "Library", true, "HIGH");
        when(reportPort.getReportById(510L)).thenReturn(lowLost);
        when(reportPort.lockReportForUpdate(610L)).thenReturn(highFound);
        when(claimRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(dovePort.evaluateOrGetSummary(any(), any(), any()))
                .thenReturn(new DoveSummaryDto(1L, "WEAK", 1, 2, 0, false, 1));

        Claim claim = claimReviewService.createClaim(2012L, 510L, 610L);

        assertEquals(RiskTier.HIGH, claim.getRiskTier());
    }

    @Test
    void rejectedClaimantMustUseReconsiderationInsteadOfANewClaim() {
        when(reportPort.getReportById(510L)).thenReturn(LOST);
        when(reportPort.lockReportForUpdate(610L)).thenReturn(FOUND);
        when(claimRepository.findByClaimantIdAndFoundReportId(2012L, 610L)).thenReturn(List.of(claim(ClaimStatus.REJECTED)));

        var exception = assertThrows(ResponseStatusException.class, () -> claimReviewService.createClaim(2012L, 510L, 610L));

        assertStatus(HttpStatus.CONFLICT, exception);
        verify(claimRepository, never()).save(any());
    }

    @Test
    void riskTierCanOnlyBeRaised_FR10() {
        Claim claim = claim(ClaimStatus.UNDER_REVIEW);
        claim.setRiskTier(RiskTier.MEDIUM);
        when(reportPort.getReportById(610L)).thenReturn(FOUND);
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));

        assertStatus(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> claimReviewService.escalateRiskTier(STAFF, 1L, RiskTier.LOW, "Looks harmless")));
        assertStatus(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> claimReviewService.escalateRiskTier(STAFF, 1L, RiskTier.MEDIUM, "Same tier")));
        assertEquals(RiskTier.MEDIUM, claim.getRiskTier());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void reconsiderationIsOncePerRejectedClaim() {
        Claim claim = claimWithId(1L, 2012L, ClaimStatus.REJECTED);
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));

        claimReviewService.requestReconsideration(1L, 2012L, "I found the box with the serial printed on it");

        assertEquals("PENDING", claim.getReconsiderationStatus());
        assertStatus(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> claimReviewService.requestReconsideration(1L, 2012L, "Asking again")));
        assertStatus(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> claimReviewService.requestReconsideration(1L, 3000L, "Not my claim")));
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, names = {"UNDER_REVIEW", "ON_HOLD", "APPROVED", "WITHDRAWN", "COMPLETED"})
    void onlyRejectedClaimsCanBeReconsidered(ClaimStatus status) {
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claimWithId(1L, 2012L, status)));

        assertStatus(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> claimReviewService.requestReconsideration(1L, 2012L, "Please look again")));
    }

    @Test
    void noReconsiderationOnceTheItemIsReturned() {
        ItemCustodyState returned = reservedFor(9L);
        returned.setCustodyStatus(CustodyStatus.RETURNED);
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claimWithId(1L, 2012L, ClaimStatus.REJECTED)));
        when(custodyRepository.findById(610L)).thenReturn(Optional.of(returned));

        assertStatus(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> claimReviewService.requestReconsideration(1L, 2012L, "Please look again")));
    }

    @Test
    void staffCanDeclineAPendingReconsideration() {
        Claim claim = claimWithId(1L, 2012L, ClaimStatus.REJECTED);
        claim.requestReconsideration("New proof");
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
        when(reportPort.getReportById(610L)).thenReturn(FOUND);

        claimReviewService.recordStaffDecision(STAFF, 1L, "DECLINE_RECONSIDERATION", "Receipt does not match the item", null);

        assertEquals(ClaimStatus.REJECTED, claim.getStatus());
        assertEquals("DECLINED", claim.getReconsiderationStatus());
    }

    @Test
    void overlongDecisionReasonIsRejectedClearly() {
        var exception = assertThrows(ResponseStatusException.class,
                () -> claimReviewService.recordStaffDecision(STAFF, 1L, "HOLD", "x".repeat(256), null));

        assertStatus(HttpStatus.BAD_REQUEST, exception);
    }

    @Test
    void custodyIntakeRequiresConditionNotes_FR11() {
        var exception = assertThrows(ResponseStatusException.class,
                () -> claimReviewService.recordCustodyIntake(STAFF, 610L, "LOCKER-2", "  "));

        assertStatus(HttpStatus.BAD_REQUEST, exception);
    }

    @Test
    void claimantCannotWithdrawSomeoneElsesClaim_FR18() {
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim(ClaimStatus.UNDER_REVIEW)));

        var exception = assertThrows(ResponseStatusException.class, () -> claimReviewService.withdrawClaim(1L, 3000L));

        assertStatus(HttpStatus.FORBIDDEN, exception);
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, names = {"APPROVED", "REJECTED", "COMPLETED", "WITHDRAWN"})
    void finalOrClosedClaimsCannotBeWithdrawn_FR18(ClaimStatus status) {
        Claim claim = claim(status);
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));

        var exception = assertThrows(ResponseStatusException.class, () -> claimReviewService.withdrawClaim(1L, 2012L));

        assertStatus(HttpStatus.CONFLICT, exception);
        assertEquals(status, claim.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, names = {"SUBMITTED", "UNDER_REVIEW", "ON_HOLD"})
    void pendingClaimsCanBeWithdrawn_FR18(ClaimStatus status) {
        Claim claim = claim(status);
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));

        claimReviewService.withdrawClaim(1L, 2012L);

        assertEquals(ClaimStatus.WITHDRAWN, claim.getStatus());
        verify(claimRepository).save(claim);
    }
}
