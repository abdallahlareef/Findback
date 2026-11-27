# Member 2 requirements audit - 27 November 2026

## Decision

**Core implementation exists; Member 2 is not yet fully signed off.** Passing tests are evidence for the covered behavior, not proof that every assignment requirement or team integration is complete. This replaces earlier statements that implied the whole Member 2 deliverable was finished.

Reviewed local base: `6288381` on `fix/member2-shared-login`, plus the audit fixes below. No new remote merge is asserted. Requirements source: the supplied four-member workload allocation (Member 2: FR-04, FR-07, FR-08, FR-09), proposal sections 4.2-4.5, 4.12, and test scenarios T02/T04-T08/T13. Original PDFs are unchanged.

## Confirmed and fixed in this audit

1. **Stale first approval after evidence changed.** Member 3's newer HIGH-risk two-reviewer flow records a first approval while the claim remains UNDER_REVIEW. EvidenceService previously invalidated only a final APPROVED state. A finder correction could therefore leave the first reviewer attached to an old evidence snapshot. The shared evidence hold method now places such claims ON_HOLD, clears the first approval through Claim.setStatus, and records a review/audit event. Supplements, supplement requests and manual/staff corrections use the same method. The focused regression reproduces a finder correction after first approval.
2. **Closed claim still offered a supplement.** The claimant evidence response previously always reported UnderReview/MoreInformationRequested, even after rejection, withdrawal or completion. It now returns the safe terminal status and hides the pending submission action. The persisted pair counter/request is preserved for authorized reopening; the server still refuses submissions on the closed claim. Regression covers all three terminal statuses.
3. **Claim selection while a request was in progress.** Disable the claim-number input while busy so an old response cannot be shown under a newly typed claim number.

The first two regression tests failed before the fixes and passed afterward.

## Requirement matrix

| Requirement / deliverable | Evidence found | Status |
|---|---|---|
| FR-04 private evidence separated from discovery | Evidence DTOs are separate from public report DTOs; ownership and staff/party checks in EvidenceService | Implemented; existing access tests pass |
| FR-04 frozen baseline and append-only history | EvidenceVersion fields non-updatable; baseline replay is idempotent; correction streams retain earlier versions | Implemented; tests pass |
| FR-07 Match / Unknown / Contradiction | Four-character normalized suffix comparison; missing values and narrative text remain Unknown; manual review with reason | Implemented; tests pass |
| FR-08 strength and distinct clues | Distinct clue-group counting, contradiction overrides Strong, critical conflicts hold cases | Implemented; tests pass |
| FR-08 staff-only summaries and provenance | Staff endpoints expose comparisons; ordinary views hide them and staff-only correction notes; timestamped versions persisted | Core implemented; traceability presentation/persistence gaps below |
| FR-09 initial + two supplements | Persistent account/found-report pair; UUID replay protection; closed-case writes denied; counter survives claim/report recreation | Implemented; H2 concurrency and restart tests pass |
| Shared login/report integration | Member 1 AuthContext, common request client, protected evidence route and role-specific layouts | Fixed in preceding commit 6288381; browser regression passed with mocked API |
| Database/ER documentation | Logical Mermaid relationships and API contract in MEMBER2-INTEGRATION.md | Present; migration/release checks still pending |
| Integrated acceptance/demo | Synthetic browser checks and backend integration tests | Partial; real PostgreSQL and M4-dependent checks remain |

## Remaining work - do not mark these complete

### Member 2 work

- **Comparison-history traceability:** manual clue-group names are used to calculate distinct matches but are not stored on VerificationAttempt. The database retains reviewer, timestamp, version IDs, outcomes and reason; the staff history UI currently shows only strength, count and reason, and the Summary DTO omits the timestamp. Persist normalized clue groups and expose/render reviewer, time, source version IDs and individual outcomes for an explainable history. Preserve legacy rows as unknown rather than inventing old group values. Re-test staff-only visibility.
- **Versioned database changes:** no Flyway/Liquibase or versioned Member 2 migration was found. Runtime configuration uses Hibernate ddl-auto=update. The allocation asks each owner to maintain migrations. Agree the shared migration mechanism with Member 1, add the m2_ schema/constraints and any history columns, then test an upgrade on a disposable database.
- **Real PostgreSQL verification:** this audit ran H2 tests. The five opt-in PostgreSQL concurrency tests were skipped because no dedicated test database was configured. Add/run evidence-specific duplicate-supplement and evidence-change/approval concurrency checks on a disposable PostgreSQL database; do not point destructive test setup at the working findback database.
- **Final demonstration and updated handoff:** run the full shared UI against a real test backend as finder, claimant and independent staff, including two supplements, denial of a third, closed/reopened claims, correction after first/final approval and non-leakage. The previous mocked browser test validates navigation/session wiring, not all real API/database behavior. Update the handoff PDF after the remaining items are verified; the earlier PDF is not a completion certificate.

### Shared integration - coordinate ownership

- **M1 + M2: baseline-before-search rule missing.** ReportController gates `/{id}/candidates` through requireDiscoveryBaseline, but `/api/reports/search` calls ReportService.search directly after authentication. SearchPage supplies no related report ID, and ReportHttpTests accepts search from a fresh account without baseline evidence. This does not return raw private evidence, but it violates the proposal's FR-17 search gate and independent-submission sequence. Member 1 owns search; agree the relevant-report selection/API contract and enforce the baseline server-side with UI guidance and regression tests.
- **M4 + M2/M3: operations are still mocks.** MockCrossModuleAdapters logs audit/notifications/revocation. Its handover check accepts any nonblank code. Real durable audit, notifications and one-time credential verification/revocation must be integrated before the complete return flow can be signed off. Test evidence-triggered holds against actual credential revocation. This is a concrete current dependency, not a completed security feature.
- **Shared release checks:** storage permissions, retention/backup and HTTPS need the deployment setup. The current single-server evidence throttle is not a shared multi-instance throttle. These limitations are already documented, not evidence of completed deployment.
- **Existing frontend lint:** full lint previously reported two errors in unchanged AuthContext.tsx and CandidatesPage.tsx; the Member 2 changed files pass. Team should resolve them before a clean release gate.

## Verification from this audit

- Two focused tests: both failed before the fixes, both passed afterward.
- Full current Maven run: **156 total, 151 passed, 5 skipped, 0 failures, 0 errors**. Five skips are opt-in PostgreSQL concurrency tests. Historical XML files in target/surefire-reports may remain from older runs; totals here use the current Maven output.
- Member 2 portion: 20 EvidenceIntegrationTest cases + one rate-limit test + one restart test = **22 passed**.
- Frontend production build passed; changed evidence component passed ESLint.
- Earlier shared-session Playwright regression remains the recorded mocked-API browser verification; not rerun or represented as a full real-backend acceptance test in this audit.
- No production/team database modified. No new audit changes pushed or merged by this audit.

## Next order

1. Review the audit fixes, then finish Member 2 comparison-history traceability.
2. Agree search gating and migration boundaries with Member 1.
3. Run dedicated PostgreSQL and real-backend UI tests, connect M4 operations when supplied.
4. Update the handoff/test evidence, obtain team review, then sign off Member 2's integrated deliverable.
