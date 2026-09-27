import { ArrowLeft, Globe2, KeyRound, User, UsersRound } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ProctoringFields, ReleaseFields, TimingFields, type TimingValue } from '@/components/admin/AssignmentFields'
import { StudentPicker } from '@/components/admin/StudentPicker'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { Alert, ErrorAlert, LoadingBlock, PageHeader } from '@/components/ui/feedback'
import { Checkbox, Field, Input, Select } from '@/components/ui/form'
import { useApi } from '@/hooks/useApi'
import { api, errorMessage } from '@/lib/api'
import { cn, fromLocalInput } from '@/lib/utils'
import type { AssignmentDetail, AssignmentType, Group, ProctoringSettings, QuizSummary, ReleaseMode, StudentRef } from '@/types/api'

const TYPES: { value: AssignmentType; label: string; description: string; icon: typeof UsersRound }[] = [
  { value: 'GROUP', label: 'Groups', description: 'Academic, course-based or custom groups', icon: UsersRound },
  { value: 'INDIVIDUAL', label: 'Individual students', description: 'Pick students by register number', icon: User },
  { value: 'CODE', label: 'Join code', description: 'Students enter a code; eligibility still applies', icon: KeyRound },
  { value: 'OPEN', label: 'Open', description: 'All active registered students', icon: Globe2 },
]

