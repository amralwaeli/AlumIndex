import { useState, useEffect, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import api from '@/lib/api'
import { CheckCircle, Loader2 } from 'lucide-react'

interface TokenInfo {
  email: string
  organization: string
}

function Brand() {
  return (
    <div className="mb-8 text-center">
      <div className="mb-4 flex items-center justify-center gap-4">
        <img src="/utm-logo.png" alt="Universiti Teknologi Malaysia" className="h-14 w-auto object-contain" />
        <span className="h-12 w-px bg-line" aria-hidden="true" />
        <img src="/ascend-2030.png" alt="ASCEND 2030" className="h-14 w-auto object-contain" />
      </div>
      <h1 className="font-display text-2xl font-semibold text-text">AlumIndex</h1>
      <p className="mt-1 text-sm text-muted">Alumni intelligence platform</p>
    </div>
  )
}

export default function RegisterPage() {
  const { token } = useParams<{ token: string }>()
  const [tokenInfo, setTokenInfo] = useState<TokenInfo | null>(null)
  const [tokenError, setTokenError] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [jobTitle, setJobTitle] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    api
      .get<TokenInfo>(`/api/register/${token}`)
      .then((res) => { setTokenInfo(res.data); setLoading(false) })
      .catch((err) => {
        const status = err.response?.status
        setTokenError(
          status === 410
            ? 'This invitation link has expired or already been used.'
            : 'Invalid invitation link.',
        )
        setLoading(false)
      })
  }, [token])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await api.post(`/api/register/${token}`, { name, jobTitle })
      setSubmitted(true)
    } catch {
      setError('Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-bone">
        <Loader2 className="h-5 w-5 animate-spin text-muted" />
      </div>
    )
  }

  if (tokenError) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-bone px-4">
        <div className="text-center max-w-sm">
          <Brand />
          <div className="rounded-xl border border-line bg-surface p-8 shadow-sm">
            <div className="flex h-10 w-10 items-center justify-center rounded-full bg-danger/10 mx-auto mb-3">
              <span className="text-danger text-lg font-semibold">!</span>
            </div>
            <h2 className="text-base font-semibold text-text mb-1">Link invalid</h2>
            <p className="text-sm text-muted">{tokenError}</p>
          </div>
        </div>
      </div>
    )
  }

  if (submitted) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-bone px-4">
        <div className="text-center max-w-sm">
          <Brand />
          <div className="rounded-xl border border-line bg-surface p-8 shadow-sm">
            <div className="flex h-10 w-10 items-center justify-center rounded-full bg-emerald/10 mx-auto mb-3">
              <CheckCircle className="h-5 w-5 text-emerald" />
            </div>
            <h2 className="text-base font-semibold text-text mb-1">Request submitted</h2>
            <p className="text-sm text-muted">
              We'll review your request and email you when approved.
            </p>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-bone px-4">
      <div className="w-full max-w-sm">
        <Brand />

        <div className="bg-surface rounded-xl border border-line p-8 shadow-sm">
          <h2 className="text-lg font-semibold text-text mb-1">Create your account</h2>
          <p className="text-sm text-muted mb-6">
            Invited for <strong className="text-text">{tokenInfo?.organization}</strong>
          </p>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-text mb-1.5">Email</label>
              <input
                type="email"
                disabled
                value={tokenInfo?.email ?? ''}
                className="w-full rounded-md border border-line bg-bone px-3 py-2.5 text-sm text-muted cursor-not-allowed"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text mb-1.5">Full name</label>
              <input
                type="text"
                required
                autoFocus
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="w-full rounded-md border border-line bg-bone px-3 py-2.5 text-sm text-text placeholder:text-muted focus:outline-none focus:ring-2 focus:ring-sapphire focus:border-transparent transition-shadow"
                placeholder="Your full name"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text mb-1.5">Job title</label>
              <input
                type="text"
                required
                value={jobTitle}
                onChange={(e) => setJobTitle(e.target.value)}
                className="w-full rounded-md border border-line bg-bone px-3 py-2.5 text-sm text-text placeholder:text-muted focus:outline-none focus:ring-2 focus:ring-sapphire focus:border-transparent transition-shadow"
                placeholder="e.g. Alumni Relations Manager"
              />
            </div>

            {error && (
              <div className="rounded-md border border-danger/20 bg-danger/5 px-3 py-2.5">
                <p className="text-sm text-danger">{error}</p>
              </div>
            )}

            <button
              type="submit"
              disabled={submitting}
              className="w-full flex items-center justify-center gap-2 rounded-md bg-sapphire px-4 py-2.5 text-sm font-medium text-white hover:bg-sapphire-dark transition-colors disabled:opacity-60"
            >
              {submitting ? <><Loader2 className="h-4 w-4 animate-spin" /> Submitting…</> : 'Submit request'}
            </button>
          </form>
        </div>

        <p className="mt-5 text-center text-xs text-muted">
          Already have an account?{' '}
          <a href="/login" className="text-sapphire hover:underline">Sign in</a>
        </p>
      </div>
    </div>
  )
}
