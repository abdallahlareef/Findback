export async function api<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  if (method !== 'GET') {
    const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' })
    if (!response.ok) throw new Error('Could not establish a secure session. Reload and try again.')
    const csrf: { headerName: string; token: string } = await response.json()
    headers[csrf.headerName] = csrf.token
    headers['Content-Type'] = 'application/json'
  }
  const response = await fetch(path, { method, headers, credentials: 'same-origin', body: body === undefined ? undefined : JSON.stringify(body) })
  const text = await response.text()
  if (!response.ok) {
    let message = response.status === 403 ? 'You do not have permission for this action.' : response.status === 401 ? 'Please sign in.' : `Request failed (${response.status}).`
    try { const error = JSON.parse(text); message = error.message || error.detail || message } catch { /* Do not display HTML error responses. */ }
    throw new Error(message)
  }
  return (text ? JSON.parse(text) : undefined) as T
}
export type Account = { id: number; name: string; email: string; role: 'USER' | 'ADMIN' }
export type Evidence = { serialSuffix: string | null; privateMark: string | null; accessoryDescription: string | null }
export type Version = { id: number; version: number; provenance: string; createdAt: string; evidence: Evidence; reason: string | null }
export type Pair = { status: string; submissionsUsed: number; pendingRequestId: string | null; versions: Version[] }
export type Summary = { claimantVersionId: number; finderVersionId: number | null; strength: string; serialOutcome: string; markOutcome: string; accessoryOutcome: string; distinctMatches: number; criticalConflict: boolean; reason: string | null }
export type StaffCase = { claimId: number; claimant: Pair; finder: Version[]; summary: Summary; history: Summary[] }
export type Report = { id: number; type: 'LOST' | 'FOUND'; category: string; campusZone: string }
export type Claim = { claimId: number; lostReportId: number; foundReportId: number; status: string }
