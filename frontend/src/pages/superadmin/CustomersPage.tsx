import { useState, useEffect, useRef, useCallback } from 'react'
import type { ReactNode, ElementType } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '@/lib/api'
import {
  Plus, MoreHorizontal, RefreshCw, AlertCircle, AlertTriangle,
  X, ChevronRight, Shield, Upload, RotateCcw, Ban, Trash2,
} from 'lucide-react'

// ── Types ─────────────────────────────────────────────────────────────────────

type SubscriptionStatus = 'active' | 'suspended' | 'expired'

interface Customer {
  id: string
  institutionName: string
  adminEmail: string
  subscriptionStatus: SubscriptionStatus
  subscriptionStart: string | null
  subscriptionEnd: string | null
  autoSuspendOnExpiry: boolean
  alumniCount: number
  userCount: number
  lastImportAt: string | null
  createdAt: string
}

interface Request {
  id: string
  name: string
  email: string
  institution: string
  jobTitle: string
  status: string
  submittedAt: string
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function fmtDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('en-US', {
    year: 'numeric', month: 'short', day: 'numeric',
  })
}

function fmtNum(n: number | null | undefined): string {
  if (n == null || isNaN(Number(n))) return '0'
  return Number(n).toLocaleString()
}

function todayStr(): string {
  return new Date().toISOString().slice(0, 10)
}

function addMonthsToDate(base: string | null, months: number): string {
  const d = base ? new Date(base) : new Date()
  d.setMonth(d.getMonth() + months)
  return d.toISOString().slice(0, 10)
}

function daysInfo(end: string | null): { text: string; cls: string } {
  if (!end) return { text: '—', cls: 'text-ink-muted' }
  const days = Math.ceil((new Date(end).getTime() - Date.now()) / 86_400_000)
  if (days < 0)  return { text: 'Expired',      cls: 'text-danger font-semibold' }
  if (days === 0) return { text: 'Expires today', cls: 'text-danger font-semibold' }
  if (days <= 7)  return { text: `${days}d left`, cls: 'text-danger font-semibold' }
  if (days <= 30) return { text: `${days}d left`, cls: 'text-amber font-medium' }
  return { text: `${days}d left`, cls: 'text-ink-muted' }
}

function borderColor(status: SubscriptionStatus, end: string | null): string {
  if (status === 'expired')   return '#BB3B2E'
  if (status === 'suspended') return '#C9791C'
  if (!end) return '#2D4BC4'
  const days = Math.ceil((new Date(end).getTime() - Date.now()) / 86_400_000)
  if (days <= 7)  return '#BB3B2E'
  if (days <= 30) return '#C9791C'
  return '#2D4BC4'
}

