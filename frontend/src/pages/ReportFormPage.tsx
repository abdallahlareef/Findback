import { useState } from 'react'
import type { FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import type { ReportPayload, ReportType } from '../types/api'
import { ErrorMessage } from '../components/Feedback'
import { RISK_LEVELS, riskLabel, riskRank, suggestRiskLevel } from '../features/claims/riskLevels'
import type { RiskLevel } from '../features/claims/riskLevels'

export function ReportFormPage({ type }: { type: ReportType }) {
  const navigate = useNavigate()
  const location = useLocation()
  const found = type === 'FOUND'
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [riskLevel, setRiskLevel] = useState<RiskLevel | ''>('')
  const [riskChosenByUser, setRiskChosenByUser] = useState(false)
  const [suggestedRisk, setSuggestedRisk] = useState<RiskLevel | null>(null)

  // Pre-select the recommended level from the category until the user picks one themselves.
  function onCategoryChange(category: string) {
    const suggestion = suggestRiskLevel(category)
    setSuggestedRisk(suggestion)
    if (!riskChosenByUser) setRiskLevel(suggestion ?? '')
  }

  const chosenPolicy = RISK_LEVELS.find((l) => l.value === riskLevel)?.policy
  const belowSuggestion = riskLevel !== '' && suggestedRisk !== null && riskRank(riskLevel) < riskRank(suggestedRisk)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(''); setBusy(true)
    const data = new FormData(event.currentTarget)
    const report: ReportPayload = {
      type,
      category: String(data.get('category')).trim(),
      brand: optional(data.get('brand')),
      model: optional(data.get('model')),
      colour: String(data.get('colour')).trim(),
      campusZone: String(data.get('campusZone')).trim(),
      eventDate: String(data.get('eventDate')),
      timeFrom: optional(data.get('timeFrom')),
      timeTo: optional(data.get('timeTo')),
      safeDescription: optional(data.get('safeDescription')),
      riskLevel: String(data.get('riskLevel')) as RiskLevel,
    }
    try { await api.createReport(report); navigate('/my-reports', { replace: true, state: { created: true } }) }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Unable to save the report.') }
    finally { setBusy(false) }
  }

  return <div className="page-wrap narrow-page">
    <p className="eyebrow">NEW {type} REPORT</p><h1>{found ? 'Report a found item' : 'Report a lost item'}</h1>
    <p className="page-intro">Add details that are safe for the campus community to see and search.</p>
    <form className="surface form-stack report-form" onSubmit={submit}>
      {error && <ErrorMessage>{error}</ErrorMessage>}
      <div className="form-grid">
        <label>Category<input name="category" required maxLength={100} placeholder="e.g. Laptop, Earbuds, Umbrella" onChange={(e) => onCategoryChange(e.target.value)} /></label>
        <label>Colour<input name="colour" required maxLength={50} placeholder="e.g. Black" /></label>
        <label>Brand <span className="optional">Optional</span><input name="brand" maxLength={100} /></label>
        <label>Model <span className="optional">Optional</span><input name="model" maxLength={100} /></label>
        <label>Campus area<input name="campusZone" required maxLength={100} placeholder="e.g. Main library" /></label>
        <label>{found ? 'Date found' : 'Date last seen'}<input name="eventDate" type="date" required /></label>
        <label>Time from <span className="optional">Optional</span><input name="timeFrom" type="time" /></label>
        <label>Time to <span className="optional">Optional</span><input name="timeTo" type="time" /></label>
      </div>
      <label>Item risk level
        <select
          name="riskLevel"
          required
          value={riskLevel}
          onChange={(e) => { setRiskChosenByUser(true); setRiskLevel(e.target.value as RiskLevel | '') }}
        >
          <option value="" disabled>Choose how valuable or sensitive the item is</option>
          {RISK_LEVELS.map((level) => <option key={level.value} value={level.value}>{level.label} – e.g. {level.examples}</option>)}
        </select>
        <span className="optional">
          {suggestedRisk ? `Recommended for this category: ${riskLabel(suggestedRisk)}. ` : ''}
          {chosenPolicy ?? 'Claims on this item are verified according to its risk level.'}
        </span>
        {belowSuggestion && suggestedRisk && (
          <span className="optional">Lower than recommended. Claims will still be checked as at least {riskLabel(suggestedRisk)}.</span>
        )}
      </label>
      <label>Safe description <span className="optional">Optional · max 500 characters</span><textarea name="safeDescription" rows={4} maxLength={500} placeholder="Only include details that are okay for anyone to see." /></label>
      <div className="notice"><strong>Keep it safe.</strong> Don’t include serial numbers, private marks, passwords, PINs, receipts, or other private evidence.</div>
      <div className="form-actions"><button className="button button-primary" disabled={busy}>{busy ? 'Saving…' : 'Submit report'}</button><button className="button button-secondary" type="button" onClick={() => navigate(location.state?.from ?? '/dashboard')}>Cancel</button></div>
    </form>
  </div>
}

function optional(value: FormDataEntryValue | null) {
  const clean = String(value ?? '').trim()
  return clean || undefined
}
