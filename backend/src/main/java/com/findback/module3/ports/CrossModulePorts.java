package com.findback.module3.ports;

public interface CrossModulePorts {
    record ReportSummaryDto(Long reportId, Long authorId, String type, String category,
                            String title, String zone, boolean baselineEvidenceSaved, String riskLevel) {}

    interface ReportLookupPort {
        ReportSummaryDto getReportById(Long reportId);

        /**
         * Same as {@link #getReportById(Long)} but also takes a row lock on the report that is held
         * until the surrounding transaction ends. Claim creation and custody intake lock the found
         * report this way so that concurrent writes for one item are serialized (FR-06, T10).
         */
        default ReportSummaryDto lockReportForUpdate(Long reportId) { return getReportById(reportId); }
    }

    /**
     * @param matchCount          distinct matching private characteristics (FR-10 thresholds use this)
     * @param identifierMatched   the serial/identifier characteristic is a confirmed MATCH (required for HIGH risk)
     */
    record DoveSummaryDto(Long claimId, String strengthLabel,
                          int matchCount, int unknownCount, int contradictionCount,
                          boolean hasCriticalIdentifierConflict, int submissionAttemptsUsed,
                          boolean identifierMatched) {}

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

    /** Safe (non-evidence) fields of a found report, for the staff custody desk. */
    record FoundItemDto(Long reportId, Long finderId, String category, String colour, String campusZone,
                        java.time.LocalDate eventDate, String reportStatus, String riskLevel) {}

    /** Member 1 boundary: every found report, claimed or not, so staff can log items handed in at the desk. */
    interface FoundItemDirectoryPort {
        java.util.List<FoundItemDto> listFoundItems();
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
