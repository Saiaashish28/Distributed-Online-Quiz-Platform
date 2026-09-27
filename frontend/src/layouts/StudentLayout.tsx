import { KeyRound, LogOut } from 'lucide-react'
import { Link, Outlet, useNavigate } from 'react-router-dom'
import { Logo } from '@/components/Logo'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/hooks/useAuth'

export function StudentLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-30 border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-3 px-4 py-3 sm:px-6">
          <Logo to="/student" />
          <div className="flex items-center gap-2">
            <div className="hidden text-right sm:block">
              <p className="text-sm font-medium text-slate-800">{user?.student?.fullName ?? user?.name}</p>
              <p className="text-xs text-slate-500">{user?.student?.registerNumber}</p>
            </div>
            <Button variant="ghost" size="icon" asChild aria-label="Change password">
              <Link to="/change-password">
                <KeyRound />
              </Link>
            </Button>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => {
                logout()
                navigate('/login?role=student')
              }}
            >
              <LogOut /> Sign out
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6 sm:px-6 animate-fade-in">
        <Outlet />
      </main>
    </div>
  )
}
