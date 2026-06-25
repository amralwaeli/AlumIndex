import { useState, useEffect, useCallback } from 'react'
import api from '@/lib/api'
import { Search, X, ChevronRight, Loader2, AlertCircle, Clock, Info } from 'lucide-react'
import { TableSkeleton, LoadError } from '@/components/ui/Skeleton'
import type { Alumni, AlumniHistory } from '@/types'

interface AlumniPage {
  content: Alumni[]
  totalElements: number
  totalPages: number
  number: number
}

const SENIORITY_OPTIONS = ['', 'Junior','Mid','Senior','Lead','Manager','Director','VP','C-Suite']
const INDUSTRY_OPTIONS  = ['', 'Technology','Finance','Healthcare','Education','Government','Energy','Consulting','Other']

function SeniorityBadge({ seniority }: { seniority: string | null }) {
  if (!seniority) return null
  const colors: Record<string, string> = {
    'C-Suite': 'bg-violet/10 text-violet',
    'VP':      'bg-sapphire/10 text-sapphire',
    'Director':'bg-sapphire/10 text-sapphire',
    'Manager': 'bg-emerald/10 text-emerald',
    'Lead':    'bg-gold/10 text-gold',
  }
  return (
    <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${colors[seniority] ?? 'bg-line text-muted'}`}>
      {seniority}
    </span>
  )
}

export default function AlumniPage() {
  const [query, setQuery] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')
  const [seniority, setSeniority] = useState('')
  const [industry, setIndustry] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<AlumniPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [selected, setSelected] = useState<string | null>(null)
  const [history, setHistory] = useState<AlumniHistory | null>(null)
  const [histLoading, setHistLoading] = useState(false)
  const [anonymising, setAnonymising] = useState<string | null>(null)

  // Debounce search input — one request per pause, not per keystroke
  useEffect(() => {
    const t = setTimeout(() => setDebouncedQuery(query), 300)
    return () => clearTimeout(t)
  }, [query])

  const load = useCallback(async () => {
    setLoading(true)
    setLoadError(false)
    try {
      const params = new URLSearchParams({ page: String(page) })
      if (debouncedQuery) params.set('query', debouncedQuery)
      if (seniority) params.set('seniority', seniority)
      if (industry)  params.set('industry', industry)
      const { data: d } = await api.get<AlumniPage>(`/api/alumni?${params}`)
      setData(d)
    } catch {
      setLoadError(true)
    } finally {
      setLoading(false)
    }
  }, [debouncedQuery, seniority, industry, page])

  useEffect(() => { load() }, [load])

  async function openProfile(id: string) {
    setSelected(id)
    setHistLoading(true)
    try {
      const { data: h } = await api.get<AlumniHistory>(`/api/alumni/${id}/history`)
      setHistory(h)
    } finally {
      setHistLoading(false)
    }
  }

  async function anonymise(id: string) {
    if (!confirm('Anonymise this alumni? This cannot be undone.')) return
    setAnonymising(id)
    try {
      await api.put(`/api/alumni/${id}/anonymise`)
      setSelected(null)
      await load()
    } finally {
      setAnonymising(null)
    }
  }

  return (
    <div className="flex h-full">
      {/* Main table area */}
      <div className={`flex flex-col flex-1 overflow-hidden transition-all ${selected ? 'mr-96' : ''}`}>
        <div className="p-8 border-b border-line bg-surface">
          <h1 className="text-xl font-semibold text-text mb-4">Alumni Database</h1>
          {/* Search & filters */}
          <div className="flex gap-3 flex-wrap">
            <div className="relative flex-1 min-w-48">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted" />
              <input
                type="text"
                placeholder="Search by name, employer…"
                value={query}
                onChange={(e) => { setQuery(e.target.value); setPage(0) }}
                className="w-full rounded-md border border-line bg-bone pl-9 pr-3 py-2 text-sm text-text placeholder:text-muted focus:outline-none focus:ring-2 focus:ring-sapphire"
              />
            </div>
            <select
              value={seniority}
              onChange={(e) => { setSeniority(e.target.value); setPage(0) }}
              className="rounded-md border border-line bg-bone px-3 py-2 text-sm text-text focus:outline-none focus:ring-2 focus:ring-sapphire"
            >
              <option value="">All seniority</option>
              {SENIORITY_OPTIONS.filter(Boolean).map(s => <option key={s} value={s}>{s}</option>)}
            </select>
            <select
              value={industry}
              onChange={(e) => { setIndustry(e.target.value); setPage(0) }}
              className="rounded-md border border-line bg-bone px-3 py-2 text-sm text-text focus:outline-none focus:ring-2 focus:ring-sapphire"
            >
              <option value="">All industries</option>
              {INDUSTRY_OPTIONS.filter(Boolean).map(i => <option key={i} value={i}>{i}</option>)}
            </select>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto">
          {loading ? (
            <TableSkeleton rows={10} cols={4} />
          ) : loadError ? (
            <LoadError message="Failed to load alumni." onRetry={load} />
          ) : (
            <>
              <table className="w-full text-sm">
                <thead className="sticky top-0 bg-surface border-b border-line">
                  <tr className="text-xs text-muted uppercase tracking-wide">
                    <th className="text-left px-6 py-3 font-medium">Name</th>
                    <th className="text-left px-6 py-3 font-medium">Employer</th>
                    <th className="text-left px-6 py-3 font-medium">Seniority</th>
                    <th className="text-left px-6 py-3 font-medium">Industry</th>
                    <th className="w-8 px-3 py-3" />
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {data?.content.map((a) => (
                    <tr
                      key={a.id}
                      onClick={() => openProfile(a.id)}
                      className={`cursor-pointer hover:bg-bone transition-colors ${selected === a.id ? 'bg-sapphire-soft' : ''}`}
                    >
                      <td className="px-6 py-3.5 font-medium text-text">{a.fullName}</td>
                      <td className="px-6 py-3.5 text-muted">{a.profile?.employer ?? '—'}</td>
                      <td className="px-6 py-3.5">
                        <SeniorityBadge seniority={a.profile?.seniority ?? null} />
                      </td>
                      <td className="px-6 py-3.5 text-muted">{a.profile?.industry ?? '—'}</td>
                      <td className="px-3 py-3.5 text-muted"><ChevronRight className="h-4 w-4" /></td>
                    </tr>
                  ))}
                  {!data?.content.length && (
                    <tr><td colSpan={5} className="py-12 text-center text-muted">No alumni found.</td></tr>
                  )}
                </tbody>
              </table>

              {/* Pagination */}
              {data && data.totalPages > 1 && (
                <div className="flex items-center justify-between px-6 py-4 border-t border-line">
                  <p className="text-xs text-muted">
                    {data.totalElements.toLocaleString()} alumni
                  </p>
                  <div className="flex gap-2">
                    <button
                      onClick={() => setPage(p => Math.max(0, p - 1))}
                      disabled={page === 0}
                      className="rounded-md border border-line px-3 py-1.5 text-xs text-muted hover:text-text disabled:opacity-40"
                    >
                      Previous
                    </button>
                    <span className="rounded-md border border-line px-3 py-1.5 text-xs text-text">
                      {page + 1} / {data.totalPages}
                    </span>
                    <button
                      onClick={() => setPage(p => p + 1)}
                      disabled={page >= data.totalPages - 1}
                      className="rounded-md border border-line px-3 py-1.5 text-xs text-muted hover:text-text disabled:opacity-40"
                    >
                      Next
                    </button>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {/* Profile drawer — Career Trajectory signature */}
      {selected && (
        <div className="fixed right-0 top-0 h-full w-96 border-l border-line bg-surface shadow-2xl overflow-y-auto z-40">
          <div className="flex items-center justify-between p-5 border-b border-line">
            <h2 className="text-sm font-semibold text-text">Alumni Profile</h2>
            <button onClick={() => setSelected(null)} className="text-muted hover:text-text">
              <X className="h-5 w-5" />
            </button>
          </div>

          {histLoading ? (
            <div className="flex justify-center py-16"><Loader2 className="h-5 w-5 animate-spin text-muted" /></div>
          ) : history ? (
            <div className="p-5 space-y-6">
              {/* Identity */}
              <div>
                <h3 className="text-lg font-semibold text-text">{history.alumni.fullName}</h3>
                {history.profile?.employer && (
                  <p className="text-sm text-muted mt-0.5">{history.profile.employer}</p>
                )}
                {history.profile?.jobTitle && (
                  <p className="text-sm text-muted">{history.profile.jobTitle}</p>
                )}
                <div className="flex gap-2 mt-2 flex-wrap">
                  <SeniorityBadge seniority={history.profile?.seniority ?? null} />
                  {history.profile?.industry && (
                    <span className="text-xs text-muted bg-bone rounded-full px-2 py-0.5">
                      {history.profile.industry}
                    </span>
                  )}
                  {history.profile?.location && (
                    <span className="text-xs text-muted bg-bone rounded-full px-2 py-0.5">
                      {history.profile.location}
                    </span>
                  )}
                </div>
              </div>

              {/* Career Trajectory — the signature feature (UC010: snapshots + events) */}
              <div>
                <h4 className="text-xs font-mono text-muted uppercase tracking-widest mb-3">
                  Career Trajectory
                </h4>
                {history.events.length === 0 && history.snapshots.length <= 1 ? (
                  <p className="text-xs text-muted leading-relaxed">
                    No career changes recorded yet
                    {history.snapshots.length === 1 && (
                      <> — single snapshot captured{' '}
                        {new Date(history.snapshots[0].capturedAt).toLocaleDateString('en-GB', {
                          day: 'numeric', month: 'short', year: 'numeric',
                        })}
                      </>
                    )}
                    . Changes will appear here when future imports detect updates.
                  </p>
                ) : (
                  <div className="relative pl-4">
                    <div className="absolute left-0 top-0 bottom-0 w-px bg-line" />
                    <div className="space-y-4">
                      {history.events.map((ev) => (
                        <div key={ev.id} className="relative">
                          <div className={`absolute -left-[17px] top-1.5 h-2.5 w-2.5 rounded-full border-2 border-surface ${
                            ev.significanceLevel === 'high' ? 'bg-danger' :
                            ev.significanceLevel === 'medium' ? 'bg-amber' : 'bg-muted'
                          }`} />
                          <div>
                            <p className="text-xs font-medium text-text capitalize">
                              {ev.eventType.replace(/_/g, ' ')}
                            </p>
                            {ev.oldValue && ev.newValue && (
                              <p className="text-xs text-muted mt-0.5">
                                {ev.oldValue} <span className="text-sapphire">→</span> {ev.newValue}
                              </p>
                            )}
                            {!ev.oldValue && ev.newValue && (
                              <p className="text-xs text-muted mt-0.5">{ev.newValue}</p>
                            )}
                            <p className="text-xs text-muted mt-0.5">
                              {new Date(ev.detectedAt).toLocaleDateString('en-GB', { month: 'short', year: 'numeric' })}
                            </p>
                          </div>
                        </div>
                      ))}
                      {history.snapshots.map((s, i) => (
                        <div key={s.id} className="relative">
                          <div className="absolute -left-[17px] top-1.5 h-2.5 w-2.5 rounded-full border-2 border-surface bg-sapphire/60" />
                          <div>
                            <p className="text-xs font-medium text-text">
                              {i === 0 ? 'First snapshot' : 'Snapshot'}
                            </p>
                            {(s.extractedFields?.employer != null || s.extractedFields?.job_title != null) && (
                              <p className="text-xs text-muted mt-0.5">
                                {[s.extractedFields?.job_title, s.extractedFields?.employer]
                                  .filter(Boolean).join(' · ')}
                              </p>
                            )}
                            <p className="text-xs text-muted mt-0.5">
                              {new Date(s.capturedAt).toLocaleDateString('en-GB', { month: 'short', year: 'numeric' })}
                            </p>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              {/* Confidence score — with explainer tooltip (Issue 7) */}
              {history.profile?.confidenceScore != null && (
                <div>
                  <div className="flex justify-between text-xs mb-1.5">
                    <span className="group relative flex items-center gap-1 text-muted">
                      Data Confidence
                      <Info className="h-3 w-3 cursor-help" />
                      <span className="pointer-events-none absolute bottom-full left-0 mb-1.5 w-56 rounded-md border border-line bg-surface p-2.5 text-[11px] leading-relaxed text-muted opacity-0 shadow-lg transition-opacity group-hover:opacity-100 z-10">
                        How certain we are that this record matches the right person.
                        Based on LinkedIn URL match, name + graduation year, and source agreement.
                      </span>
                    </span>
                    <span className="text-text font-medium">
                      {Math.round(history.profile.confidenceScore * 100)}%
                    </span>
                  </div>
                  <div className="h-1.5 rounded-full bg-bone overflow-hidden">
                    <div
                      className="h-full rounded-full bg-sapphire transition-all"
                      style={{ width: `${Math.round(history.profile.confidenceScore * 100)}%` }}
                    />
                  </div>
                </div>
              )}

              {/* Freshness (Issue 7) */}
              {history.profile?.updatedAt && (
                <div className="flex items-center gap-1.5 text-xs text-muted">
                  <Clock className="h-3 w-3" />
                  Last updated{' '}
                  {new Date(history.profile.updatedAt).toLocaleDateString('en-GB', {
                    day: 'numeric', month: 'short', year: 'numeric',
                  })}
                </div>
              )}

              {/* Donor block */}
              {history.profile?.seniority &&
                ['Director','VP','C-Suite'].includes(history.profile.seniority) && (
                  <div className="rounded-lg border border-gold/40 bg-gold-soft p-4">
                    <div className="flex items-start gap-2">
                      <AlertCircle className="h-4 w-4 text-gold mt-0.5 shrink-0" />
                      <div>
                        <p className="text-xs font-medium text-gold">High-Value Prospect</p>
                        <p className="text-xs text-muted mt-0.5">
                          {history.profile.seniority} seniority. View Donor Insights for full analysis.
                        </p>
                      </div>
                    </div>
                  </div>
              )}

              {/* Anonymise button */}
              <div className="pt-2 border-t border-line">
                <button
                  onClick={() => anonymise(history.alumni.id)}
                  disabled={anonymising === history.alumni.id}
                  className="w-full rounded-md border border-danger/30 px-4 py-2 text-sm text-danger hover:bg-danger/5 disabled:opacity-50 transition-colors"
                >
                  {anonymising === history.alumni.id ? 'Anonymising…' : 'Anonymise this record'}
                </button>
              </div>
            </div>
          ) : null}
        </div>
      )}
    </div>
  )
}
