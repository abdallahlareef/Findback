import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { claimsApi, errorMessage } from '../claims/member3Api'
import type { CaseHistory, CaseSummary } from '../claims/member3Api'

const EVENT_LABEL: Record<string, string> = {
  CLAIM_SUBMITTED: 'Claim submitted',
  CLAIM_WITHDRAWN: 'Withdrawn by claimant',
  RECONSIDERATION_REQUESTED: 'Reconsideration requested',
  APPROVE: 'Approved & item reserved',
  HOLD: 'Placed on hold',
  REJECT: 'Rejected',
  RECONSIDER: 'Reopened for review',
  DECLINE_RECONSIDERATION: 'Reconsideration declined',
  RISK_RAISED: 'Risk tier raised',
  HANDOVER: 'Handed over to owner',
  AUTO_CLOSED: 'Closed: item returned to another claimant',
  EVIDENCE_CHANGED: 'Held: evidence changed',
  RECEIVED_INTO_CUSTODY: 'Received into custody',
  STORAGE_RECONFIRMED: 'Storage re-confirmed',
  DISPUTE_RESOLVED: 'Dispute resolved',
  RELEASED_TO_OWNER: 'Released to owner',
}

const SOURCE_STYLE: Record<string, string> = {
  CLAIM: 'bg-blue-50 text-blue-800 border-blue-200',
  DECISION: 'bg-indigo-50 text-indigo-800 border-indigo-200',
  CUSTODY: 'bg-emerald-50 text-emerald-800 border-emerald-200',
}

const SOURCE_LABEL: Record<string, string> = { CLAIM: 'Claimant', DECISION: 'Staff decision', CUSTODY: 'Custody' }

/** FR-12 audit view: every decision, custody event and claimant action for one claim. */
export function CaseHistoryPage() {
  const params = useParams()
  const navigate = useNavigate()
  const selectedId = params.claimId ? Number(params.claimId) : null

  const [cases, setCases] = useState<CaseSummary[]>([])
  const [casesLoading, setCasesLoading] = useState(true)
  const [listError, setListError] = useState<string | null>(null)
  const [filter, setFilter] = useState('')
  const [lookupId, setLookupId] = useState('')

  // Keyed by claim id so a stale response never shows under a different selection.
  const [history, setHistory] = useState<{ id: number; data: CaseHistory } | null>(null)
  const [historyError, setHistoryError] = useState<{ id: number; message: string } | null>(null)

  useEffect(() => {
    let active = true
    claimsApi.historyCases()
      .then((data) => { if (active) setCases(data) })
      .catch((cause: unknown) => { if (active) setListError(errorMessage(cause, 'Could not load cases.')) })
      .finally(() => { if (active) setCasesLoading(false) })
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (selectedId === null || !Number.isSafeInteger(selectedId)) return
    let active = true
    claimsApi.caseHistory(selectedId)
      .then((data) => { if (active) setHistory({ id: selectedId, data }) })
      .catch((cause: unknown) => { if (active) setHistoryError({ id: selectedId, message: errorMessage(cause, 'Could not load this case.') }) })
    return () => { active = false }
  }, [selectedId])

  const shown = history && history.id === selectedId ? history.data : null
  const shownError = historyError && historyError.id === selectedId ? historyError.message : null
  const historyLoading = selectedId !== null && !shown && !shownError

  const needle = filter.trim().toLowerCase()
  const visibleCases = needle
    ? cases.filter((c) => `#${c.claimId} ${c.claimantName} #${c.foundReportId} ${c.itemTitle} ${c.status}`.toLowerCase().includes(needle))
    : cases

  function openLookup(e: FormEvent) {
    e.preventDefault()
    const id = Number(lookupId.replace('#', '').trim())
    if (Number.isSafeInteger(id) && id > 0) navigate(`/admin/history/${id}`)
  }

  return (
    <div className="grid grid-cols-1 gap-6 p-6 lg:grid-cols-12">
      <section className="space-y-3 lg:col-span-4" aria-label="Cases">
        <h1 className="text-xl font-bold">Case history</h1>
        <form onSubmit={openLookup} className="flex gap-2">
          <input
            aria-label="Open claim number"
            value={lookupId}
            onChange={(e) => setLookupId(e.target.value)}
            placeholder="Claim #"
            className="w-full rounded border border-slate-300 bg-white px-3 py-1.5 text-sm"
          />
          <button type="submit" className="rounded bg-slate-900 px-3 py-1.5 text-xs font-semibold text-white">Open</button>
        </form>
        <input
          aria-label="Filter cases"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          placeholder="Filter by name, item, status…"
          className="w-full rounded border border-slate-300 bg-white px-3 py-1.5 text-sm"
        />
        {listError && <p role="alert" className="text-sm text-red-700">{listError}</p>}
        {casesLoading ? (
          <p className="text-sm text-slate-600">Loading cases…</p>
        ) : visibleCases.length === 0 ? (
          <p className="text-sm text-slate-600">No cases to show.</p>
        ) : (
          <ul className="space-y-2">
            {visibleCases.map((c) => (
              <li key={c.claimId}>
                <Link
                  to={`/admin/history/${c.claimId}`}
                  className={`block rounded-lg border p-3 text-sm ${
                    c.claimId === selectedId ? 'border-blue-600 bg-blue-50/60' : 'border-slate-200 bg-white hover:border-slate-300'
                  }`}
                >
                  <span className="flex items-center justify-between gap-2">
                    <span className="font-semibold">Claim #{c.claimId} – {c.itemTitle}</span>
                    <span className="rounded bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-700">{c.status}</span>
                  </span>
                  <span className="mt-1 block text-xs text-slate-600">
                    {c.claimantName} · found #{c.foundReportId} · risk {c.riskTier} · updated {new Date(c.updatedAt).toLocaleString()}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="lg:col-span-8" aria-label="Timeline">
        {selectedId === null ? (
          <p className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600">
            Choose a case to see who did what, when and why.
          </p>
        ) : shownError ? (
          <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">{shownError}</p>
        ) : historyLoading || !shown ? (
          <p className="text-sm text-slate-600">Loading case #{selectedId}…</p>
        ) : (
          <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
            <div className="border-b border-slate-100 pb-3">
              <h2 className="text-lg font-semibold">Claim #{shown.claimId} – {shown.itemTitle}</h2>
              <p className="text-xs text-slate-600">
                Claimant {shown.claimantName} · lost report #{shown.lostReportId} · found report #{shown.foundReportId} ·
                status <strong>{shown.status}</strong> · risk <strong>{shown.riskTier}</strong>
              </p>
            </div>
            <ol className="mt-4 space-y-3">
              {shown.events.map((event, index) => (
                <li key={`${event.at}-${event.type}-${index}`} className="flex gap-3">
                  <span className="w-36 shrink-0 text-xs text-slate-500">{new Date(event.at).toLocaleString()}</span>
                  <div className="flex-1 rounded border border-slate-200 p-3">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className={`rounded border px-2 py-0.5 text-[11px] font-semibold ${SOURCE_STYLE[event.source] ?? ''}`}>
                        {SOURCE_LABEL[event.source] ?? event.source}
                      </span>
                      <span className="text-sm font-semibold">{EVENT_LABEL[event.type] ?? event.type}</span>
                      <span className="text-xs text-slate-500">by {event.actorName}</span>
                    </div>
                    <p className="mt-1 text-xs text-slate-600">{event.summary}</p>
                    {event.details && <p className="mt-1 whitespace-pre-wrap text-sm text-slate-800">{event.details}</p>}
                  </div>
                </li>
              ))}
            </ol>
          </div>
        )}
      </section>
    </div>
  )
}
