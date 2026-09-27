import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

export type Tone = 'gray' | 'blue' | 'green' | 'amber' | 'red' | 'purple' | 'indigo'

const tones: Record<Tone, string> = {
  gray: 'bg-slate-100 text-slate-700 ring-slate-200',
  blue: 'bg-sky-50 text-sky-700 ring-sky-200',
  green: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  amber: 'bg-amber-50 text-amber-800 ring-amber-200',
  red: 'bg-red-50 text-red-700 ring-red-200',
  purple: 'bg-purple-50 text-purple-700 ring-purple-200',
  indigo: 'bg-primary-50 text-primary-700 ring-primary-200',
}

export function Badge({ tone = 'gray', children, className }: { tone?: Tone; children: ReactNode; className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset',
        tones[tone],
        className,
      )}
    >
      {children}
    </span>
  )
}

const STATUS: Record<string, { tone: Tone; label: string }> = {
  // quiz
  DRAFT: { tone: 'gray', label: 'Draft' },
  PUBLISHED: { tone: 'green', label: 'Published' },
  ARCHIVED: { tone: 'amber', label: 'Archived' },
  // assignment
  ACTIVE: { tone: 'green', label: 'Active' },
  CLOSED: { tone: 'gray', label: 'Closed' },
  // session
  WAITING: { tone: 'amber', label: 'Waiting' },
  LIVE: { tone: 'red', label: 'Live' },
  ENDED: { tone: 'gray', label: 'Ended' },
  // attempts / dashboard
  UPCOMING: { tone: 'blue', label: 'Upcoming' },
  AVAILABLE: { tone: 'green', label: 'Available' },
  IN_PROGRESS: { tone: 'indigo', label: 'In progress' },
  SUBMITTED: { tone: 'purple', label: 'Submitted' },
  AUTO_SUBMITTED: { tone: 'amber', label: 'Auto-submitted' },
  EXPIRED: { tone: 'gray', label: 'Expired' },
  RESULTS_RELEASED: { tone: 'green', label: 'Results released' },
  NOT_SUBMITTED: { tone: 'gray', label: 'Not submitted' },
  // review
  PENDING: { tone: 'amber', label: 'Pending review' },
  REVIEWED: { tone: 'green', label: 'Reviewed' },
  FOLLOW_UP_REQUIRED: { tone: 'red', label: 'Follow-up required' },
  // import
  CREATE: { tone: 'green', label: 'New' },
  UPDATE: { tone: 'blue', label: 'Update' },
  SKIP_EXISTING: { tone: 'gray', label: 'Exists — skip' },
  INVALID: { tone: 'red', label: 'Invalid' },
}

export function StatusBadge({ status }: { status?: string | null }) {
  if (!status) return null
  const s = STATUS[status] ?? { tone: 'gray' as Tone, label: status }
  return (
    <Badge tone={s.tone}>
      {status === 'LIVE' && <span className="size-1.5 animate-pulse rounded-full bg-red-500" aria-hidden />}
      {s.label}
    </Badge>
  )
}
