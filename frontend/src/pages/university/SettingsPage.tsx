import { useState, useEffect } from 'react'
import { useAuth } from '@/contexts/AuthContext'
import api from '@/lib/api'
import {
  Check, X, MoreHorizontal, Copy, Eye, EyeOff, RefreshCw,
  AlertTriangle, UserPlus, Calendar, Key, Users, Loader2,
} from 'lucide-react'

type Tab = 'account' | 'team' | 'institution'

interface TeamMember {
  id: string
  email: string
  fullName: string
  role: string
  status: string
}

interface SeatInfo { used: number; limit: number }

interface InstitutionData {
  institutionName: string
  primaryContact: string | null
  contactEmail: string | null
  subscriptionStatus: string
  subscriptionStart: string | null
  subscriptionEnd: string | null
  seatLimit: number
  hasApiKey: boolean
  maskedApiKey: string | null
  allowedEmailDomain: string | null
}

function daysUntil(iso: string | null): number | null {
  if (!iso) return null
  return Math.ceil((new Date(iso).getTime() - Date.now()) / 86_400_000)
}

function Hint({ ok, text }: { ok: boolean; text: string }) {
  return (
    <span className={`flex items-center gap-1 text-xs ${ok ? 'text-emerald' : 'text-muted'}`}>
      {ok ? <Check className="h-3 w-3" /> : <X className="h-3 w-3" />}
      {text}
    </span>
  )
}

function RoleBadge({ role }: { role: string }) {
  return role === 'admin'
    ? <span className="rounded-full bg-sapphire-soft px-2 py-0.5 text-xs font-medium text-sapphire">Admin</span>
    : <span className="rounded-full border border-line bg-bone px-2 py-0.5 text-xs font-medium text-muted">Read Only</span>
}

function StatusBadge({ status }: { status: string }) {
  if (status === 'active')
    return <span className="rounded-full bg-emerald/10 px-2 py-0.5 text-xs font-medium text-emerald">Active</span>
  if (status === 'pending_activation')
    return <span className="rounded-full bg-amber/10 px-2 py-0.5 text-xs font-medium text-amber">Pending</span>
  return <span className="rounded-full bg-line px-2 py-0.5 text-xs font-medium text-muted">Inactive</span>
}

function memberBorderColor(status: string): string {
  if (status === 'active') return '#2D4BC4'
  if (status === 'pending_activation') return '#C9791C'
  return '#657182'
}

function MemberMenu({ m, openMenu, setOpenMenu, onPromote, onChangeRole, onDeactivate, onReactivate }: {
  m: TeamMember
  openMenu: string | null
  setOpenMenu: (id: string | null) => void
  onPromote: () => void
  onChangeRole: (role: string) => void
  onDeactivate: () => void
  onReactivate: () => void
}) {
  return (
    <div className="relative shrink-0">
      <button
        onClick={e => { e.stopPropagation(); setOpenMenu(openMenu === m.id ? null : m.id) }}
        className="flex h-8 w-8 items-center justify-center rounded-md text-muted hover:bg-bone hover:text-text transition-colors"
      >
        <MoreHorizontal className="h-4 w-4" />
      </button>
      {openMenu === m.id && (
        <div
          onClick={e => e.stopPropagation()}
          className="absolute right-0 top-9 z-30 w-48 rounded-lg border border-line bg-surface shadow-xl py-1"
        >
          {m.status === 'active' && (
            <button
              onClick={() => m.role === 'readonly' ? onPromote() : onChangeRole('readonly')}
              className="flex w-full items-center gap-2.5 px-3 py-2 text-sm text-muted hover:bg-bone hover:text-text transition-colors"
            >
              {m.role === 'admin' ? 'Change to Read Only' : 'Change to Admin'}
            </button>
          )}
          {m.status === 'active' && (
            <button
              onClick={onDeactivate}
              className="flex w-full items-center gap-2.5 px-3 py-2 text-sm text-danger hover:bg-danger/5 transition-colors"
            >
              Deactivate
            </button>
          )}
          {(m.status === 'inactive' || m.status === 'pending_activation') && (
            <button
              onClick={onReactivate}
              className="flex w-full items-center gap-2.5 px-3 py-2 text-sm text-emerald hover:bg-emerald/5 transition-colors"
            >
              Reactivate
            </button>
          )}
        </div>
      )}
    </div>
  )
}

