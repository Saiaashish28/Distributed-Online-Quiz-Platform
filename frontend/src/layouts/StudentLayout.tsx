import { KeyRound, LogOut } from 'lucide-react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { Logo } from '@/components/Logo'
import { useAuth } from '@/hooks/useAuth'
import { cn } from '@/lib/utils'

export function StudentLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-30 border-b border-slate-200 bg-white">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between gap-3 px-4 sm:px-6">
          <div className="flex items-center gap-6">
            <Logo to="/student" />
            <NavLink
              to="/student"
              end
              className={({ isActive }) => cn('hidden text-sm sm:block', isActive ? 'font-medium text-slate-900' : 'text-slate-500 hover:text-slate-900')}
            >
              My quizzes
            </NavLink>
          </div>
          <div className="flex items-center gap-4 text-sm">
            <div className="hidden text-right leading-tight sm:block">
              <p className="font-medium text-slate-800">{user?.student?.fullName ?? user?.name}</p>
              <p className="font-mono text-xs text-slate-500">{user?.student?.registerNumber}</p>
            </div>
            <Link to="/change-password" className="text-slate-500 hover:text-slate-900" aria-label="Change password" title="Change password">
              <KeyRound className="size-4" />
            </Link>
            <button
              type="button"
              className="inline-flex items-center gap-1.5 text-slate-600 hover:text-slate-900 cursor-pointer"
              onClick={() => {
                logout()
                navigate('/login?role=student')
              }}
            >
              <LogOut className="size-4" /> <span className="hidden sm:inline">Sign out</span>
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-7 sm:px-6 animate-fade-in">
        <Outlet />
      </main>
    </div>
  )
}
