import { useState, useEffect, useCallback } from 'react'
import api from '@/lib/api'
import { Bell, X } from 'lucide-react'
import { CardListSkeleton, LoadError } from '@/components/ui/Skeleton'
import type { CareerEvent } from '@/types'

const TYPE_OPTIONS = [
  { value: '',                label: 'All types' },
  { value: 'employer_change', label: 'Employer Change' },
  { value: 'promotion',       label: 'Promotion' },
  { value: 'job_change',      label: 'Job Change' },
]

function SignificanceBadge({ level }: { level: string }) {
  const map: Record<string, string> = {
    high:   'bg-danger/10 text-danger',
    medium: 'bg-amber/10 text-amber',
    low:    'bg-line text-muted',
  }
  return <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${map[level] ?? 'bg-line text-muted'}`}>{level}</span>
}

export default function AlertsPage() {
  const [events, setEvents] = useState<CareerEvent[]>([])
  const [typeFilter, setTypeFilter] = useState('')
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [dismissing, setDismissing] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setLoadError(false)
    try {
      const params = typeFilter ? `?type=${typeFilter}` : ''
      const res = await api.get<CareerEvent[]>(`/api/alerts${params}`)
      setEvents(res.data)
    } catch {
      setLoadError(true)
    } finally {
      setLoading(false)
    }
  }, [typeFilter])

  useEffect(() => { load() }, [load])

  async function dismiss(id: string) {
    setDismissing(id)
    try {
      await api.put(`/api/alerts/${id}/dismiss`)
      setEvents(prev => prev.filter(e => e.id !== id))
      // Let the sidebar badge refresh
      window.dispatchEvent(new Event('alerts-changed'))
    } catch {
      // keep the card; user can retry
    } finally {
      setDismissing(null)
    }
  }

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-semibold text-text">Alerts</h1>
          <p className="text-sm text-muted mt-1">High-significance career events detected across your alumni cohort.</p>
        </div>
        <select
          value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value)}
          className="rounded-md border border-line bg-bone px-3 py-2 text-sm text-text focus:outline-none focus:ring-2 focus:ring-sapphire"
        >
          {TYPE_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
      </div>

      {loading ? (
        <CardListSkeleton rows={5} />
      ) : loadError ? (
        <LoadError message="Failed to load alerts." onRetry={load} />
      ) : events.length === 0 ? (
        <div className="rounded-xl border border-line bg-surface p-12 text-center">
          <Bell className="h-8 w-8 mx-auto mb-3 text-muted opacity-40" />
          <p className="text-sm text-muted">No alerts at this time.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {events.map((e) => (
            <div key={e.id} className="rounded-xl border border-line bg-surface p-4 flex items-start justify-between gap-4">
              <div className="flex items-start gap-3">
                <div className={`mt-0.5 h-2.5 w-2.5 rounded-full shrink-0 ${
                  e.significanceLevel === 'high' ? 'bg-danger' :
                  e.significanceLevel === 'medium' ? 'bg-amber' : 'bg-muted'
                }`} />
                <div>
                  <p className="text-sm font-medium text-text">
                    {e.alumniName && <span>{e.alumniName} — </span>}
                    <span className="capitalize">{e.eventType.replace(/_/g, ' ')}</span>
                  </p>
                  {e.oldValue && e.newValue && (
                    <p className="text-sm text-muted mt-0.5">
                      {e.oldValue} <span className="text-sapphire">→</span> {e.newValue}
                    </p>
                  )}
                  {!e.oldValue && e.newValue && (
                    <p className="text-sm text-muted mt-0.5">{e.newValue}</p>
                  )}
                </div>
              </div>
              <div className="flex items-center gap-3 shrink-0">
                <SignificanceBadge level={e.significanceLevel} />
                <span className="text-xs text-muted">
                  {new Date(e.detectedAt).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })}
                </span>
                <button
                  onClick={() => dismiss(e.id)}
                  disabled={dismissing === e.id}
                  title="Dismiss alert"
                  className="flex h-7 w-7 items-center justify-center rounded-md text-muted hover:bg-bone hover:text-text disabled:opacity-40 transition-colors"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
