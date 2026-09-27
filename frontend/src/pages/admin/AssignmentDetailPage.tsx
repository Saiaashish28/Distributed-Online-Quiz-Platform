import {
  Activity,
  ArrowLeft,
  CheckCircle2,
  Copy,
  Eye,
  FileSpreadsheet,
  Flag,
  Lock,
  Play,
  RefreshCw,
  Settings,
  ShieldAlert,
  Square,
  Trash2,
  Trophy,
  Unlock,
  Users,
  Wifi,
  WifiOff,
} from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ProctoringFields, ReleaseFields, TimingFields, type TimingValue } from '@/components/admin/AssignmentFields'
import { StudentPicker } from '@/components/admin/StudentPicker'
import { Badge, StatusBadge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardBody, CardHeader, StatCard } from '@/components/ui/card'
import { ConfirmDialog, Dialog } from '@/components/ui/dialog'
import { Alert, EmptyState, ErrorAlert, LoadingBlock, PageHeader } from '@/components/ui/feedback'
import { Checkbox, Field, Input, Select, Textarea } from '@/components/ui/form'
import { Table, THead, Td, Th, Tr } from '@/components/ui/table'
import { Tabs } from '@/components/ui/tabs'
import { useToast } from '@/components/ui/toast'
import { useApi } from '@/hooks/useApi'
import { useQuizSocket, type SocketMessage } from '@/hooks/useQuizSocket'
import { api, download, errorMessage } from '@/lib/api'
import { browserTimeZone, cn, formatDateTime, formatMarks, fromLocalInput, roman, toLocalInput } from '@/lib/utils'
import { ReviewCard } from '@/pages/student/ResultPage'
import type {
  AdminAttemptDetail,
  AssignmentDetail,
  AssignmentEvents,
  Group,
  ProctoringSettings,
  ProgressCounts,
  ReleaseMode,
  ResultsResponse,
  ReviewStatus,
  StudentRef,
} from '@/types/api'
import { TYPE_LABEL } from './AssignmentsPage'

type Tab = 'monitor' | 'results' | 'proctoring' | 'export' | 'settings'

interface Participant {
  studentId: number
  registerNumber: string
  name: string
  connectedAt?: string
}

interface LiveEvent {
  eventId: number
  registerNumber: string
  fullName: string
  eventType: string
  occurredAt: string
  warningCount: number
  flagged: boolean
  autoSubmitted?: boolean
}

