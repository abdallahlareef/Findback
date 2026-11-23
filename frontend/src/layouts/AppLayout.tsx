import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    try { await logout() } finally { navigate('/login', { replace: true }) }
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <NavLink className="brand" to="/dashboard" aria-label="FindBack dashboard">
          <span className="brand-mark" aria-hidden="true">F</span><span>FindBack</span>
        </NavLink>
        <nav className="main-nav" aria-label="Main navigation">
          <NavLink to="/dashboard">Dashboard</NavLink>
          <NavLink to="/report/lost">Report Lost</NavLink>
          <NavLink to="/report/found">Report Found</NavLink>
          <NavLink to="/my-reports">My Reports</NavLink>
          <NavLink to="/search">Search</NavLink>
          <NavLink to="/claims">Claims</NavLink>
          <NavLink to="/evidence">Evidence</NavLink>
        </nav>
        <div className="account-nav">
          <span className="account-name">{user?.name}</span>
          <button className="button button-quiet button-small" onClick={handleLogout}>Log out</button>
        </div>
      </header>
      <main className="main-content"><Outlet /></main>
      <footer className="site-footer">FindBack <span>·</span> Campus lost and found</footer>
    </div>
  )
}
