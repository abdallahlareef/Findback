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
  SUBMITTED: 'bg-slate-100 text-slate-700',
  UNDER_REVIEW: 'bg-blue-50 text-blue-800',
  ON_HOLD: 'bg-amber-50 text-amber-800',
  APPROVED: 'bg-emerald-50 text-emerald-800',
  REJECTED: 'bg-red-50 text-red-800',
  WITHDRAWN: 'bg-slate-100 text-slate-500',
  COMPLETED: 'bg-emerald-50 text-emerald-800',
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
    <div className="mx-auto max-w-4xl space-y-6 p-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-xl font-bold text-slate-900">Claim a found item</h2>
        <p className="mt-1 text-sm text-slate-600">
          Choose one of your lost reports, then the found report you believe is your item. Staff review every claim
          and check the item physically before anything is released.
        </p>

        {message && (
          <div role="status" className="mt-4 rounded border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">
            {message}
          </div>
        )}
        {error && (
          <div role="alert" className="mt-4 rounded border border-red-200 bg-red-50 p-3 text-sm text-red-800">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmitClaim} className="mt-6 space-y-4">
          <div>
            <label htmlFor="claim-lost-report" className="block text-xs font-semibold text-slate-700">Your lost report *</label>
            <select
              id="claim-lost-report"
              value={lostReportId ?? ''}
              onChange={(e) => chooseLostReport(e.target.value)}
              disabled={reportsLoading}
              className="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2 text-sm"
            >
              <option value="">{reportsLoading ? 'Loading your reports…' : 'Select a lost report'}</option>
              {lostReports.map((r) => (
                <option key={r.id} value={r.id}>#{r.id} – {describeReport(r)} ({r.eventDate})</option>
              ))}
            </select>
            {!reportsLoading && lostReports.length === 0 && (
              <p className="mt-2 text-xs text-slate-600">
                You have no open lost reports. <Link className="font-semibold text-blue-700 underline" to="/report/lost">Report a lost item</Link> first.
              </p>
            )}
          </div>

          {lostReportId !== null && (
            <fieldset>
              <legend className="block text-xs font-semibold text-slate-700">Found report you are claiming *</legend>
              {candidatesLoading && <p className="mt-2 text-sm text-slate-600">Finding similar found reports…</p>}
              {baselineRequired && (
                <p className="mt-2 rounded border border-amber-200 bg-amber-50 p-2 text-xs text-amber-800">
                  Save your private baseline evidence in the <Link className="font-semibold underline" to="/evidence">Evidence Workspace</Link> before claiming.
                </p>
              )}
              {!candidatesLoading && !baselineRequired && candidates.length === 0 && (
                <p className="mt-2 text-sm text-slate-600">No matching found reports yet. Check back later.</p>
              )}
              <div className="mt-2 space-y-2">
                {candidates.map((c) => (
                  <label
                    key={c.reportId}
                    className={`flex cursor-pointer items-center justify-between gap-3 rounded border p-3 text-sm ${
                      foundReportId === c.reportId ? 'border-blue-600 bg-blue-50/50' : 'border-slate-200 hover:border-slate-300'
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
                    <span className="text-xs text-slate-500">{c.score.toFixed(1)} match score</span>
                  </label>
                ))}
              </div>
            </fieldset>
          )}

          <button
            type="submit"
            disabled={busy || lostReportId === null || foundReportId === null}
            className="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Submit ownership claim
          </button>
        </form>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <h3 className="text-lg font-bold text-slate-900">My claims</h3>
        <p className="mt-1 text-sm text-slate-600">You can withdraw a claim until staff make a final decision on it.</p>
        {claimsLoading ? (
          <p className="mt-4 text-sm text-slate-600">Loading your claims…</p>
        ) : claims.length === 0 ? (
          <p className="mt-4 text-sm text-slate-600">You have not submitted any claims yet.</p>
        ) : (
          <ul className="mt-4 divide-y divide-slate-100">
            {claims.map((claim) => {
              const lost = lostById.get(claim.lostReportId)
              return (
                <li key={claim.claimId} className="flex flex-wrap items-center justify-between gap-3 py-3">
                  <div>
                    <p className="text-sm font-semibold text-slate-900">Claim #{claim.claimId}</p>
                    <p className="text-xs text-slate-600">
                      Lost report #{claim.lostReportId}{lost ? ` (${describeReport(lost)})` : ''} → Found report #{claim.foundReportId}
                    </p>
                    <p className="text-xs text-slate-500">Submitted {new Date(claim.createdAt).toLocaleString()}</p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className={`rounded px-2 py-0.5 text-xs font-semibold ${STATUS_STYLE[claim.status]}`}>
                      {STATUS_LABEL[claim.status]}
                    </span>
                    {claim.withdrawable && (
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => handleWithdraw(claim)}
                        className="rounded bg-red-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-red-700 disabled:opacity-50"
                      >
                        Withdraw
                      </button>
                    )}
                    {claim.canRequestReconsideration && reconsiderClaimId !== claim.claimId && (
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => { setReconsiderClaimId(claim.claimId); setReconsiderReason('') }}
                        className="rounded border border-blue-600 px-3 py-1.5 text-xs font-semibold text-blue-700 hover:bg-blue-50 disabled:opacity-50"
                      >
                        Request reconsideration
                      </button>
                    )}
                  </div>
                  {RECONSIDERATION_NOTE[claim.reconsiderationStatus] && (
                    <p className="w-full text-xs text-slate-600">{RECONSIDERATION_NOTE[claim.reconsiderationStatus]}</p>
                  )}
                  {reconsiderClaimId === claim.claimId && (
                    <form onSubmit={(e) => handleRequestReconsideration(e, claim)} className="w-full space-y-2 rounded border border-blue-200 bg-blue-50 p-3">
                      <label htmlFor={`reconsider-${claim.claimId}`} className="block text-xs font-semibold text-blue-900">
                        What new proof do you have? (sent to staff; you can ask once per claim) *
                      </label>
                      <textarea
                        id={`reconsider-${claim.claimId}`}
                        rows={3}
                        maxLength={RECONSIDERATION_MAX_LENGTH}
                        value={reconsiderReason}
                        onChange={(e) => setReconsiderReason(e.target.value)}
                        placeholder="e.g. I found the original box, which shows the serial number ending in A82F."
                        className="w-full rounded border border-blue-200 bg-white px-3 py-2 text-sm"
                      />
                      <p className="text-xs text-blue-900">Don't paste full serial numbers here unless staff ask; describe what you can show them at the desk.</p>
                      <div className="flex gap-2">
                        <button
                          type="submit"
                          disabled={busy}
                          className="rounded bg-blue-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-blue-700 disabled:opacity-50"
                        >
                          Send to staff
                        </button>
                        <button
                          type="button"
                          onClick={() => setReconsiderClaimId(null)}
                          className="rounded border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
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
