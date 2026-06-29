import { useState, useRef, useEffect, useCallback } from 'react'
import api from '@/lib/api'
import { Upload, CheckCircle, XCircle, Loader2, FileText, X, AlertTriangle } from 'lucide-react'
import type { ImportBatch, Tenant } from '@/types'

/**
 * One tracked upload. Several can run at once (one per university or even
 * several for the same university) — each polls its own batch id, so
 * concurrent imports never interfere. Tenant isolation is enforced
 * server-side: every batch carries its tenantId through the whole pipeline.
 */
interface TrackedImport {
  key: string
  tenantName: string
  filename: string
  batch: ImportBatch
  status: 'processing' | 'completed' | 'failed'
  errorSummary: string | null
  degraded: boolean
}

/** True when the pipeline fell back to rule-based normalisation (OpenAI unavailable). */
function isLlmDegraded(batch: ImportBatch): boolean {
  if (!batch.errorLog) return false
  return (batch.errorLog as Array<{ code?: string }>).some(e => e?.code === 'LLM_FALLBACK')
}

/** Distinct per-row error reasons (max 2). Excludes the LLM-fallback notice, shown separately. */
function summariseErrors(batch: ImportBatch): string | null {
  if (!batch.errorLog || batch.errorLog.length === 0) return null
  const reasons = [...new Set(
    (batch.errorLog as Array<{ error?: string; code?: string }>)
      .filter(e => e?.code !== 'LLM_FALLBACK')
      .map(e => e?.error).filter(Boolean),
  )] as string[]
  if (reasons.length === 0) return null
  return reasons.slice(0, 2).join(' · ') + (reasons.length > 2 ? ' …' : '')
}

