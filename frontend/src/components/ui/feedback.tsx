import { AlertCircle, AlertTriangle, CheckCircle2, Info, Loader2 } from 'lucide-react'
import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cn('size-5 animate-spin text-primary-600', className)} aria-label="Loading" />
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
    error: 'border-red-200 bg-red-50 text-red-800',
    warning: 'border-amber-200 bg-amber-50 text-amber-900',
    success: 'border-emerald-200 bg-emerald-50 text-emerald-800',
    info: 'border-sky-200 bg-sky-50 text-sky-900',
  }[tone]
  const Icon = { error: AlertCircle, warning: AlertTriangle, success: CheckCircle2, info: Info }[tone]
  return (
    <div className={cn('flex gap-2.5 rounded-lg border px-3.5 py-3 text-sm', styles, className)} role={tone === 'error' ? 'alert' : 'status'}>
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden />
      <div className="min-w-0 space-y-1">
        {title && <p className="font-medium">{title}</p>}
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

export function EmptyState({ icon, title, description, action }: { icon?: ReactNode; title: string; description?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-14 text-center">
      {icon && <div className="mb-3 rounded-full bg-slate-100 p-3 text-slate-400 [&_svg]:size-6">{icon}</div>}
      <p className="font-medium text-slate-800">{title}</p>
      {description && <p className="mt-1 max-w-md text-sm text-slate-500">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

export function PageHeader({ title, description, actions, back }: { title: ReactNode; description?: ReactNode; actions?: ReactNode; back?: ReactNode }) {
  return (
    <div className="mb-6">
      {back && <div className="mb-2">{back}</div>}
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="min-w-0">
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900">{title}</h1>
          {description && <p className="mt-1 text-sm text-slate-500">{description}</p>}
        </div>
        {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
      </div>
    </div>
  )
}
