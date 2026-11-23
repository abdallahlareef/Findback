import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiRequestError, api } from '../../api/client'
import type { CandidateMatch, ReportResponse } from '../../types/api'
import { RECONSIDERATION_MAX_LENGTH, claimsApi, errorMessage } from './member3Api'
import type { ClaimStatus, ClaimantClaim, ReconsiderationStatus } from './member3Api'

const RECONSIDERATION_NOTE: Record<ReconsiderationStatus, string | null> = {
  NONE: null,
  PENDING: 'You asked staff to reconsider this claim. They will review your message.',
  ACCEPTED: 'Staff reopened this claim after your reconsideration request.',
  DECLINED: 'Staff reviewed your reconsideration request. The original decision stands.',
}

const STATUS_LABEL: Record<ClaimStatus, string> = {
  SUBMITTED: 'Submitted',
  UNDER_REVIEW: 'Under staff review',
  ON_HOLD: 'On hold – staff need more review',
  APPROVED: 'Approved – collect at the Security Desk',
  REJECTED: 'Not approved',
  WITHDRAWN: 'Withdrawn',
  COMPLETED: 'Returned to you',
}

const STATUS_STYLE: Record<ClaimStatus, string> = {
  SUBMITTED: 'bg-fb-chip text-fb-ink',
  UNDER_REVIEW: 'bg-fb-mint text-fb-green-dark',
  ON_HOLD: 'bg-fb-warn-bg text-fb-warn',
  APPROVED: 'bg-fb-mint text-fb-green-dark',
  REJECTED: 'bg-fb-error-bg text-fb-error',
  WITHDRAWN: 'bg-fb-chip text-fb-muted',
  COMPLETED: 'bg-fb-mint text-fb-green-dark',
}

function describeReport(report: { category: string; colour: string; campusZone: string; brand?: string | null }) {
  return [report.category, report.brand, report.colour, report.campusZone].filter(Boolean).join(' · ')
}

