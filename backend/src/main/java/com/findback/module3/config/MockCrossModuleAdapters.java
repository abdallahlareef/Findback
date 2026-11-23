package com.findback.module3.config;

import com.findback.module3.ports.CrossModulePorts.OperationsPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stand-in for Member 4's operations service (audit, notifications, handover credentials) until it
 * exists. Report lookup and DOVE are real: Member 2's EvidenceService implements those ports.
 */
@Configuration
public class MockCrossModuleAdapters {
    private static final Logger log = LoggerFactory.getLogger(MockCrossModuleAdapters.class);

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
