export type ReportType = 'LOST' | 'FOUND'
export type ReportStatus = 'ACTIVE' | 'MATCHED' | 'CLOSED'

export interface CurrentUser {
  id: number
  name: string
  email: string
  role: string
}

export interface RegisterRequest {
  name: string
  email: string
  password: string
}

export interface ReportPayload {
  type: ReportType
  category: string
  brand?: string
  model?: string
  colour: string
  campusZone: string
  eventDate: string
  timeFrom?: string
  timeTo?: string
  safeDescription?: string
}

export interface ReportResponse extends ReportPayload {
  id: number
  authorId?: number
  status: ReportStatus
  createdAt: string
  updatedAt: string
}

export interface SafeReportSummary {
  reportId: number
  type: ReportType
  category: string
  brand?: string | null
  model?: string | null
  colour: string
  campusZone: string
  eventDate: string
  timeFrom?: string | null
  timeTo?: string | null
  safeDescription?: string | null
  status: ReportStatus
  createdAt: string
}

export interface CandidateMatch {
  reportId: number
  category: string
  brand?: string | null
  model?: string | null
  colour: string
  campusZone: string
  score: number
}

export interface SearchFilters {
  type?: ReportType | ''
  category?: string
  campusZone?: string
  eventDate?: string
  status?: ReportStatus | ''
}

export interface ApiErrorBody {
  status?: number
  message?: string
}