function statusBadge(status: SubscriptionStatus) {
  const cfg: Record<SubscriptionStatus, string> = {
    active:    'bg-emerald/10 text-emerald',
    suspended: 'bg-amber/10 text-amber',
    expired:   'bg-danger/10 text-danger',
  }
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium capitalize ${cfg[status]}`}>
      {status}
    </span>
  )
}

// ── Main component ────────────────────────────────────────────────────────────

export default function CustomersPage() {
  const navigate = useNavigate()

  // Data
  const [customers, setCustomers] = useState<Customer[]>([])
  const [requests,  setRequests]  = useState<Request[]>([])
  const [loading,   setLoading]   = useState(true)
  const [loadErr,   setLoadErr]   = useState<string | null>(null)
  const [tab,       setTab]       = useState<'active' | 'pending'>('active')

  // Toast
  const [toast, setToast] = useState<{ text: string; ok: boolean } | null>(null)
  const toastTimer = useRef<ReturnType<typeof setTimeout>>()

  // Action loading
  const [acting, setActing] = useState(false)

  // ⋯ Actions menu
  const [openMenu, setOpenMenu] = useState<string | null>(null)
  const menuRef = useRef<HTMLDivElement>(null)

  // Renew modal
  const [renewTarget, setRenewTarget] = useState<Customer | null>(null)
  const [renewDate,   setRenewDate]   = useState('')

  // Suspend confirm
  const [suspendTarget, setSuspendTarget] = useState<Customer | null>(null)

  // Reactivate confirm
  const [reactivateTarget, setReactivateTarget] = useState<Customer | null>(null)

  // Offboard (2-step)
  const [offboardTarget,  setOffboardTarget]  = useState<Customer | null>(null)
  const [offboardStep,    setOffboardStep]    = useState<1 | 2>(1)
  const [offboardConfirm, setOffboardConfirm] = useState('')

  // Approve side panel
  const [approveRequest,    setApproveRequest]    = useState<Request | null>(null)
  const [approveStart,      setApproveStart]      = useState(todayStr())
  const [approveEnd,        setApproveEnd]        = useState(addMonthsToDate(null, 12))
  const [approveAutoSuspend,setApproveAutoSuspend] = useState(true)

  // Deny confirm
  const [denyTarget, setDenyTarget] = useState<Request | null>(null)

  // ── Data loading ───────────────────────────────────────────────────────────

  const load = useCallback(async () => {
    setLoading(true)
    setLoadErr(null)
    try {
      const [cRes, rRes] = await Promise.all([
        api.get<Customer[]>('/api/superadmin/customers'),
        api.get<Request[]>('/api/superadmin/requests?status=pending'),
      ])
      setCustomers(Array.isArray(cRes.data) ? cRes.data : [])
      setRequests(Array.isArray(rRes.data) ? rRes.data : [])
    } catch {
      setLoadErr('Failed to load data. Is the backend running?')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load])

  // Close menu on outside click
  useEffect(() => {
    function handler(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setOpenMenu(null)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  // ── Toast ──────────────────────────────────────────────────────────────────

  function showToast(text: string, ok = true) {
    clearTimeout(toastTimer.current)
    setToast({ text, ok })
    toastTimer.current = setTimeout(() => setToast(null), 4000)
  }

  // ── Action handlers ────────────────────────────────────────────────────────

  async function handleRenew() {
    if (!renewTarget || !renewDate) return
    setActing(true)
    try {
      await api.put(`/api/superadmin/customers/${renewTarget.id}/renew`, {
        subscriptionEnd: renewDate,
      })
      showToast(`Agreement renewed for ${renewTarget.institutionName} until ${fmtDate(renewDate)}`)
      setRenewTarget(null)
      await load()
    } catch {
      showToast('Failed to renew agreement.', false)
    } finally {
      setActing(false)
    }
  }

  async function handleSuspend() {
    if (!suspendTarget) return
    setActing(true)
    try {
      await api.put(`/api/superadmin/customers/${suspendTarget.id}/suspend`)
      showToast(`Access suspended for ${suspendTarget.institutionName}`)
      setSuspendTarget(null)
      await load()
    } catch {
      showToast('Failed to suspend access.', false)
    } finally {
      setActing(false)
    }
  }

  async function handleReactivate() {
    if (!reactivateTarget) return
    setActing(true)
    try {
      await api.put(`/api/superadmin/customers/${reactivateTarget.id}/reactivate`)
      showToast(`Access reactivated for ${reactivateTarget.institutionName}`)
      setReactivateTarget(null)
      await load()
    } catch {
      showToast('Failed to reactivate access.', false)
    } finally {
      setActing(false)
    }
  }

  async function handleOffboard() {
    if (!offboardTarget) return
    if (offboardConfirm.trim() !== offboardTarget.institutionName) return
    setActing(true)
    try {
      await api.delete(`/api/superadmin/customers/${offboardTarget.id}`, {
        data: { confirmationName: offboardConfirm.trim() },
      })
      showToast(`${offboardTarget.institutionName} has been offboarded. All data erased.`)
      setOffboardTarget(null)
      setOffboardConfirm('')
      await load()
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { error?: string } } }).response?.data?.error
      showToast(msg === 'confirmation_name_mismatch' ? 'Name does not match.' : 'Failed to offboard.', false)
    } finally {
      setActing(false)
    }
  }

  async function handleApprove() {
    if (!approveRequest) return
    setActing(true)
    try {
      await api.post(`/api/superadmin/requests/${approveRequest.id}/approve`, {
        subscriptionStart: approveStart,
        subscriptionEnd:   approveEnd,
        autoSuspendOnExpiry: approveAutoSuspend,
      })
      showToast(`${approveRequest.institution} activated. Agreement runs until ${fmtDate(approveEnd)}.`)
      setApproveRequest(null)
      await load()
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      showToast(msg ?? 'Failed to approve request.', false)
    } finally {
      setActing(false)
    }
  }

  async function handleDeny() {
    if (!denyTarget) return
    setActing(true)
    try {
      await api.post(`/api/superadmin/requests/${denyTarget.id}/deny`)
      showToast('Request denied.')
      setDenyTarget(null)
      await load()
    } catch {
      showToast('Failed to deny request.', false)
    } finally {
      setActing(false)
    }
  }

  // ── Derived ────────────────────────────────────────────────────────────────

  const pendingCount   = requests.length
  const expiredList    = customers.filter(c => c.subscriptionStatus === 'expired')
  const expiringList   = customers.filter(c => {
    if (c.subscriptionStatus !== 'active' || !c.subscriptionEnd) return false
    const days = Math.ceil((new Date(c.subscriptionEnd).getTime() - Date.now()) / 86_400_000)
    return days <= 30
  })

  // ── Loading / error ────────────────────────────────────────────────────────

  if (loading) {
    return (
      <div className="flex items-center justify-center py-24">
        <RefreshCw className="h-6 w-6 animate-spin text-ink-muted" />
      </div>
    )
  }

  if (loadErr) {
    return (
      <div className="flex flex-col items-center justify-center py-24 gap-4">
        <AlertCircle className="h-8 w-8 text-danger" />
        <p className="text-sm text-ink-muted">{loadErr}</p>
        <button
          onClick={load}
          className="flex items-center gap-2 rounded-md border border-ink-line px-4 py-2 text-sm text-ink-muted hover:text-ink-text transition-colors"
        >
          <RefreshCw className="h-4 w-4" /> Retry
        </button>
      </div>
    )
  }

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div className="p-8 max-w-5xl">

      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-semibold text-ink-text">Customers</h1>
        <button
          onClick={() => {
            // Invite flow lives on Overview for now; navigate there
            navigate('/operator/overview')
          }}
          className="flex items-center gap-2 rounded-md bg-sapphire px-4 py-2 text-sm font-medium text-white hover:opacity-90 transition-opacity"
        >
          <Plus className="h-4 w-4" /> Invite University
        </button>
      </div>

      {/* Tabs */}
      <div className="flex gap-6 border-b border-ink-line mb-6">
        {(['active', 'pending'] as const).map(t => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`pb-2.5 text-sm font-medium capitalize transition-colors border-b-2 -mb-px ${
              tab === t
                ? 'border-sapphire text-ink-text'
                : 'border-transparent text-ink-muted hover:text-ink-text'
            }`}
          >
            {t === 'active' ? 'Active' : 'Pending'}
            <span className={`ml-1.5 rounded-full px-1.5 py-0.5 text-xs ${
              tab === t ? 'bg-sapphire/10 text-sapphire' : 'bg-ink-line text-ink-muted'
            }`}>
              {t === 'active' ? customers.length : pendingCount}
            </span>
          </button>
        ))}
      </div>

      {/* ── ACTIVE TAB ── */}
      {tab === 'active' && (
        <div>
          {/* Expiry banners */}
          {expiredList.length > 0 && (
            <div className="mb-4 flex items-center gap-3 rounded-lg border border-danger/30 bg-danger/5 px-4 py-3">
              <AlertTriangle className="h-4 w-4 shrink-0 text-danger" />
              <p className="text-sm text-danger">
                <span className="font-semibold">{expiredList.length} institution{expiredList.length > 1 ? 's' : ''}</span>
                {' '}ha{expiredList.length > 1 ? 've' : 's'} an expired subscription. Renew or offboard.
              </p>
            </div>
          )}
          {expiringList.length > 0 && (
            <div className="mb-4 flex items-center gap-3 rounded-lg border border-amber/30 bg-amber/5 px-4 py-3">
              <AlertTriangle className="h-4 w-4 shrink-0 text-amber" />
              <p className="text-sm text-amber">
                <span className="font-semibold">{expiringList.length} institution{expiringList.length > 1 ? 's' : ''}</span>
                {' '}expir{expiringList.length > 1 ? 'e' : 'es'} within 30 days.
              </p>
            </div>
          )}

          {customers.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-20 text-ink-muted gap-2">
              <p className="text-sm">No customers yet.</p>
              <p className="text-xs">Invite a university to get started.</p>
            </div>
          ) : (
            <div className="space-y-3" ref={menuRef}>
              {customers.map(c => {
                const di = daysInfo(c.subscriptionEnd)
                const bc = borderColor(c.subscriptionStatus, c.subscriptionEnd)
                const isMenuOpen = openMenu === c.id

                return (
                  <div
                    key={c.id}
                    className="rounded-xl border border-ink-line bg-ink-panel"
                    style={{ borderLeft: `4px solid ${bc}` }}
                  >
                    {/* Card header */}
                    <div className="flex items-start justify-between gap-4 px-5 pt-4 pb-3">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="font-semibold text-ink-text truncate">{c.institutionName}</span>
                          {statusBadge(c.subscriptionStatus)}
                        </div>
                        <p className="text-sm text-ink-muted mt-0.5">{c.adminEmail}</p>
                      </div>

                      {/* Actions menu */}
                      <div className="relative shrink-0">
                        <button
                          onClick={() => setOpenMenu(isMenuOpen ? null : c.id)}
                          className="flex h-8 w-8 items-center justify-center rounded-md text-ink-muted hover:bg-ink-line hover:text-ink-text transition-colors"
                        >
                          <MoreHorizontal className="h-4 w-4" />
                        </button>
                        {isMenuOpen && (
                          <div className="absolute right-0 top-9 z-30 w-52 rounded-lg border border-ink-line bg-ink-panel shadow-xl py-1">
                            <MenuItem
                              icon={Shield} label="Manage Permissions"
                              onClick={() => {
                                setOpenMenu(null)
                                navigate(`/operator/permissions?tenant=${c.id}`)
                              }}
                            />
                            <MenuItem
                              icon={Upload} label="Upload Alumni Data"
                              onClick={() => {
                                setOpenMenu(null)
                                navigate(`/operator/import?tenant=${c.id}`)
                              }}
                            />
                            <MenuItem
                              icon={RotateCcw} label="Renew Agreement"
                              onClick={() => {
                                setOpenMenu(null)
                                setRenewTarget(c)
                                setRenewDate(addMonthsToDate(c.subscriptionEnd, 12))
                              }}
                            />
                            <div className="my-1 border-t border-ink-line" />
                            {c.subscriptionStatus === 'active' ? (
                              <MenuItem
                                icon={Ban} label="Suspend Access" danger
                                onClick={() => { setOpenMenu(null); setSuspendTarget(c) }}
                              />
                            ) : (
                              <MenuItem
                                icon={RotateCcw} label="Reactivate Access"
                                onClick={() => { setOpenMenu(null); setReactivateTarget(c) }}
                              />
                            )}
                            <MenuItem
                              icon={Trash2} label="Offboard & Delete" danger
                              onClick={() => {
                                setOpenMenu(null)
                                setOffboardTarget(c)
                                setOffboardStep(1)
                                setOffboardConfirm('')
                              }}
                            />
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Card stats */}
                    <div className="flex flex-wrap gap-x-6 gap-y-2 px-5 pb-4 text-xs text-ink-muted border-t border-ink-line/50 pt-3">
                      <Stat label="Alumni" value={fmtNum(c.alumniCount)} />
                      <Stat label="Users"  value={String(c.userCount)} />
                      <Stat label="Start"  value={fmtDate(c.subscriptionStart)} />
                      <Stat label="Ends"   value={fmtDate(c.subscriptionEnd)} />
                      <Stat label="Time left" value={di.text} cls={di.cls} />
                      <Stat label="Last import" value={fmtDate(c.lastImportAt)} />
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      )}

      {/* ── PENDING TAB ── */}
      {tab === 'pending' && (
        <div>
          {requests.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-20 text-ink-muted gap-2">
              <p className="text-sm">No pending requests.</p>
            </div>
          ) : (
            <div className="space-y-3">
              {requests.map(r => (
                <div key={r.id} className="rounded-xl border border-ink-line bg-ink-panel px-5 py-4">
                  <div className="flex items-start justify-between gap-4">
                    <div className="min-w-0">
                      <p className="font-semibold text-ink-text">{r.name}</p>
                      <p className="text-sm text-ink-muted">{r.institution} · {r.jobTitle}</p>
                      <p className="text-xs text-ink-muted mt-0.5">{r.email} · Applied {fmtDate(r.submittedAt)}</p>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      <button
                        onClick={() => {
                          setApproveRequest(r)
                          setApproveStart(todayStr())
                          setApproveEnd(addMonthsToDate(null, 12))
                          setApproveAutoSuspend(true)
                        }}
                        className="flex items-center gap-1.5 rounded-md bg-emerald/10 px-3 py-1.5 text-sm text-emerald hover:bg-emerald/20 transition-colors"
                      >
                        <ChevronRight className="h-4 w-4" /> Review &amp; Approve
                      </button>
                      <button
                        onClick={() => setDenyTarget(r)}
                        className="flex items-center gap-1.5 rounded-md border border-ink-line px-3 py-1.5 text-sm text-ink-muted hover:text-danger hover:border-danger/30 transition-colors"
                      >
                        Deny
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* ── RENEW MODAL ── */}
      {renewTarget && (
        <Overlay onClose={() => setRenewTarget(null)}>
          <h2 className="text-lg font-semibold text-ink-text mb-1">Renew Agreement</h2>
          <p className="text-sm text-ink-muted mb-4">{renewTarget.institutionName}</p>

          <label className="block text-xs font-medium text-ink-muted uppercase tracking-wide mb-1">
            Current end
          </label>
          <p className="text-sm text-ink-text mb-4">{fmtDate(renewTarget.subscriptionEnd)}</p>

          <label className="block text-xs font-medium text-ink-muted uppercase tracking-wide mb-1.5">
            New end date
          </label>
          <input
            type="date"
            value={renewDate}
            min={todayStr()}
            onChange={e => setRenewDate(e.target.value)}
            className="w-full rounded-md border border-ink-line bg-ink px-3 py-2 text-sm text-ink-text focus:outline-none focus:ring-2 focus:ring-sapphire mb-3"
          />

          <div className="flex gap-2 mb-5">
            {[6, 12, 24].map(mo => (
              <button
                key={mo}
                onClick={() => setRenewDate(addMonthsToDate(renewTarget.subscriptionEnd, mo))}
                className="rounded-md border border-ink-line px-2.5 py-1 text-xs text-ink-muted hover:text-ink-text hover:border-sapphire transition-colors"
              >
                +{mo < 12 ? `${mo}mo` : `${mo / 12}yr`}
              </button>
            ))}
          </div>

          <div className="flex gap-3">
            <button
              onClick={handleRenew}
              disabled={!renewDate || acting}
              className="flex-1 rounded-md bg-sapphire px-4 py-2 text-sm font-medium text-white hover:opacity-90 disabled:opacity-50 transition-opacity"
            >
              {acting ? 'Renewing…' : 'Renew'}
            </button>
            <button
              onClick={() => setRenewTarget(null)}
              className="flex-1 rounded-md border border-ink-line px-4 py-2 text-sm text-ink-muted hover:text-ink-text transition-colors"
            >
              Cancel
            </button>
          </div>
        </Overlay>
      )}

      {/* ── SUSPEND CONFIRM ── */}
      {suspendTarget && (
        <Overlay onClose={() => setSuspendTarget(null)}>
          <div className="flex items-center gap-3 mb-4">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-amber/10">
              <Ban className="h-5 w-5 text-amber" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-ink-text">Suspend Access</h2>
              <p className="text-sm text-ink-muted">{suspendTarget.institutionName}</p>
            </div>
          </div>
          <p className="text-sm text-ink-muted mb-6">
            Users at this institution will immediately lose access to AlumIndex. You can reactivate at any time.
          </p>
          <div className="flex gap-3">
            <button
              onClick={handleSuspend}
              disabled={acting}
              className="flex-1 rounded-md bg-amber/10 px-4 py-2 text-sm font-medium text-amber hover:bg-amber/20 disabled:opacity-50 transition-colors"
            >
              {acting ? 'Suspending…' : 'Suspend Access'}
            </button>
            <button
              onClick={() => setSuspendTarget(null)}
              className="flex-1 rounded-md border border-ink-line px-4 py-2 text-sm text-ink-muted hover:text-ink-text transition-colors"
            >
              Cancel
            </button>
          </div>
        </Overlay>
      )}

      {/* ── REACTIVATE CONFIRM ── */}
      {reactivateTarget && (
        <Overlay onClose={() => setReactivateTarget(null)}>
          <div className="flex items-center gap-3 mb-4">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-emerald/10">
              <RotateCcw className="h-5 w-5 text-emerald" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-ink-text">Reactivate Access</h2>
              <p className="text-sm text-ink-muted">{reactivateTarget.institutionName}</p>
            </div>
          </div>
          <p className="text-sm text-ink-muted mb-6">
            Users at this institution will immediately regain access. Make sure the agreement terms are in order.
          </p>
          <div className="flex gap-3">
            <button
              onClick={handleReactivate}
              disabled={acting}
              className="flex-1 rounded-md bg-emerald/10 px-4 py-2 text-sm font-medium text-emerald hover:bg-emerald/20 disabled:opacity-50 transition-colors"
            >
              {acting ? 'Reactivating…' : 'Reactivate'}
            </button>
            <button
              onClick={() => setReactivateTarget(null)}
              className="flex-1 rounded-md border border-ink-line px-4 py-2 text-sm text-ink-muted hover:text-ink-text transition-colors"
            >
              Cancel
            </button>
          </div>
        </Overlay>
      )}

      {/* ── OFFBOARD STEP 1 ── */}
      {offboardTarget && offboardStep === 1 && (
        <Overlay onClose={() => setOffboardTarget(null)}>
          <div className="flex items-center gap-3 mb-4">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-danger/10">
              <AlertTriangle className="h-5 w-5 text-danger" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-ink-text">Offboard &amp; Delete</h2>
              <p className="text-sm text-ink-muted">{offboardTarget.institutionName}</p>
            </div>
          </div>
          <div className="rounded-lg border border-danger/20 bg-danger/5 p-3 mb-5 space-y-1">
            <p className="text-sm text-danger font-medium">This action is permanent and cannot be undone.</p>
            <ul className="text-xs text-ink-muted list-disc list-inside space-y-0.5">
              <li><span className="font-medium text-ink-text">{fmtNum(offboardTarget.alumniCount)}</span> alumni records will be erased</li>
              <li><span className="font-medium text-ink-text">{offboardTarget.userCount}</span> user accounts will be deleted</li>
              <li>All import history and permissions will be removed</li>
              <li>An audit log entry will be preserved for compliance</li>
            </ul>
          </div>
          <div className="flex gap-3">
            <button
              onClick={() => setOffboardStep(2)}
              className="flex-1 rounded-md bg-danger/10 px-4 py-2 text-sm font-medium text-danger hover:bg-danger/20 transition-colors"
            >
              Proceed to Confirmation
            </button>
            <button
              onClick={() => setOffboardTarget(null)}
              className="flex-1 rounded-md border border-ink-line px-4 py-2 text-sm text-ink-muted hover:text-ink-text transition-colors"
            >
              Cancel
            </button>
          </div>
        </Overlay>
      )}

      {/* ── OFFBOARD STEP 2 ── */}
      {offboardTarget && offboardStep === 2 && (
        <Overlay onClose={() => setOffboardTarget(null)}>
          <h2 className="text-base font-semibold text-ink-text mb-1">Confirm Offboarding</h2>
          <p className="text-sm text-ink-muted mb-5">
            Type <span className="font-semibold text-ink-text">{offboardTarget.institutionName}</span> to permanently delete this institution and all its data.
          </p>
          <input
            type="text"
            value={offboardConfirm}
            onChange={e => setOffboardConfirm(e.target.value)}
            placeholder={offboardTarget.institutionName}
            className="w-full rounded-md border border-ink-line bg-ink px-3 py-2 text-sm text-ink-text placeholder:text-ink-muted focus:outline-none focus:ring-2 focus:ring-danger mb-5"
          />
          <div className="flex gap-3">
            <button
              onClick={handleOffboard}
              disabled={offboardConfirm.trim() !== offboardTarget.institutionName || acting}
              className="flex-1 rounded-md bg-danger px-4 py-2 text-sm font-medium text-white hover:opacity-90 disabled:opacity-40 transition-opacity"
            >
              {acting ? 'Deleting…' : 'Permanently Delete'}
            </button>
            <button
              onClick={() => setOffboardTarget(null)}
              className="flex-1 rounded-md border border-ink-line px-4 py-2 text-sm text-ink-muted hover:text-ink-text transition-colors"
            >
              Cancel
            </button>
          </div>
        </Overlay>
      )}

      {/* ── DENY CONFIRM ── */}
      {denyTarget && (
        <Overlay onClose={() => setDenyTarget(null)}>
          <h2 className="text-base font-semibold text-ink-text mb-1">Deny Request</h2>
          <p className="text-sm text-ink-muted mb-1">{denyTarget.name} · {denyTarget.institution}</p>
          <p className="text-sm text-ink-muted mb-6">
            A denial email will be sent to {denyTarget.email}. This cannot be undone.
          </p>
          <div className="flex gap-3">
            <button
              onClick={handleDeny}
              disabled={acting}
              className="flex-1 rounded-md bg-danger/10 px-4 py-2 text-sm font-medium text-danger hover:bg-danger/20 disabled:opacity-50 transition-colors"
            >
              {acting ? 'Denying…' : 'Deny Application'}
            </button>
            <button
              onClick={() => setDenyTarget(null)}
              className="flex-1 rounded-md border border-ink-line px-4 py-2 text-sm text-ink-muted hover:text-ink-text transition-colors"
            >
              Cancel
            </button>
          </div>
        </Overlay>
      )}

      {/* ── APPROVE SIDE PANEL ── */}
      {approveRequest && (
        <>
          <div
            className="fixed inset-0 z-40 bg-ink/40 backdrop-blur-sm"
            onClick={() => setApproveRequest(null)}
          />
          <div className="fixed right-0 top-0 bottom-0 z-50 w-full max-w-md bg-ink-panel border-l border-ink-line shadow-2xl flex flex-col">
            {/* Panel header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-ink-line">
              <h2 className="text-base font-semibold text-ink-text">Review Request</h2>
              <button
                onClick={() => setApproveRequest(null)}
                className="flex h-8 w-8 items-center justify-center rounded-md text-ink-muted hover:text-ink-text hover:bg-ink-line transition-colors"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            {/* Panel body */}
            <div className="flex-1 overflow-y-auto px-6 py-5 space-y-6">
              {/* Applicant */}
              <section>
                <h3 className="text-xs font-semibold text-ink-muted uppercase tracking-widest mb-3">Applicant</h3>
                <dl className="space-y-2">
                  <InfoRow label="Name"       value={approveRequest.name} />
                  <InfoRow label="Email"      value={approveRequest.email} />
                  <InfoRow label="Institution" value={approveRequest.institution} />
                  <InfoRow label="Job title"  value={approveRequest.jobTitle} />
                  <InfoRow label="Applied"    value={fmtDate(approveRequest.submittedAt)} />
                </dl>
              </section>

              {/* Agreement terms */}
              <section>
                <h3 className="text-xs font-semibold text-ink-muted uppercase tracking-widest mb-3">Agreement Terms</h3>
                <div className="space-y-3">
                  <div>
                    <label className="block text-xs text-ink-muted mb-1">Start date</label>
                    <input
                      type="date"
                      value={approveStart}
                      onChange={e => setApproveStart(e.target.value)}
                      className="w-full rounded-md border border-ink-line bg-ink px-3 py-2 text-sm text-ink-text focus:outline-none focus:ring-2 focus:ring-sapphire"
                    />
                  </div>
                  <div>
                    <label className="block text-xs text-ink-muted mb-1">End date</label>
                    <input
                      type="date"
                      value={approveEnd}
                      min={approveStart}
                      onChange={e => setApproveEnd(e.target.value)}
                      className="w-full rounded-md border border-ink-line bg-ink px-3 py-2 text-sm text-ink-text focus:outline-none focus:ring-2 focus:ring-sapphire"
                    />
                    <div className="flex gap-2 mt-2">
                      {[6, 12, 24].map(mo => (
                        <button
                          key={mo}
                          onClick={() => setApproveEnd(addMonthsToDate(approveStart, mo))}
                          className="rounded-md border border-ink-line px-2 py-1 text-xs text-ink-muted hover:text-ink-text hover:border-sapphire transition-colors"
                        >
                          +{mo < 12 ? `${mo}mo` : `${mo / 12}yr`}
                        </button>
                      ))}
                    </div>
                  </div>
                  <div className="flex items-center justify-between py-2">
                    <span className="text-sm text-ink-text">Auto-expire on end date</span>
                    <button
                      onClick={() => setApproveAutoSuspend(v => !v)}
                      className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
                        approveAutoSuspend ? 'bg-sapphire' : 'bg-ink-line'
                      }`}
                    >
                      <span className={`inline-block h-3.5 w-3.5 rounded-full bg-white shadow transition-transform ${
                        approveAutoSuspend ? 'translate-x-4' : 'translate-x-1'
                      }`} />
                    </button>
                  </div>
                </div>
              </section>

              {/* Note */}
              <section>
                <div className="rounded-lg border border-sapphire/20 bg-sapphire/5 px-3 py-2.5">
                  <p className="text-xs text-ink-muted">
                    Default data permissions will be enabled on activation. You can adjust them from the Permissions page afterwards.
                  </p>
                </div>
              </section>
            </div>

            {/* Panel footer */}
            <div className="px-6 py-4 border-t border-ink-line flex gap-3">
              <button
                onClick={handleApprove}
                disabled={!approveStart || !approveEnd || acting}
                className="flex-1 rounded-md bg-emerald/10 px-4 py-2 text-sm font-medium text-emerald hover:bg-emerald/20 disabled:opacity-50 transition-colors"
              >
                {acting ? 'Activating…' : 'Approve & Activate'}
              </button>
              <button
                onClick={() => setApproveRequest(null)}
                className="flex-1 rounded-md border border-ink-line px-4 py-2 text-sm text-ink-muted hover:text-ink-text transition-colors"
              >
                Cancel
              </button>
            </div>
          </div>
        </>
      )}

      {/* ── TOAST ── */}
      {toast && (
        <div className={`fixed bottom-6 left-1/2 -translate-x-1/2 z-[60] flex items-center gap-2 rounded-lg px-4 py-3 text-sm shadow-xl border ${
          toast.ok
            ? 'bg-ink-panel border-emerald/30 text-ink-text'
            : 'bg-ink-panel border-danger/30 text-danger'
        }`}>
          {toast.text}
        </div>
      )}
    </div>
  )
}

