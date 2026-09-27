import {
  BookOpen,
  FileSpreadsheet,
  GraduationCap,
  LayoutDashboard,
  Library,
  LogOut,
  Menu,
  Send,
  Users,
  UsersRound,
  X,
} from 'lucide-react'
import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { Logo } from '@/components/Logo'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/hooks/useAuth'
import { cn } from '@/lib/utils'

const NAV = [
  { to: '/admin', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/admin/students', label: 'Students', icon: Users },
  { to: '/admin/courses', label: 'Courses', icon: GraduationCap },
  { to: '/admin/groups', label: 'Groups', icon: UsersRound },
  { to: '/admin/question-banks', label: 'Question banks', icon: Library },
  { to: '/admin/quizzes', label: 'Quizzes', icon: BookOpen },
  { to: '/admin/assignments', label: 'Assignments', icon: Send },
  { to: '/admin/exports', label: 'Exports', icon: FileSpreadsheet },
]

export function AdminLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)

  const nav = (
    <nav className="flex flex-col gap-0.5" aria-label="Admin navigation">
      {NAV.map(({ to, label, icon: Icon, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          onClick={() => setOpen(false)}
          className={({ isActive }) =>
            cn(
              'flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
              isActive ? 'bg-primary-50 text-primary-700' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900',
            )
          }
        >
          <Icon className="size-4" aria-hidden />
          {label}
        </NavLink>
      ))}
    </nav>
  )

  const footer = (
    <div className="border-t border-slate-200 p-3">
      <p className="truncate px-2 text-sm font-medium text-slate-800">{user?.name}</p>
      <p className="truncate px-2 text-xs text-slate-500">{user?.email} · Admin</p>
      <Button
        variant="ghost"
        size="sm"
        className="mt-2 w-full justify-start"
        onClick={() => {
          logout()
          navigate('/login?role=admin')
        }}
      >
        <LogOut /> Sign out
      </Button>
    </div>
  )

  return (
    <div className="min-h-screen lg:flex">
      <aside className="sticky top-0 hidden h-screen w-60 shrink-0 flex-col border-r border-slate-200 bg-white lg:flex">
        <div className="px-5 py-4">
          <Logo to="/admin" />
        </div>
        <div className="flex-1 overflow-y-auto px-3">{nav}</div>
        {footer}
      </aside>

      <header className="sticky top-0 z-30 flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3 lg:hidden">
        <Logo to="/admin" />
        <Button variant="ghost" size="icon" aria-label="Open menu" onClick={() => setOpen(true)}>
          <Menu />
        </Button>
      </header>
      {open && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-slate-900/40" onClick={() => setOpen(false)} />
          <div className="absolute inset-y-0 left-0 flex w-64 flex-col bg-white shadow-xl">
            <div className="flex items-center justify-between px-4 py-3">
              <Logo to="/admin" />
              <Button variant="ghost" size="icon" aria-label="Close menu" onClick={() => setOpen(false)}>
                <X />
              </Button>
            </div>
            <div className="flex-1 overflow-y-auto px-3">{nav}</div>
            {footer}
          </div>
        </div>
      )}

      <main className="min-w-0 flex-1 px-4 py-6 sm:px-6 lg:px-8">
        <div className="mx-auto max-w-7xl animate-fade-in">
          <Outlet />
        </div>
      </main>
    </div>
  )
}

