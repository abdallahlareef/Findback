import { useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../api/client'
import type { SafeReportSummary, SearchFilters } from '../types/api'
import { EmptyState, ErrorMessage, Loading } from '../components/Feedback'
import { ReportCard } from '../components/ReportCard'

const emptyFilters: SearchFilters = { type: '', category: '', campusZone: '', eventDate: '', status: '' }

export function SearchPage() {
  const [filters, setFilters] = useState<SearchFilters>(emptyFilters)
  const [results, setResults] = useState<SafeReportSummary[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function search(event?: FormEvent) {
    event?.preventDefault(); setError(''); setLoading(true)
    try { setResults(await api.searchReports(filters)) }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Unable to search reports.') }
    finally { setLoading(false) }
  }

  function clear() {
    setFilters(emptyFilters); setResults(null); setError('')
  }

  return <div className="page-wrap">
    <p className="eyebrow">CAMPUS DIRECTORY</p><h1>Search reports</h1><p className="page-intro">Search safe details shared by the campus community.</p>
    <form className="surface search-form" onSubmit={search}>
      <label>Report type<select value={filters.type} onChange={(e) => setFilters({ ...filters, type: e.target.value as SearchFilters['type'] })}><option value="">Any type</option><option value="LOST">Lost</option><option value="FOUND">Found</option></select></label>
      <label>Category<input value={filters.category} onChange={(e) => setFilters({ ...filters, category: e.target.value })} placeholder="Any category" /></label>
      <label>Campus area<input value={filters.campusZone} onChange={(e) => setFilters({ ...filters, campusZone: e.target.value })} placeholder="Any area" /></label>
      <label>Event date<input type="date" value={filters.eventDate} onChange={(e) => setFilters({ ...filters, eventDate: e.target.value })} /></label>
      <label>Status<select value={filters.status} onChange={(e) => setFilters({ ...filters, status: e.target.value as SearchFilters['status'] })}><option value="">Any status</option><option value="ACTIVE">Active</option><option value="MATCHED">Matched</option><option value="CLOSED">Closed</option></select></label>
      <div className="form-actions"><button className="button button-primary" disabled={loading}>{loading ? 'Searching…' : 'Search'}</button><button type="button" className="button button-secondary" onClick={clear}>Clear filters</button></div>
    </form>
    <div className="results-heading">{results && <h2>{results.length} {results.length === 1 ? 'report' : 'reports'} found</h2>}</div>
    {loading && <Loading label="Searching reports…" />}{error && <ErrorMessage>{error}</ErrorMessage>}
    {results && results.length === 0 && !loading && <EmptyState>No reports match those filters. Try changing or clearing them.</EmptyState>}
    <section className="report-grid">{results?.map((report) => <ReportCard report={report} key={report.reportId} />)}</section>
  </div>
}
