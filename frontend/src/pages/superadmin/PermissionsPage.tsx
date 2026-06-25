import { useState, useEffect } from 'react'
import api from '@/lib/api'
import { RotateCcw, Loader2 } from 'lucide-react'
import type { Tenant } from '@/types'

interface PermDto { key: string; enabled: boolean }

// Categories and key groupings match spec §8 exactly
const CATEGORIES: { label: string; keys: string[] }[] = [
  {
    label: 'Employment Data',
    keys: ['current_employment', 'location_linkedin', 'employer_type', 'historical_employment', 'nonprofit_boards', 'corp_matching'],
  },
  {
    label: 'Net Worth Data',
    keys: ['salary', 'donation_pred', 'property', 'sec_stock'],
  },
  {
    label: 'Contact Data',
    keys: ['biz_email', 'personal_email'],
  },
  {
    label: 'Professional Insights',
    keys: ['seniority', 'news'],
  },
  {
    label: 'Data Refresh',
    keys: ['monthly', 'midyear', 'multiyear'],
  },
  {
    label: 'Verification & Matching',
    keys: ['ultra_conf', 'company_id'],
  },
  {
    label: 'Access & Support',
    keys: ['exports_users', 'support'],
  },
]

const LABELS: Record<string, string> = {
  current_employment:    'Current Employment',
  location_linkedin:     'Location (LinkedIn)',
  employer_type:         'Employer Type',
  historical_employment: 'Historical Employment',
  nonprofit_boards:      'Nonprofit Board Memberships',
  corp_matching:         'Corporate Matching',
  salary:                'Estimated Salary',
  donation_pred:         'Donation Prediction',
  property:              'Property Holdings',
  sec_stock:             'SEC / Stock Holdings',
  biz_email:             'Business Email',
  personal_email:        'Personal Email',
  seniority:             'Seniority Level',
  news:                  'News & Press Mentions',
  monthly:               'Monthly Reports',
  midyear:               'Mid-Year Reports',
  multiyear:             'Multi-Year Reports',
  ultra_conf:            'Ultra Confidence Matching',
  company_id:            'Company ID Integration',
  exports_users:         'User Data Exports',
  support:               'Priority Support',
}

export default function PermissionsPage() {
  const [tenants, setTenants] = useState<Tenant[]>([])
  const [selectedTenantId, setSelectedTenantId] = useState<string>('')
  const [loadingTenants, setLoadingTenants] = useState(true)
  const [perms, setPerms] = useState<PermDto[]>([])
  const [loading, setLoading] = useState(false)
  const [toggling, setToggling] = useState<string | null>(null)
  const [resetting, setResetting] = useState(false)

  // Load tenant list on mount
  useEffect(() => {
    api.get<Tenant[]>('/api/superadmin/customers')
      .then(res => {
        setTenants(res.data)
        if (res.data.length > 0) setSelectedTenantId(res.data[0].id)
      })
      .finally(() => setLoadingTenants(false))
  }, [])

  // Load permissions when tenant changes
  useEffect(() => {
    if (selectedTenantId) load(selectedTenantId)
  }, [selectedTenantId])

  async function load(tid: string) {
    setLoading(true)
    try {
      const { data } = await api.get<PermDto[]>(`/api/superadmin/permissions/${tid}`)
      setPerms(data)
    } finally {
      setLoading(false)
    }
  }

  async function toggle(key: string, enabled: boolean) {
    setToggling(key)
    try {
      const { data } = await api.put<PermDto>(
        `/api/superadmin/permissions/${selectedTenantId}`,
        { permissionKey: key, enabled }
      )
      setPerms(prev => prev.map(p => p.key === data.key ? data : p))
    } finally {
      setToggling(null)
    }
  }

  async function resetDefaults() {
    setResetting(true)
    try {
      const { data } = await api.post<PermDto[]>(`/api/superadmin/permissions/${selectedTenantId}/reset`)
      setPerms(data)
    } finally {
      setResetting(false)
    }
  }

  function getEnabled(key: string) {
    return perms.find(p => p.key === key)?.enabled ?? false
  }

  if (loadingTenants) {
    return (
      <div className="flex items-center justify-center h-full py-24">
        <Loader2 className="h-5 w-5 animate-spin text-ink-muted" />
      </div>
    )
  }

  return (
    <div className="p-8 max-w-3xl">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-semibold text-ink-text">Data Permissions</h1>
          <p className="text-sm text-ink-muted mt-1">
            Control which data fields are visible to a university's portal.
          </p>
        </div>
        <button
          onClick={resetDefaults}
          disabled={resetting || !selectedTenantId}
          className="flex items-center gap-2 rounded-md border border-ink-line px-3 py-2 text-sm text-ink-muted hover:text-ink-text transition-colors disabled:opacity-50"
        >
          {resetting ? <Loader2 className="h-4 w-4 animate-spin" /> : <RotateCcw className="h-4 w-4" />}
          Reset to defaults
        </button>
      </div>

      {/* Tenant selector */}
      <div className="mb-6">
        <label className="block text-xs font-mono text-ink-muted uppercase tracking-widest mb-2">
          University
        </label>
        {tenants.length === 0 ? (
          <p className="text-sm text-ink-muted">No active universities found.</p>
        ) : (
          <select
            value={selectedTenantId}
            onChange={e => setSelectedTenantId(e.target.value)}
            className="w-full rounded-md border border-ink-line bg-ink-panel px-3 py-2 text-sm text-ink-text focus:outline-none focus:ring-2 focus:ring-sapphire"
          >
            {tenants.map(t => (
              <option key={t.id} value={t.id}>{t.institutionName}</option>
            ))}
          </select>
        )}
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-16">
          <Loader2 className="h-5 w-5 animate-spin text-ink-muted" />
        </div>
      ) : (
        <div className="space-y-6">
          {CATEGORIES.map(({ label, keys }) => (
            <div key={label} className="rounded-xl border border-ink-line bg-ink-panel overflow-hidden">
              <div className="px-5 py-3 border-b border-ink-line">
                <h2 className="text-xs font-mono text-ink-muted uppercase tracking-widest">{label}</h2>
              </div>
              <div className="divide-y divide-ink-line">
                {keys.map((key) => {
                  const enabled = getEnabled(key)
                  const isToggling = toggling === key
                  return (
                    <div key={key} className="flex items-center justify-between px-5 py-3.5">
                      <span className="text-sm text-ink-text">{LABELS[key] ?? key}</span>
                      <button
                        onClick={() => toggle(key, !enabled)}
                        disabled={isToggling || !selectedTenantId}
                        className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 focus:outline-none disabled:opacity-50 ${
                          enabled ? 'bg-sapphire' : 'bg-ink-line'
                        }`}
                        role="switch"
                        aria-checked={enabled}
                      >
                        <span
                          className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition duration-200 ${
                            enabled ? 'translate-x-4' : 'translate-x-0'
                          }`}
                        />
                      </button>
                    </div>
                  )
                })}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
