import { useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

const control =
  'block w-full rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-200 disabled:bg-slate-100 disabled:text-slate-500'

export function Input({ className, ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={cn(control, 'h-9', className)} {...props} />
}

export function Textarea({ className, ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea className={cn(control, 'min-h-20 py-2', className)} {...props} />
}

export function Select({ className, children, ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select className={cn(control, 'h-9 pr-8', className)} {...props}>
      {children}
    </select>
  )
}

export function Label({ htmlFor, children, className }: { htmlFor?: string; children: ReactNode; className?: string }) {
  return (
    <label htmlFor={htmlFor} className={cn('mb-1 block text-sm font-medium text-slate-700', className)}>
      {children}
    </label>
  )
}

/** Label + control + hint/error, wired with ids for accessibility. */
export function Field({
  label,
  hint,
  error,
  required,
  children,
  className,
}: {
  label: ReactNode
  hint?: ReactNode
  error?: string | null
  required?: boolean
  children: (id: string) => ReactNode
  className?: string
}) {
  const id = useId()
  return (
    <div className={className}>
      <Label htmlFor={id}>
        {label}
        {required && <span className="ml-0.5 text-red-600" aria-hidden>*</span>}
      </Label>
      {children(id)}
      {error ? (
        <p className="mt-1 text-xs text-red-600" role="alert">{error}</p>
      ) : (
        hint && <p className="mt-1 text-xs text-slate-500">{hint}</p>
      )}
    </div>
  )
}

export function Checkbox({
  label,
  description,
  checked,
  onChange,
  disabled,
}: {
  label: ReactNode
  description?: ReactNode
  checked: boolean
  onChange: (v: boolean) => void
  disabled?: boolean
}) {
  const id = useId()
  return (
    <div className="flex items-start gap-2.5">
      <input
        id={id}
        type="checkbox"
        className="mt-0.5 size-4 rounded border-slate-300 text-primary-600 accent-primary-600"
        checked={checked}
        disabled={disabled}
        onChange={(e) => onChange(e.target.checked)}
      />
      <label htmlFor={id} className="text-sm">
        <span className="font-medium text-slate-800">{label}</span>
        {description && <span className="block text-xs text-slate-500">{description}</span>}
      </label>
    </div>
  )
}