export default function AssignmentDetailPage() {
  const { id } = useParams()
  const assignmentId = Number(id)
  const navigate = useNavigate()
  const toast = useToast()
  const { data, setData, error, loading, reload } = useApi<AssignmentDetail>(`/api/admin/assignments/${assignmentId}`)
  const [tab, setTab] = useState<Tab>('monitor')
  const [progress, setProgress] = useState<ProgressCounts | null>(null)
  const [participants, setParticipants] = useState<Participant[]>([])
  const [liveEvents, setLiveEvents] = useState<LiveEvent[]>([])
  const [confirm, setConfirm] = useState<null | 'start' | 'end' | 'release' | 'close' | 'reopen' | 'delete'>(null)
  const [resultsKey, setResultsKey] = useState(0)

  const onMessage = useCallback((msg: SocketMessage) => {
    const d = msg.data as Record<string, unknown>
    switch (msg.type) {
      case 'session_snapshot':
        setProgress(d.progress as ProgressCounts)
        setParticipants((d.participants as Participant[]) ?? [])
        break
      case 'submission_progress':
        setProgress(d as unknown as ProgressCounts)
        setResultsKey((k) => k + 1)
        break
      case 'participant_joined':
        setParticipants((p) => (p.some((x) => x.studentId === d.studentId) ? p : [...p, d as unknown as Participant]))
        break
      case 'participant_left':
        setParticipants((p) => p.filter((x) => x.studentId !== d.studentId))
        break
      case 'participant_count_updated':
        setProgress((p) => (p ? { ...p, connected: d.count as number } : p))
        break
      case 'proctoring_event_recorded':
        setLiveEvents((e) => [d as unknown as LiveEvent, ...e].slice(0, 50))
        break
      case 'session_started':
      case 'session_ended':
      case 'results_released':
        reload()
        break
    }
  }, [reload])
  const socket = useQuizSocket(assignmentId, onMessage)

  if (loading && !data) return <LoadingBlock />
  if (error || !data) return <ErrorAlert error={error ?? 'Not found'} onRetry={reload} />
  const s = data.summary
  const counts = progress ?? data.progress

  const act = async (path: string, message: string) => {
    const r = await api.post<AssignmentDetail>(`/api/admin/assignments/${assignmentId}/${path}`)
    setData(r)
    toast(message)
  }

  return (
    <>
      <PageHeader
        back={
          <Link to="/admin/assignments" className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
            <ArrowLeft className="size-4" /> Assignments
          </Link>
        }
        title={
          <span className="flex flex-wrap items-center gap-2">
            {s.name} <StatusBadge status={s.status} /> {s.liveSession && <StatusBadge status={s.sessionState} />}
            {data.proctoring.enabled ? <Badge tone="indigo">Monitoring on</Badge> : <Badge>Monitoring off</Badge>}
          </span>
        }
        description={
          <span>
            <Link className="text-primary-700 hover:underline" to={`/admin/quizzes/${s.quizId}`}>
              {s.quizTitle}
            </Link>{' '}
            · {data.questionCount} questions · {formatMarks(data.totalMarks)} marks · {s.durationMinutes} min · {TYPE_LABEL[s.type]}
          </span>
        }
        actions={
          <>
            {s.liveSession && s.sessionState === 'WAITING' && s.status === 'ACTIVE' && (
              <Button variant="success" onClick={() => setConfirm('start')}>
                <Play /> Start session
              </Button>
            )}
            {s.liveSession && s.sessionState === 'LIVE' && (
              <Button variant="destructive" onClick={() => setConfirm('end')}>
                <Square /> End session
              </Button>
            )}
            {!data.resultsReleased && (
              <Button variant="secondary" onClick={() => setConfirm('release')}>
                <Trophy /> Release results
              </Button>
            )}
          </>
        }
      />

      {s.joinCode && (
        <Card className="mb-4 flex flex-wrap items-center gap-3 p-4">
          <span className="text-sm text-slate-600">Join code</span>
          <span className="rounded-lg bg-slate-900 px-3 py-1 font-mono text-lg font-bold tracking-[0.3em] text-white">{s.joinCode}</span>
          <Button variant="ghost" size="sm" onClick={() => navigator.clipboard.writeText(s.joinCode!).then(() => toast('Code copied'))}>
            <Copy /> Copy
          </Button>
          <Button variant="ghost" size="sm" onClick={() => act('regenerate-code', 'New code generated').catch((e) => toast(errorMessage(e), 'error'))}>
            <RefreshCw /> Regenerate
          </Button>
          <span className="text-xs text-slate-500">{data.codeOpenAccess ? 'Open access: any active student with the code can join.' : 'Only eligible groups/students can join with this code.'}</span>
        </Card>
      )}

      <Tabs<Tab>
        value={tab}
        onChange={setTab}
        tabs={[
          { value: 'monitor', label: <><Activity className="size-4" /> Monitor</> },
          { value: 'results', label: <><Trophy className="size-4" /> Results</> },
          { value: 'proctoring', label: <><ShieldAlert className="size-4" /> Monitoring review</> },
          { value: 'export', label: <><FileSpreadsheet className="size-4" /> Export</> },
          { value: 'settings', label: <><Settings className="size-4" /> Settings</> },
        ]}
      />
      <div className="mt-5">
        {tab === 'monitor' && <MonitorTab data={data} counts={counts} participants={participants} liveEvents={liveEvents} socket={socket} />}
        {tab === 'results' && <ResultsTab key={resultsKey} assignmentId={assignmentId} />}
        {tab === 'proctoring' && <ProctoringTab assignmentId={assignmentId} enabled={data.proctoring.enabled} />}
        {tab === 'export' && <ExportTab data={data} />}
        {tab === 'settings' && (
          <SettingsTab
            key={data.summary.createdAt + data.serverTime}
            data={data}
            onSaved={setData}
            onClose={() => setConfirm(s.status === 'ACTIVE' ? 'close' : 'reopen')}
            onDelete={() => setConfirm('delete')}
          />
        )}
      </div>

      <ConfirmDialog
        open={confirm === 'start'}
        onOpenChange={(o) => !o && setConfirm(null)}
        title="Start the live session?"
        description={`Students in the waiting room can start immediately. The session ends automatically after ${s.durationMinutes} minutes${s.deadline ? ' or at the deadline' : ''}.`}
        confirmLabel="Start session"
        onConfirm={() => act('session/start', 'Session started')}
      />
      <ConfirmDialog
        open={confirm === 'end'}
        onOpenChange={(o) => !o && setConfirm(null)}
        title="End the session now?"
        description="All running attempts will be submitted automatically with their saved answers. This cannot be undone."
        confirmLabel="End session"
        destructive
        onConfirm={() => act('session/end', 'Session ended')}
      />
      <ConfirmDialog
        open={confirm === 'release'}
        onOpenChange={(o) => !o && setConfirm(null)}
        title="Release results?"
        description="Students will see their scores, the correct answers and explanations."
        confirmLabel="Release results"
        onConfirm={() => act('release-results', 'Results released')}
      />
      <ConfirmDialog
        open={confirm === 'close' || confirm === 'reopen'}
        onOpenChange={(o) => !o && setConfirm(null)}
        title={confirm === 'close' ? 'Close this assignment?' : 'Reopen this assignment?'}
        description={confirm === 'close' ? 'No new attempts can be started. Existing results remain available.' : 'Eligible students can start attempts again (within the time window).'}
        confirmLabel={confirm === 'close' ? 'Close' : 'Reopen'}
        onConfirm={async () => {
          setData(await api.patch<AssignmentDetail>(`/api/admin/assignments/${assignmentId}`, { status: confirm === 'close' ? 'CLOSED' : 'ACTIVE' }))
        }}
      />
      <ConfirmDialog
        open={confirm === 'delete'}
        onOpenChange={(o) => !o && setConfirm(null)}
        title="Delete assignment?"
        description="Only possible while nobody has attempted it."
        confirmLabel="Delete"
        destructive
        onConfirm={async () => {
          await api.del(`/api/admin/assignments/${assignmentId}`)
          navigate('/admin/assignments')
        }}
      />
    </>
  )
}

