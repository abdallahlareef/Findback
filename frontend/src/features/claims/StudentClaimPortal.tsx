import React, { useState } from 'react';
import { submitStudentClaimApi, withdrawStudentClaimApi } from './member3Api';

export const StudentClaimPortal: React.FC = () => {
  const [studentId, setStudentId] = useState<number>(2012);
  const [lostReportId, setLostReportId] = useState<number>(501);
  const [foundReportId, setFoundReportId] = useState<number>(602);
  
  const [withdrawClaimId, setWithdrawClaimId] = useState<number | ''>('');
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleSubmitClaim = async (e: React.FormEvent) => {
    e.preventDefault();
    setMessage(null);
    setError(null);
    try {
      const saved = await submitStudentClaimApi(studentId, lostReportId, foundReportId);
      
      // Look for 'id' first, fallback to 'claimId'
      const actualId = saved.id || saved.claimId; 
      
      setMessage(`Success! Claim #${actualId} submitted. Status is now ${saved.status}.`);
      setWithdrawClaimId(actualId); // This will now auto-fill correctly!
      
    } catch (err: any) {
      setError(err.message || 'Failed to submit claim. Check active duplicate restrictions (FR-06).');
    }
  };

  const handleWithdraw = async (e: React.FormEvent) => {
    e.preventDefault();
    setMessage(null);
    setError(null);
    if (!withdrawClaimId) return;
    try {
      await withdrawStudentClaimApi(Number(withdrawClaimId), studentId);
      setMessage(`Success! Claim #${withdrawClaimId} was withdrawn (FR-18).`);
      setWithdrawClaimId('');
    } catch (err: any) {
      setError(err.message || 'Failed to withdraw. It may already be approved or does not exist.');
    }
  };

  return (
    <div className="mx-auto max-w-4xl p-6 space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-xl font-bold text-slate-900">Student Claimant Portal (Module 3)</h2>
        <p className="text-sm text-slate-600 mt-1">
          Submit claims linked to a lost report and an independent found report (FR-06).
        </p>

        {message && (
          <div className="mt-4 rounded bg-emerald-50 p-3 text-sm text-emerald-800 border border-emerald-200">
            {message}
          </div>
        )}
        {error && (
          <div className="mt-4 rounded bg-red-50 p-3 text-sm text-red-800 border border-red-200">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmitClaim} className="mt-6 space-y-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <div>
              <label className="block text-xs font-semibold text-slate-700">Student User ID *</label>
              <input type="number" value={studentId} onChange={(e) => setStudentId(Number(e.target.value))} className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700">Lost Report ID *</label>
              <input type="number" value={lostReportId} onChange={(e) => setLostReportId(Number(e.target.value))} className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700">Found Report ID *</label>
              <input type="number" value={foundReportId} onChange={(e) => setFoundReportId(Number(e.target.value))} className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm" />
            </div>
          </div>
          <button type="submit" className="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700">
            Submit Ownership Claim
          </button>
        </form>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <h3 className="text-lg font-bold text-slate-900">Withdraw Active Claim (FR-18)</h3>
        <p className="text-sm text-slate-600 mt-1">
          Students can withdraw claims before they are approved by staff.
        </p>
        <form onSubmit={handleWithdraw} className="mt-4 flex items-end gap-4">
          <div>
            <label className="block text-xs font-semibold text-slate-700">Claim ID to Withdraw</label>
            <input type="number" value={withdrawClaimId} onChange={(e) => setWithdrawClaimId(Number(e.target.value))} className="mt-1 w-48 rounded border border-slate-300 px-3 py-2 text-sm" required />
          </div>
          <button type="submit" className="rounded bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-700">
            Withdraw Claim
          </button>
        </form>
      </div>
    </div>
  );
};
