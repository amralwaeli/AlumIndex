import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import api from '@/lib/api'
import { Building2, Users, Upload, Clock, Plus, AlertCircle, RefreshCw } from 'lucide-react'

interface OverviewData {
  totalUniversities: number
  totalAlumni: number
  importsThisMonth: number
  pendingRequests: number
  tenantsExpiringSoon: number
  recentActivity: ActivityItem[]
  recentBatches: BatchSummary[]
  failedRowsThisMonth: number
}

interface ActivityItem {
  id: string
  actionType: string
  actionDetails: string | null
  actionTime: string
  tenantName: string | null
  userEmail: string | null
}

interface BatchSummary {
  id: string
  tenantName: string
  filename: string
  status: string
  recordCount: number
  processedCount: number
  failedCount: number
  errorSummary: string | null
  uploadedAt: string
}

function KpiCard({ label, value, icon: Icon, accent, iconBg }: {
  label: string
  value: number | string
  icon: React.ElementType
  accent?: string
  iconBg?: string
}) {
  return (
    <div className="rounded-xl border border-ink-line bg-ink-panel p-5">
      <div className="flex items-start justify-between mb-4">
        <span className="font-mono text-xs text-ink-muted uppercase tracking-widest leading-tight">{label}</span>
        <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${iconBg ?? 'bg-sapphire/10'}`}>
          <Icon className={`h-4 w-4 ${accent ?? 'text-sapphire'}`} />
        </div>
      </div>
      <span className="font-display text-4xl font-semibold text-ink-text">{value}</span>
    </div>
  )
}

const statusColor: Record<string, string> = {
  completed: 'text-emerald',
  processing: 'text-amber-400',
  failed: 'text-danger',
  validated: 'text-sapphire',
}

export default function OverviewPage() {
  const [data, setData] = useState<OverviewData | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [showInvite, setShowInvite] = useState(false)
  const [inviteEmail, setInviteEmail] = useState('')
  const [inviteOrg, setInviteOrg] = useState('')
  const [inviting, setInviting] = useState(false)
  const [inviteMsg, setInviteMsg] = useState<{ type: 'ok' | 'err'; text: string } | null>(null)

  useEffect(() => {
    load()
    // silent refresh so running imports and their problems surface live
    const t = setInterval(() => loadSilent(), 10_000)
    return () => clearInterval(t)
  }, [])

  async function load() {
    setLoading(true)
    setLoadError(null)
    try {
      const res = await api.get<OverviewData>('/api/superadmin/overview')
      setData(res.data)
    } catch (err: unknown) {
      const status = (err as { response?: { status: number } }).response?.status
      setLoadError(status === 403 ? 'Access denied.' : 'Failed to load overview. Is the backend running?')
    } finally {
      setLoading(false)
    }
  }

  async function loadSilent() {
    try {
      const res = await api.get<OverviewData>('/api/superadmin/overview')
      setData(res.data)
    } catch { /* keep showing last good data */ }
  }

  const inviteEmailValid = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(inviteEmail)
  const canInvite = !inviting && inviteEmailValid && inviteOrg.trim().length > 0

  async function handleInvite(e: React.FormEvent) {
    e.preventDefault()
    if (!canInvite) return
    setInviting(true)
    setInviteMsg(null)
    const sentTo = inviteEmail
    try {
      await api.post('/api/superadmin/invite', { email: inviteEmail, organization: inviteOrg })
      setInviteEmail('')
      setInviteOrg('')
      setInviteMsg({ type: 'ok', text: `Invitation sent to ${sentTo}` })
    } catch (err: unknown) {
      const resp = (err as { response?: { status: number; data?: { message?: string } } }).response
      setInviteMsg({
        type: 'err',
        text: resp?.status === 409
          ? (resp.data?.message ?? 'An active invitation already exists for this email.')
          : resp?.status === 500
          ? 'Invitation could not be sent. Check email configuration.'
          : 'Failed to send invite.',
      })
    } finally {
      setInviting(false)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-24">
        <RefreshCw className="h-6 w-6 animate-spin text-ink-muted" />
      </div>
    )
  }

  if (loadError) {
    return (
      <div className="flex flex-col items-center justify-center py-24 gap-4">
        <AlertCircle className="h-8 w-8 text-danger" />
        <p className="text-sm text-ink-muted">{loadError}</p>
        <button
          onClick={load}
          className="flex items-center gap-2 rounded-md border border-ink-line px-4 py-2 text-sm text-ink-muted hover:text-ink-text transition-colors"
        >
          <RefreshCw className="h-4 w-4" /> Retry
        </button>
      </div>
    )
  }

  if (!data) return null

  return (
    <div className="p-8 space-y-8 max-w-6xl">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-ink-text">Platform Overview</h1>
        <button
          onClick={() => { setShowInvite(true); setInviteMsg(null) }}
          className="flex items-center gap-2 rounded-md bg-sapphire px-4 py-2 text-sm font-medium text-white hover:bg-sapphire-dark transition-colors"
        >
          <Plus className="h-4 w-4" />
          Invite University
        </button>
      </div>

      {/* KPI row */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <KpiCard label="Universities"       value={data.totalUniversities}          icon={Building2} accent="text-sapphire"  iconBg="bg-sapphire/10" />
        <KpiCard label="Total Alumni"       value={data.totalAlumni.toLocaleString()} icon={Users}   accent="text-emerald"   iconBg="bg-emerald/10" />
        <KpiCard label="Imports This Month" value={data.importsThisMonth}            icon={Upload}   accent="text-amber-400" iconBg="bg-amber-400/10" />
        <KpiCard
          label="Pending Requests"
          value={data.pendingRequests}
          icon={Clock}
          accent={data.pendingRequests > 0 ? 'text-danger' : 'text-ink-muted'}
          iconBg={data.pendingRequests > 0 ? 'bg-danger/10' : 'bg-ink-line'}
        />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* Recent Activity */}
        <section className="rounded-xl border border-ink-line bg-ink-panel p-5">
          <h2 className="text-sm font-semibold text-ink-text mb-4">Recent Activity</h2>
          {data.recentActivity.length === 0 ? (
            <p className="text-sm text-ink-muted">No activity yet.</p>
          ) : (
            <ol className="space-y-3">
              {data.recentActivity.map((a) => (
                <li key={a.id} className="flex items-start gap-3 text-sm">
                  <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-sapphire" />
                  <div className="min-w-0">
                    <span className="font-medium text-ink-text">{a.actionType}</span>
                    {a.actionDetails && (
                      <span className="text-ink-muted"> — {a.actionDetails}</span>
                    )}
                    <p className="text-xs text-ink-muted truncate">
                      {a.tenantName ?? '—'} · {a.userEmail ?? 'system'} ·{' '}
                      {new Date(a.actionTime).toLocaleString()}
                    </p>
                  </div>
                </li>
              ))}
            </ol>
          )}
        </section>

        {/* Platform Health */}
        <section className="rounded-xl border border-ink-line bg-ink-panel p-5 space-y-5">
          <h2 className="text-sm font-semibold text-ink-text">Platform Health</h2>

          {data.tenantsExpiringSoon > 0 && (
            <div className="flex items-center gap-2 rounded-lg bg-amber-400/10 px-3 py-2 text-sm text-amber-400">
              <AlertCircle className="h-4 w-4 shrink-0" />
              <span>{data.tenantsExpiringSoon} subscription{data.tenantsExpiringSoon !== 1 ? 's' : ''} expiring within 30 days</span>
            </div>
          )}
          {data.failedRowsThisMonth > 0 && (
            <div className="flex items-center gap-2 rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger">
              <AlertCircle className="h-4 w-4 shrink-0" />
              <span>{data.failedRowsThisMonth.toLocaleString()} failed rows this month</span>
            </div>
          )}
          {data.recentBatches.some(b => b.errorSummary?.includes('OpenAI')) && (
            <div className="flex items-center gap-2 rounded-lg bg-amber-400/10 px-3 py-2 text-sm text-amber-400">
              <AlertCircle className="h-4 w-4 shrink-0" />
              <span>
                OpenAI unavailable during recent imports (quota?) — rows were normalised
                with the built-in fallback instead of the LLM.
              </span>
            </div>
          )}

          <div>
            <p className="font-mono text-xs text-ink-muted uppercase tracking-widest mb-3">Recent Imports</p>
            {data.recentBatches.length === 0 ? (
              <p className="text-sm text-ink-muted">No imports yet.</p>
            ) : (
              <ul className="space-y-2">
                {data.recentBatches.map((b) => (
                  <li key={b.id} className="text-sm">
                    <div className="flex items-center justify-between">
                      <div className="min-w-0 mr-3">
                        <span className="font-medium text-ink-text">{b.tenantName}</span>
                        <span className="text-ink-muted"> / {b.filename}</span>
                      </div>
                      <div className="flex items-center gap-3 shrink-0 text-xs">
                        <span className={statusColor[b.status] ?? 'text-ink-muted'}>
                          {b.status === 'processing'
                            ? `processing ${b.processedCount}/${b.recordCount}`
                            : b.status}
                        </span>
                        {b.status !== 'processing' && (
                          <span className="text-ink-muted">{b.recordCount.toLocaleString()} rows</span>
                        )}
                        {b.failedCount > 0 && (
                          <span className="text-danger">{b.failedCount} failed</span>
                        )}
                      </div>
                    </div>
                    {b.errorSummary && (
                      <p className="mt-0.5 text-xs text-amber-400 truncate">{b.errorSummary}</p>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </div>

          {data.pendingRequests > 0 && (
            <Link
              to="/operator/customers"
              className="flex items-center gap-2 text-sm text-sapphire hover:underline"
            >
              <Clock className="h-4 w-4" />
              {data.pendingRequests} pending request{data.pendingRequests !== 1 ? 's' : ''} — review now
            </Link>
          )}
        </section>
      </div>

      {/* Invite modal */}
      {showInvite && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/80 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-xl border border-ink-line bg-ink-panel p-6 shadow-2xl">
            <h2 className="text-lg font-semibold text-ink-text mb-4">Invite University</h2>
            <form onSubmit={handleInvite} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-ink-text mb-1.5">Admin email</label>
                <input
                  type="email" required value={inviteEmail}
                  onChange={(e) => { setInviteEmail(e.target.value); setInviteMsg(null) }}
                  className="w-full rounded-md border border-ink-line bg-ink px-3 py-2 text-sm text-ink-text placeholder:text-ink-muted focus:outline-none focus:ring-2 focus:ring-sapphire"
                  placeholder="admin@university.edu"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink-text mb-1.5">Organization name</label>
                <input
                  type="text" required value={inviteOrg}
                  onChange={(e) => { setInviteOrg(e.target.value); setInviteMsg(null) }}
                  className="w-full rounded-md border border-ink-line bg-ink px-3 py-2 text-sm text-ink-text placeholder:text-ink-muted focus:outline-none focus:ring-2 focus:ring-sapphire"
                  placeholder="University of Technology Malaysia"
                />
              </div>
              {inviteMsg && (
                <p className={`text-sm ${inviteMsg.type === 'ok' ? 'text-emerald' : 'text-danger'}`}>
                  {inviteMsg.text}
                </p>
              )}
              <div className="flex gap-3 pt-2">
                <button
                  type="submit" disabled={!canInvite}
                  className="flex-1 rounded-md bg-sapphire px-4 py-2 text-sm font-medium text-white hover:bg-sapphire-dark disabled:opacity-50 transition-colors"
                >
                  {inviting ? 'Sending…' : 'Send Invite'}
                </button>
                <button
                  type="button" onClick={() => setShowInvite(false)}
                  className="flex-1 rounded-md border border-ink-line px-4 py-2 text-sm text-ink-muted hover:text-ink-text transition-colors"
                >
                  Cancel
                </button>
              </div>
              <p className="text-xs text-ink-muted">Invitation link expires in 20 minutes.</p>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
