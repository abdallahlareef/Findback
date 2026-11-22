import type { ReactNode } from 'react'

export function ErrorMessage({ children }: { children: string }) {
  return <div className="alert alert-error" role="alert">{children}</div>
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return <p className="loading" role="status">{label}</p>
}

export function EmptyState({ children }: { children: ReactNode }) {
  return <div className="empty-state">{children}</div>
}
