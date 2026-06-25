import { useState, useEffect, useCallback } from 'react'
import api from '@/lib/api'
import { ClipboardList } from 'lucide-react'
import { TableSkeleton, LoadError } from '@/components/ui/Skeleton'
import type { AuditLog, Page } from '@/types'

export default function AuditLogPage() {
  const [data, setData] = useState<Page<AuditLog> | null>(null)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)

  const load = useCallback(() => {
    setLoading(true)
    setLoadError(false)
    api.get<Page<AuditLog>>(`/api/audit-logs?page=${page}`)
      .then(res => setData(res.data))
      .catch(() => setLoadError(true))
      .finally(() => setLoading(false))
  }, [page])

  useEffect(() => { load() }, [load])

  return (
    <div className="p-8">
      <h1 className="text-xl font-semibold text-text mb-6">Audit Log</h1>

      {loading ? (
        <TableSkeleton rows={10} cols={4} />
      ) : loadError ? (
        <LoadError message="Failed to load audit log." onRetry={load} />
      ) : !data?.content.length ? (
        <div className="rounded-xl border border-line bg-surface p-12 text-center">
          <ClipboardList className="h-8 w-8 mx-auto mb-3 text-muted opacity-40" />
          <p className="text-sm text-muted">No audit events yet.</p>
        </div>
      ) : (
        <>
          <div className="rounded-xl border border-line bg-surface overflow-hidden">
            <table className="w-full text-sm">
              <thead className="border-b border-line bg-bone">
                <tr className="text-xs text-muted uppercase tracking-wide">
                  <th className="text-left px-5 py-3 font-medium">Time</th>
                  <th className="text-left px-5 py-3 font-medium">User</th>
                  <th className="text-left px-5 py-3 font-medium">Action</th>
                  <th className="text-left px-5 py-3 font-medium">Details</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {data.content.map((log) => (
                  <tr key={log.id} className="hover:bg-bone transition-colors">
                    <td className="px-5 py-3 text-muted font-mono text-xs whitespace-nowrap">
                      {new Date(log.actionTime).toLocaleString('en-GB', {
                        day: 'numeric', month: 'short', year: 'numeric',
                        hour: '2-digit', minute: '2-digit',
                      })}
                    </td>
                    <td className="px-5 py-3 text-text">
                      <p className="font-medium">{log.userEmail ?? '—'}</p>
                    </td>
                    <td className="px-5 py-3">
                      <span className="inline-block rounded-full bg-sapphire-soft text-sapphire px-2 py-0.5 text-xs font-mono">
                        {log.actionType}
                      </span>
                    </td>
                    <td className="px-5 py-3 text-muted text-xs max-w-xs truncate">{log.actionDetails}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {data.totalPages > 1 && (
            <div className="flex items-center justify-between mt-4">
              <p className="text-xs text-muted">{data.totalElements.toLocaleString()} entries</p>
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
  )
}
