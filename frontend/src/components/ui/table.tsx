import type { HTMLAttributes, TdHTMLAttributes, ThHTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

export function Table({ className, ...props }: HTMLAttributes<HTMLTableElement>) {
  return (
    <div className="overflow-x-auto">
      <table className={cn('w-full text-left text-sm', className)} {...props} />
    </div>
  )
}

export function THead({ className, ...props }: HTMLAttributes<HTMLTableSectionElement>) {
  return <thead className={cn('border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500', className)} {...props} />
}

export function Th({ className, ...props }: ThHTMLAttributes<HTMLTableCellElement>) {
  return <th scope="col" className={cn('whitespace-nowrap px-4 py-2.5 font-medium', className)} {...props} />
}

export function Td({ className, ...props }: TdHTMLAttributes<HTMLTableCellElement>) {
  return <td className={cn('px-4 py-2.5 align-middle text-slate-700', className)} {...props} />
}

export function Tr({ className, ...props }: HTMLAttributes<HTMLTableRowElement>) {
  return <tr className={cn('border-b border-slate-100 last:border-0 hover:bg-slate-50/60', className)} {...props} />
}
