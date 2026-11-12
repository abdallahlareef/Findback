import React, { useState } from 'react';
import { StaffReviewDashboard } from './features/claims/StaffReviewDashboard';
import { StudentClaimPortal } from './features/claims/StudentClaimPortal';

export default function App() {
  const [view, setView] = useState<'staff' | 'student'>('staff');

  return (
    <div className="min-h-screen bg-slate-100">
      <nav className="bg-slate-900 px-6 py-3 text-white flex items-center justify-between shadow">
        <div className="font-bold tracking-wide">FindBack Prototype · Module 3</div>
        <div className="flex gap-2">
          <button
            onClick={() => setView('staff')}
            className={`rounded px-3 py-1.5 text-xs font-semibold transition ${
              view === 'staff' ? 'bg-blue-600 text-white' : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
            }`}
          >
            Staff Dashboard
          </button>
          <button
            onClick={() => setView('student')}
            className={`rounded px-3 py-1.5 text-xs font-semibold transition ${
              view === 'student' ? 'bg-blue-600 text-white' : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
            }`}
          >
            Student Portal
          </button>
        </div>
      </nav>

      <main>
        {view === 'staff' ? <StaffReviewDashboard /> : <StudentClaimPortal />}
      </main>
    </div>
  );
}
