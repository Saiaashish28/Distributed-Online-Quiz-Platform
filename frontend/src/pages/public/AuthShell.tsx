import type { ReactNode } from 'react'
import { Logo } from '@/components/Logo'
import { Card } from '@/components/ui/card'

export function AuthShell({ title, subtitle, children, footer }: { title: string; subtitle?: ReactNode; children: ReactNode; footer?: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-gradient-to-b from-white to-slate-100 px-4 py-10">
      <div className="mb-6">
        <Logo />
      </div>
      <Card className="w-full max-w-md p-6 sm:p-8">
        <h1 className="text-xl font-semibold text-slate-900">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
        <div className="mt-6">{children}</div>
      </Card>
      {footer && <div className="mt-4 text-sm text-slate-600">{footer}</div>}
    </div>
  )
}
