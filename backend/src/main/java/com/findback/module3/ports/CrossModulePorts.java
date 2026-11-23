package com.findback.module3.ports;

public interface CrossModulePorts {
    record ReportSummaryDto(Long reportId, Long authorId, String type, String category,
                            String title, String zone, boolean baselineEvidenceSaved) {}

    interface ReportLookupPort {
        ReportSummaryDto getReportById(Long reportId);

        /**
         * Same as {@link #getReportById(Long)} but also takes a row lock on the report that is held
         * until the surrounding transaction ends. Claim creation and custody intake lock the found
         * report this way so that concurrent writes for one item are serialized (FR-06, T10).
         */
        default ReportSummaryDto lockReportForUpdate(Long reportId) { return getReportById(reportId); }
    }

    record DoveSummaryDto(Long claimId, String strengthLabel,
                          int matchCount, int unknownCount, int contradictionCount,
                          boolean hasCriticalIdentifierConflict, int submissionAttemptsUsed) {}

    interface DoveEvaluationPort {
        DoveSummaryDto evaluateOrGetSummary(Long claimId, Long lostReportId, Long foundReportId);
        default boolean hasPendingSupplement(Long claimId) { return false; }
    }

    /** Staff-facing display name for a claimant; never used in claimant-facing responses. */
    interface ClaimantDirectoryPort {
        String displayName(Long userId);
    }

    /** Member 1 boundary: closes reports once their item has been returned to its owner. */
    interface ReportLifecyclePort {
        void closeReport(Long reportId);
    }

    /** Member 4 boundary: audit trail, notifications and one-time handover credentials. */
    interface OperationsPort {
        void recordAuditEvent(Long actorId, String action, String caseRef, String outcome);
        void sendSafeNotification(Long recipientUserId, String safeMessage);
        void issueHandoverCredential(Long claimId, Long claimantId);
        /** True only if the code is the claim's active one-time credential; a valid code is used up. */
        boolean verifyAndConsumeHandoverCredential(Long claimId, String code);
        void revokeActiveHandoverCredential(Long claimId, String reason);
    }
}
