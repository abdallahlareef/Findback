import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

const actions = [
  { to: '/report/lost', number: '01', title: 'Report a lost item', detail: 'Tell the campus community what you are looking for.', label: 'Create lost report' },
  { to: '/report/found', number: '02', title: 'Report a found item', detail: 'Help a found belonging get back to its owner.', label: 'Create found report' },
  { to: '/my-reports', number: '03', title: 'View my reports', detail: 'Check the reports you have submitted.', label: 'Open my reports' },
  { to: '/search', number: '04', title: 'Search reports', detail: 'Browse safe details about campus lost and found items.', label: 'Search reports' },
]

export function DashboardPage() {
  const { user } = useAuth()
  return <div className="page-wrap">
    <section className="welcome-banner"><div><p className="eyebrow">YOUR CAMPUS, CONNECTED</p><h1>Welcome, {user?.name}</h1><p>Let’s help the things that matter find their way back.</p></div><span className="welcome-icon" aria-hidden="true">↗</span></section>
    <div className="section-heading"><div><p className="eyebrow">GET STARTED</p><h2>What would you like to do?</h2></div></div>
    <section className="action-grid">
      {actions.map((action) => <Link className="action-card" to={action.to} key={action.to}>
        <span className="action-number">{action.number}</span><h3>{action.title}</h3><p>{action.detail}</p><span className="text-link">{action.label}<span aria-hidden="true"> →</span></span>
      </Link>)}
    </section>
    <p className="privacy-note"><span aria-hidden="true">✦</span> Share only safe, public details. Never include private identifying information in a report.</p>
  </div>
}
