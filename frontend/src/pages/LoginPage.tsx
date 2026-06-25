import { useState, type FormEvent } from 'react'
import { useAuth } from '@/contexts/AuthContext'
import { Loader2 } from 'lucide-react'

export default function LoginPage() {
  const { login } = useAuth()
  const [email, setEmail]       = useState('')
  const [password, setPassword] = useState('')
  const [error, setError]       = useState<string | null>(null)
  const [loading, setLoading]   = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await login(email, password)
    } catch (err: unknown) {
      const e = err as { response?: { status: number; data?: { message?: string } } }
      const status = e.response?.status
      const msg    = e.response?.data?.message
      if (msg === 'Account not yet activated. Check your email for an activation link.') {
        setError('Your account is pending activation. Check your email for an activation link.')
      } else if (msg) {
        setError(msg)
      } else if (status === 401 || status === 403) {
        setError('Invalid email or password.')
      } else {
        setError('Service temporarily unavailable. Please try again.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-bone px-4">
      <div className="w-full max-w-sm">

        {/* Brand */}
        <div className="mb-8 text-center">
          <div className="mb-4 flex items-center justify-center gap-4">
            <img src="/utm-logo.png" alt="Universiti Teknologi Malaysia" className="h-14 w-auto object-contain" />
            <span className="h-12 w-px bg-line" aria-hidden="true" />
            <img src="/ascend-2030.png" alt="ASCEND 2030" className="h-14 w-auto object-contain" />
          </div>
          <h1 className="font-display text-2xl font-semibold text-text">AlumIndex</h1>
          <p className="mt-1 text-sm text-muted">Alumni intelligence platform</p>
        </div>

        {/* Card */}
        <div className="rounded-xl border border-line bg-surface p-8 shadow-sm">
          <h2 className="text-lg font-semibold text-text mb-5">Sign in to your account</h2>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-text mb-1.5">Email</label>
              <input
                type="email"
                required
                autoFocus
                value={email}
                onChange={e => setEmail(e.target.value)}
                className="w-full rounded-md border border-line bg-bone px-3 py-2.5 text-sm text-text placeholder:text-muted focus:outline-none focus:ring-2 focus:ring-sapphire focus:border-transparent transition-shadow"
                placeholder="you@university.edu"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-text mb-1.5">Password</label>
              <input
                type="password"
                required
                value={password}
                onChange={e => setPassword(e.target.value)}
                className="w-full rounded-md border border-line bg-bone px-3 py-2.5 text-sm text-text focus:outline-none focus:ring-2 focus:ring-sapphire focus:border-transparent transition-shadow"
              />
            </div>

            {error && (
              <div className="rounded-md border border-danger/20 bg-danger/5 px-3 py-2.5">
                <p className="text-sm text-danger">{error}</p>
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full flex items-center justify-center gap-2 rounded-md bg-sapphire px-4 py-2.5 text-sm font-medium text-white hover:bg-sapphire-dark transition-colors disabled:opacity-60"
            >
              {loading ? <><Loader2 className="h-4 w-4 animate-spin" /> Signing in…</> : 'Sign in'}
            </button>
          </form>
        </div>

        <p className="mt-5 text-center text-xs text-muted">
          New university?{' '}
          <a href="mailto:admin@alumindex.app" className="text-sapphire hover:underline">
            Contact the operator
          </a>
        </p>
      </div>
    </div>
  )
}
