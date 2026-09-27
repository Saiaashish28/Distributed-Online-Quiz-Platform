import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

export type Tone = 'gray' | 'blue' | 'green' | 'amber' | 'red' | 'purple' | 'indigo'

const tones: Record<Tone, { box: string; dot: string }> = {
  gray: { box: 'bg-slate-100 text-slate-700', dot: 'bg-slate-400' },
  blue: { box: 'bg-sky-50 text-sky-800', dot: 'bg-sky-500' },
  green: { box: 'bg-emerald-50 text-emerald-800', dot: 'bg-emerald-600' },
  amber: { box: 'bg-amber-50 text-amber-900', dot: 'bg-amber-500' },
  red: { box: 'bg-red-50 text-red-800', dot: 'bg-red-600' },
  purple: { box: 'bg-violet-50 text-violet-800', dot: 'bg-violet-500' },
  indigo: { box: 'bg-primary-50 text-primary-800', dot: 'bg-primary-600' },
}

export function Badge({ tone = 'gray', children, className, dot }: { tone?: Tone; children: ReactNode; className?: string; dot?: boolean }) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 whitespace-nowrap rounded px-1.5 py-0.5 text-xs font-medium leading-4',
        tones[tone].box,
        className,
      )}
    >
      {dot && <span className={cn('size-1.5 rounded-full', tones[tone].dot)} aria-hidden />}
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
  FOLLOW_UP_REQUIRED: { tone: 'red', label: 'Follow-up' },
  // import
  CREATE: { tone: 'green', label: 'New' },
  UPDATE: { tone: 'blue', label: 'Update' },
  SKIP_EXISTING: { tone: 'gray', label: 'Exists, skip' },
  INVALID: { tone: 'red', label: 'Invalid' },
}

export function StatusBadge({ status }: { status?: string | null }) {
  if (!status) return null
  const s = STATUS[status] ?? { tone: 'gray' as Tone, label: status }
  return (
    <Badge tone={s.tone} dot>
      {s.label}
    </Badge>
  )
}
