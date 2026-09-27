import { CheckCircle2, AlertCircle, X } from 'lucide-react'
import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'
import { cn } from '@/lib/utils'

interface Toast {
  id: number
  message: string
  tone: 'success' | 'error'
}

const ToastContext = createContext<(message: string, tone?: Toast['tone']) => void>(() => {})

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const push = useCallback((message: string, tone: Toast['tone'] = 'success') => {
    const id = Date.now() + Math.random()
    setToasts((t) => [...t, { id, message, tone }])
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 4500)
  }, [])
  return (
    <ToastContext.Provider value={push}>
      {children}
      <div className="pointer-events-none fixed bottom-4 right-4 z-[60] flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2" aria-live="polite">
        {toasts.map((t) => (
          <div
            key={t.id}
            className={cn(
              'animate-fade-in pointer-events-auto flex items-start gap-2 rounded-lg border bg-white px-3 py-2.5 text-sm shadow-lg',
              t.tone === 'success' ? 'border-emerald-200' : 'border-red-200',
            )}
          >
            {t.tone === 'success' ? <CheckCircle2 className="mt-0.5 size-4 text-emerald-600" /> : <AlertCircle className="mt-0.5 size-4 text-red-600" />}
            <p className="flex-1 text-slate-700">{t.message}</p>
            <button type="button" aria-label="Dismiss" onClick={() => setToasts((x) => x.filter((y) => y.id !== t.id))}>
              <X className="size-4 text-slate-400" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export const useToast = () => useContext(ToastContext)
