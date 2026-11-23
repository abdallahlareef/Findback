import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { NOTE_MAX_LENGTH, claimsApi, errorMessage } from '../claims/member3Api'
import type { CustodyDeskItem, CustodyStatus, ItemCustodyHistory } from '../claims/member3Api'

type Tab = 'WAITING' | 'STORED' | 'RETURNED' | 'ALL'

const TABS: { value: Tab; label: string }[] = [
  { value: 'WAITING', label: 'Waiting for intake' },
  { value: 'STORED', label: 'In storage' },
  { value: 'RETURNED', label: 'Returned' },
  { value: 'ALL', label: 'All' },
]

const STATUS_STYLE: Record<CustodyStatus, string> = {
  REPORTED: 'bg-fb-warn-bg text-fb-warn',
  IN_CUSTODY: 'bg-fb-mint text-fb-green-dark',
  RESERVED: 'bg-fb-info-bg text-fb-info',
  RETURNED: 'bg-fb-chip text-fb-ink',
}

const STATUS_LABEL: Record<CustodyStatus, string> = {
  REPORTED: 'Not received yet',
  IN_CUSTODY: 'In custody',
  RESERVED: 'Reserved for a claim',
  RETURNED: 'Returned to owner',
}

const EVENT_LABEL: Record<string, string> = {
  RECEIVED_INTO_CUSTODY: 'Received into custody',
  STORAGE_RECONFIRMED: 'Storage moved / re-confirmed',
  DISPUTE_RAISED: 'Dispute raised (approved claim put on hold)',
  DISPUTE_RESOLVED: 'Dispute resolved',
  RELEASED_TO_OWNER: 'Released to owner',
}

function inTab(item: CustodyDeskItem, tab: Tab): boolean {
  switch (tab) {
    case 'WAITING': return item.custodyStatus === 'REPORTED' && item.reportStatus !== 'CLOSED'
    case 'STORED': return item.custodyStatus === 'IN_CUSTODY' || item.custodyStatus === 'RESERVED'
    case 'RETURNED': return item.custodyStatus === 'RETURNED'
    default: return true
  }
}