// ------------------------------------------------------------------------ monitor

function MonitorTab({
  data,
  counts,
  participants,
  liveEvents,
  socket,
}: {
  data: AssignmentDetail
  counts: ProgressCounts
  participants: Participant[]
  liveEvents: LiveEvent[]
  socket: string
}) {
  const s = data.summary
  const done = counts.submitted + counts.autoSubmitted
  return (
    <div className="space-y-6">
      <div className="flex items-center gap-2 text-xs text-slate-500">
        {socket === 'open' ? <Wifi className="size-3.5 text-emerald-600" /> : <WifiOff className="size-3.5 text-amber-600" />}
        {socket === 'open' ? 'Live updates connected' : 'Reconnecting to live updates…'}
      </div>
      <div className="grid gap-4 sm:grid-cols-3 lg:grid-cols-6">
        <StatCard label="Assigned" value={counts.eligible} />
        <StatCard label="Connected" value={counts.connected} />
        <StatCard label="Not started" value={counts.notStarted} />
        <StatCard label="In progress" value={counts.inProgress} />
        <StatCard label="Submitted" value={counts.submitted} />
        <StatCard label="Auto-submitted" value={counts.autoSubmitted} />
      </div>
      <div>
        <div className="mb-1 flex justify-between text-sm text-slate-600">
          <span>Submission progress</span>
          <span className="tabular-nums">
            {done} / {counts.eligible}
          </span>
        </div>
        <div className="h-1.5 overflow-hidden rounded-full bg-slate-200">
          <div className="h-full bg-primary-600 transition-all" style={{ width: `${counts.eligible ? (done / counts.eligible) * 100 : 0}%` }} />
        </div>
      </div>
      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader title={`Connected students (${participants.length})`} description="Students with this quiz open right now." />
          {participants.length === 0 ? (
            <EmptyState icon={<Users />} title="Nobody connected" />
          ) : (
            <ul className="max-h-80 divide-y divide-slate-100 overflow-auto">
              {participants.map((p) => (
                <li key={p.studentId} className="flex items-center justify-between px-5 py-2 text-sm">
                  <span>
                    <span className="font-mono text-xs">{p.registerNumber}</span> · {p.name}
                  </span>
                  <span className="size-2 rounded-full bg-emerald-500" aria-label="online" />
                </li>
              ))}
            </ul>
          )}
        </Card>
        <Card>
          <CardHeader title="Session & schedule" />
          <dl className="divide-y divide-slate-100 text-sm">
            {[
              ['Opens', s.availableFrom ? formatDateTime(s.availableFrom) : 'Immediately'],
              ['Deadline', s.deadline ? formatDateTime(s.deadline) : 'None'],
              ...(s.liveSession
                ? [
                    ['Session started', formatDateTime(data.sessionStartedAt)],
                    ['Session ends', formatDateTime(data.sessionEndsAt)],
                    ...(data.sessionEndedAt ? [['Session ended', formatDateTime(data.sessionEndedAt)]] : []),
                  ]
                : []),
              ['Results', `${s.resultsVisible ? 'Visible to students' : 'Hidden'} (${s.resultsReleaseMode.replace('_', ' ').toLowerCase()})`],
              ['Monitoring', data.proctoring.enabled ? 'On, auto-submit after 3 page-leaves' : 'Off'],
            ].map(([k, v]) => (
              <div key={k} className="flex justify-between gap-4 px-5 py-2.5">
                <dt className="text-slate-500">{k}</dt>
                <dd className="text-right text-slate-800">{v}</dd>
              </div>
            ))}
          </dl>
        </Card>
      </div>
      {data.proctoring.enabled && (
        <Card>
          <CardHeader title="Live monitoring feed" description="Signals only — review them before drawing conclusions." />
          {liveEvents.length === 0 ? (
            <p className="px-5 py-4 text-sm text-slate-500">No events since you opened this page.</p>
          ) : (
            <ul className="max-h-72 divide-y divide-slate-100 overflow-auto text-sm">
              {liveEvents.map((e) => (
                <li key={e.eventId} className="flex items-center gap-3 px-5 py-2">
                  <span className="text-xs text-slate-500">{new Date(e.occurredAt).toLocaleTimeString()}</span>
                  <span className="font-mono text-xs">{e.registerNumber}</span>
                  <span>{e.fullName}</span>
                  <Badge tone={e.eventType === 'FOCUS_LOST' || e.eventType === 'FULLSCREEN_EXIT' ? 'amber' : 'gray'}>{e.eventType.replace('_', ' ').toLowerCase()}</Badge>
                  <span className="ml-auto text-xs text-slate-500">warnings: {e.warningCount}</span>
                  {e.autoSubmitted && <Badge tone="red">auto-submitted</Badge>}
                  {e.flagged && <Flag className="size-4 text-red-600" aria-label="Flagged" />}
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}
    </div>
  )
}

// ------------------------------------------------------------------------ results

function ResultsTab({ assignmentId }: { assignmentId: number }) {
  const { data, error, loading, reload } = useApi<ResultsResponse>(`/api/admin/assignments/${assignmentId}/results`)
  const [filter, setFilter] = useState('')
  const [attemptId, setAttemptId] = useState<number | null>(null)
  if (loading && !data) return <LoadingBlock />
  if (error || !data) return <ErrorAlert error={error} onRetry={reload} />
  const st = data.stats
  const rows = data.rows.filter((r) => !filter || r.status === filter)
  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-4">
        <StatCard label="Average" value={st.average != null ? `${formatMarks(st.average)} / ${formatMarks(st.maximumScore)}` : '—'} />
        <StatCard label="Highest" value={formatMarks(st.highest)} />
        <StatCard label="Lowest" value={formatMarks(st.lowest)} />
        <StatCard label="Submitted" value={`${st.submitted + st.autoSubmitted} / ${st.eligible}`} hint={`${st.autoSubmitted} auto-submitted`} />
      </div>
      <Card>
        <CardHeader
          title="Student results"
          description="Marks use each student's best finalized attempt."
          actions={
            <Select aria-label="Filter by status" className="w-44" value={filter} onChange={(e) => setFilter(e.target.value)}>
              <option value="">All statuses</option>
              <option value="SUBMITTED">Submitted</option>
              <option value="AUTO_SUBMITTED">Auto-submitted</option>
              <option value="IN_PROGRESS">In progress</option>
              <option value="NOT_SUBMITTED">Not submitted</option>
            </Select>
          }
        />
        {rows.length === 0 ? (
          <EmptyState title="No students in this view" />
        ) : (
          <Table>
            <THead>
              <tr>
                <Th>Register no.</Th>
                <Th>Name</Th>
                <Th>Class</Th>
                <Th>Status</Th>
                <Th className="text-right">Marks</Th>
                <Th className="text-right">Correct</Th>
                <Th>Submitted</Th>
                <Th>Monitoring</Th>
                <Th />
              </tr>
            </THead>
            <tbody>
              {rows.map((r) => (
                <Tr key={r.studentId}>
                  <Td className="font-mono text-xs">{r.registerNumber}</Td>
                  <Td>{r.fullName}</Td>
                  <Td className="text-xs">
                    {roman(r.academicYear)}-{r.department}
                    {r.section && `-${r.section}`}
                  </Td>
                  <Td>
                    <StatusBadge status={r.status} />
                  </Td>
                  <Td className="text-right tabular-nums">{r.score != null ? `${formatMarks(r.score)} / ${formatMarks(r.maximumScore)}` : '—'}</Td>
                  <Td className="text-right tabular-nums">{r.correctCount != null ? `${r.correctCount}/${r.questionCount}` : '—'}</Td>
                  <Td className="whitespace-nowrap text-xs text-slate-500">{formatDateTime(r.submittedAt)}</Td>
                  <Td>
                    {r.flagged ? <Badge tone="red">Flagged · {r.warningCount}</Badge> : r.warningCount > 0 ? <Badge tone="amber">{r.warningCount}</Badge> : <span className="text-xs text-slate-400">—</span>}
                  </Td>
                  <Td className="text-right">
                    {r.attemptId && (
                      <Button variant="ghost" size="icon" aria-label={`View attempt of ${r.registerNumber}`} onClick={() => setAttemptId(r.attemptId!)}>
                        <Eye />
                      </Button>
                    )}
                  </Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
      <AttemptDialog attemptId={attemptId} onClose={() => setAttemptId(null)} />
    </div>
  )
}

function AttemptDialog({ attemptId, onClose }: { attemptId: number | null; onClose: () => void }) {
  const { data, error, loading } = useApi<AdminAttemptDetail>(attemptId ? `/api/admin/attempts/${attemptId}` : null)
  return (
    <Dialog open={attemptId !== null} onOpenChange={(o) => !o && onClose()} title={data ? `${data.student.registerNumber} · ${data.student.fullName}` : 'Attempt'} size="lg">
      {loading || !data ? (
        error ? <ErrorAlert error={error} /> : <LoadingBlock />
      ) : (
        <div className="space-y-3">
          <div className="flex flex-wrap gap-2 text-sm">
            <StatusBadge status={data.status} />
            <Badge>Attempt {data.attemptNumber}</Badge>
            <Badge tone="indigo">
              {formatMarks(data.score)} / {formatMarks(data.maximumScore)}
            </Badge>
            <span className="text-slate-500">
              {formatDateTime(data.startedAt)} → {formatDateTime(data.submittedAt)}
            </span>
          </div>
          {data.questions.map((q) => (
            <ReviewCard key={q.questionId} q={q} />
          ))}
        </div>
      )}
    </Dialog>
  )
}

// --------------------------------------------------------------------- proctoring

function ProctoringTab({ assignmentId, enabled }: { assignmentId: number; enabled: boolean }) {
  const { data, error, loading, reload } = useApi<AssignmentEvents>(`/api/admin/assignments/${assignmentId}/proctoring-events`)
  const [open, setOpen] = useState<number | null>(null)
  const [notes, setNotes] = useState<Record<number, string>>({})
  const toast = useToast()

  const reviewAttempt = async (attemptId: number, reviewStatus: ReviewStatus) => {
    try {
      await api.patch(`/api/admin/attempts/${attemptId}/proctoring-review`, { reviewStatus, notes: notes[attemptId] })
      toast('Review saved')
      reload()
    } catch (e) {
      toast(errorMessage(e), 'error')
    }
  }
  const reviewEvent = async (eventId: number, reviewStatus: ReviewStatus) => {
    try {
      await api.patch(`/api/admin/proctoring-events/${eventId}/review`, { reviewStatus })
      reload()
    } catch (e) {
      toast(errorMessage(e), 'error')
    }
  }

  if (loading && !data) return <LoadingBlock />
  if (error || !data) return <ErrorAlert error={error} onRetry={reload} />
  return (
    <div className="space-y-4">
      <Alert tone="info" title="Signals, not proof">
        Focus and fullscreen events show that the quiz page lost focus — they can't tell why (notifications, accidental clicks, accessibility tools). Browsers can't detect every
        application switch. Review in context. By policy an attempt is submitted automatically after 3 such events: its saved answers are graded and nothing is deducted.
      </Alert>
      {!enabled && (
        <Alert tone="warning" title="Browser monitoring is off for this assignment">
          No page-leave events are recorded and nobody is auto-submitted. Turn it on under the Settings tab; it applies to attempts started afterwards.
        </Alert>
      )}
      {data.attempts.length === 0 ? (
        <Card>
          <EmptyState icon={<ShieldAlert />} title="No monitoring events recorded" />
        </Card>
      ) : (
        data.attempts.map((a) => (
          <Card key={a.attemptId} className={a.flagged ? 'border-red-200' : undefined}>
            <button type="button" className="flex w-full flex-wrap items-center gap-3 px-5 py-3 text-left cursor-pointer" onClick={() => setOpen(open === a.attemptId ? null : a.attemptId)} aria-expanded={open === a.attemptId}>
              <span className="font-mono text-xs">{a.registerNumber}</span>
              <span className="font-medium text-slate-800">{a.fullName}</span>
              <StatusBadge status={a.attemptStatus} />
              {a.flagged && (
                <Badge tone="red">
                  <Flag className="size-3" /> Flagged
                </Badge>
              )}
              <span className="ml-auto flex items-center gap-2 text-sm text-slate-600">
                {a.warningCount} warning(s) / threshold {data.warningThreshold}
                {a.pendingReviewCount > 0 ? <Badge tone="amber">{a.pendingReviewCount} pending</Badge> : <Badge tone="green">Reviewed</Badge>}
              </span>
            </button>
            {open === a.attemptId && (
              <div className="border-t border-slate-100 px-5 py-4">
                <ol className="relative space-y-2 border-l border-slate-200 pl-4">
                  {a.events.map((e) => (
                    <li key={e.id} className="text-sm">
                      <span className={cn('absolute -left-1.5 mt-1.5 size-3 rounded-full border-2 border-white', e.eventType === 'FOCUS_LOST' || e.eventType === 'FULLSCREEN_EXIT' ? 'bg-amber-500' : 'bg-slate-300')} />
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="tabular-nums text-slate-500">{new Date(e.occurredAt).toLocaleTimeString()}</span>
                        <span className="font-medium">{e.eventType.replace('_', ' ').toLowerCase()}</span>
                        {typeof e.metadata?.awayMs === 'number' && <span className="text-xs text-slate-500">away {Math.round((e.metadata.awayMs as number) / 1000)}s</span>}
                        <StatusBadge status={e.reviewStatus} />
                        {e.reviewedBy && <span className="text-xs text-slate-500">by {e.reviewedBy}</span>}
                        {e.reviewStatus === 'PENDING' && (e.eventType === 'FOCUS_LOST' || e.eventType === 'FULLSCREEN_EXIT') && (
                          <Button variant="ghost" size="sm" onClick={() => reviewEvent(e.id, 'REVIEWED')}>
                            <CheckCircle2 /> Mark reviewed
                          </Button>
                        )}
                      </div>
                      {e.reviewNotes && <p className="mt-0.5 text-xs text-slate-600">Note: {e.reviewNotes}</p>}
                    </li>
                  ))}
                </ol>
                <div className="mt-4 space-y-2">
                  <Textarea
                    aria-label="Review notes"
                    rows={2}
                    placeholder="Review notes (optional)"
                    value={notes[a.attemptId] ?? ''}
                    onChange={(e) => setNotes((n) => ({ ...n, [a.attemptId]: e.target.value }))}
                    maxLength={1000}
                  />
                  <div className="flex flex-wrap gap-2">
                    <Button size="sm" variant="success" onClick={() => reviewAttempt(a.attemptId, 'REVIEWED')}>
                      <CheckCircle2 /> Mark all reviewed
                    </Button>
                    <Button size="sm" variant="secondary" onClick={() => reviewAttempt(a.attemptId, 'FOLLOW_UP_REQUIRED')}>
                      <Flag /> Follow-up required
                    </Button>
                  </div>
                </div>
              </div>
            )}
          </Card>
        ))
      )}
    </div>
  )
}

// ------------------------------------------------------------------------- export

function ExportTab({ data }: { data: AssignmentDetail }) {
  const [nonSubmitterMarks, setNonSubmitterMarks] = useState<'BLANK' | 'ZERO'>('BLANK')
  const [includeResponses, setIncludeResponses] = useState(true)
  const [groupId, setGroupId] = useState('')
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const tz = encodeURIComponent(browserTimeZone())

  const run = async (key: string, path: string) => {
    setBusy(key)
    setError(null)
    try {
      await download(path, 'export.xlsx')
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(null)
    }
  }

  const base = `/api/admin/assignments/${data.summary.id}/export`
  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <Card>
        <CardHeader title="Marksheet" description="Every assigned student, including non-submitters (NOT_SUBMITTED)." />
        <CardBody className="space-y-4">
          <Field label="Marks for non-submitters">
            {(id) => (
              <Select id={id} value={nonSubmitterMarks} onChange={(e) => setNonSubmitterMarks(e.target.value as 'BLANK' | 'ZERO')}>
                <option value="BLANK">Leave blank</option>
                <option value="ZERO">Record as zero</option>
              </Select>
            )}
          </Field>
          {data.groups.length > 0 && (
            <Field label="Limit to group" hint="Group marksheet">
              {(id) => (
                <Select id={id} value={groupId} onChange={(e) => setGroupId(e.target.value)}>
                  <option value="">All assigned students</option>
                  {data.groups.map((g) => (
                    <option key={g.id} value={g.id}>
                      {g.name}
                    </option>
                  ))}
                </Select>
              )}
            </Field>
          )}
          <Checkbox checked={includeResponses} onChange={setIncludeResponses} label="Include the Detailed Responses sheet" />
          <Button
            onClick={() =>
              run('marks', `${base}/marksheet?nonSubmitterMarks=${nonSubmitterMarks}&includeResponses=${includeResponses}&tz=${tz}${groupId ? `&groupId=${groupId}` : ''}`)
            }
            loading={busy === 'marks'}
          >
            <FileSpreadsheet /> Download marksheet (.xlsx)
          </Button>
        </CardBody>
      </Card>
      <Card>
        <CardHeader title="Detailed responses" description="One row per student per question, with selected and correct answers (admin only)." />
        <CardBody className="space-y-4">
          <Button variant="secondary" onClick={() => run('responses', `${base}/responses?tz=${tz}`)} loading={busy === 'responses'}>
            <FileSpreadsheet /> Download responses (.xlsx)
          </Button>
          <p className="text-xs text-slate-500">
            Workbooks contain Marksheet / Detailed Responses / Summary sheets with frozen headers and filters. Timestamps use your time zone ({browserTimeZone()}). Cells that
            look like formulas are neutralized.
          </p>
          <Link to="/admin/exports" className="text-sm text-primary-700 hover:underline">
            Consolidated marks across several assignments →
          </Link>
        </CardBody>
      </Card>
      <div className="lg:col-span-2">
        <ErrorAlert error={error} />
      </div>
    </div>
  )
}

// ----------------------------------------------------------------------- settings

function SettingsTab({ data, onSaved, onClose, onDelete }: { data: AssignmentDetail; onSaved: (d: AssignmentDetail) => void; onClose: () => void; onDelete: () => void }) {
  const s = data.summary
  const toast = useToast()
  const groups = useApi<Group[]>('/api/admin/groups')
  const [name, setName] = useState(s.name)
  const [timing, setTiming] = useState<TimingValue>({
    availableFrom: toLocalInput(s.availableFrom),
    deadline: toLocalInput(s.deadline),
    durationMinutes: String(s.durationMinutes),
    maxAttempts: String(s.maxAttempts),
  })
  const [release, setRelease] = useState<ReleaseMode>(s.resultsReleaseMode)
  const [leaderboard, setLeaderboard] = useState(data.leaderboardEnabled)
  const [proctoring, setProctoring] = useState<ProctoringSettings>(data.proctoring)
  const [groupIds, setGroupIds] = useState<number[]>(data.groups.map((g) => g.id))
  const [students, setStudents] = useState<StudentRef[]>(data.students)
  const [codeOpenAccess, setCodeOpenAccess] = useState(data.codeOpenAccess)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  useEffect(() => setError(null), [data])

  const save = async () => {
    setPending(true)
    setError(null)
    try {
      const body: Record<string, unknown> = {
        name,
        availableFrom: fromLocalInput(timing.availableFrom) ?? undefined,
        clearAvailableFrom: !timing.availableFrom,
        deadline: fromLocalInput(timing.deadline) ?? undefined,
        clearDeadline: !timing.deadline,
        durationMinutes: Number(timing.durationMinutes),
        maxAttempts: Number(timing.maxAttempts),
        resultsReleaseMode: release,
        leaderboardEnabled: leaderboard,
        proctoring,
      }
      if (s.type === 'GROUP' || s.type === 'CODE') body.groupIds = groupIds
      if (s.type === 'INDIVIDUAL' || s.type === 'CODE') body.studentIds = students.map((x) => x.id)
      if (s.type === 'CODE') body.codeOpenAccess = codeOpenAccess
      onSaved(await api.patch<AssignmentDetail>(`/api/admin/assignments/${s.id}`, body))
      toast('Settings saved')
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
      <div className="space-y-6">
        <Card>
          <CardHeader title="General & timing" description="Changes to duration apply to attempts started afterwards." />
          <CardBody className="space-y-4">
            <Field label="Name">{(id) => <Input id={id} value={name} onChange={(e) => setName(e.target.value)} />}</Field>
            <TimingFields value={timing} onChange={setTiming} />
          </CardBody>
        </Card>
        {s.type !== 'OPEN' && (
          <Card>
            <CardHeader title="Audience" />
            <CardBody className="space-y-4">
              {(s.type === 'GROUP' || s.type === 'CODE') && (
                <div className="flex flex-wrap gap-2">
                  {groups.data?.map((g) => {
                    const on = groupIds.includes(g.id)
                    return (
                      <button
                        key={g.id}
                        type="button"
                        aria-pressed={on}
                        onClick={() => setGroupIds((ids) => (on ? ids.filter((x) => x !== g.id) : [...ids, g.id]))}
                        className={cn('rounded-lg border px-3 py-1.5 text-sm cursor-pointer', on ? 'border-primary-500 bg-primary-50 text-primary-800' : 'border-slate-300 hover:bg-slate-50')}
                      >
                        {g.name} <span className="text-xs text-slate-500">{g.memberCount}</span>
                      </button>
                    )
                  })}
                </div>
              )}
              {(s.type === 'INDIVIDUAL' || s.type === 'CODE') && <StudentPicker value={students} onChange={setStudents} />}
              {s.type === 'CODE' && <Checkbox checked={codeOpenAccess} onChange={setCodeOpenAccess} label="Open access for anyone with the code" />}
            </CardBody>
          </Card>
        )}
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
        <ErrorAlert error={error} />
        <Button className="w-full" onClick={save} loading={pending}>
          Save settings
        </Button>
        <div className="flex gap-2">
          <Button variant="secondary" className="flex-1" onClick={onClose}>
            {s.status === 'ACTIVE' ? (
              <>
                <Lock /> Close
              </>
            ) : (
              <>
                <Unlock /> Reopen
              </>
            )}
          </Button>
          {!data.hasAttempts && (
            <Button variant="destructive" className="flex-1" onClick={onDelete}>
              <Trash2 /> Delete
            </Button>
          )}
        </div>
      </div>
    </div>
  )
}
