import { Link } from 'react-router-dom'
import { cn } from '@/lib/utils'

export function LogoMark({ className }: { className?: string }) {
  return (
    <span className={cn('grid size-7 place-items-center rounded-[5px] bg-primary-700 text-white', className)} aria-hidden>
      <svg viewBox="0 0 32 32" className="size-4">
        <path d="M8 16.5l5 5L24 10.5" fill="none" stroke="currentColor" strokeWidth="3.2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </span>
  )
}

export function Logo({ to = '/', inverted }: { to?: string; inverted?: boolean }) {
  return (
    <Link to={to} className={cn('flex items-center gap-2 font-semibold', inverted ? 'text-white' : 'text-slate-900')}>
      <LogoMark className={inverted ? 'bg-white text-primary-800' : undefined} />
      <span className="text-[17px] tracking-tight">QuizSphere</span>
    </Link>
  )
}