/** FR-11 custody desk: receive items as they are handed in and track where every item is stored. */
export function CustodyDeskPage() {
  const params = useParams()
  const navigate = useNavigate()
  const selectedId = params.foundReportId ? Number(params.foundReportId) : null

  const [items, setItems] = useState<CustodyDeskItem[]>([])
  const [loading, setLoading] = useState(true)
  const [version, setVersion] = useState(0)
  const [tab, setTab] = useState<Tab>('WAITING')
  const [search, setSearch] = useState('')

  // Keyed by item id so a slow response never shows under a different selection.
  const [history, setHistory] = useState<{ id: number; data: ItemCustodyHistory } | null>(null)
  const [historyError, setHistoryError] = useState<{ id: number; message: string } | null>(null)

  const [storageRef, setStorageRef] = useState('')
  const [conditionNotes, setConditionNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)
  const [banner, setBanner] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    claimsApi.custodyDesk()
      .then((data) => { if (active) setItems(data) })
      .catch((cause: unknown) => { if (active) setErrorMsg(errorMessage(cause, 'Could not load the custody desk.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [version])

  useEffect(() => {
    if (selectedId === null || !Number.isSafeInteger(selectedId)) return
    let active = true
    claimsApi.itemCustodyHistory(selectedId)
      .then((data) => { if (active) setHistory({ id: selectedId, data }) })
      .catch((cause: unknown) => { if (active) setHistoryError({ id: selectedId, message: errorMessage(cause, 'Could not load this item.') }) })
    return () => { active = false }
  }, [selectedId, version])

  const selected = items.find((i) => i.foundReportId === selectedId) ?? null
  const shownHistory = history && history.id === selectedId ? history.data : null
  const shownHistoryError = historyError && historyError.id === selectedId ? historyError.message : null

  const needle = search.trim().toLowerCase()
  const visible = items
    .filter((i) => inTab(i, tab))
    .filter((i) => !needle || `#${i.foundReportId} ${i.category} ${i.colour} ${i.campusZone} ${i.storageReference ?? ''} ${i.finderName}`
      .toLowerCase().includes(needle))
  const counts = Object.fromEntries(TABS.map((t) => [t.value, items.filter((i) => inTab(i, t.value)).length])) as Record<Tab, number>

  function select(id: number) {
    setStorageRef('')
    setConditionNotes('')
    setErrorMsg(null)
    setBanner(null)
    navigate(`/admin/custody/${id}`)
  }

  async function handleStore(e: FormEvent, item: CustodyDeskItem) {
    e.preventDefault()
    if (!storageRef.trim() || !conditionNotes.trim()) {
      setErrorMsg('Storage location and condition notes are both required (FR-11).')
      return
    }
    setErrorMsg(null)
    setBanner(null)
    setBusy(true)
    try {
      await claimsApi.recordCustodyIntake(item.foundReportId, storageRef.trim(), conditionNotes.trim())
      setBanner(item.custodyStatus === 'REPORTED'
        ? `Found item #${item.foundReportId} received into custody at ${storageRef.trim()}.`
        : `Found item #${item.foundReportId} storage updated to ${storageRef.trim()}.`)
      setStorageRef('')
      setConditionNotes('')
    } catch (cause: unknown) {
      setErrorMsg(errorMessage(cause, 'The custody record was not saved.'))
    } finally {
      setBusy(false)
      setVersion((v) => v + 1)
    }
  }

  const canStore = selected !== null && selected.reportStatus !== 'CLOSED'
    && (selected.custodyStatus === 'REPORTED' || selected.custodyStatus === 'IN_CUSTODY')

  return (
    <div>
      <p className="eyebrow">STAFF · CUSTODY</p>
      <h1>Custody desk</h1>
      <p className="page-intro">Receive found items when they are handed in, before anyone claims them, and track where each one is stored.</p>
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
      <section className="space-y-3 lg:col-span-5" aria-label="Found items">
        <div className="flex flex-wrap gap-1" role="tablist">
          {TABS.map((t) => (
            <button
              key={t.value}
              type="button"
              role="tab"
              aria-selected={tab === t.value}
              onClick={() => setTab(t.value)}
              className={`button button-small ${tab === t.value ? 'button-primary' : 'button-secondary'}`}
            >
              {t.label} ({counts[t.value] ?? 0})
            </button>
          ))}
        </div>
        <input
          aria-label="Search items"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search item #, category, locker, finder…"
          className="w-full rounded-lg border border-fb-line bg-white px-3 py-1.5 text-sm"
        />
        {loading ? (
          <p className="text-sm text-fb-muted">Loading items…</p>
        ) : visible.length === 0 ? (
          <p className="rounded-lg border border-fb-line bg-white p-4 text-sm text-fb-muted">No items here.</p>
        ) : (
          <ul className="space-y-2">
            {visible.map((item) => (
              <li key={item.foundReportId}>
                <button
                  type="button"
                  onClick={() => select(item.foundReportId)}
                  className={`w-full rounded-2xl border p-3 text-left text-sm ${
                    item.foundReportId === selectedId ? 'border-fb-green bg-fb-mint/60' : 'border-fb-line bg-white hover:border-fb-mint-line'
                  }`}
                >
                  <span className="flex items-center justify-between gap-2">
                    <span className="font-semibold">#{item.foundReportId} – {item.category} · {item.colour}</span>
                    <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${STATUS_STYLE[item.custodyStatus]}`}>
                      {STATUS_LABEL[item.custodyStatus]}
                    </span>
                  </span>
                  <span className="mt-1 block text-xs text-fb-muted">
                    {item.campusZone} · found {item.eventDate} · by {item.finderName}
                    {item.storageReference && <> · <strong>{item.storageReference}</strong></>}
                    {item.activeClaimsCount > 0 && <> · {item.activeClaimsCount} active claim{item.activeClaimsCount === 1 ? '' : 's'}</>}
                  </span>
                  {item.disputeBlocked && <span className="mt-1 block text-xs font-semibold text-fb-error">Blocked by a dispute</span>}
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="lg:col-span-7" aria-label="Item details">
        {errorMsg && <div role="alert" className="mb-3 rounded-lg border border-fb-error-line bg-fb-error-bg p-3 text-sm text-fb-error">{errorMsg}</div>}
        {banner && <div role="status" className="mb-3 rounded-lg border border-fb-mint-line bg-fb-mint p-3 text-sm text-fb-green-dark">{banner}</div>}

        {selectedId === null ? (
          <p className="empty-state">Choose an item to receive it into custody, move it, or see its custody log.</p>
        ) : shownHistoryError ? (
          <p role="alert" className="rounded-2xl border border-fb-error-line bg-fb-error-bg p-4 text-sm text-fb-error">{shownHistoryError}</p>
        ) : !selected ? (
          <p className="text-sm text-fb-muted">Loading item #{selectedId}…</p>
        ) : (
          <div className="space-y-4 rounded-2xl border border-fb-line bg-white p-5 shadow-sm">
            <div className="flex flex-wrap items-start justify-between gap-2 border-b border-fb-line pb-3">
              <div>
                <h3>Found item #{selected.foundReportId} – {selected.category}</h3>
                <p className="text-xs text-fb-muted">
                  {selected.colour} · {selected.campusZone} · found {selected.eventDate} by {selected.finderName}
                  {selected.riskLevel && <> · risk {selected.riskLevel}</>} · report {selected.reportStatus}
                </p>
              </div>
              <span className={`rounded-full px-2.5 py-1 text-xs font-bold ${STATUS_STYLE[selected.custodyStatus]}`}>
                {STATUS_LABEL[selected.custodyStatus]}
              </span>
            </div>

            {selected.custodyStatus !== 'REPORTED' && (
              <p className="text-sm">
                <strong>Storage:</strong> {selected.storageReference ?? '—'}
                {selected.receivedByName && <span className="text-fb-muted"> · received by {selected.receivedByName}</span>}
                {selected.custodyUpdatedAt && <span className="text-fb-muted"> · last change {new Date(selected.custodyUpdatedAt).toLocaleString()}</span>}
              </p>
            )}
            {selected.custodyStatus === 'RESERVED' && selected.reservedByClaimId !== null && (
              <p className="text-sm text-fb-green-dark">
                Reserved for approved claim{' '}
                <Link className="font-semibold underline" to={`/admin/history/${selected.reservedByClaimId}`}>#{selected.reservedByClaimId}</Link>.
                Release it with Complete handover in the review queue.
              </p>
            )}
            {selected.disputeBlocked && (
              <p className="rounded-lg border border-fb-error-line bg-fb-error-bg p-2 text-xs text-fb-error">
                Blocked by an unresolved dispute
                {selected.disputeRaisedByName && <> raised by <strong>{selected.disputeRaisedByName}</strong></>}
                {selected.disputeReason && <>: “{selected.disputeReason}”</>}. A different staff member must resolve it
                from the case in the <Link className="font-semibold underline" to="/admin">review queue</Link>.
              </p>
            )}

            {canStore && (
              <form onSubmit={(e) => handleStore(e, selected)} className="space-y-3 rounded-lg border border-fb-line bg-fb-bg p-3">
                <p className="text-sm font-semibold">
                  {selected.custodyStatus === 'REPORTED' ? 'Receive item into custody' : 'Move item / re-confirm storage'}
                </p>
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <div>
                    <label htmlFor="desk-storage" className="block text-xs font-semibold text-fb-ink">Storage / locker reference *</label>
                    <input
                      id="desk-storage"
                      maxLength={100}
                      value={storageRef}
                      onChange={(e) => setStorageRef(e.target.value)}
                      placeholder="e.g. SEC-LOCKER-B12"
                      className="mt-1 w-full rounded-lg border border-fb-line bg-white px-3 py-1.5 text-sm"
                    />
                  </div>
                  <div>
                    <label htmlFor="desk-condition" className="block text-xs font-semibold text-fb-ink">Condition notes *</label>
                    <input
                      id="desk-condition"
                      maxLength={NOTE_MAX_LENGTH}
                      value={conditionNotes}
                      onChange={(e) => setConditionNotes(e.target.value)}
                      placeholder={selected.custodyStatus === 'REPORTED' ? 'Visible condition when received' : 'Why it moved / current condition'}
                      className="mt-1 w-full rounded-lg border border-fb-line bg-white px-3 py-1.5 text-sm"
                    />
                  </div>
                </div>
                <button
                  type="submit"
                  disabled={busy}
                  className="button button-primary button-small"
                >
                  {selected.custodyStatus === 'REPORTED' ? 'Confirm intake' : 'Save new location'}
                </button>
              </form>
            )}

            <div>
              <h3 className="text-sm font-semibold">Custody log</h3>
              {!shownHistory ? (
                <p className="mt-1! text-xs text-fb-muted">Loading…</p>
              ) : shownHistory.events.length === 0 ? (
                <p className="mt-1! text-xs text-fb-muted">Not received into custody yet.</p>
              ) : (
                <ol className="mt-2 space-y-2">
                  {shownHistory.events.map((event, index) => (
                    <li key={`${event.at}-${index}`} className="rounded-lg border border-fb-line p-2 text-sm">
                      <span className="font-semibold">{EVENT_LABEL[event.type] ?? event.type}</span>
                      <span className="text-xs text-fb-muted"> · {new Date(event.at).toLocaleString()} · by {event.actorName}</span>
                      <p className="text-xs text-fb-muted">{event.summary}</p>
                      {event.details && <p className="text-xs text-fb-ink">{event.details}</p>}
                    </li>
                  ))}
                </ol>
              )}
            </div>

            <div>
              <h3 className="text-sm font-semibold">Claims on this item</h3>
              {!shownHistory ? null : shownHistory.claims.length === 0 ? (
                <p className="mt-1! text-xs text-fb-muted">No claims yet.</p>
              ) : (
                <ul className="mt-2 space-y-1 text-sm">
                  {shownHistory.claims.map((c) => (
                    <li key={c.claimId}>
                      <Link className="font-semibold text-fb-green underline" to={`/admin/history/${c.claimId}`}>Claim #{c.claimId}</Link>
                      <span className="text-xs text-fb-muted"> · {c.claimantName} · {c.status} · risk {c.riskTier}</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        )}
      </section>
    </div>
    </div>
  )
}
