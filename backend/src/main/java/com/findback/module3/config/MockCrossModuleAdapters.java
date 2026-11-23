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
    @org.springframework.context.annotation.Profile("module3-standalone-demo")
    public ReportLookupPort mockReportLookupPort() {
        return reportId -> {
            if (reportId >= 600L) {
                return new ReportSummaryDto(reportId, 99L, "FOUND", "EARBUDS",
                        "Black JBL Tune Earbuds", "Main Library", true, null);
            }
            return new ReportSummaryDto(reportId, 2012L, "LOST", "EARBUDS",
                    "Black JBL Tune Earbuds", "Main Library", true, null);
        };
    }

    @Bean
    @org.springframework.context.annotation.Profile("module3-standalone-demo")
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
            public void issueHandoverCredential(Long claimId, Long claimantId) {
                log.info("[M4-HANDOVER-MOCK] Issued one-time credential for Claim #{} to User #{}", claimId, claimantId);
            }

            @Override
            public boolean verifyAndConsumeHandoverCredential(Long claimId, String code) {
                // Placeholder until Member 4's credential service exists: no real code is issued yet,
                // so any non-blank code is accepted. Must be replaced before release.
                log.warn("[M4-HANDOVER-MOCK] Accepting handover code for Claim #{} WITHOUT verification", claimId);
                return code != null && !code.isBlank();
            }

            @Override
            public void revokeActiveHandoverCredential(Long claimId, String reason) {
                log.info("[M4-HANDOVER-MOCK] Revoked credential for Claim #{} | Reason: {}", claimId, reason);
            }
        };
    }
}