// ── Sub-components ────────────────────────────────────────────────────────────

function Overlay({ children, onClose }: { children: ReactNode; onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/70 backdrop-blur-sm p-4">
      <div className="w-full max-w-sm rounded-xl bg-ink-panel border border-ink-line p-6 shadow-2xl relative">
        <button
          onClick={onClose}
          className="absolute right-4 top-4 flex h-7 w-7 items-center justify-center rounded-md text-ink-muted hover:text-ink-text hover:bg-ink-line transition-colors"
        >
          <X className="h-4 w-4" />
        </button>
        {children}
      </div>
    </div>
  )
}

function MenuItem({
  icon: Icon, label, onClick, danger = false,
}: {
  icon: ElementType
  label: string
  onClick: () => void
  danger?: boolean
}) {
  return (
    <button
      onClick={onClick}
      className={`flex w-full items-center gap-2.5 px-3 py-2 text-sm transition-colors ${
        danger
          ? 'text-danger hover:bg-danger/5'
          : 'text-ink-muted hover:text-ink-text hover:bg-ink-line'
      }`}
    >
      <Icon className="h-4 w-4 shrink-0" />
      {label}
    </button>
  )
}

function Stat({ label, value, cls = 'text-ink-text' }: {
  label: string; value: string; cls?: string
}) {
  return (
    <span>
      <span className="text-ink-muted">{label}: </span>
      <span className={cls}>{value}</span>
    </span>
  )
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline gap-2">
      <dt className="w-24 shrink-0 text-xs text-ink-muted">{label}</dt>
      <dd className="text-sm text-ink-text">{value}</dd>
    </div>
  )
}
