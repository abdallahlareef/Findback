import { Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { Loading } from './components/Feedback'
import { useAuth } from './context/AuthContext'
import { AppLayout } from './layouts/AppLayout'
import { CandidatesPage } from './pages/CandidatesPage'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage, RegisterPage } from './pages/AuthPages'
import { MyReportsPage } from './pages/MyReportsPage'
import { ReportFormPage } from './pages/ReportFormPage'
import { SearchPage } from './pages/SearchPage'
import EvidenceWorkspace from './features/evidence/EvidenceWorkspace'
import { StudentClaimPortal } from './features/claims/StudentClaimPortal'
import { StaffReviewDashboard } from './features/claims/StaffReviewDashboard'
import { AdminLayout } from './features/admin/AdminLayout'
import { CaseHistoryPage } from './features/admin/CaseHistoryPage'

export function App() {
  const { user, loading } = useAuth()
  if (loading) return <Loading label="Preparing your FindBack account…" />
  return <Routes>
    <Route path="/" element={<Navigate to={user ? '/dashboard' : '/login'} replace />} />
    <Route path="/login" element={<LoginPage />} />
    <Route path="/register" element={<RegisterPage />} />
    <Route element={<RequireAuth />}>
      <Route element={<AppLayout />}>
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/report/lost" element={<ReportFormPage type="LOST" />} />
        <Route path="/report/found" element={<ReportFormPage type="FOUND" />} />
        <Route path="/my-reports" element={<MyReportsPage />} />
        <Route path="/search" element={<SearchPage />} />
        <Route path="/reports/:id/candidates" element={<CandidatesPage />} />
        <Route path="/claims" element={<StudentClaimPortal />} />
      </Route>
      {/* Admin console (test UI): administration functions only, separate from the user layout. */}
      <Route element={<RequireAdmin />}>
        <Route path="/admin" element={<AdminLayout />}>
          <Route index element={<StaffReviewDashboard />} />
          <Route path="history" element={<CaseHistoryPage />} />
          <Route path="history/:claimId" element={<CaseHistoryPage />} />
        </Route>
        <Route path="/staff/reviews" element={<Navigate to="/admin" replace />} />
      </Route>
      <Route path="/evidence" element={<EvidenceWorkspace />} />
    </Route>
    <Route path="*" element={<Navigate to={user ? '/dashboard' : '/login'} replace />} />
  </Routes>
}

function RequireAuth() {
  const { user } = useAuth()
  return user ? <Outlet /> : <Navigate to="/login" replace />
}

function RequireAdmin() {
  const { user } = useAuth()
  return user?.role === 'ADMIN' ? <Outlet /> : <Navigate to="/dashboard" replace />
}
