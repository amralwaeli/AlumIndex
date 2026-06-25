import { useState } from 'react'
import api from '@/lib/api'
import {
  Download, FileText, Loader2, CheckCircle, AlertCircle,
  Heart, Bell, ClipboardList, PieChart,
} from 'lucide-react'

const REPORTS = [
  {
    key: 'alumni',
    title: 'Alumni Database Export',
    description: 'Full list of alumni with current employment, seniority, industry, location, and graduation year.',
    icon: FileText,
  },
  {
    key: 'donors',
    title: 'Donor Insights Export',
    description: 'Giving likelihood, capacity ranges, wealth indicators, and suggested approach per prospect.',
    icon: Heart,
  },
  {
    key: 'events',
    title: 'Career Events Export',
    description: 'All detected career changes — employer moves, promotions, and job changes with timestamps.',
    icon: Bell,
  },
  {
    key: 'audit',
    title: 'Audit Log Export',
    description: 'Compliance record of the last 1,000 actions performed in your institution account.',
    icon: ClipboardList,
  },
  {
    key: 'summary',
    title: 'Cohort Summary Report',
    description: 'Headline KPIs plus seniority and industry distributions in a single file.',
    icon: PieChart,
  },
]

export default function ReportsPage() {
  const [downloading, setDownloading] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function download(type: string) {
    setDownloading(type)
    setDone(null)
    setError(null)
    try {
      const { data, headers } = await api.get(`/api/reports/${type}/export`, {
        responseType: 'blob',
      })
      const disposition = headers['content-disposition'] ?? ''
      const filename = disposition.match(/filename="?([^";\n]+)"?/)?.[1] ?? `${type}-report.csv`
      const url = URL.createObjectURL(new Blob([data]))
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      a.click()
      URL.revokeObjectURL(url)
      setDone(type)
    } catch {
      setError(`Failed to generate the ${type} report. Please try again.`)
    } finally {
      setDownloading(null)
    }
  }

  return (
    <div className="p-8 max-w-2xl">
      <h1 className="text-xl font-semibold text-text mb-2">Reports</h1>
      <p className="text-sm text-muted mb-8">Download data exports for offline analysis or compliance records.</p>

      {error && (
        <div className="mb-4 flex items-center gap-2 rounded-md border border-danger/20 bg-danger/5 px-3 py-2.5 text-sm text-danger">
          <AlertCircle className="h-4 w-4 shrink-0" />
          {error}
        </div>
      )}

      <div className="space-y-4">
        {REPORTS.map(({ key, title, description, icon: Icon }) => (
          <div key={key} className="rounded-xl border border-line bg-surface p-5 flex items-start justify-between gap-4">
            <div className="flex items-start gap-4">
              <div className="mt-0.5 rounded-lg bg-sapphire-soft p-2.5">
                <Icon className="h-5 w-5 text-sapphire" />
              </div>
              <div>
                <p className="text-sm font-medium text-text">{title}</p>
                <p className="text-xs text-muted mt-1 max-w-sm">{description}</p>
              </div>
            </div>
            <button
              onClick={() => download(key)}
              disabled={downloading !== null}
              className="flex items-center gap-2 rounded-md bg-sapphire px-4 py-2 text-sm font-medium text-white hover:bg-sapphire-dark disabled:opacity-50 transition-colors shrink-0"
            >
              {downloading === key ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : done === key ? (
                <CheckCircle className="h-4 w-4" />
              ) : (
                <Download className="h-4 w-4" />
              )}
              {downloading === key ? 'Preparing…' : done === key ? 'Downloaded' : 'Export CSV'}
            </button>
          </div>
        ))}
      </div>

      <div className="mt-8 rounded-lg border border-line bg-bone p-4 text-xs text-muted">
        Exports include only data fields permitted by your operator. Contact your AlumIndex operator
        to enable additional fields.
      </div>
    </div>
  )
}
