import { CalendarClock, Clock, KeyRound, ListChecks, Radio, ShieldCheck } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { StatusBadge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { EmptyState, ErrorAlert, LoadingBlock, PageHeader } from '@/components/ui/feedback'
import { Input } from '@/components/ui/form'
import { Tabs } from '@/components/ui/tabs'
import { useToast } from '@/components/ui/toast'
import { useApi } from '@/hooks/useApi'
import { useAuth } from '@/hooks/useAuth'
import { api, errorMessage } from '@/lib/api'
import { formatDateTime, roman } from '@/lib/utils'
import type { StudentAssignment } from '@/types/api'

const DONE = new Set(['SUBMITTED', 'AUTO_SUBMITTED', 'RESULTS_RELEASED', 'EXPIRED'])

export default function StudentDashboard() {
  const { user } = useAuth()
  const { data, error, loading, reload } = useApi<StudentAssignment[]>('/api/student/assignments')
  const [tab, setTab] = useState<'current' | 'done'>('current')
  const current = data?.filter((a) => !DONE.has(a.status) || a.canStart) ?? []
  const done = data?.filter((a) => DONE.has(a.status) && !a.canStart) ?? []
  const list = tab === 'current' ? current : done
  const s = user?.student

  return (
    <>
      <PageHeader
        title={`Welcome, ${s?.fullName ?? user?.name}`}
        description={s ? `${s.registerNumber} · ${roman(s.academicYear)} year ${s.department}${s.section ? ` · Section ${s.section}` : ''}` : undefined}
      />
      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div className="space-y-4">
          <Tabs
            value={tab}
            onChange={setTab}
            tabs={[
              { value: 'current', label: `Assigned (${current.length})` },
              { value: 'done', label: `Completed (${done.length})` },
            ]}
          />
          {loading && !data ? (
            <LoadingBlock />
          ) : error ? (
            <ErrorAlert error={error} onRetry={reload} />
          ) : list.length === 0 ? (
            <Card>
              <EmptyState
                icon={<ListChecks />}
                title={tab === 'current' ? 'No quizzes assigned right now' : 'Nothing completed yet'}
                description={tab === 'current' ? 'Quizzes assigned to your class, groups or courses will appear here. Got a join code? Enter it on the right.' : undefined}
              />
            </Card>
          ) : (
            <div className="grid gap-3 sm:grid-cols-2">
              {list.map((a) => (
                <AssignmentCard key={a.id} a={a} />
              ))}
            </div>
          )}
        </div>
        <JoinByCode onJoined={reload} />
      </div>
    </>
  )
}

function AssignmentCard({ a }: { a: StudentAssignment }) {
  const action = a.inProgressAttemptId ? (
    <Button asChild size="sm">
      <Link to={`/student/attempts/${a.inProgressAttemptId}`}>Continue quiz</Link>
    </Button>
  ) : a.status === 'RESULTS_RELEASED' && a.latestFinalAttemptId && !a.canStart ? (
    <Button asChild size="sm" variant="secondary">
      <Link to={`/student/attempts/${a.latestFinalAttemptId}/result`}>View result</Link>
    </Button>
  ) : (
    <Button asChild size="sm" variant={a.canStart ? 'default' : 'secondary'}>
      <Link to={`/student/assignments/${a.id}`}>{a.canStart ? 'Open' : 'Details'}</Link>
    </Button>
  )
  return (
    <Card className="flex flex-col p-4">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate font-semibold text-slate-900">{a.name}</p>
          <p className="truncate text-sm text-slate-500">
            {a.quizTitle}
            {a.course && ` · ${a.course.courseCode}`}
          </p>
        </div>
        <StatusBadge status={a.status} />
      </div>
      <dl className="mt-3 space-y-1.5 text-sm text-slate-600">
        <div className="flex items-center gap-2">
          <Clock className="size-4 text-slate-400" /> {a.durationMinutes} min · {a.questionCount} questions
        </div>
        {a.deadline && (
          <div className="flex items-center gap-2">
            <CalendarClock className="size-4 text-slate-400" /> Due {formatDateTime(a.deadline)}
          </div>
        )}
        {a.availableFrom && a.status === 'UPCOMING' && (
          <div className="flex items-center gap-2">
            <CalendarClock className="size-4 text-slate-400" /> Opens {formatDateTime(a.availableFrom)}
          </div>
        )}
        {a.liveSession && (
          <div className="flex items-center gap-2">
            <Radio className="size-4 text-slate-400" /> Live session · <StatusBadge status={a.sessionState} />
          </div>
        )}
        {a.proctoring.enabled && (
          <div className="flex items-center gap-2">
            <ShieldCheck className="size-4 text-slate-400" /> Browser monitoring on
          </div>
        )}
      </dl>
      <div className="mt-4 flex items-center justify-between pt-1">
        <span className="text-xs text-slate-500">
          Attempts {a.attemptsUsed}/{a.maxAttempts}
        </span>
        {action}
      </div>
    </Card>
  )
}

function JoinByCode({ onJoined }: { onJoined: () => void }) {
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const navigate = useNavigate()
  const toast = useToast()
  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setPending(true)
    setError(null)
    try {
      const a = await api.post<StudentAssignment>('/api/student/assignments/join', { code: code.trim() })
      toast(`Joined "${a.name}"`)
      onJoined()
      navigate(`/student/assignments/${a.id}`)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setPending(false)
    }
  }
  return (
    <Card className="h-fit">
      <CardHeader title="Join with a code" description="Your instructor may share a 6-character code." />
      <CardBody>
        <form onSubmit={submit} className="space-y-3">
          <div className="relative">
            <KeyRound className="pointer-events-none absolute left-3 top-2.5 size-4 text-slate-400" />
            <Input
              aria-label="Join code"
              className="pl-9 font-mono uppercase tracking-widest"
              maxLength={12}
              placeholder="ABC123"
              value={code}
              onChange={(e) => setCode(e.target.value.toUpperCase())}
              required
            />
          </div>
          <ErrorAlert error={error} />
          <Button type="submit" className="w-full" loading={pending}>
            Join quiz
          </Button>
          <p className="text-xs text-slate-500">A code only works if the quiz is assigned to you, unless your instructor opened it to everyone.</p>
        </form>
      </CardBody>
    </Card>
  )
}
