import type { ReactNode } from 'react'
import { Logo } from '@/components/Logo'

export function AuthShell({ title, subtitle, children, footer }: { title: string; subtitle?: ReactNode; children: ReactNode; footer?: ReactNode }) {
  return (
    <div className="grid min-h-screen lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
      <aside className="hidden flex-col justify-between bg-primary-800 p-10 text-primary-50 lg:flex">
        <Logo inverted />
        <div className="max-w-sm">
          <p className="text-2xl font-semibold leading-snug text-white">Online quizzes for your department.</p>
          <p className="mt-3 text-sm leading-relaxed text-primary-100/80">
            Answers are saved as you go. If your connection drops or the page reloads, you continue where you left off with the same time remaining.
          </p>
        </div>
        <p className="text-xs text-primary-100/60">QuizSphere</p>
      </aside>
      <div className="flex flex-col bg-white">
        <div className="px-6 pt-6 lg:hidden">
          <Logo />
        </div>
        <div className="flex flex-1 items-center justify-center px-6 py-10">
          <div className="w-full max-w-sm">
            <h1 className="text-2xl font-semibold text-slate-900">{title}</h1>
            {subtitle && <p className="mt-1.5 text-sm text-slate-500">{subtitle}</p>}
            <div className="mt-7">{children}</div>
            {footer && <div className="mt-6 border-t border-slate-200 pt-4 text-sm text-slate-600">{footer}</div>}
          </div>
        </div>
      </div>
    </div>
  )
}
