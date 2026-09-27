import { ArrowRight } from 'lucide-react'
import { Link, Navigate } from 'react-router-dom'
import { Logo } from '@/components/Logo'
import { useAuth } from '@/hooks/useAuth'

const FACTS = [
  {
    term: 'For faculty',
    text: 'Import rosters, build class and course groups, author question banks, run live sessions, and export marksheets to Excel.',
  },
  {
    term: 'For students',
    text: 'Sign in with your register number, take the quizzes assigned to you, and see your results once they are released.',
  },
  {
    term: 'Fair by design',
    text: 'Timing and scoring happen on the server. Answers save as you go and survive a refresh or a dropped connection.',
  },
]

export default function LandingPage() {
  const { user, loading } = useAuth()
  if (!loading && user) return <Navigate to={user.role === 'ADMIN' ? '/admin' : '/student'} replace />
  return (
    <div className="flex min-h-screen flex-col bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4 sm:px-6">
          <Logo />
          <Link to="/register" className="text-sm text-slate-600 hover:text-slate-900">
            Create a student account
          </Link>
        </div>
      </header>

      <main className="mx-auto grid w-full max-w-5xl flex-1 gap-12 px-4 py-14 sm:px-6 lg:grid-cols-[1fr_380px] lg:py-20">
        <section>
          <h1 className="max-w-xl text-3xl font-semibold leading-tight text-slate-900 sm:text-[40px]">
            Class quizzes and tests, run from one place.
          </h1>
          <p className="mt-4 max-w-lg text-base leading-relaxed text-slate-600">
            QuizSphere is the department's online assessment system. Faculty assign quizzes to the right students;
            students take them in the browser on any device.
          </p>
          <dl className="mt-10 max-w-xl divide-y divide-slate-200 border-y border-slate-200">
            {FACTS.map((f) => (
              <div key={f.term} className="grid gap-1 py-4 sm:grid-cols-[140px_1fr] sm:gap-6">
                <dt className="text-sm font-medium text-slate-900">{f.term}</dt>
                <dd className="text-sm leading-relaxed text-slate-600">{f.text}</dd>
              </div>
            ))}
          </dl>
        </section>

        <section aria-labelledby="signin" className="order-first h-fit rounded-lg lg:order-none border border-slate-200 bg-white">
          <h2 id="signin" className="border-b border-slate-200 px-5 py-3.5 text-[15px] font-semibold text-slate-900">
            Sign in
          </h2>
          <div className="divide-y divide-slate-200">
            <SignInRow to="/login?role=student" title="Student" detail="Use your register number" />
            <SignInRow to="/login?role=admin" title="Faculty / administrator" detail="Use your institutional email" />
          </div>
          <p className="border-t border-slate-200 bg-slate-50 px-5 py-3 text-[13px] text-slate-500">
            First time? Your department may already have created your account — the initial password is your register number.
          </p>
        </section>
      </main>

      <footer className="border-t border-slate-200 py-5 text-center text-xs text-slate-500">
        Browser monitoring, when enabled for a quiz, records page-leave events and is disclosed before you start.
      </footer>
    </div>
  )
}

function SignInRow({ to, title, detail }: { to: string; title: string; detail: string }) {
  return (
    <Link to={to} className="group flex items-center justify-between px-5 py-4 hover:bg-slate-50">
      <span>
        <span className="block text-sm font-medium text-slate-900">{title}</span>
        <span className="block text-[13px] text-slate-500">{detail}</span>
      </span>
      <ArrowRight className="size-4 text-slate-400 transition-transform group-hover:translate-x-0.5 group-hover:text-primary-700" />
    </Link>
  )
}
