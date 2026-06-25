import { useState, useEffect } from 'react'
import { useParams } from 'react-router-dom'
import api from '@/lib/api'
import { Check, X, Loader2, BarChart3 } from 'lucide-react'

function Hint({ ok, text }: { ok: boolean; text: string }) {
  return (
    <span className={`flex items-center gap-1.5 text-xs ${ok ? 'text-emerald' : 'text-muted'}`}>
      {ok ? <Check className="h-3 w-3" /> : <X className="h-3 w-3" />}
      {text}
    </span>
  )
}

export default function ActivatePage() {
  const { token } = useParams<{ token: string }>()

  const [info, setInfo] = useState<{ email: string; institutionName: string } | null>(null)
  const [pageStatus, setPageStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [errorText, setErrorText] = useState('')

  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState('')

  const hasMinLen = password.length >= 8
  const hasUppercase = /[A-Z]/.test(password)
  const hasNumber = /\d/.test(password)
  const pwStrong = hasMinLen && hasUppercase && hasNumber
  const pwMatches = password.length > 0 && password === confirm
  const canSubmit = pwStrong && pwMatches && !submitting

  useEffect(() => {
    if (!token) { setErrorText('Missing activation token.'); setPageStatus('error'); return }
    api.get(`/api/auth/activate/${token}`)
      .then(res => { setInfo(res.data); setPageStatus('ready') })
      .catch(err => {
        const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
        setErrorText(
          msg === 'already_used'
            ? 'This activation link has already been used. Contact your administrator for a new invite.'
            : msg === 'token_expired'
            ? 'This activation link has expired (24-hour limit). Contact your administrator for a new invite.'
            : 'Invalid or unrecognised activation link.'
        )
        setPageStatus('error')
      })
  }, [token])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!canSubmit) return
    setSubmitting(true)
    setSubmitError('')
    try {
      const res = await api.post<{ token: string; role: string }>(`/api/auth/activate/${token}`, { password })
      localStorage.setItem('alumindex_token', res.data.token)
      // Full reload so AuthProvider re-initialises from localStorage
      window.location.href = res.data.role === 'superadmin' ? '/operator/overview' : '/university/dashboard'
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      setSubmitError(
        msg === 'already_used' ? 'This link has already been used.'
        : msg === 'token_expired' ? 'This link has expired.'
        : 'Failed to activate account. Please try again.'
      )
      setSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen bg-bone flex items-center justify-center p-4">
      <div className="w-full max-w-sm">
        {/* Brand */}
        <div className="mb-8 text-center">
          <div className="inline-flex items-center justify-center h-12 w-12 rounded-xl bg-sapphire mb-4">
            <BarChart3 className="h-6 w-6 text-white" />
          </div>
          <h1 className="font-display text-2xl font-semibold text-text">AlumIndex</h1>
          <p className="mt-1 text-sm text-muted">Alumni intelligence platform</p>
        </div>

        {pageStatus === 'loading' && (
          <div className="flex justify-center py-12">
            <Loader2 className="h-6 w-6 animate-spin text-muted" />
          </div>
        )}

        {pageStatus === 'error' && (
          <div className="rounded-xl border border-line bg-surface p-6 text-center space-y-4">
            <X className="mx-auto h-8 w-8 text-danger" />
            <p className="text-sm text-text font-medium">Activation link invalid</p>
            <p className="text-sm text-muted">{errorText}</p>
          </div>
        )}

        {pageStatus === 'ready' && info && (
          <div className="rounded-xl border border-line bg-surface p-6 shadow-sm space-y-5">
            <div>
              <h2 className="text-lg font-semibold text-text">Set your password</h2>
              <p className="mt-1 text-sm text-muted">
                You've been invited to <span className="font-medium text-text">{info.institutionName}</span> as{' '}
                <span className="font-medium text-text">{info.email}</span>.
              </p>
            </div>

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-text mb-1.5">Password</label>
                <input
                  type="password"
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  className="w-full rounded-md border border-line bg-bone px-3 py-2 text-sm text-text focus:outline-none focus:ring-2 focus:ring-sapphire"
                  placeholder="Choose a strong password"
                  required
                  autoFocus
                />
                {password.length > 0 && (
                  <div className="mt-2 space-y-1">
                    <Hint ok={hasMinLen} text="At least 8 characters" />
                    <Hint ok={hasUppercase} text="At least 1 uppercase letter" />
                    <Hint ok={hasNumber} text="At least 1 number" />
                  </div>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-text mb-1.5">Confirm password</label>
                <input
                  type="password"
                  value={confirm}
                  onChange={e => setConfirm(e.target.value)}
                  className="w-full rounded-md border border-line bg-bone px-3 py-2 text-sm text-text focus:outline-none focus:ring-2 focus:ring-sapphire"
                  placeholder="Re-enter your password"
                  required
                />
                {confirm.length > 0 && !pwMatches && (
                  <p className="mt-1 text-xs text-danger">Passwords do not match.</p>
                )}
              </div>

              {submitError && (
                <div className="rounded-md border border-danger/20 bg-danger/5 px-3 py-2.5">
                  <p className="text-sm text-danger">{submitError}</p>
                </div>
              )}

              <button
                type="submit"
                disabled={!canSubmit}
                className="w-full flex items-center justify-center gap-2 rounded-md bg-sapphire px-4 py-2.5 text-sm font-medium text-white hover:bg-sapphire-dark disabled:opacity-50 transition-colors"
              >
                {submitting ? <><Loader2 className="h-4 w-4 animate-spin" /> Activating…</> : 'Activate account'}
              </button>
            </form>
          </div>
        )}
      </div>
    </div>
  )
}
