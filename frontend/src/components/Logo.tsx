import { Link } from 'react-router-dom'

export function Logo({ to = '/' }: { to?: string }) {
  return (
    <Link to={to} className="flex items-center gap-2 font-semibold text-slate-900">
      <span className="grid size-8 place-items-center rounded-lg bg-primary-600 text-white">
        <svg viewBox="0 0 32 32" className="size-5" aria-hidden>
          <circle cx="15" cy="15" r="8" fill="none" stroke="currentColor" strokeWidth="3" />
          <path d="M20 20l5 5" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
        </svg>
      </span>
      <span className="text-lg tracking-tight">QuizSphere</span>
    </Link>
  )
}
