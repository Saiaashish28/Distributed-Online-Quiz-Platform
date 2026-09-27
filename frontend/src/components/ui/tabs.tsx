import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

export function Tabs<T extends string>({
  value,
  onChange,
  tabs,
}: {
  value: T
  onChange: (v: T) => void
  tabs: { value: T; label: ReactNode }[]
}) {
  return (
    <div role="tablist" className="flex gap-1 overflow-x-auto border-b border-slate-200">
      {tabs.map((t) => (
        <button
          key={t.value}
          role="tab"
          type="button"
          aria-selected={value === t.value}
          onClick={() => onChange(t.value)}
          className={cn(
            '-mb-px inline-flex items-center gap-1.5 whitespace-nowrap border-b-2 px-3 py-2 text-sm font-medium transition-colors cursor-pointer',
            value === t.value ? 'border-primary-600 text-primary-700' : 'border-transparent text-slate-500 hover:text-slate-800',
          )}
        >
          {t.label}
        </button>
      ))}
    </div>
  )
}
