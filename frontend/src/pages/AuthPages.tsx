import { useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { ApiRequestError } from '../api/client'
import { useAuth } from '../context/AuthContext'
import { ErrorMessage } from '../components/Feedback'

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to="/dashboard" replace />

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(''); setBusy(true)
    try { await login(email, password); navigate('/dashboard', { replace: true }) }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Unable to sign in. Try again.') }
    finally { setBusy(false) }
  }

  return <AuthFrame title="Welcome back" intro="Sign in to keep your campus items moving home.">
    <form className="form-stack" onSubmit={submit}>
      {error && <ErrorMessage>{error}</ErrorMessage>}
      <label>Email address<input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} /></label>
      <label>Password<input type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} /></label>
      <button className="button button-primary button-wide" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
      <p className="form-footnote">New to FindBack? <Link to="/register">Create an account</Link></p>
    </form>
  </AuthFrame>
}

export function RegisterPage() {
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  if (user) return <Navigate to="/dashboard" replace />

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(''); setBusy(true)
    try { await register({ name, email, password }); navigate('/login', { replace: true, state: { registered: true } }) }
    catch (cause) {
      const status = cause instanceof ApiRequestError ? `${cause.message} ` : ''
      setError(status || (cause instanceof Error ? cause.message : 'Unable to create the account.'))
    } finally { setBusy(false) }
  }

  return <AuthFrame title="Create your account" intro="A campus account is all you need to get started.">
    <form className="form-stack" onSubmit={submit}>
      {error && <ErrorMessage>{error}</ErrorMessage>}
      <label>Name<input autoComplete="name" maxLength={100} required value={name} onChange={(e) => setName(e.target.value)} /></label>
      <label>Email address<input type="email" autoComplete="email" maxLength={150} required value={email} onChange={(e) => setEmail(e.target.value)} /></label>
      <label>Password<input type="password" autoComplete="new-password" minLength={8} maxLength={100} required value={password} onChange={(e) => setPassword(e.target.value)} /><span className="field-help">Use at least 8 characters.</span></label>
      <button className="button button-primary button-wide" disabled={busy}>{busy ? 'Creating account…' : 'Create account'}</button>
      <p className="form-footnote">Already registered? <Link to="/login">Sign in</Link></p>
    </form>
  </AuthFrame>
}

function AuthFrame({ title, intro, children }: { title: string; intro: string; children: ReactNode }) {
  return <main className="auth-page">
    <section className="auth-aside"><div className="brand brand-light"><span className="brand-mark">F</span><span>FindBack</span></div>
      <div><p className="eyebrow">FOUND ON CAMPUS</p><h1>Good things<br />find their way back.</h1><p className="aside-copy">A simple place to report lost belongings and help campus finds reach their owners.</p></div>
      <span className="aside-note">Built for the campus community</span>
    </section>
    <section className="auth-panel"><div className="auth-form-wrap"><h2>{title}</h2><p className="muted">{intro}</p>{children}</div></section>
  </main>
}
