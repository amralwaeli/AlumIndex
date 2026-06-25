import { useState, useEffect, useCallback } from 'react'
import api from '@/lib/api'
import {
  BarChart, Bar, PieChart, Pie, Cell, ResponsiveContainer,
  XAxis, YAxis, Tooltip,
} from 'recharts'
import { Users, Briefcase, Bell, TrendingUp } from 'lucide-react'
import { KpiSkeleton, Skeleton, LoadError } from '@/components/ui/Skeleton'

interface Kpis {
  totalAlumni: number
  employmentRate: number
  careerChangeAlerts: number
  highValueProspects: number
}

interface SeniorityCount { seniority: string; count: number }
interface IndustryCount  { industry: string;  count: number }

interface CareerEvent {
  id: string
  alumniId: string
  alumniName?: string | null
  eventType: string
  oldValue: string | null
  newValue: string | null
  significanceLevel: string
  detectedAt: string
}

const PIE_COLORS = ['#2D4BC4','#A9791F','#1C8A5A','#6D4AA6','#C9791C','#657182']

export default function DashboardPage() {
  const [kpis, setKpis] = useState<Kpis | null>(null)
  const [seniority, setSeniority] = useState<SeniorityCount[]>([])
  const [industry, setIndustry] = useState<IndustryCount[]>([])
  const [events, setEvents] = useState<CareerEvent[]>([])
  const [years, setYears] = useState<number[]>([])
  const [year, setYear] = useState<string>('')   // '' = all cohorts
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)

  // Cohort years load once
  useEffect(() => {
    api.get<number[]>('/api/dashboard/years')
      .then(res => setYears(res.data))
      .catch(() => { /* dropdown simply stays empty */ })
  }, [])

  const load = useCallback(async () => {
    setLoading(true)
    setLoadError(false)
    const qs = year ? `?year=${year}` : ''
    try {
      const [k, s, ind, ev] = await Promise.all([
        api.get<Kpis>(`/api/dashboard/metrics${qs}`),
        api.get<SeniorityCount[]>(`/api/dashboard/seniority${qs}`),
        api.get<IndustryCount[]>(`/api/dashboard/industry${qs}`),
        api.get<CareerEvent[]>(`/api/dashboard/events${qs}`),
      ])
      setKpis(k.data)
      setSeniority(s.data)
      setIndustry(ind.data.slice(0, 8))
      setEvents(ev.data)
    } catch {
      setLoadError(true)
    } finally {
      setLoading(false)
    }
  }, [year])

  useEffect(() => { load() }, [load])

  const employPct = kpis ? Math.round(kpis.employmentRate * 100) : 0

  if (loadError) {
    return (
      <div className="p-6 sm:p-8 max-w-6xl">
        <h1 className="text-xl font-semibold text-text mb-6">Dashboard</h1>
        <LoadError message="Failed to load dashboard. Is the backend running?" onRetry={load} />
      </div>
    )
  }

  return (
    <div className="p-6 sm:p-8 max-w-6xl">
      {/* Page header + cohort filter (Issue 6) */}
      <div className="mb-7 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-text">Dashboard</h1>
          <p className="text-sm text-muted mt-1">Overview of your alumni network and engagement signals.</p>
        </div>
        <select
          value={year}
          onChange={e => setYear(e.target.value)}
          className="rounded-md border border-line bg-surface px-3 py-2 text-sm text-text focus:outline-none focus:ring-2 focus:ring-sapphire"
        >
          <option value="">All cohorts</option>
          {years.map(y => <option key={y} value={y}>Class of {y}</option>)}
        </select>
      </div>

      {loading ? (
        <div className="space-y-8">
          <KpiSkeleton />
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <Skeleton className="h-72 rounded-xl" />
            <Skeleton className="h-72 rounded-xl" />
          </div>
        </div>
      ) : (
      <>
      {/* KPI cards */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4 mb-8">
        {[
          { label: 'Total Alumni',         value: (kpis?.totalAlumni ?? 0).toLocaleString(), icon: Users,      bg: 'bg-sapphire/5', iconCls: 'text-sapphire', valCls: 'text-sapphire' },
          { label: 'Employment Rate',      value: `${employPct}%`,                            icon: Briefcase,  bg: 'bg-emerald/5', iconCls: 'text-emerald',  valCls: 'text-emerald' },
          { label: 'Career Alerts',        value: String(kpis?.careerChangeAlerts ?? 0),      icon: Bell,       bg: 'bg-amber/5',   iconCls: 'text-amber',    valCls: 'text-amber' },
          { label: 'High-Value Prospects', value: String(kpis?.highValueProspects ?? 0),      icon: TrendingUp, bg: 'bg-violet/5',  iconCls: 'text-violet',   valCls: 'text-violet' },
        ].map(({ label, value, icon: Icon, bg, iconCls, valCls }) => (
          <div key={label} className="rounded-xl border border-line bg-surface p-5">
            <div className="flex items-start justify-between mb-4">
              <span className="text-xs font-medium text-muted leading-tight">{label}</span>
              <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${bg}`}>
                <Icon className={`h-4 w-4 ${iconCls}`} />
              </div>
            </div>
            <p className={`text-3xl font-semibold font-mono ${valCls}`}>{value}</p>
          </div>
        ))}
      </div>

      {/* Charts row */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2 mb-8">
        {/* Seniority distribution — pie */}
        <div className="rounded-xl border border-line bg-surface p-5">
          <h2 className="text-sm font-medium text-text mb-4">Seniority Distribution</h2>
          {seniority.length === 0 ? (
            <p className="text-sm text-muted py-8 text-center">No data yet.</p>
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie
                  data={seniority}
                  dataKey="count"
                  nameKey="seniority"
                  cx="50%"
                  cy="50%"
                  outerRadius={80}
                  label={({ seniority: s, percent }) => `${s} ${Math.round((percent ?? 0) * 100)}%`}
                  labelLine={false}
                >
                  {seniority.map((_, i) => (
                    <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>

        {/* Industry spread — bar */}
        <div className="rounded-xl border border-line bg-surface p-5">
          <h2 className="text-sm font-medium text-text mb-4">Top Industries</h2>
          {industry.length === 0 ? (
            <p className="text-sm text-muted py-8 text-center">No data yet.</p>
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={industry} layout="vertical" margin={{ left: 0, right: 24 }}>
                <XAxis type="number" tick={{ fontSize: 11 }} />
                <YAxis type="category" dataKey="industry" tick={{ fontSize: 11 }} width={100} />
                <Tooltip />
                <Bar dataKey="count" fill="#2D4BC4" radius={[0, 4, 4, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>

      {/* Recent career events */}
      <div className="rounded-xl border border-line bg-surface p-5">
        <h2 className="text-sm font-medium text-text mb-4">Recent Career Events</h2>
        {events.length === 0 ? (
          <p className="text-sm text-muted text-center py-6">No career events detected yet.</p>
        ) : (
          <div className="divide-y divide-line">
            {events.slice(0, 8).map((e) => (
              <div key={e.id} className="flex items-center justify-between py-3">
                <div>
                  <span className={`inline-block mr-2 rounded-full px-2 py-0.5 text-xs font-medium ${
                    e.significanceLevel === 'high' ? 'bg-danger/10 text-danger' :
                    e.significanceLevel === 'medium' ? 'bg-amber/10 text-amber' : 'bg-line text-muted'
                  }`}>
                    {e.eventType.replace('_', ' ')}
                  </span>
                  <span className="text-sm text-muted">
                    {e.alumniName && <span className="font-medium text-text">{e.alumniName}: </span>}
                    {e.oldValue && e.newValue ? `${e.oldValue} → ${e.newValue}` : (e.newValue ?? '')}
                  </span>
                </div>
                <span className="text-xs text-muted">
                  {new Date(e.detectedAt).toLocaleDateString()}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
      </>
      )}
    </div>
  )
}
