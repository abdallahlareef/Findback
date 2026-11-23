import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'

/**
 * Admin console shell (test UI). Only administration functions live here; user features stay in
 * the normal app layout. Expected to be redesigned later, so it is kept deliberately simple.
 */
export function AdminLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    try { await logout() } finally { navigate('/login', { replace: true }) }
  }

  const tab = ({ isActive }: { isActive: boolean }) =>
    `rounded px-3 py-1.5 text-sm font-medium ${isActive ? 'bg-white/15 text-white' : 'text-slate-300 hover:text-white'}`

  return (
    <div className="min-h-screen bg-slate-100 text-slate-900">
      <header className="bg-slate-900 text-white">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-3 px-6 py-3">
          <div className="flex items-center gap-3">
            <span className="text-lg font-bold">FindBack Admin</span>
            <span className="rounded bg-amber-400 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-slate-900">Test UI</span>
          </div>
          <nav className="flex flex-wrap gap-1" aria-label="Admin navigation">
            <NavLink to="/admin" end className={tab}>Review queue</NavLink>
            <NavLink to="/admin/history" className={tab}>Case history</NavLink>
            <NavLink to="/evidence" className={tab}>Evidence review</NavLink>
          </nav>
          <div className="flex items-center gap-3 text-sm">
            <span className="text-slate-300">{user?.name}</span>
            <NavLink to="/dashboard" className="text-slate-300 underline hover:text-white">User app</NavLink>
            <button
              type="button"
              onClick={handleLogout}
              className="rounded border border-slate-600 px-3 py-1 text-xs hover:bg-slate-800"
            >
              Log out
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-7xl">
        <Outlet />
      </main>
    </div>
  )
}
