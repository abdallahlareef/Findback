import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { NOTE_MAX_LENGTH, RISK_REASON_MAX_LENGTH, claimsApi, errorMessage } from './member3Api'
import type { ClaimStatus, DecisionAction, RiskTier, StaffClaimCaseView } from './member3Api'
import { RISK_LEVELS, riskRank } from './riskLevels'

const CLOSED: ClaimStatus[] = ['REJECTED', 'WITHDRAWN', 'COMPLETED']

const NEXT_STATUS: Record<DecisionAction, ClaimStatus> = {
  APPROVE: 'APPROVED',
  HOLD: 'ON_HOLD',
  REJECT: 'REJECTED',
  RECONSIDER: 'UNDER_REVIEW',
  DECLINE_RECONSIDERATION: 'REJECTED',
}

/** UI hints only. The server re-checks every rule and its answer is what counts. */
function approvalBlockers(c: StaffClaimCaseView): string[] {
  const blockers: string[] = []
  if (c.status !== 'UNDER_REVIEW') blockers.push(`Only UNDER_REVIEW claims can be approved (this one is ${c.status}).`)
  if (c.custodyStatus !== 'IN_CUSTODY') blockers.push(`Physical custody must be IN_CUSTODY (currently ${c.custodyStatus}).`)
  if (c.disputeBlocked) blockers.push('The item is blocked by an unresolved dispute. Resolve it in section 1 first.')
  if (c.doveSummary.hasCriticalConflict) blockers.push('DOVE reports an unresolved critical identifier conflict.')
  if (c.doveSummary.pendingSupplement) blockers.push('A requested evidence supplement is still pending.')
  return blockers
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

  async function run(action: () => Promise<unknown>, success: string, fallbackError: string) {
    setErrorMsg(null)
    setStatusBanner(null)
    setBusy(true)
    try {
      await action()
      setStatusBanner(success)
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
      `Claim #${activeCase.claimId} moved to ${NEXT_STATUS[action]}. The decision and reason were recorded.`,
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
    const ok = await run(
      () => claimsApi.escalateRisk(activeCase.claimId, selectedTier, riskReason.trim()),
      `Claim #${activeCase.claimId} risk tier raised to ${selectedTier}.`,
      'The risk tier was not changed.',
    )
    if (ok) setRiskReason('')
  }

  const blockers = activeCase ? approvalBlockers(activeCase) : []
  const isClosed = activeCase ? CLOSED.includes(activeCase.status) : true
  const pendingReconsiderations = cases.filter((c) => c.reconsiderationPending).length
  // Tiers can only be raised (server-enforced); offer just the levels above the current one.
  const higherTiers: RiskTier[] = activeCase
    ? (['MEDIUM', 'HIGH'] as RiskTier[]).filter((t) => riskRank(t) > riskRank(activeCase.riskTier))
    : []
  const selectedTier: RiskTier | undefined = higherTiers.includes(riskTier) ? riskTier : higherTiers[higherTiers.length - 1]
  const tierPolicy = activeCase ? RISK_LEVELS.find((l) => l.value === activeCase.riskTier)?.policy : undefined

  return (
    <div className="p-6 text-slate-900">
      <header className="mb-6 flex flex-wrap items-center justify-between gap-4 border-b border-slate-200 pb-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Claims, custody &amp; staff review</h1>
          <p className="text-sm text-slate-600">
            Cases you are involved in (as claimant, finder or competing claimant) are not shown to you.
          </p>
        </div>
        <div className="flex items-center gap-3 text-xs">
          <span className="text-slate-600">Reviewing as <strong>{user?.name}</strong></span>
          <button
            type="button"
            onClick={refreshQueue}
            disabled={busy}
            className="rounded border border-slate-300 bg-white px-3 py-1.5 font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50"
          >
            Refresh
          </button>
        </div>
      </header>

      {errorMsg && (
        <div role="alert" className="mb-4 rounded-md border border-red-300 bg-red-50 p-3 text-sm font-medium text-red-800">
          {errorMsg}
        </div>
      )}
      {statusBanner && (
        <div role="status" className="mb-4 rounded-md border border-emerald-300 bg-emerald-50 p-3 text-sm font-medium text-emerald-800">
          {statusBanner}
        </div>
      )}
      {pendingReconsiderations > 0 && (
        <div className="mb-4 rounded-md border border-blue-300 bg-blue-50 p-3 text-sm font-medium text-blue-900">
          {pendingReconsiderations === 1 ? '1 claimant has' : `${pendingReconsiderations} claimants have`} asked for a
          rejected claim to be reconsidered. These cases are at the top of the queue.
        </div>
      )}

      {loading ? (
        <p className="text-sm text-slate-600">Loading the review queue…</p>
      ) : !activeCase ? (
        <p className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600">
          There are no claims waiting for your review.
        </p>
      ) : (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
          <section className="space-y-3 lg:col-span-4" aria-label="Claims queue">
            <h2 className="text-sm font-semibold uppercase tracking-wider text-slate-500">
              Review queue ({cases.length})
            </h2>
            {cases.map((c) => (
              <button
                key={c.claimId}
                type="button"
                onClick={() => setSelectedId(c.claimId)}
                className={`w-full rounded-lg border p-4 text-left transition ${
                  c.claimId === activeCase.claimId
                    ? 'border-blue-600 bg-blue-50/50 shadow-sm'
                    : 'border-slate-200 bg-white hover:border-slate-300'
                }`}
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-semibold text-slate-900">Claim #{c.claimId} – {c.itemTitle}</span>
                  <span className="rounded bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-700">{c.status}</span>
                </div>
                <div className="mt-2 flex flex-wrap gap-2 text-xs text-slate-600">
                  <span>{c.claimantName}</span>
                  <span>|</span>
                  <span>Found #{c.foundReportId}</span>
                  <span>|</span>
                  <span>Risk: <strong>{c.riskTier}</strong></span>
                  <span>|</span>
                  <span>Custody: <strong>{c.custodyStatus}</strong></span>
                  {c.competingActiveClaimsCount > 0 && (
                    <span className="font-semibold text-amber-700">+{c.competingActiveClaimsCount} competing</span>
                  )}
                </div>
                {c.reconsiderationPending && (
                  <span className="mt-2 inline-block rounded bg-blue-600 px-2 py-0.5 text-xs font-semibold text-white">
                    Reconsideration requested
                  </span>
                )}
              </button>
            ))}
          </section>

          <section className="space-y-6 lg:col-span-8">
            <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-3">
                <div>
                  <h2 className="text-lg font-semibold">1. Physical custody (Found report #{activeCase.foundReportId})</h2>
                  <p className="text-xs text-slate-500">
                    Zone: {activeCase.campusZone} | Category: {activeCase.category} | Claimant: {activeCase.claimantName} (lost report #{activeCase.lostReportId})
                  </p>
                  <Link to={`/admin/history/${activeCase.claimId}`} className="text-xs font-semibold text-blue-700 underline">
                    View case history
                  </Link>
                </div>
                <span className="rounded-full bg-slate-800 px-3 py-1 text-xs font-bold text-white">
                  Custody: {activeCase.custodyStatus}
                </span>
              </div>

              {activeCase.disputeBlocked && (
                <form onSubmit={handleResolveDispute} className="mt-3 space-y-2 rounded border border-red-200 bg-red-50 p-3">
                  <p className="text-xs font-medium text-red-800">
                    This item is blocked by an unresolved dispute (an approved claim was put on hold). No claim can be
                    approved until the dispute is resolved.
                  </p>
                  <label htmlFor="dispute-resolution" className="block text-xs font-semibold text-red-900">
                    How was the dispute resolved? (recorded in the custody log) *
                  </label>
                  <textarea
                    id="dispute-resolution"
                    rows={2}
                    maxLength={NOTE_MAX_LENGTH}
                    value={disputeResolution}
                    onChange={(e) => setDisputeResolution(e.target.value)}
                    placeholder="e.g. Ownership re-confirmed with student ID; competing claimant withdrew"
                    className="w-full rounded border border-red-200 bg-white px-3 py-1.5 text-sm"
                  />
                  <button
                    type="submit"
                    disabled={busy}
                    className="rounded bg-red-700 px-4 py-2 text-xs font-semibold text-white hover:bg-red-800 disabled:opacity-50"
                  >
                    Resolve dispute
                  </button>
                </form>
              )}

              {activeCase.custodyStatus === 'REPORTED' ? (
                <form onSubmit={handleCustodyConfirm} className="mt-4 space-y-3">
                  <p className="rounded border border-amber-200 bg-amber-50 p-2 text-xs font-medium text-amber-700">
                    The item is not yet in physical custody. Inspect and log it before any claim can be approved (FR-11).
                  </p>
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                    <div>
                      <label htmlFor="storage-ref" className="block text-xs font-semibold text-slate-700">Storage / locker reference *</label>
                      <input
                        id="storage-ref"
                        type="text"
                        maxLength={100}
                        value={storageRef}
                        onChange={(e) => setStorageRef(e.target.value)}
                        placeholder="e.g. SEC-LOCKER-B12"
                        className="mt-1 w-full rounded border border-slate-300 px-3 py-1.5 text-sm"
                      />
                    </div>
                    <div>
                      <label htmlFor="condition-notes" className="block text-xs font-semibold text-slate-700">Receiving condition notes *</label>
                      <input
                        id="condition-notes"
                        type="text"
                        maxLength={NOTE_MAX_LENGTH}
                        value={conditionNotes}
                        onChange={(e) => setConditionNotes(e.target.value)}
                        placeholder="Visible condition when received"
                        className="mt-1 w-full rounded border border-slate-300 px-3 py-1.5 text-sm"
                      />
                    </div>
                  </div>
                  <button
                    type="submit"
                    disabled={busy}
                    className="rounded bg-slate-900 px-4 py-2 text-xs font-semibold text-white hover:bg-slate-800 disabled:opacity-50"
                  >
                    Confirm physical custody intake
                  </button>
                </form>
              ) : (
                <div className="mt-3 text-sm text-slate-700">
                  <p><strong>Storage location:</strong> {activeCase.storageReference}</p>
                  {activeCase.custodyStatus === 'RESERVED' ? (
                    <>
                      <p className="mt-1 text-xs text-blue-700">
                        Reserved for approved claim #{activeCase.reservedByClaimId}. No other claim can be approved for this item.
                      </p>
                      {activeCase.status === 'APPROVED' && activeCase.reservedByClaimId === activeCase.claimId && (
                        <form onSubmit={handleHandover} className="mt-3 space-y-3 rounded border border-emerald-200 bg-emerald-50 p-3">
                          <p className="text-xs font-semibold text-emerald-900">Complete handover at the Security Desk</p>
                          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                            <div>
                              <label htmlFor="handover-code" className="block text-xs font-semibold text-slate-700">One-time handover code *</label>
                              <input
                                id="handover-code"
                                type="text"
                                autoComplete="off"
                                value={handoverCode}
                                onChange={(e) => setHandoverCode(e.target.value)}
                                placeholder="Code shown by the claimant"
                                className="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-1.5 text-sm"
                              />
                            </div>
                            <div>
                              <label htmlFor="identity-notes" className="block text-xs font-semibold text-slate-700">Identity check performed *</label>
                              <input
                                id="identity-notes"
                                type="text"
                                maxLength={NOTE_MAX_LENGTH}
                                value={identityNotes}
                                onChange={(e) => setIdentityNotes(e.target.value)}
                                placeholder="e.g. Student ID card matched account name"
                                className="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-1.5 text-sm"
                              />
                            </div>
                          </div>
                          <button
                            type="submit"
                            disabled={busy}
                            className="rounded bg-emerald-700 px-4 py-2 text-xs font-semibold text-white hover:bg-emerald-800 disabled:opacity-50"
                          >
                            Release item to owner
                          </button>
                        </form>
                      )}
                    </>
                  ) : activeCase.custodyStatus === 'IN_CUSTODY' ? (
                    <p className="mt-1 text-xs text-emerald-700">Physical custody confirmed. The item can be reserved by one approved claim.</p>
                  ) : (
                    <p className="mt-1 text-xs text-slate-600">The item has been returned.</p>
                  )}
                </div>
              )}
            </div>

            <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-3">
                <h2 className="text-lg font-semibold">2. DOVE summary &amp; risk tier</h2>
                <span className="rounded bg-indigo-50 px-2.5 py-1 text-xs font-bold text-indigo-800">
                  DOVE: {activeCase.doveSummary.strengthLabel}
                </span>
              </div>

              <div className="mt-4 grid grid-cols-2 gap-4 text-center sm:grid-cols-4">
                <div className="rounded border border-emerald-200 bg-emerald-50 p-3">
                  <div className="text-xl font-bold text-emerald-800">{activeCase.doveSummary.matchCount}</div>
                  <div className="text-xs text-emerald-700">Distinct matches</div>
                </div>
                <div className="rounded border border-slate-200 bg-slate-50 p-3">
                  <div className="text-xl font-bold text-slate-700">{activeCase.doveSummary.unknownCount}</div>
                  <div className="text-xs text-slate-600">Unknown / manual</div>
                </div>
                <div className="rounded border border-red-200 bg-red-50 p-3">
                  <div className="text-xl font-bold text-red-800">{activeCase.doveSummary.contradictionCount}</div>
                  <div className="text-xs text-red-700">Contradictions</div>
                </div>
                <div className="rounded border border-blue-200 bg-blue-50 p-3">
                  <div className="text-xl font-bold text-blue-800">{activeCase.doveSummary.submissionsUsed} / 3</div>
                  <div className="text-xs text-blue-700">Submissions used</div>
                </div>
              </div>
              {activeCase.flagReason && (
                <p className="mt-3 text-xs font-medium text-amber-800">Internal flag: {activeCase.flagReason}</p>
              )}
              <p className="mt-3 text-xs text-slate-600">
                Compare the private evidence in the <Link className="font-semibold text-blue-700 underline" to="/evidence">Evidence Workspace</Link>.
              </p>

              <form onSubmit={handleEscalation} className="mt-4 space-y-3 border-t border-slate-100 pt-4">
                <p className="text-sm">
                  Current risk tier: <strong>{activeCase.riskTier}</strong>
                  {activeCase.escalationReason && <span className="text-slate-600"> – {activeCase.escalationReason}</span>}
                </p>
                {tierPolicy && <p className="text-xs text-slate-600">Policy for this tier: {tierPolicy}</p>}
                {higherTiers.length === 0 ? (
                  <p className="text-xs text-slate-500">This claim is already at the highest risk tier.</p>
                ) : (
                  <>
                    <div className="grid grid-cols-1 gap-3 sm:grid-cols-4">
                      <div>
                        <label htmlFor="risk-tier" className="block text-xs font-semibold text-slate-700">Raise to</label>
                        <select
                          id="risk-tier"
                          value={selectedTier}
                          onChange={(e) => setRiskTier(e.target.value as RiskTier)}
                          className="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-1.5 text-sm"
                        >
                          {higherTiers.map((t) => <option key={t} value={t}>{t}</option>)}
                        </select>
                      </div>
                      <div className="sm:col-span-3">
                        <label htmlFor="risk-reason" className="block text-xs font-semibold text-slate-700">Documented reason *</label>
                        <input
                          id="risk-reason"
                          type="text"
                          maxLength={RISK_REASON_MAX_LENGTH}
                          value={riskReason}
                          onChange={(e) => setRiskReason(e.target.value)}
                          placeholder="Why this claim needs stricter review"
                          className="mt-1 w-full rounded border border-slate-300 px-3 py-1.5 text-sm"
                        />
                      </div>
                    </div>
                    <p className="text-xs text-slate-500">Risk tiers can only be raised, never lowered.</p>
                    <button
                      type="submit"
                      disabled={busy || isClosed}
                      className="rounded bg-indigo-600 px-4 py-2 text-xs font-semibold text-white hover:bg-indigo-700 disabled:opacity-50"
                    >
                      Raise risk tier
                    </button>
                  </>
                )}
              </form>
            </div>

            <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
              <h2 className="border-b border-slate-100 pb-3 text-lg font-semibold">3. Record review decision (FR-12)</h2>
              <div className="mt-4 space-y-4">
                <div>
                  <label htmlFor="physical-notes" className="block text-xs font-semibold text-slate-700">Physical verification notes (optional)</label>
                  <input
                    id="physical-notes"
                    type="text"
                    maxLength={NOTE_MAX_LENGTH}
                    value={physicalNotes}
                    onChange={(e) => setPhysicalNotes(e.target.value)}
                    className="mt-1 w-full rounded border border-slate-300 px-3 py-1.5 text-sm"
                    placeholder="What you checked on the physical item"
                  />
                </div>
                <div>
                  <label htmlFor="decision-reason" className="block text-xs font-semibold text-slate-700">
                    Decision reason (recorded in the audit trail) *
                  </label>
                  <textarea
                    id="decision-reason"
                    rows={2}
                    maxLength={NOTE_MAX_LENGTH}
                    value={decisionReason}
                    onChange={(e) => setDecisionReason(e.target.value)}
                    className="mt-1 w-full rounded border border-slate-300 px-3 py-1.5 text-sm"
                    placeholder="State clearly why this claim is being approved, held, rejected or reconsidered"
                  />
                </div>

                {activeCase.reconsiderationPending && (
                  <div className="rounded border border-blue-300 bg-blue-50 p-3 text-sm text-blue-900">
                    <p className="text-xs font-semibold uppercase tracking-wide">
                      Reconsideration requested by {activeCase.claimantName}
                      {activeCase.reconsiderationRequestedAt && ` · ${new Date(activeCase.reconsiderationRequestedAt).toLocaleString()}`}
                    </p>
                    <p className="mt-1 whitespace-pre-wrap">{activeCase.reconsiderationReason}</p>
                    <p className="mt-2 text-xs">Reopen the claim to review it again, or decline the request with a reason (final).</p>
                  </div>
                )}

                {activeCase.status === 'UNDER_REVIEW' && blockers.length > 0 && (
                  <ul className="list-disc space-y-1 rounded border border-amber-200 bg-amber-50 p-3 pl-6 text-xs text-amber-800">
                    {blockers.map((b) => <li key={b}>{b}</li>)}
                  </ul>
                )}

                <div className="flex flex-wrap gap-3 pt-2">
                  <button
                    type="button"
                    onClick={() => handleDecision('APPROVE')}
                    disabled={busy || blockers.length > 0}
                    className="rounded bg-emerald-600 px-4 py-2 text-xs font-bold text-white hover:bg-emerald-700 disabled:opacity-50"
                  >
                    Approve &amp; reserve item
                  </button>
                  {!isClosed && (
                    <>
                      <button
                        type="button"
                        onClick={() => handleDecision('HOLD')}
                        disabled={busy}
                        className="rounded bg-amber-600 px-4 py-2 text-xs font-bold text-white hover:bg-amber-700 disabled:opacity-50"
                      >
                        Place on hold
                      </button>
                      <button
                        type="button"
                        onClick={() => handleDecision('REJECT')}
                        disabled={busy}
                        className="rounded bg-red-600 px-4 py-2 text-xs font-bold text-white hover:bg-red-700 disabled:opacity-50"
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
                      className="rounded bg-blue-600 px-4 py-2 text-xs font-bold text-white hover:bg-blue-700 disabled:opacity-50"
                    >
                      Reconsider (reopen for review)
                    </button>
                  )}
                  {activeCase.reconsiderationPending && (
                    <button
                      type="button"
                      onClick={() => handleDecision('DECLINE_RECONSIDERATION')}
                      disabled={busy}
                      className="rounded border border-blue-600 bg-white px-4 py-2 text-xs font-bold text-blue-700 hover:bg-blue-50 disabled:opacity-50"
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
