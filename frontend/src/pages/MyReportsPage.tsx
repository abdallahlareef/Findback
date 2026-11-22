import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { api } from '../api/client'
import { EmptyState, ErrorMessage, Loading } from '../components/Feedback'
import { ReportCard } from '../components/ReportCard'
import type { ReportResponse } from '../types/api'

export function MyReportsPage() {
  const location = useLocation()
  const [reports, setReports] = useState<ReportResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  useEffect(() => {
    api.getMyReports().then(setReports).catch((cause: unknown) => setError(cause instanceof Error ? cause.message : 'Unable to load reports.')).finally(() => setLoading(false))
  }, [])

  return <div className="page-wrap">
    <div className="section-heading"><div><p className="eyebrow">YOUR ACTIVITY</p><h1>My reports</h1><p className="page-intro">Reports you have shared with the campus.</p></div><Link className="button button-primary" to="/report/lost">＋ New report</Link></div>
    {location.state?.created && <div className="alert alert-success" role="status">Your report was submitted.</div>}
    {loading && <Loading label="Loading your reports…" />}{error && <ErrorMessage>{error}</ErrorMessage>}
    {!loading && !error && reports.length === 0 && <EmptyState>You haven’t submitted any reports yet. <Link to="/report/lost">Create your first report</Link>.</EmptyState>}
    <section className="report-grid">{reports.map((report) => <ReportCard report={report} key={report.id} action={report.type === 'LOST' ? <Link className="text-link" to={`/reports/${report.id}/candidates`}>View candidate matches →</Link> : undefined} />)}</section>
  </div>
}
