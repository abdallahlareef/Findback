package com.findback.module3.config;

import com.findback.module3.ports.CrossModulePorts.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MockCrossModuleAdapters {
    private static final Logger log = LoggerFactory.getLogger(MockCrossModuleAdapters.class);

    @Bean
    public ReportLookupPort mockReportLookupPort() {
        return reportId -> {
            if (reportId >= 600L) {
                return new ReportSummaryDto(reportId, 99L, "FOUND", "EARBUDS",
                        "Black JBL Tune Earbuds", "Main Library", true);
            }
            return new ReportSummaryDto(reportId, 2012L, "LOST", "EARBUDS",
                    "Black JBL Tune Earbuds", "Main Library", true);
        };
    }

    @Bean
    public DoveEvaluationPort mockDoveEvaluationPort() {
        return (claimId, lostReportId, foundReportId) -> {
            if (lostReportId != null && lostReportId == 509L) {
                return new DoveSummaryDto(claimId, "REVIEW_REQUIRED", 1, 1, 1, true, 2);
            }
            return new DoveSummaryDto(claimId, "STRONG", 3, 0, 0, false, 1);
        };
    }

    @Bean
    public OperationsPort mockOperationsPort() {
        return new OperationsPort() {
            @Override
            public void recordAuditEvent(Long actorId, String action, String caseRef, String outcome) {
                log.info("[M4-AUDIT-MOCK] Actor={} Action={} Case={} Outcome={}", actorId, action, caseRef, outcome);
            }

            @Override
            public void sendSafeNotification(Long recipientUserId, String safeMessage) {
                log.info("[M4-NOTIFY-MOCK] To User #{}: {}", recipientUserId, safeMessage);
            }

            @Override
            public void revokeActiveHandoverCredential(Long claimId, String reason) {
                log.info("[M4-HANDOVER-MOCK] Revoked credential for Claim #{} | Reason: {}", claimId, reason);
            }
        };
    }
}