export default function AssignmentFormPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const quizzes = useApi<QuizSummary[]>('/api/admin/quizzes')
  const groups = useApi<Group[]>('/api/admin/groups')
  const published = quizzes.data?.filter((q) => q.status === 'PUBLISHED') ?? []

  const [quizId, setQuizId] = useState(params.get('quizId') ?? '')
  const [name, setName] = useState('')
  const [type, setType] = useState<AssignmentType>('GROUP')
  const [groupIds, setGroupIds] = useState<number[]>([])
  const [students, setStudents] = useState<StudentRef[]>([])
  const [codeOpenAccess, setCodeOpenAccess] = useState(false)
  const [timing, setTiming] = useState<TimingValue>({ availableFrom: '', deadline: '', durationMinutes: '', maxAttempts: '1' })
  const [liveSession, setLiveSession] = useState(false)
  const [release, setRelease] = useState<ReleaseMode>('MANUAL')
  const [leaderboard, setLeaderboard] = useState(false)
  const [proctoring, setProctoring] = useState<ProctoringSettings>({ enabled: false, warningThreshold: 3, showWarnings: true, flagForReview: true, requireFullscreen: false })
  const [openConfirmed, setOpenConfirmed] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  const quiz = published.find((q) => String(q.id) === quizId)
  useEffect(() => {
    if (quiz) {
      setTiming((t) => ({ ...t, durationMinutes: t.durationMinutes || String(quiz.durationMinutes) }))
      setName((n) => n || quiz.title)
    }
  }, [quiz])

  const submit = async () => {
    setPending(true)
    setError(null)
    try {
      const a = await api.post<AssignmentDetail>('/api/admin/assignments', {
        quizId: Number(quizId),
        name,
        type,
        groupIds: type === 'GROUP' || type === 'CODE' ? groupIds : [],
        studentIds: type === 'INDIVIDUAL' || type === 'CODE' ? students.map((s) => s.id) : [],
        codeOpenAccess: type === 'CODE' && codeOpenAccess,
        availableFrom: fromLocalInput(timing.availableFrom),
        deadline: fromLocalInput(timing.deadline),
        durationMinutes: Number(timing.durationMinutes) || undefined,
        maxAttempts: Number(timing.maxAttempts) || 1,
        resultsReleaseMode: release,
        leaderboardEnabled: leaderboard,
        liveSession,
        proctoring,
      })
      navigate(`/admin/assignments/${a.summary.id}`)
    } catch (e) {
      setError(errorMessage(e))
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } finally {
      setPending(false)
    }
  }

  const targetsOk =
    (type === 'GROUP' && groupIds.length > 0) ||
    (type === 'INDIVIDUAL' && students.length > 0) ||
    (type === 'CODE' && (codeOpenAccess || groupIds.length > 0 || students.length > 0)) ||
    (type === 'OPEN' && openConfirmed)

  if (quizzes.loading && !quizzes.data) return <LoadingBlock />

  return (
    <>
      <PageHeader
        back={
          <Link to="/admin/assignments" className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
            <ArrowLeft className="size-4" /> Assignments
          </Link>
        }
        title="New assignment"
      />
      <ErrorAlert error={error} />
      <div className="mt-4 grid gap-6 lg:grid-cols-[1fr_360px]">
        <div className="space-y-6">
          <Card>
            <CardHeader title="Quiz" />
            <CardBody className="grid gap-4 sm:grid-cols-2">
              <Field label="Published quiz" required>
                {(id) => (
                  <Select id={id} value={quizId} onChange={(e) => setQuizId(e.target.value)}>
                    <option value="">Select a quiz</option>
                    {published.map((q) => (
                      <option key={q.id} value={q.id}>
                        {q.title} ({q.questionCount} Q)
                      </option>
                    ))}
                  </Select>
                )}
              </Field>
              <Field label="Assignment name" required hint="e.g. III-CSE A — Unit test 1">
                {(id) => <Input id={id} value={name} onChange={(e) => setName(e.target.value)} maxLength={200} />}
              </Field>
              {published.length === 0 && (
                <Alert tone="warning" className="sm:col-span-2">
                  No published quizzes. <Link className="underline" to="/admin/quizzes">Publish a quiz</Link> first.
                </Alert>
              )}
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Audience" description="Students never see assignments they aren't eligible for." />
            <CardBody className="space-y-5">
              <div className="grid gap-2 sm:grid-cols-2" role="radiogroup" aria-label="Assignment type">
                {TYPES.map(({ value, label, description, icon: Icon }) => (
                  <button
                    key={value}
                    type="button"
                    role="radio"
                    aria-checked={type === value}
                    onClick={() => setType(value)}
                    className={cn(
                      'flex items-start gap-3 rounded-lg border p-3 text-left cursor-pointer',
                      type === value ? 'border-primary-500 bg-primary-50 ring-1 ring-primary-500' : 'border-slate-200 hover:bg-slate-50',
                    )}
                  >
                    <Icon className="mt-0.5 size-5 text-primary-600" />
                    <span>
                      <span className="block text-sm font-medium text-slate-900">{label}</span>
                      <span className="block text-xs text-slate-500">{description}</span>
                    </span>
                  </button>
                ))}
              </div>

              {(type === 'GROUP' || type === 'CODE') && (
                <div>
                  <p className="mb-1.5 text-sm font-medium text-slate-700">{type === 'CODE' ? 'Eligible groups (optional)' : 'Groups'}</p>
                  {groups.data?.length ? (
                    <div className="flex flex-wrap gap-2">
                      {groups.data.map((g) => {
                        const on = groupIds.includes(g.id)
                        return (
                          <button
                            key={g.id}
                            type="button"
                            aria-pressed={on}
                            onClick={() => setGroupIds((ids) => (on ? ids.filter((x) => x !== g.id) : [...ids, g.id]))}
                            className={cn(
                              'rounded-lg border px-3 py-1.5 text-left text-sm cursor-pointer',
                              on ? 'border-primary-500 bg-primary-50 text-primary-800' : 'border-slate-300 hover:bg-slate-50',
                            )}
                          >
                            <span className="font-medium">{g.name}</span>
                            <span className="ml-2 text-xs text-slate-500">{g.memberCount} students</span>
                          </button>
                        )
                      })}
                    </div>
                  ) : (
                    <p className="text-sm text-slate-500">
                      No groups yet. <Link className="text-primary-700 underline" to="/admin/groups">Create groups</Link>.
                    </p>
                  )}
                </div>
              )}
              {(type === 'INDIVIDUAL' || type === 'CODE') && (
                <div>
                  <p className="mb-1.5 text-sm font-medium text-slate-700">{type === 'CODE' ? 'Eligible students (optional)' : 'Students'}</p>
                  <StudentPicker value={students} onChange={setStudents} />
                </div>
              )}
              {type === 'CODE' && (
                <div className="rounded-lg bg-amber-50 p-3">
                  <Checkbox
                    checked={codeOpenAccess}
                    onChange={setCodeOpenAccess}
                    label="Open access: any active student with the code may join"
                    description="Off by default — the code then only works for the groups/students selected above."
                  />
                </div>
              )}
              {type === 'OPEN' && (
                <div className="rounded-lg bg-amber-50 p-3">
                  <Checkbox checked={openConfirmed} onChange={setOpenConfirmed} label="I understand every active registered student will see this quiz" />
                </div>
              )}
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Timing" />
            <CardBody className="space-y-4">
              <TimingFields value={timing} onChange={setTiming} />
              <Checkbox
                checked={liveSession}
                onChange={setLiveSession}
                label="Live session"
                description="Students wait in a waiting room until you start the session; ending it auto-submits running attempts."
              />
            </CardBody>
          </Card>
        </div>

        <div className="space-y-6">
          <Card>
            <CardHeader title="Results" />
            <CardBody>
              <ReleaseFields mode={release} leaderboard={leaderboard} onMode={setRelease} onLeaderboard={setLeaderboard} />
            </CardBody>
          </Card>
          <Card>
            <CardHeader title="Exam integrity" />
            <CardBody>
              <ProctoringFields value={proctoring} onChange={setProctoring} />
            </CardBody>
          </Card>
          <Card className="p-4">
            {quiz && (
              <p className="mb-3 text-sm text-slate-600">
                <Badge tone="indigo">{quiz.questionCount} questions</Badge> <Badge>{timing.durationMinutes || quiz.durationMinutes} min</Badge>
              </p>
            )}
            <Button className="w-full" size="lg" onClick={submit} loading={pending} disabled={!quizId || !name.trim() || !targetsOk}>
              Create assignment
            </Button>
          </Card>
        </div>
      </div>
    </>
  )
}
