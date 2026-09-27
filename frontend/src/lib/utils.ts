import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

const ROMAN = ['', 'I', 'II', 'III', 'IV', 'V', 'VI']

export function roman(year?: number | null) {
  if (!year) return ''
  return ROMAN[year] ?? String(year)
}

export function formatDateTime(iso?: string | null) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  })
}

export function formatMarks(v?: number | null) {
  if (v === null || v === undefined) return '—'
  return Number.isInteger(v) ? String(v) : v.toFixed(2).replace(/0$/, '')
}

/** "HH:MM:SS" / "MM:SS" countdown text for a number of milliseconds. */
export function formatCountdown(ms: number) {
  const total = Math.max(0, Math.floor(ms / 1000))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`
}

/** Converts an ISO instant to the value format of <input type="datetime-local"> (local time). */
export function toLocalInput(iso?: string | null) {
  if (!iso) return ''
  const d = new Date(iso)
  const off = d.getTimezoneOffset()
  return new Date(d.getTime() - off * 60000).toISOString().slice(0, 16)
}

export function fromLocalInput(value: string) {
  return value ? new Date(value).toISOString() : null
}

export const browserTimeZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone
