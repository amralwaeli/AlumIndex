import { AlertCircle, RefreshCw } from 'lucide-react'

/** Pulsing placeholder block. Size with className (h-*, w-*). */
export function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`animate-pulse rounded-md bg-line/60 ${className}`} />
}

/** Skeleton for a KPI card row. */
export function KpiSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="rounded-xl border border-line bg-surface p-5">
          <div className="flex items-start justify-between mb-4">
            <Skeleton className="h-3 w-20" />
            <Skeleton className="h-8 w-8 rounded-lg" />
          </div>
          <Skeleton className="h-8 w-16" />
        </div>
      ))}
    </div>
  )
}

/** Skeleton for a list of card rows. */
export function CardListSkeleton({ rows = 5 }: { rows?: number }) {
  return (
    <div className="space-y-3">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="rounded-xl border border-line bg-surface p-5">
          <div className="flex items-center justify-between">
            <div className="space-y-2">
              <Skeleton className="h-4 w-40" />
              <Skeleton className="h-3 w-56" />
            </div>
            <Skeleton className="h-7 w-7 rounded-md" />
          </div>
        </div>
      ))}
    </div>
  )
}

/** Skeleton table rows. */
export function TableSkeleton({ rows = 8, cols = 4 }: { rows?: number; cols?: number }) {
  return (
    <div className="divide-y divide-line">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="flex items-center gap-6 px-6 py-3.5">
          {Array.from({ length: cols }).map((_, j) => (
            <Skeleton key={j} className={`h-3.5 ${j === 0 ? 'w-40' : 'w-24'}`} />
          ))}
        </div>
      ))}
    </div>
  )
}

/** Standard load-failure state with retry. */
export function LoadError({ message, onRetry }: { message?: string; onRetry: () => void }) {
  return (
    <div className="flex flex-col items-center justify-center py-20 gap-4">
      <AlertCircle className="h-8 w-8 text-danger" />
      <p className="text-sm text-muted">{message ?? 'Failed to load data. Please try again.'}</p>
      <button
        onClick={onRetry}
        className="flex items-center gap-2 rounded-md border border-line px-4 py-2 text-sm text-muted hover:text-text hover:bg-bone transition-colors"
      >
        <RefreshCw className="h-4 w-4" /> Retry
      </button>
    </div>
  )
}
