package com.findback.module3.ports;

public interface CrossModulePorts {
    record ReportSummaryDto(Long reportId, Long authorId, String type, String category,
                            String title, String zone, boolean baselineEvidenceSaved) {}

    interface ReportLookupPort {
        ReportSummaryDto getReportById(Long reportId);
    }

    record DoveSummaryDto(Long claimId, String strengthLabel,
                          int matchCount, int unknownCount, int contradictionCount,
                          boolean hasCriticalIdentifierConflict, int submissionAttemptsUsed) {}

    interface DoveEvaluationPort {
        DoveSummaryDto evaluateOrGetSummary(Long claimId, Long lostReportId, Long foundReportId);
    }

    interface OperationsPort {
        void recordAuditEvent(Long actorId, String action, String caseRef, String outcome);
        void sendSafeNotification(Long recipientUserId, String safeMessage);
        void revokeActiveHandoverCredential(Long claimId, String reason);
    }
}
