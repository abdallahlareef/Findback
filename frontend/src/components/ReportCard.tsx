import type { ReactNode } from 'react'
import type { CandidateMatch, ReportResponse, SafeReportSummary } from '../types/api'

type ReportLike = ReportResponse | SafeReportSummary | CandidateMatch

export function ReportCard({ report, action }: { report: ReportLike; action?: ReactNode }) {
  const isCandidate = 'score' in report
  const id = 'id' in report ? report.id : report.reportId
  return (
    <article className="report-card">
      <div className="report-card-top">
        <span className={`type-pill type-${isCandidate ? 'found' : report.type.toLowerCase()}`}>
          {isCandidate ? 'FOUND MATCH' : report.type}
        </span>
        {!isCandidate && <span className={`status-pill status-${report.status.toLowerCase()}`}>{report.status}</span>}
        {isCandidate && <span className="score">{report.score.toFixed(1)} <small>match score</small></span>}
      </div>
      <h3>{report.category}</h3>
      <p className="item-name">{[report.brand, report.model].filter(Boolean).join(' ') || 'Brand or model not specified'}</p>
      <dl className="report-details">
        <div><dt>Colour</dt><dd>{report.colour}</dd></div>
        <div><dt>Campus area</dt><dd>{report.campusZone}</dd></div>
        {'eventDate' in report && <div><dt>Date</dt><dd>{report.eventDate}</dd></div>}
      </dl>
      {action && <div className="card-action">{action}</div>}
      <span className="sr-only">Report {id}</span>
    </article>
  )
}
