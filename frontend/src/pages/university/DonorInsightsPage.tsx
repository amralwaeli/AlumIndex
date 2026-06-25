import { useState, useEffect, useCallback } from 'react'
import api from '@/lib/api'
import { TrendingUp, Lock } from 'lucide-react'
import { TableSkeleton, LoadError } from '@/components/ui/Skeleton'
import type { DonorInsight } from '@/types'

const SORT_OPTIONS = [
  { value: 'likelihood', label: 'Giving Likelihood' },
  { value: 'capacity',   label: 'Estimated Capacity' },
]

function WealthBadge({ level }: { level: string }) {
  const map: Record<string, string> = {
    high:   'bg-violet/10 text-violet',
    medium: 'bg-gold/10 text-gold',
    low:    'bg-line text-muted',
  }
  return (
    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${map[level] ?? 'bg-line text-muted'}`}>
      {level}
    </span>
  )
}

function LikelihoodBar({ value }: { value: number }) {
  const color = value >= 80 ? 'bg-emerald' : value >= 60 ? 'bg-gold' : value >= 40 ? 'bg-amber' : 'bg-muted'
  return (
    <div className="flex items-center gap-2">
      <div className="h-1.5 w-20 rounded-full bg-bone overflow-hidden">
        <div className={`h-full rounded-full ${color}`} style={{ width: `${value}%` }} />
      </div>
      <span className="text-xs text-muted">{value}%</span>
    </div>
  )
}

function LockedCell() {
  return (
    <span className="flex items-center gap-1 text-xs text-muted">
      <Lock className="h-3.5 w-3.5" /> Locked
    </span>
  )
}

function formatCapacity(min: number, max: number): string {
  if (!min || isNaN(min) || !max || isNaN(max)) return '—'
  return `RM ${min.toLocaleString()} – RM ${max.toLocaleString()}`
}

function getApproach(likelihood: number): string {
  if (likelihood > 80) return 'Personal outreach — high priority'
  if (likelihood >= 50) return 'Targeted campaign'
  return 'Annual newsletter'
}

type WealthFilter     = 'all' | 'high' | 'medium' | 'low'
type CorpMatchFilter  = 'all' | 'yes' | 'no'
type LikelihoodFilter = 0 | 40 | 60 | 80

export default function DonorInsightsPage() {
  const [donors,  setDonors]  = useState<DonorInsight[]>([])
  const [sort,    setSort]    = useState('likelihood')
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)

  const [filterWealth,      setFilterWealth]      = useState<WealthFilter>('all')
  const [filterLikelihood,  setFilterLikelihood]  = useState<LikelihoodFilter>(0)
  const [filterCorpMatch,   setFilterCorpMatch]   = useState<CorpMatchFilter>('all')

  const load = useCallback(() => {
    setLoading(true)
    setLoadError(false)
    api.get<DonorInsight[]>(`/api/donors?sort=${sort}`)
      .then(res => setDonors(res.data))
      .catch(() => setLoadError(true))
      .finally(() => setLoading(false))
  }, [sort])

  useEffect(() => { load() }, [load])

  const filtered = donors.filter(d => {
    if (filterWealth !== 'all' && d.wealthIndicator !== filterWealth) return false
    if (filterLikelihood > 0) {
      if (d.givingLikelihood < 0 || d.givingLikelihood < filterLikelihood) return false
    }
    if (filterCorpMatch === 'yes' && !d.employerMatchingAvailable) return false
    if (filterCorpMatch === 'no'  &&  d.employerMatchingAvailable) return false
    return true
  })

  const filtersActive = filterWealth !== 'all' || filterLikelihood !== 0 || filterCorpMatch !== 'all'

  function resetFilters() {
    setFilterWealth('all')
    setFilterLikelihood(0)
    setFilterCorpMatch('all')
  }

  return (
    <div className="p-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div>
          <h1 className="text-xl font-semibold text-text">Donor Insights</h1>
          <p className="text-sm text-muted mt-1">AI-ranked alumni by giving likelihood and estimated capacity.</p>
        </div>
        <select
          value={sort}
          onChange={(e) => setSort(e.target.value)}
          className="rounded-md border border-line bg-bone px-3 py-2 text-sm text-text focus:outline-none focus:ring-2 focus:ring-sapphire"
        >
          {SORT_OPTIONS.map(o => (
            <option key={o.value} value={o.value}>
              {o.value === sort ? `Sort: ${o.label}` : o.label}
            </option>
          ))}
        </select>
      </div>

      {/* Filter bar */}
      <div className="flex flex-wrap items-center gap-2 mb-4">
        <select
          value={filterWealth}
          onChange={e => setFilterWealth(e.target.value as WealthFilter)}
          className="rounded-md border border-line bg-bone px-3 py-2 text-sm text-text focus:outline-none focus:ring-2 focus:ring-sapphire"
        >
          <option value="all">All wealth levels</option>
          <option value="high">High</option>
          <option value="medium">Medium</option>
          <option value="low">Low</option>
        </select>

        <select
          value={String(filterLikelihood)}
          onChange={e => setFilterLikelihood(Number(e.target.value) as LikelihoodFilter)}
          className="rounded-md border border-line bg-bone px-3 py-2 text-sm text-text focus:outline-none focus:ring-2 focus:ring-sapphire"
        >
          <option value="0">Any likelihood</option>
          <option value="80">≥ 80% likelihood</option>
          <option value="60">≥ 60% likelihood</option>
          <option value="40">≥ 40% likelihood</option>
        </select>

        <select
          value={filterCorpMatch}
          onChange={e => setFilterCorpMatch(e.target.value as CorpMatchFilter)}
          className="rounded-md border border-line bg-bone px-3 py-2 text-sm text-text focus:outline-none focus:ring-2 focus:ring-sapphire"
        >
          <option value="all">Corp match: all</option>
          <option value="yes">Corp match: yes</option>
          <option value="no">Corp match: no</option>
        </select>

        {filtersActive && (
          <>
            <span className="text-xs text-muted">
              {filtered.length} of {donors.length} shown
            </span>
            <button
              onClick={resetFilters}
              className="text-xs text-sapphire hover:underline"
            >
              Clear filters
            </button>
          </>
        )}
      </div>

      {loading ? (
        <TableSkeleton rows={8} cols={5} />
      ) : loadError ? (
        <LoadError message="Failed to load donor insights." onRetry={load} />
      ) : (
        <div className="rounded-xl border border-line bg-surface overflow-hidden">
          <table className="w-full text-sm">
            <thead className="border-b border-line bg-bone">
              <tr className="text-xs text-muted uppercase tracking-wide">
                <th className="text-left px-5 py-3 font-medium">Name</th>
                <th className="text-left px-5 py-3 font-medium">Employer</th>
                <th className="text-left px-5 py-3 font-medium">Wealth</th>
                <th className="text-left px-5 py-3 font-medium">Likelihood</th>
                <th className="text-left px-5 py-3 font-medium">Capacity</th>
                <th className="text-left px-5 py-3 font-medium">Corp Match</th>
                <th className="text-left px-5 py-3 font-medium">Approach</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {filtered.map((d) => {
                const likelihoodLocked = d.givingLikelihood < 0
                const capacityLocked  = d.capacityMin < 0

                return (
                  <tr key={d.alumniId} className="hover:bg-bone transition-colors">
                    {/* Name — always visible */}
                    <td className="px-5 py-3.5 font-medium text-text">{d.fullName}</td>

                    {/* Employer — always visible */}
                    <td className="px-5 py-3.5 text-muted">{d.employer ?? '—'}</td>

                    {/* Wealth — always visible (not gated) */}
                    <td className="px-5 py-3.5">
                      <WealthBadge level={d.wealthIndicator} />
                    </td>

                    {/* Likelihood — locked when salary permission is off */}
                    <td className="px-5 py-3.5">
                      {likelihoodLocked
                        ? <LockedCell />
                        : <LikelihoodBar value={d.givingLikelihood} />}
                    </td>

                    {/* Capacity — locked when donation_pred permission is off */}
                    <td className="px-5 py-3.5 text-xs text-muted">
                      {capacityLocked
                        ? <LockedCell />
                        : formatCapacity(d.capacityMin, d.capacityMax)}
                    </td>

                    {/* Corp match */}
                    <td className="px-5 py-3.5">
                      {d.employerMatchingAvailable
                        ? <span className="text-xs text-emerald font-medium">Yes</span>
                        : <span className="text-xs text-muted">—</span>}
                    </td>

                    {/* Approach — derived from likelihood, never hardcoded */}
                    <td className="px-5 py-3.5 text-xs text-muted max-w-40 truncate">
                      {likelihoodLocked
                        ? '—'
                        : getApproach(d.givingLikelihood)}
                    </td>
                  </tr>
                )
              })}
              {filtered.length === 0 && (
                <tr>
                  <td colSpan={7} className="py-12 text-center text-muted">
                    <TrendingUp className="h-6 w-6 mx-auto mb-2 opacity-40" />
                    {donors.length === 0
                      ? 'No donor insights available. Import alumni data to get started.'
                      : 'No results match the selected filters.'}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
