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

interface NavItem {
  to: string
  label: string
  icon: typeof Users
  end?: boolean
}

const SECTIONS: { label: string | null; items: NavItem[] }[] = [
  { label: null, items: [{ to: '/admin', label: 'Overview', icon: LayoutDashboard, end: true }] },
  {
    label: 'People',
    items: [
      { to: '/admin/students', label: 'Students', icon: Users },
      { to: '/admin/courses', label: 'Courses', icon: GraduationCap },
      { to: '/admin/groups', label: 'Groups', icon: UsersRound },
    ],
  },
  {
    label: 'Assessment',
    items: [
      { to: '/admin/question-banks', label: 'Question banks', icon: Library },
      { to: '/admin/quizzes', label: 'Quizzes', icon: BookOpen },
      { to: '/admin/assignments', label: 'Assignments', icon: Send },
    ],
  },
  { label: 'Reports', items: [{ to: '/admin/exports', label: 'Exports', icon: FileSpreadsheet }] },
]

export function AdminLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)

  const nav = (
    <nav className="space-y-5" aria-label="Admin navigation">
      {SECTIONS.map((section, i) => (
        <div key={i}>
          {section.label && <p className="mb-1 px-3 text-[11px] font-medium uppercase tracking-[0.08em] text-slate-500">{section.label}</p>}
          <div className="space-y-px">
            {section.items.map(({ to, label, icon: Icon, end }) => (
              <NavLink
                key={to}
                to={to}
                end={end}
                onClick={() => setOpen(false)}
                className={({ isActive }) =>
                  cn(
                    'relative flex items-center gap-2.5 rounded-md px-3 py-1.5 text-sm transition-colors',
                    isActive
                      ? 'bg-white font-medium text-slate-900 shadow-[0_0_0_1px_var(--color-slate-200)] before:absolute before:inset-y-1.5 before:left-0 before:w-0.5 before:rounded-full before:bg-primary-600'
                      : 'text-slate-600 hover:bg-slate-200/50 hover:text-slate-900',
                  )
                }
              >
                <Icon className="size-4 text-slate-500" aria-hidden />
                {label}
              </NavLink>
            ))}
          </div>
        </div>
      ))}
    </nav>
  )

  const footer = (
    <div className="border-t border-slate-200 px-4 py-3">
      <p className="truncate text-sm font-medium text-slate-800">{user?.name}</p>
      <p className="truncate text-xs text-slate-500">{user?.email}</p>
      <button
        type="button"
        className="mt-2 inline-flex items-center gap-1.5 text-[13px] text-slate-600 hover:text-slate-900 cursor-pointer"
        onClick={() => {
          logout()
          navigate('/login?role=admin')
        }}
      >
        <LogOut className="size-3.5" /> Sign out
      </button>
    </div>
  )

  return (
    <div className="min-h-screen lg:flex">
      <aside className="sticky top-0 hidden h-screen w-56 shrink-0 flex-col border-r border-slate-200 bg-slate-100 lg:flex">
        <div className="px-4 py-4">
          <Logo to="/admin" />
        </div>
        <div className="flex-1 overflow-y-auto px-2 py-2">{nav}</div>
        {footer}
      </aside>

      <header className="sticky top-0 z-30 flex items-center justify-between border-b border-slate-200 bg-slate-100 px-4 py-2.5 lg:hidden">
        <Logo to="/admin" />
        <Button variant="ghost" size="icon" aria-label="Open menu" onClick={() => setOpen(true)}>
          <Menu />
        </Button>
      </header>
      {open && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-slate-900/35" onClick={() => setOpen(false)} />
          <div className="absolute inset-y-0 left-0 flex w-64 flex-col bg-slate-100">
            <div className="flex items-center justify-between px-4 py-3">
              <Logo to="/admin" />
              <Button variant="ghost" size="icon" aria-label="Close menu" onClick={() => setOpen(false)}>
                <X />
              </Button>
            </div>
            <div className="flex-1 overflow-y-auto px-2 py-2">{nav}</div>
            {footer}
          </div>
        </div>
      )}

      <main className="min-w-0 flex-1 px-4 py-7 sm:px-8">
        <div className="mx-auto max-w-6xl animate-fade-in">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
