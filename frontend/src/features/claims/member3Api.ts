import { request } from '../../api/client'

// All calls go through the shared client: it sends the session cookie and CSRF token, and the
// backend takes the user's identity from the session. Never send user IDs from the browser.

export type ClaimStatus =
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'ON_HOLD'
  | 'APPROVED'
  | 'REJECTED'
  | 'WITHDRAWN'
  | 'COMPLETED'

export type RiskTier = 'LOW' | 'MEDIUM' | 'HIGH'
export type CustodyStatus = 'REPORTED' | 'IN_CUSTODY' | 'RESERVED' | 'RETURNED'
export type DoveStrength = 'WEAK' | 'MODERATE' | 'STRONG' | 'REVIEW_REQUIRED'
export type DecisionAction = 'APPROVE' | 'HOLD' | 'REJECT' | 'RECONSIDER'

/** What a claimant may see about their own claim. */
export interface ClaimantClaim {
  claimId: number
  lostReportId: number
  foundReportId: number
  status: ClaimStatus
  createdAt: string
  updatedAt: string
  withdrawable: boolean
}

/** One row of the staff review queue (GET /api/v1/claims/staff/cases). */
export interface StaffClaimCaseView {
  claimId: number
  claimantId: number
  claimantName: string
  lostReportId: number
  foundReportId: number
  itemTitle: string
  category: string
  campusZone: string
  status: ClaimStatus
  riskTier: RiskTier
  escalationReason: string | null
  internalReviewFlag: boolean
  flagReason: string | null
  custodyStatus: CustodyStatus
  storageReference: string | null
  disputeBlocked: boolean
  reservedByClaimId: number | null
  doveSummary: {
    strengthLabel: DoveStrength
    matchCount: number
    unknownCount: number
    contradictionCount: number
    hasCriticalConflict: boolean
    submissionsUsed: number
    pendingSupplement: boolean
  }
  competingActiveClaimsCount: number
  createdAt: string
  updatedAt: string
}

const json = (body: unknown) => JSON.stringify(body)

export const claimsApi = {
  myClaims: () => request<ClaimantClaim[]>('/api/v1/claims/mine'),

  submitClaim: (lostReportId: number, foundReportId: number) =>
    request<ClaimantClaim>('/api/v1/claims', { method: 'POST', body: json({ lostReportId, foundReportId }) }),

  withdrawClaim: (claimId: number) =>
    request<ClaimantClaim>(`/api/v1/claims/${claimId}`, { method: 'DELETE' }),

  reviewQueue: () => request<StaffClaimCaseView[]>('/api/v1/claims/staff/cases'),

  recordCustodyIntake: (foundReportId: number, storageReference: string, conditionNotes: string) =>
    request<unknown>(`/api/v1/claims/staff/custody/${foundReportId}/intake`, {
      method: 'POST',
      body: json({ storageReference, conditionNotes }),
    }),

  resolveDispute: (foundReportId: number, resolution: string) =>
    request<unknown>(`/api/v1/claims/staff/custody/${foundReportId}/dispute/resolve`, {
      method: 'POST',
      body: json({ resolution }),
    }),

  submitDecision: (claimId: number, action: DecisionAction, reason: string, physicalCheckNotes: string) =>
    request<unknown>(`/api/v1/claims/staff/claims/${claimId}/decision`, {
      method: 'POST',
      body: json({ action, reason, physicalCheckNotes }),
    }),

  escalateRisk: (claimId: number, riskTier: RiskTier, reason: string) =>
    request<unknown>(`/api/v1/claims/staff/claims/${claimId}/risk`, {
      method: 'PATCH',
      body: json({ riskTier, reason }),
    }),
}

export function errorMessage(cause: unknown, fallback: string): string {
  return cause instanceof Error && cause.message ? cause.message : fallback
}
