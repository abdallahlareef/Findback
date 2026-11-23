import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { NOTE_MAX_LENGTH, RISK_REASON_MAX_LENGTH, claimsApi, errorMessage, requiredMatches } from './member3Api'
import type { ClaimStatus, DecisionAction, RiskTier, StaffClaimCaseView } from './member3Api'
import { RISK_LEVELS, riskRank } from './riskLevels'

const CLOSED: ClaimStatus[] = ['REJECTED', 'WITHDRAWN', 'COMPLETED']

/** FR-10 requirement shown next to the DOVE counts; the server enforces the same rule. */
function tierRequirement(c: StaffClaimCaseView): string {
  const needed = requiredMatches(c.riskTier)
  const base = `${c.riskTier} risk: at least ${needed} matching private characteristic${needed === 1 ? '' : 's'}`
  return c.riskTier === 'HIGH'
    ? `${base}, including the serial/identifier, plus physical inspection notes and approval by two different staff members.`
    : `${base}.`
}

export function StaffReviewDashboard() {
  const { user } = useAuth()
  const [cases, setCases] = useState<StaffClaimCaseView[]>([])
  const [loading, setLoading] = useState(true)
  const [queueVersion, setQueueVersion] = useState(0)
  const [selectedId, setSelectedId] = useState<number | null>(null)

  const [storageRef, setStorageRef] = useState('')
  const [conditionNotes, setConditionNotes] = useState('')
  const [decisionReason, setDecisionReason] = useState('')
  const [physicalNotes, setPhysicalNotes] = useState('')
  const [riskTier, setRiskTier] = useState<RiskTier>('HIGH')
  const [riskReason, setRiskReason] = useState('')
  const [disputeResolution, setDisputeResolution] = useState('')
  const [handoverCode, setHandoverCode] = useState('')
  const [identityNotes, setIdentityNotes] = useState('')

  const [busy, setBusy] = useState(false)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)
  const [statusBanner, setStatusBanner] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    claimsApi.reviewQueue()
      .then((data) => { if (active) setCases(data) })
      .catch((cause: unknown) => { if (active) setErrorMsg(errorMessage(cause, 'Could not load the review queue.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [queueVersion])

  const activeCase = cases.find((c) => c.claimId === selectedId) ?? cases[0] ?? null

  function refreshQueue() {
    setQueueVersion((v) => v + 1)
  }

  async function run<T>(action: () => Promise<T>, success: string | ((value: T) => string), fallbackError: string) {
    setErrorMsg(null)
    setStatusBanner(null)
    setBusy(true)
    try {
      const value = await action()
      setStatusBanner(typeof success === 'function' ? success(value) : success)
      return true
    } catch (cause: unknown) {
      setErrorMsg(errorMessage(cause, fallbackError))
      return false
    } finally {
      setBusy(false)
      refreshQueue()
    }
  }

  async function handleCustodyConfirm(e: FormEvent) {
    e.preventDefault()
    if (!activeCase) return
    if (!storageRef.trim() || !conditionNotes.trim()) {
      setErrorMsg('Storage reference and receiving condition notes are required for custody intake (FR-11).')
      return
    }
    const ok = await run(
      () => claimsApi.recordCustodyIntake(activeCase.foundReportId, storageRef.trim(), conditionNotes.trim()),
      `Found item #${activeCase.foundReportId} recorded IN_CUSTODY at ${storageRef.trim()}.`,
      'Custody intake failed.',
    )
    if (ok) {
      setStorageRef('')
      setConditionNotes('')
    }
  }

  async function handleHandover(e: FormEvent) {
    e.preventDefault()
    if (!activeCase) return
    if (!handoverCode.trim() || !identityNotes.trim()) {
      setErrorMsg("Enter the claimant's one-time handover code and record the identity check (FR-12).")
      return
    }
    if (!window.confirm(`Release found item #${activeCase.foundReportId} to ${activeCase.claimantName}? This closes the case.`)) return
    const ok = await run(
      () => claimsApi.completeHandover(activeCase.claimId, handoverCode.trim(), identityNotes.trim()),
      `Item handed over to ${activeCase.claimantName}. Claim #${activeCase.claimId} is complete and the reports are closed.`,
      'The handover was not recorded.',
    )
    if (ok) {
      setHandoverCode('')
      setIdentityNotes('')
    }
  }

  async function handleResolveDispute(e: FormEvent) {
    e.preventDefault()
    if (!activeCase) return
    if (!disputeResolution.trim()) {
      setErrorMsg('Record how the dispute was resolved before clearing it (FR-12).')
      return
    }
    const ok = await run(
      () => claimsApi.resolveDispute(activeCase.foundReportId, disputeResolution.trim()),
      `Dispute on found item #${activeCase.foundReportId} resolved. The item can be reserved again.`,
      'The dispute was not resolved.',
    )
    if (ok) setDisputeResolution('')
  }

  async function handleDecision(action: DecisionAction) {
    if (!activeCase) return
    if (!decisionReason.trim()) {
      setErrorMsg('Record a clear reason for every review decision (FR-12).')
      return
    }
    const ok = await run(
      () => claimsApi.submitDecision(activeCase.claimId, action, decisionReason.trim(), physicalNotes.trim()),
      (result) => action === 'APPROVE' && result.status === 'UNDER_REVIEW'
        ? `First approval recorded for claim #${activeCase.claimId}. A different staff member must give the second approval (HIGH risk).`
        : `Claim #${activeCase.claimId} is now ${result.status}. The decision and reason were recorded.`,
      'The decision was not recorded.',
    )
    if (ok) {
      setDecisionReason('')
      setPhysicalNotes('')
    }
  }

  async function handleEscalation(e: FormEvent) {
    e.preventDefault()
    if (!activeCase) return
    if (!riskReason.trim()) {
      setErrorMsg('A documented reason is required to change the risk tier (FR-10).')
      return
    }
    if (!selectedTier) return
    if (activeCase.status === 'APPROVED' && !window.confirm(
      `Claim #${activeCase.claimId} is approved and its item is reserved. Raising the tier releases the reservation, `
      + `revokes the handover code and sends the claim back for review under ${selectedTier} rules. Continue?`,
    )) return
    const ok = await run(
      () => claimsApi.escalateRisk(activeCase.claimId, selectedTier, riskReason.trim()),
      (result) => activeCase.status === 'APPROVED' && result.status === 'UNDER_REVIEW'
        ? `Claim #${activeCase.claimId} raised to ${selectedTier} and sent back for review. Its reservation was released.`
        : `Claim #${activeCase.claimId} risk tier raised to ${selectedTier}.`,
      'The risk tier was not changed.',
    )
    if (ok) setRiskReason('')
  }

  // Blockers come from the server (same rules it enforces on approval); the notes check depends on this form.
  const blockers = activeCase ? activeCase.approvalBlockers : []
  const awaitingSecondApproval = activeCase !== null && activeCase.firstApproverId !== null
  const isHighFirstApproval = activeCase !== null && activeCase.riskTier === 'HIGH' && !awaitingSecondApproval
  const inspectionNotesMissing = isHighFirstApproval && !physicalNotes.trim()
  const approveLabel = activeCase?.riskTier !== 'HIGH'
    ? 'Approve & reserve item'
    : awaitingSecondApproval ? 'Give second approval & reserve item' : 'Give first approval (1 of 2)'
  const isClosed = activeCase ? CLOSED.includes(activeCase.status) : true
  const pendingReconsiderations = cases.filter((c) => c.reconsiderationPending).length
  // Tiers can only be raised (server-enforced); offer just the levels above the current one.
  const higherTiers: RiskTier[] = activeCase
    ? (['MEDIUM', 'HIGH'] as RiskTier[]).filter((t) => riskRank(t) > riskRank(activeCase.riskTier))
    : []
  const selectedTier: RiskTier | undefined = higherTiers.includes(riskTier) ? riskTier : higherTiers[higherTiers.length - 1]
  const tierPolicy = activeCase ? RISK_LEVELS.find((l) => l.value === activeCase.riskTier)?.policy : undefined

  return (
    <div className="text-fb-ink">
      <div className="section-heading">
        <div>
          <p className="eyebrow">STAFF REVIEW</p>
          <h1>Review queue</h1>
          <p className="page-intro mb-0!">
            Reviewing as <strong>{user?.name}</strong>. Cases you are involved in (as claimant, finder or competing
            claimant) are not shown to you.
          </p>
        </div>
        <button type="button" onClick={refreshQueue} disabled={busy} className="button button-secondary button-small">
          Refresh
        </button>
      </div>

      {errorMsg && <div role="alert" className="alert alert-error mb-4">{errorMsg}</div>}
      {statusBanner && <div role="status" className="alert alert-success">{statusBanner}</div>}
      {pendingReconsiderations > 0 && (
        <div className="notice mb-4">
          <strong>{pendingReconsiderations === 1 ? '1 claimant has' : `${pendingReconsiderations} claimants have`}</strong> asked
          for a rejected claim to be reconsidered. These cases are at the top of the queue.
        </div>
      )}

      {loading ? (
        <p className="text-sm text-fb-muted">Loading the review queue…</p>
      ) : !activeCase ? (
        <p className="empty-state">There are no claims waiting for your review.</p>
      ) : (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
          <section className="space-y-3 lg:col-span-4" aria-label="Claims queue">
            <h2 className="eyebrow mb-1!">
              CASES ({cases.length})
            </h2>
            {cases.map((c) => (
              <button
                key={c.claimId}
                type="button"
                onClick={() => setSelectedId(c.claimId)}
                className={`w-full rounded-2xl border p-4 text-left transition ${
                  c.claimId === activeCase.claimId
                    ? 'border-fb-green bg-fb-mint/50 shadow-sm'
                    : 'border-fb-line bg-white hover:border-fb-mint-line'
                }`}
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-semibold text-fb-ink">Claim #{c.claimId} – {c.itemTitle}</span>
                  <span className="rounded-full bg-fb-chip px-2 py-0.5 text-xs font-semibold text-fb-ink">{c.status}</span>
                </div>
                <div className="mt-2 flex flex-wrap gap-2 text-xs text-fb-muted">
                  <span>{c.claimantName}</span>
                  <span>|</span>
                  <span>Found #{c.foundReportId}</span>
                  <span>|</span>
                  <span>Risk: <strong>{c.riskTier}</strong></span>
                  <span>|</span>
                  <span>Custody: <strong>{c.custodyStatus}</strong></span>
                  {c.competingActiveClaimsCount > 0 && (
                    <span className="font-semibold text-fb-warn">+{c.competingActiveClaimsCount} competing</span>
                  )}
                </div>
                {c.firstApproverId !== null && (
                  <span className="mr-2 mt-2 inline-block rounded-full bg-fb-info px-2 py-0.5 text-xs font-semibold text-white">
                    Awaiting 2nd approval
                  </span>
                )}
                {c.reconsiderationPending && (
                  <span className="mt-2 inline-block rounded-full bg-fb-green px-2 py-0.5 text-xs font-semibold text-white">
                    Reconsideration requested
                  </span>
                )}
              </button>
            ))}
          </section>

          <section className="space-y-6 lg:col-span-8">
            <div className="rounded-2xl border border-fb-line bg-white p-5 shadow-sm">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-fb-line pb-3">
                <div>
                  <h3>1. Physical custody (Found report #{activeCase.foundReportId})</h3>
                  <p className="text-xs text-fb-muted">
                    Zone: {activeCase.campusZone} | Category: {activeCase.category} | Claimant: {activeCase.claimantName} (lost report #{activeCase.lostReportId})
                  </p>
                  <Link to={`/admin/history/${activeCase.claimId}`} className="text-xs font-semibold text-fb-green underline">
                    View case history
                  </Link>
                </div>
                <span className="rounded-full bg-fb-green px-3 py-1 text-xs font-bold text-white">
                  Custody: {activeCase.custodyStatus}
                </span>
              </div>

              {activeCase.disputeBlocked && !activeCase.canResolveDispute && (
                <div className="mt-3 space-y-1 rounded-lg border border-fb-error-line bg-fb-error-bg p-3 text-xs text-fb-error">
                  <p className="font-medium">
                    You raised this dispute{activeCase.disputeRaisedAt && ` on ${new Date(activeCase.disputeRaisedAt).toLocaleString()}`}
                    {activeCase.disputeReason && `: “${activeCase.disputeReason}”`}.
                  </p>
                  <p>
                    Two-person rule: a different staff member must review the case and resolve the dispute. No claim can be
                    approved for this item until then.
                  </p>
                </div>
              )}

              {activeCase.disputeBlocked && activeCase.canResolveDispute && (
                <form onSubmit={handleResolveDispute} className="mt-3 space-y-2 rounded-lg border border-fb-error-line bg-fb-error-bg p-3">
                  <p className="text-xs font-medium text-fb-error">
                    This item is blocked by an unresolved dispute (an approved claim was put on hold). No claim can be
                    approved until the dispute is resolved.
                  </p>
                  {activeCase.disputeRaisedByName && (
                    <p className="text-xs text-fb-error">
                      Raised by <strong>{activeCase.disputeRaisedByName}</strong>
                      {activeCase.disputeRaisedAt && ` on ${new Date(activeCase.disputeRaisedAt).toLocaleString()}`}
                      {activeCase.disputeReason && `: “${activeCase.disputeReason}”`}
                    </p>
                  )}
                  <label htmlFor="dispute-resolution" className="block text-xs font-semibold text-fb-error">
                    How was the dispute resolved? (recorded in the custody log) *
                  </label>
                  <textarea
                    id="dispute-resolution"
                    rows={2}
                    maxLength={NOTE_MAX_LENGTH}
                    value={disputeResolution}
                    onChange={(e) => setDisputeResolution(e.target.value)}
                    placeholder="e.g. Ownership re-confirmed with student ID; competing claimant withdrew"
                    className="w-full rounded-lg border border-fb-error-line bg-white px-3 py-1.5 text-sm"
                  />
                  <button
                    type="submit"
                    disabled={busy}
                    className="button button-small bg-fb-coral text-white hover:bg-fb-error"
                  >
                    Resolve dispute
                  </button>
                </form>
              )}

              {activeCase.custodyStatus === 'REPORTED' ? (
                <form onSubmit={handleCustodyConfirm} className="mt-4 space-y-3">
                  <p className="rounded-lg border border-fb-warn-line bg-fb-warn-bg p-2 text-xs font-medium text-fb-warn">
                    The item is not yet in physical custody. Inspect and log it before any claim can be approved (FR-11).
                  </p>
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                    <div>
                      <label htmlFor="storage-ref" className="block text-xs font-semibold text-fb-ink">Storage / locker reference *</label>
                      <input
                        id="storage-ref"
                        type="text"
                        maxLength={100}
                        value={storageRef}
                        onChange={(e) => setStorageRef(e.target.value)}
                        placeholder="e.g. SEC-LOCKER-B12"
                        className="mt-1 w-full rounded-lg border border-fb-line px-3 py-1.5 text-sm"
                      />
                    </div>
                    <div>
                      <label htmlFor="condition-notes" className="block text-xs font-semibold text-fb-ink">Receiving condition notes *</label>
                      <input
                        id="condition-notes"
                        type="text"
                        maxLength={NOTE_MAX_LENGTH}
                        value={conditionNotes}
                        onChange={(e) => setConditionNotes(e.target.value)}
                        placeholder="Visible condition when received"
                        className="mt-1 w-full rounded-lg border border-fb-line px-3 py-1.5 text-sm"
                      />
                    </div>
                  </div>
                  <button
                    type="submit"
                    disabled={busy}
                    className="button button-primary button-small"
                  >
                    Confirm physical custody intake
                  </button>
                </form>
              ) : (
                <div className="mt-3 text-sm text-fb-ink">
                  <p><strong>Storage location:</strong> {activeCase.storageReference}</p>
                  {activeCase.custodyStatus === 'RESERVED' ? (
                    <>
                      <p className="mt-1! text-xs text-fb-green">
                        Reserved for approved claim #{activeCase.reservedByClaimId}. No other claim can be approved for this item.
                      </p>
                      {activeCase.status === 'APPROVED' && activeCase.reservedByClaimId === activeCase.claimId && (
                        <form onSubmit={handleHandover} className="mt-3 space-y-3 rounded-lg border border-fb-mint-line bg-fb-mint p-3">
                          <p className="text-xs font-semibold text-fb-green-dark">Complete handover at the Security Desk</p>
                          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                            <div>
                              <label htmlFor="handover-code" className="block text-xs font-semibold text-fb-ink">One-time handover code *</label>
                              <input
                                id="handover-code"
                                type="text"
                                autoComplete="off"
                                value={handoverCode}
                                onChange={(e) => setHandoverCode(e.target.value)}
                                placeholder="Code shown by the claimant"
                                className="mt-1 w-full rounded-lg border border-fb-line bg-white px-3 py-1.5 text-sm"
                              />
                            </div>
                            <div>
                              <label htmlFor="identity-notes" className="block text-xs font-semibold text-fb-ink">Identity check performed *</label>
                              <input
                                id="identity-notes"
                                type="text"
                                maxLength={NOTE_MAX_LENGTH}
                                value={identityNotes}
                                onChange={(e) => setIdentityNotes(e.target.value)}
                                placeholder="e.g. Student ID card matched account name"
                                className="mt-1 w-full rounded-lg border border-fb-line bg-white px-3 py-1.5 text-sm"
                              />
                            </div>
                          </div>
                          <button
                            type="submit"
                            disabled={busy}
                            className="button button-primary button-small"
                          >
                            Release item to owner
                          </button>
                        </form>
                      )}
                    </>
                  ) : activeCase.custodyStatus === 'IN_CUSTODY' ? (
                    <p className="mt-1! text-xs text-fb-green">Physical custody confirmed. The item can be reserved by one approved claim.</p>
                  ) : (
                    <p className="mt-1! text-xs text-fb-muted">The item has been returned.</p>
                  )}
                </div>
              )}
            </div>

            <div className="rounded-2xl border border-fb-line bg-white p-5 shadow-sm">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-fb-line pb-3">
                <h3 className="mb-0!">2. DOVE summary &amp; risk tier</h3>
                <span className="rounded-full bg-fb-mint px-2.5 py-1 text-xs font-bold text-fb-green-dark">
                  DOVE: {activeCase.doveSummary.strengthLabel}
                </span>
              </div>

              <div className="mt-4 grid grid-cols-2 gap-4 text-center sm:grid-cols-4">
                <div className="rounded-lg border border-fb-mint-line bg-fb-mint p-3">
                  <div className="text-xl font-bold text-fb-green-dark">{activeCase.doveSummary.matchCount}</div>
                  <div className="text-xs text-fb-green">Distinct matches</div>
                </div>
                <div className="rounded-lg border border-fb-line bg-fb-bg p-3">
                  <div className="text-xl font-bold text-fb-ink">{activeCase.doveSummary.unknownCount}</div>
                  <div className="text-xs text-fb-muted">Unknown / manual</div>
                </div>
                <div className="rounded-lg border border-fb-error-line bg-fb-error-bg p-3">
                  <div className="text-xl font-bold text-fb-error">{activeCase.doveSummary.contradictionCount}</div>
                  <div className="text-xs text-fb-error">Contradictions</div>
                </div>
                <div className="rounded-lg border border-fb-mint-line bg-fb-mint p-3">
                  <div className="text-xl font-bold text-fb-green-dark">{activeCase.doveSummary.submissionsUsed} / 3</div>
                  <div className="text-xs text-fb-green">Submissions used</div>
                </div>
              </div>
              <p className="mt-3! rounded-lg border border-fb-line bg-fb-bg p-2 text-xs text-fb-ink">
                <strong>Required to approve:</strong> {tierRequirement(activeCase)}{' '}
                Serial/identifier: <strong>{activeCase.doveSummary.identifierMatched ? 'confirmed match' : 'not confirmed'}</strong>.
              </p>
              {activeCase.flagReason && (
                <p className="mt-3! text-xs font-medium text-fb-warn">Internal flag: {activeCase.flagReason}</p>
              )}
              <p className="mt-3! text-xs text-fb-muted">
                Compare the private evidence in the <Link className="font-semibold text-fb-green underline" to="/evidence">Evidence Workspace</Link>.
              </p>

              <form onSubmit={handleEscalation} className="mt-4 space-y-3 border-t border-fb-line pt-4">
                <p className="text-sm">
                  Current risk tier: <strong>{activeCase.riskTier}</strong>
                  {activeCase.escalationReason && <span className="text-fb-muted"> – {activeCase.escalationReason}</span>}
                </p>
                {tierPolicy && <p className="text-xs text-fb-muted">Policy for this tier: {tierPolicy}</p>}
                {higherTiers.length === 0 ? (
                  <p className="text-xs text-fb-muted">This claim is already at the highest risk tier.</p>
                ) : (
                  <>
                    <div className="grid grid-cols-1 gap-3 sm:grid-cols-4">
                      <div>
                        <label htmlFor="risk-tier" className="block text-xs font-semibold text-fb-ink">Raise to</label>
                        <select
                          id="risk-tier"
                          value={selectedTier}
                          onChange={(e) => setRiskTier(e.target.value as RiskTier)}
                          className="mt-1 w-full rounded-lg border border-fb-line bg-white px-3 py-1.5 text-sm"
                        >
                          {higherTiers.map((t) => <option key={t} value={t}>{t}</option>)}
                        </select>
                      </div>
                      <div className="sm:col-span-3">
                        <label htmlFor="risk-reason" className="block text-xs font-semibold text-fb-ink">Documented reason *</label>
                        <input
                          id="risk-reason"
                          type="text"
                          maxLength={RISK_REASON_MAX_LENGTH}
                          value={riskReason}
                          onChange={(e) => setRiskReason(e.target.value)}
                          placeholder="Why this claim needs stricter review"
                          className="mt-1 w-full rounded-lg border border-fb-line px-3 py-1.5 text-sm"
                        />
                      </div>
                    </div>
                    <p className="text-xs text-fb-muted">
                      Risk tiers can only be raised, never lowered. Raising an approved claim releases its reservation and
                      sends it back for review under the stricter rules.
                    </p>
                    <button
                      type="submit"
                      disabled={busy || isClosed}
                      className="button button-primary button-small"
                    >
                      Raise risk tier
                    </button>
                  </>
                )}
              </form>
            </div>

            <div className="rounded-2xl border border-fb-line bg-white p-5 shadow-sm">
              <h3 className="border-b border-fb-line pb-3">3. Record review decision (FR-12)</h3>
              <div className="mt-4 space-y-4">
                <div>
                  <label htmlFor="physical-notes" className="block text-xs font-semibold text-fb-ink">
                    {isHighFirstApproval && activeCase.status === 'UNDER_REVIEW'
                      ? 'Physical inspection notes (required for a HIGH risk approval) *'
                      : 'Physical verification notes (optional)'}
                  </label>
                  <input
                    id="physical-notes"
                    type="text"
                    maxLength={NOTE_MAX_LENGTH}
                    value={physicalNotes}
                    onChange={(e) => setPhysicalNotes(e.target.value)}
                    className="mt-1 w-full rounded-lg border border-fb-line px-3 py-1.5 text-sm"
                    placeholder="What you checked on the physical item"
                  />
                </div>
                <div>
                  <label htmlFor="decision-reason" className="block text-xs font-semibold text-fb-ink">
                    Decision reason (recorded in the audit trail) *
                  </label>
                  <textarea
                    id="decision-reason"
                    rows={2}
                    maxLength={NOTE_MAX_LENGTH}
                    value={decisionReason}
                    onChange={(e) => setDecisionReason(e.target.value)}
                    className="mt-1 w-full rounded-lg border border-fb-line px-3 py-1.5 text-sm"
                    placeholder="State clearly why this claim is being approved, held, rejected or reconsidered"
                  />
                </div>

                {activeCase.reconsiderationPending && (
                  <div className="rounded-lg border border-fb-mint-line bg-fb-mint p-3 text-sm text-fb-green-dark">
                    <p className="text-xs font-semibold uppercase tracking-wide">
                      Reconsideration requested by {activeCase.claimantName}
                      {activeCase.reconsiderationRequestedAt && ` · ${new Date(activeCase.reconsiderationRequestedAt).toLocaleString()}`}
                    </p>
                    <p className="mt-1! whitespace-pre-wrap">{activeCase.reconsiderationReason}</p>
                    <p className="mt-2! text-xs">Reopen the claim to review it again, or decline the request with a reason (final).</p>
                  </div>
                )}

                {activeCase.riskTier === 'HIGH' && activeCase.status === 'UNDER_REVIEW' && (
                  <p className="rounded-lg border border-fb-info-line bg-fb-info-bg p-3 text-xs text-fb-info">
                    {awaitingSecondApproval
                      ? `First approval given by ${activeCase.firstApproverName}${activeCase.firstApprovedAt ? ` on ${new Date(activeCase.firstApprovedAt).toLocaleString()}` : ''}. A different staff member must review the case and give the second approval before the item is reserved.`
                      : 'HIGH risk: mandatory review by two different staff members. The first approval records your inspection; nothing is reserved until a second staff member approves.'}
                  </p>
                )}

                {activeCase.status === 'UNDER_REVIEW' && (blockers.length > 0 || inspectionNotesMissing) && (
                  <ul className="list-disc space-y-1 rounded-lg border border-fb-warn-line bg-fb-warn-bg p-3 pl-6 text-xs text-fb-warn">
                    {blockers.map((b) => <li key={b}>{b}</li>)}
                    {inspectionNotesMissing && <li>Enter physical inspection notes above to give the first approval.</li>}
                  </ul>
                )}

                <div className="flex flex-wrap gap-3 pt-2">
                  <button
                    type="button"
                    onClick={() => handleDecision('APPROVE')}
                    disabled={busy || blockers.length > 0 || inspectionNotesMissing}
                    className="button button-primary button-small"
                  >
                    {approveLabel}
                  </button>
                  {!isClosed && (
                    <>
                      <button
                        type="button"
                        onClick={() => handleDecision('HOLD')}
                        disabled={busy}
                        className="button button-small bg-fb-warn text-white hover:bg-fb-warn-dark"
                      >
                        Place on hold
                      </button>
                      <button
                        type="button"
                        onClick={() => handleDecision('REJECT')}
                        disabled={busy}
                        className="button button-small bg-fb-coral text-white hover:bg-fb-error"
                      >
                        Reject claim
                      </button>
                    </>
                  )}
                  {(activeCase.status === 'ON_HOLD' || activeCase.status === 'REJECTED') && (
                    <button
                      type="button"
                      onClick={() => handleDecision('RECONSIDER')}
                      disabled={busy}
                      className="button button-primary button-small"
                    >
                      Reconsider (reopen for review)
                    </button>
                  )}
                  {activeCase.reconsiderationPending && (
                    <button
                      type="button"
                      onClick={() => handleDecision('DECLINE_RECONSIDERATION')}
                      disabled={busy}
                      className="button button-secondary button-small text-fb-green"
                    >
                      Decline request
                    </button>
                  )}
                </div>
              </div>
            </div>
          </section>
        </div>
      )}
    </div>
  )
}
