import { AlertCircle, AlertTriangle, CheckCircle2, Info, Loader2 } from 'lucide-react'
import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cn('size-4 animate-spin text-slate-500', className)} aria-label="Loading" />
}

export function LoadingBlock({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-16 text-sm text-slate-500" role="status">
      <Spinner /> {label}
    </div>
  )
}

type AlertTone = 'error' | 'warning' | 'success' | 'info'

export function Alert({ tone = 'info', title, children, className }: { tone?: AlertTone; title?: ReactNode; children?: ReactNode; className?: string }) {
  const styles = {
    error: 'border-red-200 bg-red-50/70 text-red-900 [&_svg]:text-red-600',
    warning: 'border-amber-200 bg-amber-50/70 text-amber-950 [&_svg]:text-amber-600',
    success: 'border-emerald-200 bg-emerald-50/70 text-emerald-900 [&_svg]:text-emerald-600',
    info: 'border-slate-200 bg-white text-slate-700 [&_svg]:text-primary-600',
  }[tone]
  const Icon = { error: AlertCircle, warning: AlertTriangle, success: CheckCircle2, info: Info }[tone]
  return (
    <div className={cn('flex gap-2.5 rounded-lg border px-3.5 py-3 text-sm', styles, className)} role={tone === 'error' ? 'alert' : 'status'}>
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden />
      <div className="min-w-0 space-y-0.5">
        {title && <p className="font-medium text-slate-900">{title}</p>}
        {children && <div>{children}</div>}
      </div>
    </div>
  )
}

export function ErrorAlert({ error, onRetry }: { error?: string | null; onRetry?: () => void }) {
  if (!error) return null
  return (
    <Alert tone="error">
      {error}
      {onRetry && (
        <button type="button" onClick={onRetry} className="ml-2 font-medium underline">
          Retry
        </button>
      )}
    </Alert>
  )
}

export function EmptyState({ title, description, action }: { icon?: ReactNode; title: string; description?: ReactNode; action?: ReactNode }) {
  return (
    <div className="px-6 py-12 text-center">
      <p className="text-sm font-medium text-slate-800">{title}</p>
      {description && <p className="mx-auto mt-1 max-w-md text-sm text-slate-500">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

export function PageHeader({ title, description, actions, back }: { title: ReactNode; description?: ReactNode; actions?: ReactNode; back?: ReactNode }) {
  return (
    <div className="mb-6 border-b border-slate-200 pb-5">
      {back && <div className="mb-3 text-[13px]">{back}</div>}
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="min-w-0">
          <h1 className="text-[22px] font-semibold text-slate-900">{title}</h1>
          {description && <p className="mt-1 text-sm text-slate-500">{description}</p>}
        </div>
        {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
      </div>
    </div>
  )
}
