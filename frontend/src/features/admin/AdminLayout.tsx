import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'

/**
 * Staff console shell. Uses the same layout classes as the student app (styles.css: topbar, brand,
 * main-nav, account-nav, main-content, site-footer) so both look like one product; only the
 * navigation differs, and staff accounts never see student features.
 */
export function AdminLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    try { await logout() } finally { navigate('/login', { replace: true }) }
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <NavLink className="brand" to="/admin" aria-label="FindBack staff console">
          <span className="brand-mark" aria-hidden="true">F</span>
          <span>FindBack</span>
          <span className="rounded-full bg-fb-mint px-2.5 py-0.5 text-xs font-bold tracking-normal text-fb-green">Staff</span>
        </NavLink>
        <nav className="main-nav" aria-label="Staff navigation">
          <NavLink to="/admin" end>Review queue</NavLink>
          <NavLink to="/admin/custody">Custody desk</NavLink>
          <NavLink to="/admin/history">Case history</NavLink>
          <NavLink to="/evidence">Evidence review</NavLink>
        </nav>
        <div className="account-nav">
          <span className="account-name">{user?.name}</span>
          <button className="button button-quiet button-small" onClick={handleLogout}>Log out</button>
        </div>
      </header>
      <main className="main-content"><Outlet /></main>
      <footer className="site-footer">FindBack <span>·</span> Staff console</footer>
    </div>
  )
}
