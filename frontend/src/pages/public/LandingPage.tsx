import { BarChart3, FileSpreadsheet, Radio, ShieldCheck, UsersRound, Zap } from 'lucide-react'
import { Link, Navigate } from 'react-router-dom'
import { Logo } from '@/components/Logo'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/hooks/useAuth'

const FEATURES = [
  { icon: UsersRound, title: 'Academic & course groups', text: 'Target III-CSE, II-ECE-A or a course cohort. Dynamic groups follow roster changes automatically.' },
  { icon: Radio, title: 'Live sessions', text: 'Start and end sessions from the console; students join a waiting room and get real-time updates.' },
  { icon: Zap, title: 'Autosave & recovery', text: 'Every answer is saved to the server. Refresh or reconnect and continue with the same timer.' },
  { icon: ShieldCheck, title: 'Fair by design', text: 'Server-side timers and scoring. Answer keys never reach the browser before results are released.' },
  { icon: BarChart3, title: 'Results & review', text: 'Release results when you choose, with optional leaderboards and per-question review.' },
  { icon: FileSpreadsheet, title: 'Excel marksheets', text: 'Export marksheets and detailed responses mapped to register numbers, including non-submitters.' },
]

export default function LandingPage() {
  const { user, loading } = useAuth()
  if (!loading && user) return <Navigate to={user.role === 'ADMIN' ? '/admin' : '/student'} replace />
  return (
    <div className="min-h-screen bg-gradient-to-b from-white to-slate-50">
      <header className="mx-auto flex max-w-6xl items-center justify-between px-4 py-4 sm:px-6">
        <Logo />
        <div className="flex gap-2">
          <Button variant="ghost" asChild>
            <Link to="/login?role=admin">Faculty sign in</Link>
          </Button>
          <Button asChild>
            <Link to="/login?role=student">Student sign in</Link>
          </Button>
        </div>
      </header>

      <section className="mx-auto max-w-6xl px-4 pb-12 pt-14 text-center sm:px-6 sm:pt-20">
        <p className="mx-auto mb-4 inline-flex rounded-full bg-primary-50 px-3 py-1 text-xs font-medium text-primary-700">
          Distributed online quiz platform
        </p>
        <h1 className="mx-auto max-w-3xl text-4xl font-bold tracking-tight text-slate-900 sm:text-5xl">
          Run quizzes for every class, section and course — reliably.
        </h1>
        <p className="mx-auto mt-5 max-w-2xl text-lg text-slate-600">
          QuizSphere lets faculty manage rosters, author question banks, assign quizzes to the right students and export
          marksheets, while students take quizzes with autosave and live updates.
        </p>
        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <Button size="lg" asChild>
            <Link to="/login?role=student">I'm a student</Link>
          </Button>
          <Button size="lg" variant="secondary" asChild>
            <Link to="/login?role=admin">I'm faculty / admin</Link>
          </Button>
        </div>
      </section>

      <section className="mx-auto grid max-w-6xl gap-4 px-4 pb-20 sm:grid-cols-2 sm:px-6 lg:grid-cols-3">
        {FEATURES.map(({ icon: Icon, title, text }) => (
          <div key={title} className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <Icon className="size-6 text-primary-600" aria-hidden />
            <h2 className="mt-3 font-semibold text-slate-900">{title}</h2>
            <p className="mt-1 text-sm text-slate-600">{text}</p>
          </div>
        ))}
      </section>
      <footer className="border-t border-slate-200 py-6 text-center text-xs text-slate-500">
        Browser monitoring records signals for review; it is not proof of misconduct and cannot detect everything.
      </footer>
    </div>
  )
}