export function StudentClaimPortal() {
  const [claims, setClaims] = useState<ClaimantClaim[]>([])
  const [claimsLoading, setClaimsLoading] = useState(true)
  const [claimsVersion, setClaimsVersion] = useState(0)
  const [lostReports, setLostReports] = useState<ReportResponse[]>([])
  const [reportsLoading, setReportsLoading] = useState(true)

  const [lostReportId, setLostReportId] = useState<number | null>(null)
  const [candidates, setCandidates] = useState<CandidateMatch[]>([])
  const [candidatesLoading, setCandidatesLoading] = useState(false)
  const [baselineRequired, setBaselineRequired] = useState(false)
  const [foundReportId, setFoundReportId] = useState<number | null>(null)

  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reconsiderClaimId, setReconsiderClaimId] = useState<number | null>(null)
  const [reconsiderReason, setReconsiderReason] = useState('')

  useEffect(() => {
    let active = true
    claimsApi.myClaims()
      .then((data) => { if (active) setClaims(data) })
      .catch((cause: unknown) => { if (active) setError(errorMessage(cause, 'Could not load your claims.')) })
      .finally(() => { if (active) setClaimsLoading(false) })
    return () => { active = false }
  }, [claimsVersion])

  useEffect(() => {
    let active = true
    api.getMyReports()
      .then((reports) => { if (active) setLostReports(reports.filter((r) => r.type === 'LOST' && r.status !== 'CLOSED')) })
      .catch((cause: unknown) => { if (active) setError(errorMessage(cause, 'Could not load your reports.')) })
      .finally(() => { if (active) setReportsLoading(false) })
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (lostReportId === null) return
    let active = true
    api.getCandidates(lostReportId)
      .then((matches) => { if (active) setCandidates(matches) })
      .catch((cause: unknown) => {
        if (!active) return
        if (cause instanceof ApiRequestError && cause.status === 409) setBaselineRequired(true)
        else setError(errorMessage(cause, 'Could not load candidate found reports.'))
      })
      .finally(() => { if (active) setCandidatesLoading(false) })
    return () => { active = false }
  }, [lostReportId])

  const lostById = useMemo(() => new Map(lostReports.map((r) => [r.id, r])), [lostReports])

  function chooseLostReport(value: string) {
    const id = value ? Number(value) : null
    setLostReportId(id)
    setFoundReportId(null)
    setCandidates([])
    setBaselineRequired(false)
    setCandidatesLoading(id !== null)
  }

  function reloadClaims() {
    setClaimsVersion((v) => v + 1)
  }

  async function handleSubmitClaim(e: FormEvent) {
    e.preventDefault()
    if (lostReportId === null || foundReportId === null) return
    setMessage(null)
    setError(null)
    setBusy(true)
    try {
      const saved = await claimsApi.submitClaim(lostReportId, foundReportId)
      setMessage(`Claim #${saved.claimId} submitted. Status: ${STATUS_LABEL[saved.status]}.`)
      setFoundReportId(null)
      reloadClaims()
    } catch (cause: unknown) {
      setError(errorMessage(cause, 'Failed to submit claim.'))
    } finally {
      setBusy(false)
    }
  }

  async function handleWithdraw(claim: ClaimantClaim) {
    if (!window.confirm(`Withdraw claim #${claim.claimId}? This cannot be undone.`)) return
    setMessage(null)
    setError(null)
    setBusy(true)
    try {
      await claimsApi.withdrawClaim(claim.claimId)
      setMessage(`Claim #${claim.claimId} was withdrawn.`)
      reloadClaims()
    } catch (cause: unknown) {
      setError(errorMessage(cause, 'Failed to withdraw the claim.'))
    } finally {
      setBusy(false)
    }
  }

  async function handleRequestReconsideration(e: FormEvent, claim: ClaimantClaim) {
    e.preventDefault()
    if (!reconsiderReason.trim()) {
      setError('Describe the new proof you have so staff can decide whether to reopen your claim.')
      return
    }
    setMessage(null)
    setError(null)
    setBusy(true)
    try {
      await claimsApi.requestReconsideration(claim.claimId, reconsiderReason.trim())
      setMessage(`Your reconsideration request for claim #${claim.claimId} was sent to staff.`)
      setReconsiderClaimId(null)
      setReconsiderReason('')
      reloadClaims()
    } catch (cause: unknown) {
      setError(errorMessage(cause, 'The reconsideration request was not sent.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page-wrap narrow-page space-y-6">
      <div>
        <p className="eyebrow">CLAIMS</p>
        <h1>Claim a found item</h1>
        <p className="page-intro mb-0!">
          Choose one of your lost reports, then the found report you believe is your item. Staff review every claim
          and check the item physically before anything is released.
        </p>
      </div>

      {message && <div role="status" className="alert alert-success">{message}</div>}
      {error && <div role="alert" className="alert alert-error">{error}</div>}

      <div className="surface">
        <form onSubmit={handleSubmitClaim} className="space-y-4">
          <div>
            <label htmlFor="claim-lost-report" className="block text-xs font-semibold text-fb-ink">Your lost report *</label>
            <select
              id="claim-lost-report"
              value={lostReportId ?? ''}
              onChange={(e) => chooseLostReport(e.target.value)}
              disabled={reportsLoading}
              className="mt-1 w-full rounded-lg border border-fb-line bg-white px-3 py-2 text-sm"
            >
              <option value="">{reportsLoading ? 'Loading your reports…' : 'Select a lost report'}</option>
              {lostReports.map((r) => (
                <option key={r.id} value={r.id}>#{r.id} – {describeReport(r)} ({r.eventDate})</option>
              ))}
            </select>
            {!reportsLoading && lostReports.length === 0 && (
              <p className="mt-2! text-xs text-fb-muted">
                You have no open lost reports. <Link className="font-semibold text-fb-green underline" to="/report/lost">Report a lost item</Link> first.
              </p>
            )}
          </div>

          {lostReportId !== null && (
            <fieldset>
              <legend className="block text-xs font-semibold text-fb-ink">Found report you are claiming *</legend>
              {candidatesLoading && <p className="mt-2! text-sm text-fb-muted">Finding similar found reports…</p>}
              {baselineRequired && (
                <p className="mt-2! rounded-lg border border-fb-warn-line bg-fb-warn-bg p-2 text-xs text-fb-warn">
                  Save your private baseline evidence in the <Link className="font-semibold underline" to="/evidence">Evidence Workspace</Link> before claiming.
                </p>
              )}
              {!candidatesLoading && !baselineRequired && candidates.length === 0 && (
                <p className="mt-2! text-sm text-fb-muted">No matching found reports yet. Check back later.</p>
              )}
              <div className="mt-2 space-y-2">
                {candidates.map((c) => (
                  <label
                    key={c.reportId}
                    className={`flex cursor-pointer items-center justify-between gap-3 rounded-lg border p-3 text-sm ${
                      foundReportId === c.reportId ? 'border-fb-green bg-fb-mint/50' : 'border-fb-line hover:border-fb-mint-line'
                    }`}
                  >
                    <span className="flex items-center gap-3">
                      <input
                        type="radio"
                        name="found-report"
                        value={c.reportId}
                        checked={foundReportId === c.reportId}
                        onChange={() => setFoundReportId(c.reportId)}
                      />
                      <span>Found report #{c.reportId} – {describeReport(c)}</span>
                    </span>
                    <span className="text-xs text-fb-muted">{c.score.toFixed(1)} match score</span>
                  </label>
                ))}
              </div>
            </fieldset>
          )}

          <button
            type="submit"
            disabled={busy || lostReportId === null || foundReportId === null}
            className="button button-primary button-small"
          >
            Submit ownership claim
          </button>
        </form>
      </div>

      <div className="surface">
        <h3>My claims</h3>
        <p className="mt-1! text-sm text-fb-muted">You can withdraw a claim until staff make a final decision on it.</p>
        {claimsLoading ? (
          <p className="mt-4! text-sm text-fb-muted">Loading your claims…</p>
        ) : claims.length === 0 ? (
          <p className="mt-4! text-sm text-fb-muted">You have not submitted any claims yet.</p>
        ) : (
          <ul className="mt-4 divide-y divide-fb-line">
            {claims.map((claim) => {
              const lost = lostById.get(claim.lostReportId)
              return (
                <li key={claim.claimId} className="flex flex-wrap items-center justify-between gap-3 py-3">
                  <div>
                    <p className="text-sm font-semibold text-fb-ink">Claim #{claim.claimId}</p>
                    <p className="text-xs text-fb-muted">
                      Lost report #{claim.lostReportId}{lost ? ` (${describeReport(lost)})` : ''} → Found report #{claim.foundReportId}
                    </p>
                    <p className="text-xs text-fb-muted">Submitted {new Date(claim.createdAt).toLocaleString()}</p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${STATUS_STYLE[claim.status]}`}>
                      {STATUS_LABEL[claim.status]}
                    </span>
                    {claim.withdrawable && (
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => handleWithdraw(claim)}
                        className="button button-small bg-fb-coral text-white hover:bg-fb-error"
                      >
                        Withdraw
                      </button>
                    )}
                    {claim.canRequestReconsideration && reconsiderClaimId !== claim.claimId && (
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => { setReconsiderClaimId(claim.claimId); setReconsiderReason('') }}
                        className="button button-secondary button-small text-fb-green"
                      >
                        Request reconsideration
                      </button>
                    )}
                  </div>
                  {RECONSIDERATION_NOTE[claim.reconsiderationStatus] && (
                    <p className="w-full text-xs text-fb-muted">{RECONSIDERATION_NOTE[claim.reconsiderationStatus]}</p>
                  )}
                  {reconsiderClaimId === claim.claimId && (
                    <form onSubmit={(e) => handleRequestReconsideration(e, claim)} className="w-full space-y-2 rounded-lg border border-fb-mint-line bg-fb-mint p-3">
                      <label htmlFor={`reconsider-${claim.claimId}`} className="block text-xs font-semibold text-fb-green-dark">
                        What new proof do you have? (sent to staff; you can ask once per claim) *
                      </label>
                      <textarea
                        id={`reconsider-${claim.claimId}`}
                        rows={3}
                        maxLength={RECONSIDERATION_MAX_LENGTH}
                        value={reconsiderReason}
                        onChange={(e) => setReconsiderReason(e.target.value)}
                        placeholder="e.g. I found the original box, which shows the serial number ending in A82F."
                        className="w-full rounded-lg border border-fb-mint-line bg-white px-3 py-2 text-sm"
                      />
                      <p className="text-xs text-fb-green-dark">Don't paste full serial numbers here unless staff ask; describe what you can show them at the desk.</p>
                      <div className="flex gap-2">
                        <button
                          type="submit"
                          disabled={busy}
                          className="button button-primary button-small"
                        >
                          Send to staff
                        </button>
                        <button
                          type="button"
                          onClick={() => setReconsiderClaimId(null)}
                          className="button button-secondary button-small"
                        >
                          Cancel
                        </button>
                      </div>
                    </form>
                  )}
                </li>
              )
            })}
          </ul>
        )}
      </div>
    </div>
  )
}
