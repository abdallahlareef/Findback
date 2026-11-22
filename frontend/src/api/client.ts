import type {
  ApiErrorBody,
  CandidateMatch,
  CurrentUser,
  RegisterRequest,
  ReportPayload,
  ReportResponse,
  SafeReportSummary,
  SearchFilters,
} from '../types/api'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

export class ApiRequestError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  const method = (init.method ?? 'GET').toUpperCase()
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  headers.set('Accept', 'application/json')

  if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
    const csrfResponse = await fetch(`${API_BASE}/api/auth/csrf`, {
      headers: { Accept: 'application/json' },
      credentials: 'include',
    })
    if (!csrfResponse.ok) {
      throw new ApiRequestError(csrfResponse.status, 'Could not establish a secure session. Reload and try again.')
    }
    const csrf = (await csrfResponse.json()) as { headerName: string; token: string }
    headers.set(csrf.headerName, csrf.token)
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    method,
    headers,
    credentials: 'include',
  })

  if (!response.ok) {
    let message = `Request failed (${response.status})`
    try {
      const body = (await response.json()) as ApiErrorBody
      if (body.message) message = body.message
    } catch {
      // Some security responses have no JSON body.
    }
    throw new ApiRequestError(response.status, message)
  }

  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export const api = {
  register: (data: RegisterRequest) =>
    request<CurrentUser>('/api/auth/register', { method: 'POST', body: JSON.stringify(data) }),
  login: (email: string, password: string) =>
    request<CurrentUser>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),
  logout: () => request<void>('/api/auth/logout', { method: 'POST' }),
  getCurrentUser: () => request<CurrentUser>('/api/auth/me'),
  createReport: (report: ReportPayload) =>
    request<ReportResponse>('/api/reports', { method: 'POST', body: JSON.stringify(report) }),
  getMyReports: () => request<ReportResponse[]>('/api/reports/mine'),
  searchReports: (filters: SearchFilters) => {
    const params = new URLSearchParams()
    for (const [key, value] of Object.entries(filters)) {
      if (value) params.set(key, value)
    }
    const query = params.toString()
    return request<SafeReportSummary[]>(`/api/reports/search${query ? `?${query}` : ''}`)
  },
  getCandidates: (lostReportId: number) =>
    request<CandidateMatch[]>(`/api/reports/${lostReportId}/candidates`),
}