export default function ImportPage() {
  const [imports, setImports] = useState<TrackedImport[]>([])
  const [uploadError, setUploadError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const [dragOver, setDragOver] = useState(false)
  const [tenants, setTenants] = useState<Tenant[]>([])
  const [selectedTenantId, setSelectedTenantId] = useState<string>('')
  const [loadingTenants, setLoadingTenants] = useState(true)
  const fileRef = useRef<HTMLInputElement>(null)
  const pollersRef = useRef<Map<string, ReturnType<typeof setInterval>>>(new Map())

  useEffect(() => {
    api.get<Tenant[]>('/api/superadmin/customers')
      .then(res => {
        setTenants(res.data)
        if (res.data.length > 0) setSelectedTenantId(res.data[0].id)
      })
      .finally(() => setLoadingTenants(false))
  }, [])

  // stop every poller on unmount
  useEffect(() => () => {
    pollersRef.current.forEach(t => clearInterval(t))
    pollersRef.current.clear()
  }, [])

  function stopPolling(key: string) {
    const t = pollersRef.current.get(key)
    if (t) clearInterval(t)
    pollersRef.current.delete(key)
  }

  const pollBatch = useCallback(async (id: string) => {
    if (!id) return
    try {
      const { data } = await api.get<ImportBatch>(`/api/superadmin/import/${id}`)
      if (!data?.id) return
      setImports(prev => prev.map(imp => imp.key === id
        ? {
            ...imp,
            batch: data,
            status: data.status === 'completed' ? 'completed'
                  : data.status === 'failed' ? 'failed' : 'processing',
            errorSummary: summariseErrors(data),
            degraded: isLlmDegraded(data),
          }
        : imp))
      if (data.status === 'completed' || data.status === 'failed') stopPolling(id)
    } catch { /* ignore transient poll errors */ }
  }, [])

  async function handleFile(file: File) {
    if (!selectedTenantId) {
      setUploadError('Please select a university before uploading.')
      return
    }

    const maxBytes = 50 * 1024 * 1024
    const ext = file.name.toLowerCase()
    if (file.size > maxBytes) {
      setUploadError('File exceeds 50 MB limit.')
      return
    }
    if (!ext.endsWith('.csv') && !ext.endsWith('.xlsx') && !ext.endsWith('.xls')) {
      setUploadError('Only CSV and Excel files are accepted.')
      return
    }

    setUploadError(null)
    setUploading(true)
    const tenantName = tenants.find(t => t.id === selectedTenantId)?.institutionName ?? '—'

    const formData = new FormData()
    formData.append('file', file)

    try {
      const { data } = await api.post<ImportBatch>(
        `/api/superadmin/import?tenantId=${selectedTenantId}`,
        formData,
        { headers: { 'Content-Type': 'multipart/form-data' } }
      )
      if (data?.id) {
        setImports(prev => [{
          key: data.id,
          tenantName,
          filename: file.name,
          batch: data,
          status: 'processing',
          errorSummary: null,
          degraded: false,
        }, ...prev])
        pollersRef.current.set(data.id, setInterval(() => pollBatch(data.id), 3000))
      }
    } catch (err: unknown) {
      const status = (err as { response?: { status: number } }).response?.status
      const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      setUploadError(status === 413 ? 'File exceeds 50 MB limit.' : (msg ?? 'Upload failed.'))
    } finally {
      setUploading(false)
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  function dismiss(key: string) {
    stopPolling(key)
    setImports(prev => prev.filter(imp => imp.key !== key))
  }

  return (
    <div className="p-8 max-w-2xl">
      <h1 className="text-xl font-semibold text-ink-text mb-2">Data Import</h1>
      <p className="text-sm text-ink-muted mb-8">
        Upload a CSV or Excel export from a compliant alumni data provider.
        Maximum 50 MB per file. You can start imports for several universities
        at the same time — each runs independently.
      </p>

      {/* Tenant selector — never locked: pick another university while imports run */}
      <div className="mb-6">
        <label className="block text-xs font-mono text-ink-muted uppercase tracking-widest mb-2">
          University
        </label>
        {loadingTenants ? (
          <div className="flex items-center gap-2 text-sm text-ink-muted">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading universities…
          </div>
        ) : tenants.length === 0 ? (
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

      {/* Drop zone — always available */}
      <div
        onDragOver={(e) => { e.preventDefault(); setDragOver(true) }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => {
          e.preventDefault()
          setDragOver(false)
          const file = e.dataTransfer.files[0]
          if (file) handleFile(file)
        }}
        onClick={() => fileRef.current?.click()}
        className={`cursor-pointer rounded-xl border-2 border-dashed p-12 text-center transition-colors ${
          dragOver ? 'border-sapphire bg-sapphire/5' : 'border-ink-line hover:border-sapphire/50'
        }`}
      >
        {uploading
          ? <Loader2 className="mx-auto h-8 w-8 text-sapphire animate-spin mb-3" />
          : <Upload className="mx-auto h-8 w-8 text-ink-muted mb-3" />}
        <p className="text-sm font-medium text-ink-text">
          Drop file here or <span className="text-sapphire">browse</span>
        </p>
        <p className="text-xs text-ink-muted mt-1">CSV · XLSX · XLS — up to 50 MB</p>
        {uploadError && (
          <p className="mt-3 text-sm text-danger">{uploadError}</p>
        )}
        <input
          ref={fileRef} type="file" accept=".csv,.xlsx,.xls"
          className="hidden"
          onChange={(e) => { const f = e.target.files?.[0]; if (f) handleFile(f) }}
        />
      </div>

      {/* One card per running/finished import — all states come from the backend */}
      {imports.map(imp => (
        <div key={imp.key} className="mt-6 rounded-xl border border-ink-line bg-ink-panel p-6">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-3 min-w-0">
              <div className="shrink-0">
                {imp.status === 'processing' && <Loader2 className="h-5 w-5 text-sapphire animate-spin" />}
                {imp.status === 'completed' && (imp.degraded
                  ? <AlertTriangle className="h-5 w-5 text-amber" />
                  : <CheckCircle className="h-5 w-5 text-emerald" />)}
                {imp.status === 'failed' && <XCircle className="h-5 w-5 text-danger" />}
              </div>
              <div className="min-w-0">
                <p className="text-sm font-medium text-ink-text truncate">{imp.filename}</p>
                <p className="text-xs text-ink-muted">{imp.tenantName}</p>
              </div>
            </div>
            <div className="flex items-center gap-3 shrink-0">
              <span className={`text-xs font-mono uppercase tracking-widest ${
                imp.status === 'processing' ? 'text-sapphire' :
                imp.status === 'completed'  ? 'text-emerald' : 'text-danger'
              }`}>
                {imp.status}
              </span>
              {imp.status !== 'processing' && (
                <button onClick={() => dismiss(imp.key)}
                        className="text-ink-muted hover:text-ink-text" aria-label="Dismiss">
                  <X className="h-4 w-4" />
                </button>
              )}
            </div>
          </div>

          {/* AI degraded warning — the import silently fell back to rule-based normalisation */}
          {imp.degraded && (
            <div className="mb-4 flex items-start gap-2 rounded-lg border border-amber/40 bg-amber/10 p-3">
              <AlertTriangle className="h-4 w-4 text-amber mt-0.5 shrink-0" />
              <p className="text-xs text-amber leading-relaxed">
                <span className="font-semibold">AI normalisation was unavailable.</span> These rows were
                imported with basic rule-based normalisation (lower quality, confidence ~0.50). Check your
                OpenAI API key and quota, then delete this batch and re-import for full results.
              </p>
            </div>
          )}

          {/* Real row progress reported by the pipeline */}
          {imp.status === 'processing' && (
            <div className="mb-4">
              <div className="flex items-center justify-between text-xs text-ink-muted mb-1.5">
                <span>Rows processed</span>
                <span className="font-mono">
                  {imp.batch.processedCount} / {imp.batch.recordCount}
                </span>
              </div>
              <div className="h-2 rounded-full bg-ink-line overflow-hidden">
                <div
                  className="h-full bg-sapphire transition-all duration-500"
                  style={{
                    width: `${imp.batch.recordCount > 0
                      ? Math.round((imp.batch.processedCount / imp.batch.recordCount) * 100)
                      : 0}%`,
                  }}
                />
              </div>
            </div>
          )}

          {/* Live result counts straight from the batch row */}
          <div className="border-t border-ink-line pt-4 grid grid-cols-5 gap-3 text-center">
            {[
              { label: 'Total',     value: imp.batch.recordCount,    className: 'text-ink-text' },
              { label: 'Inserted',  value: imp.batch.insertedCount,  className: 'text-emerald' },
              { label: 'Updated',   value: imp.batch.updatedCount,   className: 'text-sapphire' },
              { label: 'Unchanged', value: imp.batch.unchangedCount, className: 'text-ink-muted' },
              { label: 'Failed',    value: imp.batch.failedCount,    className: imp.batch.failedCount > 0 ? 'text-danger' : 'text-ink-muted' },
            ].map(({ label, value, className }) => (
              <div key={label}>
                <p className={`text-2xl font-semibold font-mono ${className}`}>{value}</p>
                <p className="text-xs text-ink-muted mt-0.5">{label}</p>
              </div>
            ))}
          </div>

          {imp.errorSummary && (
            <p className="mt-4 text-sm text-danger border-t border-ink-line pt-3">
              {imp.errorSummary}
            </p>
          )}
        </div>
      ))}

      {/* Required columns hint */}
      <div className="mt-8 flex items-start gap-3 rounded-lg border border-ink-line p-4">
        <FileText className="h-4 w-4 text-ink-muted mt-0.5 shrink-0" />
        <div>
          <p className="text-sm font-medium text-ink-text">Required columns</p>
          <p className="text-xs text-ink-muted mt-1">
            A name column (<span className="font-mono">full_name</span> or{' '}
            <span className="font-mono">first_name + last_name</span> — common variants
            are recognised automatically). All other fields are mapped and normalised for you.
          </p>
        </div>
      </div>
    </div>
  )
}
