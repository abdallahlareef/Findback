export type ClaimStatus =
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'ON_HOLD'
  | 'APPROVED'
  | 'REJECTED'
  | 'WITHDRAWN'
  | 'COMPLETED';

export type RiskTier = 'LOW' | 'MEDIUM' | 'HIGH';
export type CustodyStatus = 'REPORTED' | 'IN_CUSTODY' | 'RESERVED' | 'RETURNED';
export type DoveStrength = 'WEAK' | 'MODERATE' | 'STRONG' | 'REVIEW_REQUIRED';

export interface StaffClaimCaseView {
  claimId: number;
  claimantId: number;
  claimantName: string;
  lostReportId: number;
  foundReportId: number;
  itemTitle: string;
  category: string;
  campusZone: string;
  status: ClaimStatus;
  riskTier: RiskTier;
  escalationReason?: string;
  custodyStatus: CustodyStatus;
  storageReference?: string;
  disputeBlocked: boolean;
  doveSummary: {
    strengthLabel: DoveStrength;
    matchCount: number;
    unknownCount: number;
    contradictionCount: number;
    hasCriticalConflict: boolean;
    submissionsUsed: number;
  };
  competingActiveClaimsCount: number;
  createdAt: string;
}

export async function createClaimApi(claimantId: number, lostReportId: number, foundReportId: number) {
  const res = await fetch('/api/v1/claims', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': String(claimantId),
    },
    body: JSON.stringify({ lostReportId, foundReportId }),
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

export async function recordCustodyIntakeApi(
  staffId: number,
  foundReportId: number,
  storageReference: string,
  conditionNotes: string
) {
  const res = await fetch(`/api/v1/staff/custody/${foundReportId}/intake`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': String(staffId),
    },
    body: JSON.stringify({ storageReference, conditionNotes }),
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

export async function submitStaffDecisionApi(
  staffId: number,
  claimId: number,
  action: 'APPROVE' | 'HOLD' | 'REJECT' | 'RECONSIDER',
  reason: string,
  physicalCheckNotes: string
) {
  const res = await fetch(`/api/v1/staff/claims/${claimId}/decision`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': String(staffId),
    },
    body: JSON.stringify({ action, reason, physicalCheckNotes }),
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

export async function submitStudentClaimApi(claimantId: number, lostReportId: number, foundReportId: number) {
  const res = await fetch('/api/v1/claims', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': String(claimantId),
    },
    body: JSON.stringify({ lostReportId, foundReportId }),
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

export async function withdrawStudentClaimApi(claimId: number, claimantId: number) {
  const res = await fetch(`/api/v1/claims/${claimId}`, {
    method: 'DELETE',
    headers: {
      'X-User-Id': String(claimantId),
    }
  });
  if (!res.ok) throw new Error(await res.text());
  return res.text();
}
