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
export type DecisionAction = 'APPROVE' | 'HOLD' | 'REJECT' | 'RECONSIDER' | 'DECLINE_RECONSIDERATION'
export type ReconsiderationStatus = 'NONE' | 'PENDING' | 'ACCEPTED' | 'DECLINED'

/** Server-side length limits, mirrored so forms can stop typing at the limit. */
export const NOTE_MAX_LENGTH = 255
export const RISK_REASON_MAX_LENGTH = 200
export const RECONSIDERATION_MAX_LENGTH = 1000

/** What a claimant may see about their own claim. */
export interface ClaimantClaim {
  claimId: number
  lostReportId: number
  foundReportId: number
  status: ClaimStatus
  createdAt: string
  updatedAt: string
  withdrawable: boolean
  reconsiderationStatus: ReconsiderationStatus
  canRequestReconsideration: boolean
}

/** Staff-only case timeline (GET /api/v1/claims/staff/claims/{id}/history). */
export interface CaseHistory {
  claimId: number
  claimantId: number
  claimantName: string
  lostReportId: number
  foundReportId: number
  itemTitle: string
  status: ClaimStatus
  riskTier: RiskTier
  events: {
    at: string
    source: 'CLAIM' | 'DECISION' | 'CUSTODY'
    type: string
    actorName: string
    summary: string
    details: string | null
  }[]
}

export interface CaseSummary {
  claimId: number
  claimantName: string
  foundReportId: number
  itemTitle: string
  status: ClaimStatus
  riskTier: RiskTier
  createdAt: string
  updatedAt: string
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
  /** Who put the approved claim on hold, when and why; that person cannot resolve the dispute. */
  disputeRaisedByName: string | null
  disputeReason: string | null
  disputeRaisedAt: string | null
  canResolveDispute: boolean
  doveSummary: {
    strengthLabel: DoveStrength
    matchCount: number
    unknownCount: number
    contradictionCount: number
    hasCriticalConflict: boolean
    submissionsUsed: number
    pendingSupplement: boolean
    identifierMatched: boolean
  }
  competingActiveClaimsCount: number
  reconsiderationPending: boolean
  reconsiderationReason: string | null
  reconsiderationRequestedAt: string | null
  /** HIGH risk two-person rule: set after the first of two approvals. */
  firstApproverId: number | null
  firstApproverName: string | null
  firstApprovedAt: string | null
  /** Server-computed reasons the current reviewer cannot approve yet (empty = approvable). */
  approvalBlockers: string[]
  createdAt: string
  updatedAt: string
}

/** One found item on the staff custody desk (GET /api/v1/claims/staff/custody). */
export interface CustodyDeskItem {
  foundReportId: number
  category: string
  colour: string
  campusZone: string
  eventDate: string
  reportStatus: 'ACTIVE' | 'MATCHED' | 'CLOSED'
  riskLevel: RiskTier | null
  finderName: string
  custodyStatus: CustodyStatus
  storageReference: string | null
  receivedByName: string | null
  custodyUpdatedAt: string | null
  disputeBlocked: boolean
  disputeRaisedByName: string | null
  disputeReason: string | null
  reservedByClaimId: number | null
  activeClaimsCount: number
  totalClaimsCount: number
}

export interface ItemCustodyHistory {
  foundReportId: number
  category: string
  campusZone: string
  finderName: string
  custodyStatus: CustodyStatus
  storageReference: string | null
  disputeBlocked: boolean
  reservedByClaimId: number | null
  events: CaseHistory['events']
  claims: { claimId: number; claimantName: string; status: ClaimStatus; riskTier: RiskTier; createdAt: string }[]
}

export interface DecisionResult {
  status: ClaimStatus
  firstApproverId: number | null
}

/** FR-10: distinct matching private characteristics needed per tier (mirrors the server). */
export function requiredMatches(tier: RiskTier): number {
  return tier === 'LOW' ? 1 : 2
}

const json = (body: unknown) => JSON.stringify(body)

export const claimsApi = {
  myClaims: () => request<ClaimantClaim[]>('/api/v1/claims/mine'),

  submitClaim: (lostReportId: number, foundReportId: number) =>
    request<ClaimantClaim>('/api/v1/claims', { method: 'POST', body: json({ lostReportId, foundReportId }) }),

  withdrawClaim: (claimId: number) =>
    request<ClaimantClaim>(`/api/v1/claims/${claimId}`, { method: 'DELETE' }),

  requestReconsideration: (claimId: number, reason: string) =>
    request<ClaimantClaim>(`/api/v1/claims/${claimId}/reconsideration`, { method: 'POST', body: json({ reason }) }),

  reviewQueue: () => request<StaffClaimCaseView[]>('/api/v1/claims/staff/cases'),

  historyCases: () => request<CaseSummary[]>('/api/v1/claims/staff/history'),

  caseHistory: (claimId: number) => request<CaseHistory>(`/api/v1/claims/staff/claims/${claimId}/history`),

  custodyDesk: () => request<CustodyDeskItem[]>('/api/v1/claims/staff/custody'),

  itemCustodyHistory: (foundReportId: number) =>
    request<ItemCustodyHistory>(`/api/v1/claims/staff/custody/${foundReportId}/history`),

  recordCustodyIntake: (foundReportId: number, storageReference: string, conditionNotes: string) =>
    request<unknown>(`/api/v1/claims/staff/custody/${foundReportId}/intake`, {
      method: 'POST',
      body: json({ storageReference, conditionNotes }),
    }),

  completeHandover: (claimId: number, handoverCode: string, identityCheckNotes: string) =>
    request<unknown>(`/api/v1/claims/staff/claims/${claimId}/handover`, {
      method: 'POST',
      body: json({ handoverCode, identityCheckNotes }),
    }),

  resolveDispute: (foundReportId: number, resolution: string) =>
    request<unknown>(`/api/v1/claims/staff/custody/${foundReportId}/dispute/resolve`, {
      method: 'POST',
      body: json({ resolution }),
    }),

  submitDecision: (claimId: number, action: DecisionAction, reason: string, physicalCheckNotes: string) =>
    request<DecisionResult>(`/api/v1/claims/staff/claims/${claimId}/decision`, {
      method: 'POST',
      body: json({ action, reason, physicalCheckNotes }),
    }),

  escalateRisk: (claimId: number, riskTier: RiskTier, reason: string) =>
    request<DecisionResult>(`/api/v1/claims/staff/claims/${claimId}/risk`, {
      method: 'PATCH',
      body: json({ riskTier, reason }),
    }),
}

export function errorMessage(cause: unknown, fallback: string): string {
  return cause instanceof Error && cause.message ? cause.message : fallback
}
