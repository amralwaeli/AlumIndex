import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-MY', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('en-MY', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatPercent(value: number): string {
  return `${Math.round(value * 100)}%`
}

export function formatCapacity(min: number, max: number): string {
  const fmt = (n: number) =>
    n >= 1_000_000
      ? `RM ${(n / 1_000_000).toFixed(1)}M`
      : `RM ${(n / 1_000).toFixed(0)}K`
  return `${fmt(min)} – ${fmt(max)}`
}
