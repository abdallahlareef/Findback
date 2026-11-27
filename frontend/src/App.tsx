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
import { CustodyDeskPage } from './features/admin/CustodyDeskPage'

export function App() {
  const { user, loading } = useAuth()
  if (loading) return <Loading label="Preparing your FindBack account…" />
  const home = !user ? '/login' : user.role === 'ADMIN' ? '/admin' : '/dashboard'
  return <Routes>
    <Route path="/" element={<Navigate to={home} replace />} />
    <Route path="/login" element={<LoginPage />} />
    <Route path="/register" element={<RegisterPage />} />
    <Route element={<RequireAuth />}>
      {/* Student features. Staff accounts are admin-only and are sent to the admin console. */}
      <Route element={<RequireStudent />}>
        <Route element={<AppLayout />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/report/lost" element={<ReportFormPage type="LOST" />} />
          <Route path="/report/found" element={<ReportFormPage type="FOUND" />} />
          <Route path="/my-reports" element={<MyReportsPage />} />
          <Route path="/search" element={<SearchPage />} />
          <Route path="/reports/:id/candidates" element={<CandidatesPage />} />
          <Route path="/claims" element={<StudentClaimPortal />} />
        </Route>
      </Route>
      {/* Admin console (test UI): administration functions only, separate from the user layout. */}
      <Route element={<RequireAdmin />}>
        <Route path="/admin" element={<AdminLayout />}>
          <Route index element={<StaffReviewDashboard />} />
          <Route path="custody" element={<CustodyDeskPage />} />
          <Route path="custody/:foundReportId" element={<CustodyDeskPage />} />
          <Route path="history" element={<CaseHistoryPage />} />
          <Route path="history/:claimId" element={<CaseHistoryPage />} />
        </Route>
        <Route path="/staff/reviews" element={<Navigate to="/admin" replace />} />
      </Route>
      <Route element={user?.role === 'ADMIN' ? <AdminLayout /> : <AppLayout />}>
        <Route path="/evidence" element={<EvidenceWorkspace key={user?.id} />} />
      </Route>
    </Route>
    <Route path="*" element={<Navigate to={home} replace />} />
  </Routes>
}

function RequireAuth() {
  const { user } = useAuth()
  return user ? <Outlet /> : <Navigate to="/login" replace />
}

/** Admin-only accounts: staff never see student pages (the server refuses those actions too). */
function RequireStudent() {
  const { user } = useAuth()
  return user?.role === 'ADMIN' ? <Navigate to="/admin" replace /> : <Outlet />
}

function RequireAdmin() {
  const { user } = useAuth()
  return user?.role === 'ADMIN' ? <Outlet /> : <Navigate to="/dashboard" replace />
}
