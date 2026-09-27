import * as D from '@radix-ui/react-dialog'
import { X } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { cn } from '@/lib/utils'
import { Button } from './button'
import { ErrorAlert } from './feedback'

export function Dialog({
  open,
  onOpenChange,
  title,
  description,
  children,
  footer,
  size = 'md',
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: ReactNode
  description?: ReactNode
  children?: ReactNode
  footer?: ReactNode
  size?: 'sm' | 'md' | 'lg' | 'xl'
}) {
  const width = { sm: 'max-w-md', md: 'max-w-lg', lg: 'max-w-3xl', xl: 'max-w-6xl' }[size]
  return (
    <D.Root open={open} onOpenChange={onOpenChange}>
      <D.Portal>
        <D.Overlay className="fixed inset-0 z-40 bg-slate-900/40 backdrop-blur-[1px]" />
        <D.Content
          className={cn(
            'animate-fade-in fixed left-1/2 top-1/2 z-50 flex max-h-[90vh] w-[calc(100%-2rem)] -translate-x-1/2 -translate-y-1/2 flex-col rounded-xl bg-white shadow-xl',
            width,
          )}
        >
          <div className="flex items-start justify-between gap-4 border-b border-slate-100 px-5 py-4">
            <div>
              <D.Title className="text-base font-semibold text-slate-900">{title}</D.Title>
              {description ? (
                <D.Description className="mt-0.5 text-sm text-slate-500">{description}</D.Description>
              ) : (
                <D.Description className="sr-only">{typeof title === 'string' ? title : 'Dialog'}</D.Description>
              )}
            </div>
            <D.Close asChild>
              <Button variant="ghost" size="icon" aria-label="Close">
                <X />
              </Button>
            </D.Close>
          </div>
          <div className="overflow-y-auto px-5 py-4">{children}</div>
          {footer && <div className="flex flex-wrap justify-end gap-2 border-t border-slate-100 px-5 py-3">{footer}</div>}
        </D.Content>
      </D.Portal>
    </D.Root>
  )
}

/** Confirmation dialog that runs an async action and surfaces its error. */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel = 'Confirm',
  destructive,
  onConfirm,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: ReactNode
  description: ReactNode
  confirmLabel?: string
  destructive?: boolean
  onConfirm: () => Promise<unknown> | unknown
}) {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const run = async () => {
    setPending(true)
    setError(null)
    try {
      await onConfirm()
      onOpenChange(false)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Action failed')
    } finally {
      setPending(false)
    }
  }
  return (
    <Dialog
      open={open}
      onOpenChange={(o) => {
        if (!o) setError(null)
        onOpenChange(o)
      }}
      title={title}
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={() => onOpenChange(false)} disabled={pending}>
            Cancel
          </Button>
          <Button variant={destructive ? 'destructive' : 'default'} onClick={run} loading={pending}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <div className="space-y-3 text-sm text-slate-600">
        <div>{description}</div>
        <ErrorAlert error={error} />
      </div>
    </Dialog>
  )
}
