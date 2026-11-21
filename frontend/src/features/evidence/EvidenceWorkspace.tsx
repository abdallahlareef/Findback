import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { api } from './api'
import type { Account, Evidence, Version, Pair, StaffCase, Report, Claim } from './api'
import './evidence.css'

const blank: Evidence = { serialSuffix: '', privateMark: '', accessoryDescription: '' }
function EvidenceFields({ value, onChange }: { value: Evidence; onChange: (data: Evidence) => void }) {
  return <fieldset><legend>Private identifying details</legend><p>Leave unknown details blank. Never enter passwords, unlock PINs or payment-card numbers.</p>
    <label>Serial suffix (last 4 letters or digits)<input maxLength={4} pattern="[A-Za-z0-9]{4}" value={value.serialSuffix || ''} onChange={e => onChange({ ...value, serialSuffix: e.target.value })} /></label>
    <label>Private identifying mark<textarea maxLength={500} value={value.privateMark || ''} onChange={e => onChange({ ...value, privateMark: e.target.value })} /></label>
    <label>Distinctive accessory<textarea maxLength={500} value={value.accessoryDescription || ''} onChange={e => onChange({ ...value, accessoryDescription: e.target.value })} /></label>
  </fieldset>
}
function History({ versions }: { versions: Version[] }) {
  return <div>{versions.map(v => <details key={v.id}><summary>Version {v.version} · {v.provenance} · {new Date(v.createdAt).toLocaleString()}</summary>
    <dl><dt>Serial suffix</dt><dd>{v.evidence.serialSuffix || 'Unknown'}</dd><dt>Private mark</dt><dd>{v.evidence.privateMark || 'Unknown'}</dd><dt>Accessory</dt><dd>{v.evidence.accessoryDescription || 'Unknown'}</dd></dl>{v.reason && <p>Reason: {v.reason}</p>}</details>)}</div>
}
export default function EvidenceWorkspace() {
  const [account, setAccount] = useState<Account | null>(null)
  const [ready, setReady] = useState(false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [register, setRegister] = useState(false)
  const [reports, setReports] = useState<Report[]>([])
  const [report, setReport] = useState<Report | null>(null)
  const [versions, setVersions] = useState<Version[]>([])
  const [data, setData] = useState<Evidence>(blank)
  const [reason, setReason] = useState('')
  const [claimId, setClaimId] = useState('')
  const [pair, setPair] = useState<Pair | null>(null)
  const [staffCase, setStaffCase] = useState<StaffCase | null>(null)
  const [staffMode, setStaffMode] = useState(false)
  const [claims, setClaims] = useState<Claim[]>([])
  const [candidates, setCandidates] = useState<{ reportId: number; id?: number; category: string; campusZone: string }[]>([])
  const [requestId, setRequestId] = useState(() => crypto.randomUUID())
  useEffect(() => { let alive = true; api<Account>('/api/auth/me').then(a => { if (alive) setAccount(a) }).catch(() => {}).finally(() => { if (alive) setReady(true) }); return () => { alive = false } }, [])
  async function run(action: () => Promise<void>) { setBusy(true); setError(''); setMessage(''); try { await action() } catch (e) { setError(e instanceof Error ? e.message : 'Request failed.') } finally { setBusy(false) } }
  async function refreshReports() { setReports(await api<Report[]>('/api/reports/mine')) }
  async function loadClaim(id: string, staff: boolean) {
    setPair(null); setStaffCase(null); setData(blank); setReason(''); setRequestId(crypto.randomUUID())
    if (staff) setStaffCase(await api<StaffCase>(`/api/evidence/staff/claims/${id}`))
    else setPair(await api<Pair>(`/api/evidence/claims/${id}`))
  }
  function clearPrivateState() { setReports([]); setReport(null); setVersions([]); setData(blank); setPair(null); setStaffCase(null); setClaims([]); setCandidates([]); setClaimId(''); setReason(''); setStaffMode(false) }
  async function signIn(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const f = new FormData(e.currentTarget)
    await run(async () => { if (register) await api('/api/auth/register', 'POST', { name: f.get('name'), email: f.get('email'), password: f.get('password') }); const a = await api<Account>('/api/auth/login', 'POST', { email: f.get('email'), password: f.get('password') }); clearPrivateState(); setAccount(a); await refreshReports() })
  }
  if (!ready) return <main className="evidence-app">Loading session…</main>
  return <main className="evidence-app"><header><p className="eyebrow">FINDBACK · PRIVATE EVIDENCE</p><h1>Keep your clues private.</h1><p>Independent evidence. Staff-reviewed decisions.</p></header>
    <aside className="notice">Integration demo — use invented details only. Operational audit and handover services still depend on Member 4.</aside>
    {error && <p className="error" role="alert">{error}</p>}{message && <p className="success" role="status">{message}</p>}
    {!account ? <form onSubmit={signIn}><h2>{register ? 'Create an account' : 'Sign in'}</h2>{register && <label>Name<input name="name" required maxLength={100} autoComplete="name" /></label>}<label>Email<input name="email" type="email" required autoComplete="username" /></label><label>Password<input name="password" type="password" minLength={8} required autoComplete={register ? 'new-password' : 'current-password'} /></label><button disabled={busy}>{register ? 'Register and sign in' : 'Sign in'}</button><button type="button" className="secondary" onClick={() => setRegister(!register)}>{register ? 'Use existing account' : 'Create account'}</button></form> : <>
      <nav><span>{account.name} · {account.role === 'ADMIN' ? 'Staff' : 'Member'}</span><button disabled={busy} onClick={() => void run(async () => { await api('/api/auth/logout', 'POST'); clearPrivateState(); setAccount(null) })}>Sign out</button></nav>
      <section><h2>1. Your reports and baseline evidence</h2><p>Save a baseline before viewing candidates. Earlier evidence stays unchanged.</p><button disabled={busy} onClick={() => void run(refreshReports)}>Load my reports</button>
        <div className="report-list">{reports.map(r => <button className="secondary" disabled={busy} key={r.id} onClick={() => void run(async () => { setReport(null); setVersions([]); setCandidates([]); const history = await api<Version[]>(`/api/evidence/reports/${r.id}`); setReport(r); setVersions(history); setData(blank); setReason(''); setRequestId(crypto.randomUUID()) })}>#{r.id} · {r.type} · {r.category}</button>)}</div>
        <details><summary>Create a report for the evidence workflow</summary><form onSubmit={e => { e.preventDefault(); const f = new FormData(e.currentTarget); void run(async () => { const r = await api<Report>('/api/reports', 'POST', { type: f.get('type'), category: f.get('category'), colour: f.get('colour'), campusZone: f.get('zone'), eventDate: f.get('date') }); await refreshReports(); setReport(r); setVersions([]); setCandidates([]); setData(blank); setMessage(`Report #${r.id} created. Save its private baseline below.`) }) }}><label>Type<select name="type"><option>LOST</option><option>FOUND</option></select></label><label>Category<input name="category" required maxLength={100} placeholder="Earbuds" /></label><label>Broad colour<input name="colour" required maxLength={50} /></label><label>Campus zone<input name="zone" required maxLength={100} /></label><label>Date<input name="date" type="date" required /></label><button disabled={busy}>Create report</button></form></details>
        {report && <article><h3>Report #{report.id} · {report.type}</h3><History versions={versions} />
          {(versions.length === 0 || report.type === 'FOUND') && <form onSubmit={e => { e.preventDefault(); void run(async () => { const path = `/api/evidence/reports/${report.id}/${versions.length ? 'finder-corrections' : 'baseline'}`; await api(path, 'POST', versions.length ? { evidence: data, reason, requestId } : data); setVersions(await api<Version[]>(`/api/evidence/reports/${report.id}`)); setRequestId(crypto.randomUUID()); setData(blank); setReason(''); setMessage('Evidence saved. Earlier versions are preserved.') }) }}><EvidenceFields value={data} onChange={setData} />{versions.length > 0 && <label>Correction reason<textarea value={reason} maxLength={500} required onChange={e => setReason(e.target.value)} /></label>}<button disabled={busy}>{versions.length ? 'Append finder correction' : 'Save baseline'}</button></form>}
          {report.type === 'LOST' && versions.length > 0 && <><p>Claimant supplements are submitted against a claim after staff request them.</p><button disabled={busy} onClick={() => void run(async () => { setCandidates(await api(`/api/reports/${report.id}/candidates`)) })}>Find candidates</button><ul>{candidates.map(c => { const id = c.reportId ?? c.id; return <li key={id}>{c.category} · {c.campusZone} · #{id} <button disabled={busy} onClick={() => void run(async () => { const claim = await api<Claim>('/api/v1/claims', 'POST', { lostReportId: report.id, foundReportId: id }); setClaimId(String(claim.claimId)); setStaffMode(false); await loadClaim(String(claim.claimId), false); setMessage(`Claim #${claim.claimId} submitted for review.`) })}>Submit claim</button></li> })}</ul></>}
        </article>}
      </section>
      <section><h2>2. Claim evidence and supplements</h2><form onSubmit={e => { e.preventDefault(); void run(() => loadClaim(claimId, staffMode)) }}><label>Claim number<input type="number" min={1} required value={claimId} onChange={e => { setClaimId(e.target.value); setPair(null); setStaffCase(null) }} /></label>{account.role === 'ADMIN' && <label><input type="checkbox" checked={staffMode} onChange={e => { setStaffMode(e.target.checked); setPair(null); setStaffCase(null) }} /> Staff review (cannot review your own case)</label>}<button disabled={busy}>Load claim evidence</button></form>
        {account.role === 'ADMIN' && <><button disabled={busy} onClick={() => void run(async () => setClaims(await api('/api/v1/claims/staff/claims')))}>List claims</button><ul>{claims.map(c => <li key={c.claimId}><button disabled={busy} onClick={() => void run(async () => { setClaimId(String(c.claimId)); setStaffMode(true); await loadClaim(String(c.claimId), true) })}>Claim #{c.claimId} · {c.status}</button></li>)}</ul></>}
        {pair && <article><p>Status: {pair.status} · Submissions used: {pair.submissionsUsed}/3</p><History versions={pair.versions} />{pair.pendingRequestId && <form onSubmit={e => { e.preventDefault(); void run(async () => { setPair(await api(`/api/evidence/claims/${claimId}/supplements`, 'POST', { requestId: pair.pendingRequestId, reason, evidence: data })); setData(blank); setReason(''); setMessage('Supplement submitted for review.') }) }}><EvidenceFields value={data} onChange={setData} /><label>Reason<textarea maxLength={500} required value={reason} onChange={e => setReason(e.target.value)} /></label><button disabled={busy}>Submit requested supplement</button></form>}</article>}
        {staffCase && <article><h3>Staff-only DOVE comparison</h3><p>{staffCase.summary.strength} · {staffCase.summary.distinctMatches} distinct matches</p><table><thead><tr><th>Clue</th><th>Assessment</th></tr></thead><tbody><tr><td>Serial suffix</td><td>{staffCase.summary.serialOutcome}</td></tr><tr><td>Private mark</td><td>{staffCase.summary.markOutcome}</td></tr><tr><td>Accessory</td><td>{staffCase.summary.accessoryOutcome}</td></tr></tbody></table><p>Every return needs a separate staff decision and physical checks.</p><h4>Claimant history</h4><History versions={staffCase.claimant.versions} /><h4>Finder history</h4><History versions={staffCase.finder} />
          <form onSubmit={e => { e.preventDefault(); void run(async () => { await api(`/api/evidence/staff/claims/${claimId}/supplement-requests`, 'POST', { requestId, reason }); await loadClaim(claimId, true); setMessage('Supplement requested. The claimant sees a neutral request.') }) }}><label>Staff reason for requesting a supplement<textarea maxLength={500} required value={reason} onChange={e => setReason(e.target.value)} /></label><button disabled={busy || !!staffCase.claimant.pendingRequestId || staffCase.claimant.submissionsUsed >= 3}>Request supplement</button></form>
          <details><summary>Record a manual assessment</summary><p>Count only useful private clues. Use the same group name for details belonging to one clue. Do not count public colour, brand or model.</p><form onSubmit={e => { e.preventDefault(); const f = new FormData(e.currentTarget); void run(async () => { await api(`/api/evidence/staff/claims/${claimId}/assessments`, 'POST', { claimantVersionId: staffCase.summary.claimantVersionId, finderVersionId: staffCase.summary.finderVersionId, serialOutcome: f.get('serial'), markOutcome: f.get('mark'), accessoryOutcome: f.get('accessory'), serialClueGroup: f.get('serialGroup'), markClueGroup: f.get('markGroup'), accessoryClueGroup: f.get('accessoryGroup'), criticalConflict: f.get('critical') === 'on', reason: f.get('reason') }); await loadClaim(claimId, true); setMessage('Assessment recorded with an immutable history.') }) }}>{['serial', 'mark', 'accessory'].map(slot => <fieldset key={slot}><legend>{slot}</legend><label>Outcome<select name={slot} defaultValue="UNKNOWN"><option>UNKNOWN</option><option>MATCH</option><option>CONTRADICTION</option></select></label><label>Distinct clue group (required for a match)<input name={`${slot}Group`} maxLength={100} /></label></fieldset>)}<label><input type="checkbox" name="critical" /> Unresolved critical identifier contradiction</label><label>Assessment reason<textarea name="reason" maxLength={500} required /></label><button disabled={busy}>Record assessment</button></form></details><details><summary>Append a staff evidence correction</summary><p>This preserves original evidence and does not consume a claimant submission. Staff corrections remain visible only to reviewers.</p><form onSubmit={e => { e.preventDefault(); const f = new FormData(e.currentTarget); void run(async () => { await api(`/api/evidence/staff/claims/${claimId}/corrections/${f.get('side')}`, 'POST', { requestId, evidence: data, reason: f.get('correctionReason') }); await loadClaim(claimId, true); setMessage('Staff correction recorded; linked cases require fresh review if previously approved.') }) }}><label>Evidence side<select name="side"><option>CLAIMANT</option><option>FINDER</option></select></label><EvidenceFields value={data} onChange={setData} /><label>Correction reason<textarea name="correctionReason" maxLength={500} required /></label><button disabled={busy}>Append staff correction</button></form></details><details><summary>Comparison history ({staffCase.history.length})</summary><ol>{staffCase.history.map((h, i) => <li key={i}>{h.strength} · {h.distinctMatches} distinct matches · {h.reason || 'Automatic structured comparison'}</li>)}</ol></details>
        </article>}
      </section>
    </>}
  </main>
}
