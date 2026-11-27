import { request } from '../../api/client'

/** Use the shared API base, session cookies, CSRF handling and error messages. */
export function api<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  return request<T>(path, { method, body: body === undefined ? undefined : JSON.stringify(body) })
}
export type Evidence = { serialSuffix: string | null; privateMark: string | null; accessoryDescription: string | null }
export type Version = { id: number; version: number; provenance: string; createdAt: string; evidence: Evidence; reason: string | null }
export type Pair = { status: string; submissionsUsed: number; pendingRequestId: string | null; versions: Version[] }
export type Summary = { claimantVersionId: number; finderVersionId: number | null; strength: string; serialOutcome: string; markOutcome: string; accessoryOutcome: string; distinctMatches: number; criticalConflict: boolean; reason: string | null }
export type StaffCase = { claimId: number; claimant: Pair; finder: Version[]; summary: Summary; history: Summary[] }
export type Report = { id: number; type: 'LOST' | 'FOUND'; category: string; campusZone: string }
export type Claim = { claimId: number; lostReportId: number; foundReportId: number; status: string }