export default function SettingsPage() {
  const { user, refreshUser } = useAuth()
  const [tab, setTab] = useState<Tab>('account')

  // My Account
  const [pwForm, setPwForm] = useState({ current: '', newPw: '', confirm: '' })
  const [pwLoading, setPwLoading] = useState(false)
  const [pwMsg, setPwMsg] = useState<{ type: 'ok' | 'err'; text: string } | null>(null)
  const [nameValue, setNameValue] = useState(user?.fullName ?? '')
  const [nameLoading, setNameLoading] = useState(false)
  const [nameMsg, setNameMsg] = useState<{ type: 'ok' | 'err'; text: string } | null>(null)

  // Team
  const [members, setMembers] = useState<TeamMember[]>([])
  const [seatInfo, setSeatInfo] = useState<SeatInfo | null>(null)
  const [teamLoading, setTeamLoading] = useState(false)
  const [teamLoaded, setTeamLoaded] = useState(false)
  const [openMenu, setOpenMenu] = useState<string | null>(null)
  const [deactivateTarget, setDeactivateTarget] = useState<TeamMember | null>(null)
  const [deactivating, setDeactivating] = useState(false)
  const [promoteTarget, setPromoteTarget] = useState<TeamMember | null>(null)
  const [promoting, setPromoting] = useState(false)
  const [teamMsg, setTeamMsg] = useState<{ type: 'ok' | 'err'; text: string } | null>(null)
  const [inviteEmail, setInviteEmail] = useState('')
  const [inviteRole] = useState<'readonly'>('readonly')
  const [inviting, setInviting] = useState(false)
  const [inviteMsg, setInviteMsg] = useState<{ type: 'ok' | 'err'; text: string } | null>(null)

  // Institution
  const [institution, setInstitution] = useState<InstitutionData | null>(null)
  const [instLoading, setInstLoading] = useState(false)
  const [instLoaded, setInstLoaded] = useState(false)
  const [editContact, setEditContact] = useState('')
  const [editEmail, setEditEmail] = useState('')
  const [savingInst, setSavingInst] = useState(false)
  const [instMsg, setInstMsg] = useState<{ type: 'ok' | 'err'; text: string } | null>(null)
  const [revealedKey, setRevealedKey] = useState<string | null>(null)
  const [showKey, setShowKey] = useState(false)
  const [keyCopied, setKeyCopied] = useState(false)
  const [showRotate, setShowRotate] = useState(false)
  const [rotating, setRotating] = useState(false)
  const [newKeyBanner, setNewKeyBanner] = useState<string | null>(null)

  // Derived — password form
  const newPw = pwForm.newPw
  const hasMinLen = newPw.length >= 8
  const hasUppercase = /[A-Z]/.test(newPw)
  const hasNumber = /\d/.test(newPw)
  const pwStrong = hasMinLen && hasUppercase && hasNumber
  const pwMatches = newPw.length > 0 && newPw === pwForm.confirm

  const isAdmin = user?.role === 'admin'
  const tabs: { id: Tab; label: string }[] = [
    { id: 'account', label: 'My Account' },
    ...(isAdmin
      ? [{ id: 'team' as Tab, label: 'Team' }, { id: 'institution' as Tab, label: 'Institution' }]
      : []),
  ]

  useEffect(() => {
    if (tab === 'team' && !teamLoaded) loadTeam()
  }, [tab]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (tab === 'institution' && !instLoaded) loadInstitution()
  }, [tab]) // eslint-disable-line react-hooks/exhaustive-deps

  // Close action menu on any outside click
  useEffect(() => {
    if (!openMenu) return
    const h = () => setOpenMenu(null)
    document.addEventListener('click', h)
    return () => document.removeEventListener('click', h)
  }, [openMenu])

  // ── Loaders ────────────────────────────────────────────────────────────────

  async function loadTeam() {
    setTeamLoading(true)
    try {
      const [mr, sr, ir] = await Promise.all([
        api.get<TeamMember[]>('/api/users'),
        api.get<SeatInfo>('/api/users/seat-limit'),
        api.get<InstitutionData>('/api/settings/institution'),
      ])
      setMembers(mr.data)
      setSeatInfo(sr.data)
      setInstitution(ir.data)
      setTeamLoaded(true)
    } catch {
      setTeamMsg({ type: 'err', text: 'Failed to load team data.' })
    } finally {
      setTeamLoading(false)
    }
  }

  async function loadInstitution() {
    setInstLoading(true)
    try {
      const res = await api.get<InstitutionData>('/api/settings/institution')
      setInstitution(res.data)
      setEditContact(res.data.primaryContact ?? '')
      setEditEmail(res.data.contactEmail ?? '')
      setInstLoaded(true)
    } catch {
      setInstMsg({ type: 'err', text: 'Failed to load institution data.' })
    } finally {
      setInstLoading(false)
    }
  }

  // ── My Account handlers ────────────────────────────────────────────────────

  async function handleChangePw(e: React.FormEvent) {
    e.preventDefault()
    if (!pwStrong || !pwMatches) return
    setPwLoading(true); setPwMsg(null)
    try {
      await api.put('/api/auth/password', { currentPassword: pwForm.current, newPassword: pwForm.newPw })
      setPwMsg({ type: 'ok', text: 'Password updated.' })
      setPwForm({ current: '', newPw: '', confirm: '' })
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      setPwMsg({
        type: 'err',
        text: msg === 'current_password_incorrect' ? 'Current password is incorrect.'
          : msg === 'same_as_current' ? 'New password must differ from your current one.'
          : 'Failed to update password.',
      })
    } finally {
      setPwLoading(false)
    }
  }

  async function handleUpdateName(e: React.FormEvent) {
    e.preventDefault()
    if (!nameValue.trim()) return
    setNameLoading(true); setNameMsg(null)
    try {
      await api.put('/api/auth/me', { fullName: nameValue.trim() })
      await refreshUser()
      setNameMsg({ type: 'ok', text: 'Display name updated.' })
    } catch {
      setNameMsg({ type: 'err', text: 'Failed to update name.' })
    } finally {
      setNameLoading(false)
    }
  }

  // ── Team handlers ──────────────────────────────────────────────────────────

  async function handleChangeRole(m: TeamMember, newRole: string) {
    setOpenMenu(null); setTeamMsg(null)
    try {
      const res = await api.put<TeamMember>(`/api/users/${m.id}/role`, { role: newRole })
      setMembers(prev => prev.map(u => u.id === m.id ? res.data : u))
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      setTeamMsg({
        type: 'err',
        text: msg === 'last_admin' ? 'Cannot remove the last admin.'
          : msg === 'cannot_change_own_role' ? 'You cannot change your own role.'
          : 'Failed to change role.',
      })
    }
  }

  async function doPromote() {
    if (!promoteTarget) return
    setPromoting(true); setTeamMsg(null)
    try {
      await api.put(`/api/users/${promoteTarget.id}/role`, { role: 'admin' })
      setPromoteTarget(null)
      await loadTeam()
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      setTeamMsg({
        type: 'err',
        text: msg === 'cannot_change_own_role' ? 'You cannot change your own role.'
          : 'Failed to change role.',
      })
      setPromoteTarget(null)
    } finally {
      setPromoting(false)
    }
  }

  async function doDeactivate() {
    if (!deactivateTarget) return
    setDeactivating(true); setTeamMsg(null)
    try {
      const res = await api.put<TeamMember>(`/api/users/${deactivateTarget.id}/deactivate`)
      setMembers(prev => prev.map(u => u.id === deactivateTarget.id ? res.data : u))
      setDeactivateTarget(null)
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      setTeamMsg({
        type: 'err',
        text: msg === 'last_admin' ? 'Cannot deactivate the last admin.' : 'Failed to deactivate user.',
      })
      setDeactivateTarget(null)
    } finally {
      setDeactivating(false)
    }
  }

  async function handleReactivate(m: TeamMember) {
    setOpenMenu(null); setTeamMsg(null)
    try {
      const res = await api.put<TeamMember>(`/api/users/${m.id}/reactivate`)
      setMembers(prev => prev.map(u => u.id === m.id ? res.data : u))
    } catch {
      setTeamMsg({ type: 'err', text: 'Failed to reactivate user.' })
    }
  }

  const myDomain = user?.email?.includes('@') ? user.email.split('@')[1].toLowerCase() : ''
  const inviteEmailTrimmed = inviteEmail.trim()
  const inviteDomainOk = !inviteEmailTrimmed.includes('@')
    || inviteEmailTrimmed.split('@')[1]?.toLowerCase() === myDomain
  const canInvite = !inviting && inviteEmailTrimmed.length > 0 && inviteDomainOk

  async function handleInvite(e: React.FormEvent) {
    e.preventDefault()
    if (!inviteEmailTrimmed) return
    setInviting(true); setInviteMsg(null)
    const sent = inviteEmailTrimmed
    try {
      await api.post('/api/users', { email: sent, role: inviteRole })
      setInviteEmail('')
      setInviteMsg({ type: 'ok', text: `Activation link sent to ${sent}.` })
      await loadTeam()
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } }).response?.status
      const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      setInviteMsg({
        type: 'err',
        text: msg === 'seat_limit_reached' ? 'Seat limit reached. Upgrade your plan to add more users.'
          : msg === 'email_domain_mismatch' ? `Only @${myDomain} addresses are allowed.`
          : status === 409 ? 'A user with this email already exists.'
          : status === 500 ? 'Invitation could not be sent. Check email configuration.'
          : 'Failed to send invite.',
      })
    } finally {
      setInviting(false)
    }
  }

  // ── Institution handlers ───────────────────────────────────────────────────

  async function handleSaveInstitution(e: React.FormEvent) {
    e.preventDefault()
    setSavingInst(true); setInstMsg(null)
    try {
      const res = await api.put<InstitutionData>('/api/settings/institution', {
        primaryContact: editContact,
        contactEmail: editEmail,
      })
      setInstitution(res.data)
      setInstMsg({ type: 'ok', text: 'Account info updated.' })
    } catch {
      setInstMsg({ type: 'err', text: 'Failed to save changes.' })
    } finally {
      setSavingInst(false)
    }
  }

  async function handleRevealKey() {
    try {
      const res = await api.get<{ key: string }>('/api/settings/api-key')
      setRevealedKey(res.data.key)
      setShowKey(true)
    } catch { /* no-op */ }
  }

  async function handleRotateKey() {
    setRotating(true)
    try {
      const res = await api.post<{ newKey: string }>('/api/settings/rotate-api-key')
      const k = res.data.newKey
      setNewKeyBanner(k)
      setRevealedKey(k)
      setShowKey(true)
      setShowRotate(false)
      setInstitution(prev => prev ? { ...prev, hasApiKey: true, maskedApiKey: 'aix_' + '•'.repeat(20) } : prev)
    } catch {
      setInstMsg({ type: 'err', text: 'Failed to rotate API key.' })
      setShowRotate(false)
    } finally {
      setRotating(false)
    }
  }

  async function copyKey(text: string) {
    await navigator.clipboard.writeText(text)
    setKeyCopied(true)
    setTimeout(() => setKeyCopied(false), 2000)
  }

  // ── Derived ───────────────────────────────────────────────────────────────

  const activeCount = members.filter(m => m.status === 'active').length

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div className="p-4 sm:p-8 max-w-4xl">
      <h1 className="text-xl font-semibold text-text mb-6">Settings</h1>

      <div className="flex flex-col sm:flex-row gap-6 sm:gap-8">
        {/* Tab nav — horizontal strip on mobile, vertical sidebar on sm+ */}
        <nav className="flex flex-row sm:flex-col sm:w-44 sm:shrink-0 sm:space-y-0.5 sm:pt-1 gap-1 sm:gap-0 border-b sm:border-b-0 pb-4 sm:pb-0 overflow-x-auto">
          {tabs.map(t => (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              className={`shrink-0 rounded-md px-3 py-2 text-sm transition-colors text-left whitespace-nowrap ${
                tab === t.id
                  ? 'bg-sapphire-soft text-sapphire font-medium'
                  : 'text-muted hover:bg-bone hover:text-text'
              }`}
            >
              {t.label}
            </button>
          ))}
        </nav>

        {/* Content */}
        <div className="flex-1 min-w-0 space-y-6">

          {/* ── My Account ─────────────────────────────────────────────────── */}
          {tab === 'account' && (
            <>
              <section className="rounded-xl border border-line bg-surface p-6">
                <h2 className="text-sm font-semibold text-text mb-4">Change Password</h2>
                <form onSubmit={handleChangePw} className="space-y-4 max-w-sm">
                  <div>
                    <label className="block text-sm font-medium text-text mb-1.5">Current password</label>
                    <input type="password" value={pwForm.current}
                      onChange={e => setPwForm(p => ({ ...p, current: e.target.value }))}
                      className="w-full rounded-md border border-line bg-bone px-3 py-2 text-sm text-text focus:outline-none focus:ring-2 focus:ring-sapphire"
                      required
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-text mb-1.5">New password</label>
                    <input type="password" value={pwForm.newPw}
                      onChange={e => setPwForm(p => ({ ...p, newPw: e.target.value }))}
                      className="w-full rounded-md border border-line bg-bone px-3 py-2 text-sm text-text focus:outline-none focus:ring-2 focus:ring-sapphire"
                      required
                    />
                    {newPw.length > 0 && (
                      <div className="mt-2 space-y-1">
                        <Hint ok={hasMinLen} text="At least 8 characters" />
                        <Hint ok={hasUppercase} text="At least 1 uppercase letter" />
                        <Hint ok={hasNumber} text="At least 1 number" />
                      </div>
                    )}
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-text mb-1.5">Confirm new password</label>
                    <input type="password" value={pwForm.confirm}
                      onChange={e => setPwForm(p => ({ ...p, confirm: e.target.value }))}
                      className="w-full rounded-md border border-line bg-bone px-3 py-2 text-sm text-text focus:outline-none focus:ring-2 focus:ring-sapphire"
                      required
                    />
                    {pwForm.confirm.length > 0 && !pwMatches && (
                      <p className="mt-1 text-xs text-danger">Passwords do not match.</p>
                    )}
                  </div>
                  {pwMsg && <p className={`text-sm ${pwMsg.type === 'ok' ? 'text-emerald' : 'text-danger'}`}>{pwMsg.text}</p>}
                  <button type="submit" disabled={pwLoading || !pwStrong || !pwMatches || !pwForm.current}
                    className="rounded-md bg-sapphire px-4 py-2 text-sm font-medium text-white hover:bg-sapphire-dark disabled:opacity-50 transition-colors"
                  >
                    {pwLoading ? 'Saving…' : 'Update Password'}
                  </button>
                </form>
              </section>

              <section className="rounded-xl border border-line bg-surface p-6">
                <h2 className="text-sm font-semibold text-text mb-4">Profile</h2>
                <form onSubmit={handleUpdateName} className="space-y-4 max-w-sm">
                  <div>
                    <label className="block text-sm font-medium text-text mb-1.5">Full name</label>
                    <input type="text" value={nameValue}
                      onChange={e => setNameValue(e.target.value)}
                      className="w-full rounded-md border border-line bg-bone px-3 py-2 text-sm text-text focus:outline-none focus:ring-2 focus:ring-sapphire"
                      required
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-text mb-1.5">Email</label>
                    <input type="text" value={user?.email ?? ''} readOnly
                      className="w-full rounded-md border border-line bg-bone px-3 py-2 text-sm text-muted cursor-not-allowed"
                    />
                    <p className="mt-1 text-xs text-muted">Email changes require administrator assistance.</p>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-text mb-1.5">Role</label>
                    <span className="inline-flex items-center rounded-full bg-sapphire-soft px-2.5 py-1 text-xs font-medium text-sapphire">
                      {user?.role === 'admin' ? 'Admin' : 'Read Only'}
                    </span>
                  </div>
                  {nameMsg && <p className={`text-sm ${nameMsg.type === 'ok' ? 'text-emerald' : 'text-danger'}`}>{nameMsg.text}</p>}
                  <button type="submit" disabled={nameLoading || !nameValue.trim()}
                    className="rounded-md bg-sapphire px-4 py-2 text-sm font-medium text-white hover:bg-sapphire-dark disabled:opacity-50 transition-colors"
                  >
                    {nameLoading ? 'Saving…' : 'Save Name'}
                  </button>
                </form>
              </section>

              <section className="rounded-xl border border-line bg-surface p-6">
                <h2 className="text-sm font-semibold text-text mb-2">Active Sessions</h2>
                <p className="text-sm text-muted">Session management coming soon.</p>
              </section>
            </>
          )}

          {/* ── Team ───────────────────────────────────────────────────────── */}
          {tab === 'team' && (
            <>
              {teamLoading && (
                <div className="flex justify-center py-16">
                  <Loader2 className="h-5 w-5 animate-spin text-muted" />
                </div>
              )}

              {!teamLoading && (
                <>
                  {/* Seats KPI card */}
                  {seatInfo && (
                    <section className="rounded-xl border border-line bg-surface p-5">
                      <div className="flex items-start justify-between mb-4">
                        <div>
                          <p className="text-xs font-medium text-muted uppercase tracking-wide mb-1">Seats</p>
                          <p className="text-3xl font-semibold font-mono text-text">
                            {activeCount}
                            <span className="text-base font-normal text-muted"> / {seatInfo.limit} used</span>
                          </p>
                        </div>
                        <div className={`flex h-9 w-9 items-center justify-center rounded-lg ${
                          activeCount >= seatInfo.limit ? 'bg-danger/10'
                            : activeCount / seatInfo.limit >= 0.8 ? 'bg-amber/10'
                            : 'bg-sapphire/5'
                        }`}>
                          <Users className={`h-4 w-4 ${
                            activeCount >= seatInfo.limit ? 'text-danger'
                              : activeCount / seatInfo.limit >= 0.8 ? 'text-amber'
                              : 'text-sapphire'
                          }`} />
                        </div>
                      </div>
                      <div className="h-1.5 rounded-full bg-bone overflow-hidden">
                        <div
                          className={`h-full rounded-full transition-all ${
                            activeCount >= seatInfo.limit ? 'bg-danger'
                              : activeCount / seatInfo.limit >= 0.8 ? 'bg-amber'
                              : 'bg-sapphire'
                          }`}
                          style={{ width: `${Math.min(100, (activeCount / seatInfo.limit) * 100)}%` }}
                        />
                      </div>
                    </section>
                  )}

                  {teamMsg && (
                    <div className={`flex items-center gap-2 rounded-md border px-3 py-2.5 text-sm ${
                      teamMsg.type === 'ok'
                        ? 'border-emerald/20 bg-emerald/5 text-emerald'
                        : 'border-danger/20 bg-danger/5 text-danger'
                    }`}>
                      {teamMsg.text}
                      <button className="ml-auto shrink-0 text-xs underline opacity-70" onClick={() => setTeamMsg(null)}>
                        Dismiss
                      </button>
                    </div>
                  )}

                  {/* Member cards — same pattern as CustomersPage */}
                  <section>
                    <h2 className="text-sm font-semibold text-text mb-3">Team Members</h2>
                    <div className="space-y-3">
                      {members.map(m => (
                        <div
                          key={m.id}
                          className="rounded-xl border border-line bg-surface"
                          style={{ borderLeft: `4px solid ${memberBorderColor(m.status)}` }}
                        >
                          <div className="flex items-start justify-between gap-4 px-5 pt-4 pb-3">
                            <div className="min-w-0">
                              <div className="flex items-center gap-2 flex-wrap">
                                <span className="font-semibold text-text">{m.fullName}</span>
                                <RoleBadge role={m.role} />
                                <StatusBadge status={m.status} />
                                {m.id === user?.id && (
                                  <span className="rounded-full border border-line px-2 py-0.5 text-xs text-muted">You</span>
                                )}
                              </div>
                              <p className="text-sm text-muted mt-0.5">{m.email}</p>
                            </div>
                            {m.id !== user?.id && (
                              <MemberMenu m={m} openMenu={openMenu} setOpenMenu={setOpenMenu}
                                onPromote={() => { setOpenMenu(null); setPromoteTarget(m) }}
                                onChangeRole={(r) => handleChangeRole(m, r)}
                                onDeactivate={() => { setOpenMenu(null); setDeactivateTarget(m) }}
                                onReactivate={() => handleReactivate(m)}
                              />
                            )}
                          </div>
                        </div>
                      ))}
                      {members.length === 0 && (
                        <div className="rounded-xl border border-line bg-surface px-6 py-10 text-center text-sm text-muted">
                          No team members yet.
                        </div>
                      )}
                    </div>
                  </section>

                  {/* Invite section */}
                  <section className="rounded-xl border border-line bg-surface p-6">
                    <div className="flex items-center gap-2.5 mb-4">
                      <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-sapphire/10">
                        <UserPlus className="h-4 w-4 text-sapphire" />
                      </div>
                      <h2 className="text-sm font-semibold text-text">Invite Team Member</h2>
                    </div>
                    <form onSubmit={handleInvite} className="space-y-3">
                      <div className="flex flex-wrap items-start gap-3">
                        <div className="flex-1 min-w-48">
                          <label className="block text-xs font-medium text-muted mb-1.5">Email address</label>
                          <input type="email" value={inviteEmail}
                            onChange={e => { setInviteEmail(e.target.value); setInviteMsg(null) }}
                            placeholder={myDomain ? `colleague@${myDomain}` : 'colleague@university.edu'}
                            className={`w-full rounded-md border px-3 py-2 text-sm text-text placeholder:text-muted focus:outline-none focus:ring-2 focus:ring-sapphire ${
                              inviteEmailTrimmed && !inviteDomainOk
                                ? 'border-danger bg-danger/5'
                                : 'border-line bg-bone'
                            }`}
                            required
                          />
                        </div>
                        <div>
                          <label className="block text-xs font-medium text-muted mb-1.5">Role</label>
                          <div className="rounded-md border border-line bg-bone px-3 py-2 text-sm text-muted whitespace-nowrap">
                            Read Only
                          </div>
                        </div>
                        <button type="submit" disabled={!canInvite}
                          className="mt-[1.375rem] rounded-md bg-sapphire px-4 py-2 text-sm font-medium text-white hover:bg-sapphire-dark disabled:opacity-50 transition-colors whitespace-nowrap"
                        >
                          {inviting ? 'Sending…' : 'Send Invite'}
                        </button>
                      </div>
                      {inviteEmailTrimmed && !inviteDomainOk && (
                        <p className="text-xs text-danger">Email must end in @{myDomain}</p>
                      )}
                      {myDomain && !inviteEmailTrimmed && (
                        <p className="text-xs text-muted">
                          Only <span className="font-medium text-text">@{myDomain}</span> addresses allowed.
                        </p>
                      )}
                      {inviteMsg && (
                        <div className={`rounded-md border px-3 py-2.5 text-sm ${
                          inviteMsg.type === 'ok'
                            ? 'border-emerald/20 bg-emerald/5 text-emerald'
                            : 'border-danger/20 bg-danger/5 text-danger'
                        }`}>
                          {inviteMsg.text}
                        </div>
                      )}
                    </form>
                    <p className="mt-3 text-xs text-muted">Activation link expires in 24 hours.</p>
                  </section>
                </>
              )}
            </>
          )}

          {/* ── Institution ────────────────────────────────────────────────── */}
          {tab === 'institution' && (
            <>
              {instLoading && <p className="py-12 text-center text-sm text-muted">Loading…</p>}

              {!instLoading && institution && (
                <>
                  <section className="rounded-xl border border-line bg-surface p-6">
                    <h2 className="text-sm font-semibold text-text mb-4">Account Info</h2>
                    <form onSubmit={handleSaveInstitution} className="space-y-4 max-w-sm">
                      <div>
                        <label className="block text-sm font-medium text-text mb-1.5">Institution</label>
                        <input type="text" value={institution.institutionName} readOnly
                          className="w-full rounded-md border border-line bg-bone px-3 py-2 text-sm text-muted cursor-not-allowed"
                        />
                        <p className="mt-1 text-xs text-muted">Managed by the platform operator.</p>
                      </div>
                      <div>
                        <label className="block text-sm font-medium text-text mb-1.5">Primary contact name</label>
                        <input type="text" value={editContact}
                          onChange={e => setEditContact(e.target.value)}
                          className="w-full rounded-md border border-line bg-bone px-3 py-2 text-sm text-text focus:outline-none focus:ring-2 focus:ring-sapphire"
                        />
                      </div>
                      <div>
                        <label className="block text-sm font-medium text-text mb-1.5">Contact email</label>
                        <input type="email" value={editEmail}
                          onChange={e => setEditEmail(e.target.value)}
                          className="w-full rounded-md border border-line bg-bone px-3 py-2 text-sm text-text focus:outline-none focus:ring-2 focus:ring-sapphire"
                        />
                      </div>
                      {instMsg && (
                        <p className={`text-sm ${instMsg.type === 'ok' ? 'text-emerald' : 'text-danger'}`}>{instMsg.text}</p>
                      )}
                      <button type="submit" disabled={savingInst}
                        className="rounded-md bg-sapphire px-4 py-2 text-sm font-medium text-white hover:bg-sapphire-dark disabled:opacity-50 transition-colors"
                      >
                        {savingInst ? 'Saving…' : 'Save Changes'}
                      </button>
                    </form>
                  </section>

                  <section className="rounded-xl border border-line bg-surface p-6">
                    <div className="flex items-center gap-2 mb-4">
                      <Calendar className="h-4 w-4 text-muted" />
                      <h2 className="text-sm font-semibold text-text">Subscription</h2>
                    </div>
                    <SubscriptionDisplay data={institution} />
                  </section>

                  <section className="rounded-xl border border-line bg-surface p-6">
                    <div className="flex items-center gap-2 mb-1">
                      <Key className="h-4 w-4 text-muted" />
                      <h2 className="text-sm font-semibold text-text">API Key</h2>
                    </div>
                    <p className="text-xs text-muted mb-4">
                      Use this key to authenticate requests to the AlumIndex API.
                    </p>

                    {newKeyBanner && (
                      <div className="mb-4 rounded-md border border-emerald/30 bg-emerald/10 p-3">
                        <p className="mb-1.5 text-xs font-medium text-emerald">
                          New key generated — copy it now. It will not be shown again after leaving this page.
                        </p>
                        <div className="flex items-center gap-2">
                          <code className="flex-1 overflow-x-auto rounded bg-bone px-2 py-1 font-mono text-xs text-text">
                            {newKeyBanner}
                          </code>
                          <button onClick={() => copyKey(newKeyBanner)}
                            className="shrink-0 rounded p-1 text-muted hover:bg-bone hover:text-sapphire transition-colors"
                            title="Copy"
                          >
                            {keyCopied ? <Check className="h-4 w-4 text-emerald" /> : <Copy className="h-4 w-4" />}
                          </button>
                        </div>
                      </div>
                    )}

                    {institution.hasApiKey && !newKeyBanner && (
                      <div className="mb-4 flex items-center gap-2">
                        <code className="flex-1 overflow-x-auto rounded-md border border-line bg-bone px-3 py-2 font-mono text-sm text-text">
                          {showKey && revealedKey ? revealedKey : (institution.maskedApiKey ?? 'aix_' + '•'.repeat(20))}
                        </code>
                        <button
                          onClick={() => showKey ? setShowKey(false) : handleRevealKey()}
                          className="shrink-0 rounded p-1.5 text-muted hover:bg-bone hover:text-text transition-colors"
                          title={showKey ? 'Hide' : 'Reveal'}
                        >
                          {showKey ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                        </button>
                        {showKey && revealedKey && (
                          <button onClick={() => copyKey(revealedKey)}
                            className="shrink-0 rounded p-1.5 text-muted hover:bg-bone hover:text-sapphire transition-colors"
                            title="Copy"
                          >
                            {keyCopied ? <Check className="h-4 w-4 text-emerald" /> : <Copy className="h-4 w-4" />}
                          </button>
                        )}
                      </div>
                    )}

                    {!institution.hasApiKey && !newKeyBanner && (
                      <p className="mb-4 text-sm text-muted">No API key generated yet.</p>
                    )}

                    {showRotate ? (
                      <div className="rounded-md border border-amber/40 bg-amber/10 p-3">
                        <p className="mb-3 text-sm text-amber">
                          This will immediately invalidate the current key. Any existing integrations will stop working.
                        </p>
                        <div className="flex gap-2">
                          <button onClick={() => setShowRotate(false)}
                            className="rounded-md border border-line px-3 py-1.5 text-xs text-muted hover:bg-bone transition-colors"
                          >
                            Cancel
                          </button>
                          <button onClick={handleRotateKey} disabled={rotating}
                            className="rounded-md bg-amber px-3 py-1.5 text-xs font-medium text-white hover:opacity-90 disabled:opacity-50 transition-colors"
                          >
                            {rotating ? 'Rotating…' : 'Confirm Rotate'}
                          </button>
                        </div>
                      </div>
                    ) : (
                      <button
                        onClick={() => { setShowRotate(true); setNewKeyBanner(null) }}
                        className="flex items-center gap-1.5 rounded-md border border-line px-3 py-1.5 text-sm text-muted hover:bg-bone hover:text-text transition-colors"
                      >
                        <RefreshCw className="h-3.5 w-3.5" />
                        {institution.hasApiKey ? 'Rotate Key' : 'Generate Key'}
                      </button>
                    )}
                  </section>
                </>
              )}

              {!instLoading && !institution && (
                <p className="text-sm text-danger">
                  Failed to load institution data. Please refresh and try again.
                </p>
              )}
            </>
          )}

        </div>
      </div>

      {/* Promote to Admin confirmation modal */}
      {promoteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
          <div className="mx-4 w-full max-w-sm rounded-xl border border-line bg-surface p-6 shadow-xl">
            <div className="mb-4 flex items-center gap-3">
              <AlertTriangle className="h-5 w-5 shrink-0 text-amber" />
              <h3 className="text-sm font-semibold text-text">Change admin role?</h3>
            </div>
            <p className="mb-6 text-sm text-muted">
              <span className="font-medium text-text">{promoteTarget.fullName}</span> will become Admin.{' '}
              <span className="font-medium text-text">{user?.fullName ?? 'You'}</span> will be downgraded to Read Only.{' '}
              Only one Admin is allowed per institution.
            </p>
            <div className="flex justify-end gap-3">
              <button onClick={() => setPromoteTarget(null)}
                className="rounded-md border border-line px-4 py-2 text-sm text-muted hover:bg-bone transition-colors"
              >
                Cancel
              </button>
              <button onClick={doPromote} disabled={promoting}
                className="rounded-md bg-sapphire px-4 py-2 text-sm font-medium text-white hover:bg-sapphire-dark disabled:opacity-50 transition-colors"
              >
                {promoting ? 'Changing…' : 'Confirm'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Deactivate confirmation modal */}
      {deactivateTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
          <div className="mx-4 w-full max-w-sm rounded-xl border border-line bg-surface p-6 shadow-xl">
            <div className="mb-4 flex items-center gap-3">
              <AlertTriangle className="h-5 w-5 shrink-0 text-amber" />
              <h3 className="text-sm font-semibold text-text">Deactivate user?</h3>
            </div>
            <p className="mb-6 text-sm text-muted">
              <span className="font-medium text-text">{deactivateTarget.fullName}</span> will lose
              access immediately. You can reactivate them at any time.
            </p>
            <div className="flex justify-end gap-3">
              <button onClick={() => setDeactivateTarget(null)}
                className="rounded-md border border-line px-4 py-2 text-sm text-muted hover:bg-bone transition-colors"
              >
                Cancel
              </button>
              <button onClick={doDeactivate} disabled={deactivating}
                className="rounded-md bg-danger px-4 py-2 text-sm font-medium text-white hover:opacity-90 disabled:opacity-50 transition-colors"
              >
                {deactivating ? 'Deactivating…' : 'Deactivate'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

// ─── Subscription display ─────────────────────────────────────────────────────

function SubscriptionDisplay({ data }: { data: InstitutionData }) {
  const days = daysUntil(data.subscriptionEnd)
  const status = data.subscriptionStatus

  let badge: React.ReactNode
  if (status === 'suspended') {
    badge = <span className="rounded-full bg-amber/10 px-2.5 py-1 text-xs font-medium text-amber">Suspended</span>
  } else if (status === 'pending') {
    badge = <span className="rounded-full bg-gold-soft px-2.5 py-1 text-xs font-medium text-gold">Pending Approval</span>
  } else if (status === 'denied') {
    badge = <span className="rounded-full bg-danger/10 px-2.5 py-1 text-xs font-medium text-danger">Denied</span>
  } else if (status === 'trial') {
    badge = <span className="rounded-full bg-gold-soft px-2.5 py-1 text-xs font-medium text-gold">Trial</span>
  } else {
    badge = <span className="rounded-full bg-emerald/10 px-2.5 py-1 text-xs font-medium text-emerald">Active</span>
  }

  const startDate = data.subscriptionStart
    ? new Date(data.subscriptionStart).toLocaleDateString('en-MY', { day: 'numeric', month: 'long', year: 'numeric' })
    : null

  let expiryNode: React.ReactNode = null
  if (data.subscriptionEnd) {
    const endFormatted = new Date(data.subscriptionEnd).toLocaleDateString('en-MY', {
      day: 'numeric', month: 'long', year: 'numeric',
    })
    if (days !== null && days <= 0) {
      expiryNode = <span className="text-sm font-medium text-danger">Expired on {endFormatted}</span>
    } else if (days !== null && days <= 7) {
      expiryNode = <span className="text-sm font-medium text-danger">Expires in {days} day{days !== 1 ? 's' : ''} — {endFormatted}</span>
    } else if (days !== null && days <= 30) {
      expiryNode = <span className="text-sm text-amber">Expires in {days} days — {endFormatted}</span>
    } else if (days !== null) {
      expiryNode = <span className="text-sm text-muted">Expires {endFormatted} ({days} days remaining)</span>
    }
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-3">
        <span className="w-16 shrink-0 text-sm text-muted">Status</span>
        {badge}
      </div>
      {startDate && (
        <div className="flex items-center gap-3">
          <span className="w-16 shrink-0 text-sm text-muted">Started</span>
          <span className="text-sm text-text">{startDate}</span>
        </div>
      )}
      {expiryNode && (
        <div className="flex items-center gap-3">
          <span className="w-16 shrink-0 text-sm text-muted">Expiry</span>
          {expiryNode}
        </div>
      )}
      <div className="flex items-center gap-3">
        <span className="w-16 shrink-0 text-sm text-muted">Seats</span>
        <span className="text-sm text-text">{data.seatLimit} total</span>
      </div>
    </div>
  )
}
