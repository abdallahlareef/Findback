import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api/client'
import type { CandidateMatch } from '../types/api'
import { EmptyState, ErrorMessage, Loading } from '../components/Feedback'
import { ReportCard } from '../components/ReportCard'

export function CandidatesPage() {
  const { id } = useParams()
  const reportId = Number(id)
  const [candidates, setCandidates] = useState<CandidateMatch[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  useEffect(() => {
    if (!Number.isSafeInteger(reportId) || reportId < 1) { setError('Invalid report ID.'); setLoading(false); return }
    api.getCandidates(reportId).then(setCandidates).catch((cause: unknown) => setError(cause instanceof Error ? cause.message : 'Unable to load candidate reports.')).finally(() => setLoading(false))
  }, [reportId])

  return <div className="page-wrap">
    <Link className="back-link" to="/my-reports">← Back to my reports</Link>
    <p className="eyebrow">DISCOVERY RESULTS</p><h1>Candidate matches</h1>
    <p className="page-intro">These found reports share some details with your lost report.</p>
    <div className="notice score-notice"><strong>Match score</strong> ranks potentially similar reports. It does not prove ownership.</div>
    {loading && <Loading label="Finding similar reports…" />}{error && <ErrorMessage>{error}</ErrorMessage>}
    {!loading && !error && candidates.length === 0 && <EmptyState>No candidate reports are available yet.</EmptyState>}
    <section className="report-grid">{candidates.map((candidate) => <ReportCard report={candidate} key={candidate.reportId} />)}</section>
  </div>
}
