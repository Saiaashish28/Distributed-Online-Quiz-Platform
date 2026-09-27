import { ArrowLeft, CalendarClock, Clock, Hash, Radio, ShieldCheck, Trophy, Users } from 'lucide-react'
import { useCallback, useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { StatusBadge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { Alert, ErrorAlert, LoadingBlock, PageHeader } from '@/components/ui/feedback'
import { Checkbox } from '@/components/ui/form'
import { Table, THead, Td, Th, Tr } from '@/components/ui/table'
import { useApi } from '@/hooks/useApi'
import { useQuizSocket, type SocketMessage } from '@/hooks/useQuizSocket'
import { api, errorMessage } from '@/lib/api'
import { formatDateTime, formatMarks } from '@/lib/utils'
import type { AttemptView, LeaderboardEntry, StudentAssignment } from '@/types/api'

export default function AssignmentPage() {
  const { id } = useParams()
  const assignmentId = Number(id)
  const navigate = useNavigate()
  const { data: a, error, loading, reload } = useApi<StudentAssignment>(`/api/student/assignments/${assignmentId}`)
  const [participants, setParticipants] = useState<number | null>(null)
  const [consent, setConsent] = useState(false)
  const [starting, setStarting] = useState(false)
  const [startError, setStartError] = useState<string | null>(null)

  const onMessage = useCallback(
    (msg: SocketMessage) => {
      if (msg.type === 'session_snapshot' || msg.type === 'participant_count_updated') {
        const d = msg.data as { participantCount?: number; count?: number }
        setParticipants(d.participantCount ?? d.count ?? null)
      }
      if (['session_started', 'session_ended', 'results_released'].includes(msg.type)) reload()
    },
    [reload],
  )
  const socket = useQuizSocket(a?.liveSession ? assignmentId : null, onMessage)

  const start = async () => {
    setStarting(true)
    setStartError(null)
    try {
      if (a?.proctoring.requireFullscreen && document.fullscreenEnabled) {
        await document.documentElement.requestFullscreen().catch(() => undefined)
      }
      const attempt = await api.post<AttemptView>(`/api/student/assignments/${assignmentId}/start`)
      navigate(`/student/attempts/${attempt.attemptId}`)
    } catch (e) {
      setStartError(errorMessage(e))
      reload()
    } finally {
      setStarting(false)
    }
  }

  if (loading && !a) return <LoadingBlock />
  if (error || !a) return <ErrorAlert error={error ?? 'Not found'} onRetry={reload} />

  const waiting = a.liveSession && a.sessionState === 'WAITING'
  const needsConsent = a.proctoring.enabled && !a.inProgressAttemptId

  return (
    <>
      <PageHeader
        back={
          <Link to="/student" className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
            <ArrowLeft className="size-4" /> Dashboard
          </Link>
        }
        title={a.name}
        description={`${a.quizTitle}${a.course ? ` · ${a.course.courseCode} ${a.course.courseName}` : ''}`}
        actions={<StatusBadge status={a.status} />}
      />
      <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
        <div className="space-y-6">
          <Card>
            <CardHeader title="Instructions" />
            <CardBody className="space-y-4 text-sm text-slate-700">
              {a.description && <p>{a.description}</p>}
              <p className="whitespace-pre-line">{a.instructions || 'Answer every question. Your answers are saved automatically as you go.'}</p>
              <ul className="list-disc space-y-1 pl-5 text-slate-600">
                <li>The timer is controlled by the server. Refreshing or reconnecting does not reset it.</li>
                <li>When time runs out, your saved answers are submitted automatically.</li>
                <li>Unanswered questions score zero.</li>
              </ul>
            </CardBody>
          </Card>

          {a.proctoring.enabled && (
            <Alert tone="info" title="Browser monitoring is enabled for this quiz">
              <p>
                While you take the quiz, the page records when it loses focus (you switch tabs/windows) and returns
                {a.proctoring.requireFullscreen && ', and when you leave fullscreen'}. Only the event type and time are stored.
                No camera, microphone or screen recording is used.{' '}
                <strong>
                  If you leave the quiz page {a.proctoring.autoSubmitWarnings} times, your quiz is submitted automatically
                </strong>{' '}
                and only the answers saved so far are graded. Events are also shared with your instructor for review.
              </p>
            </Alert>
          )}

          {a.resultsVisible && a.latestFinalAttemptId && a.leaderboardEnabled && <Leaderboard assignmentId={a.id} />}
        </div>

        <div className="space-y-4">
          <Card>
            <CardBody className="space-y-3 text-sm">
              <Row icon={<Clock />} label="Duration" value={`${a.durationMinutes} minutes`} />
              <Row icon={<Hash />} label="Questions" value={`${a.questionCount} · ${formatMarks(a.totalMarks)} marks`} />
              <Row icon={<CalendarClock />} label="Opens" value={a.availableFrom ? formatDateTime(a.availableFrom) : 'Now'} />
              <Row icon={<CalendarClock />} label="Deadline" value={a.deadline ? formatDateTime(a.deadline) : 'None'} />
              <Row icon={<Trophy />} label="Attempts" value={`${a.attemptsUsed} of ${a.maxAttempts} used`} />
              {a.proctoring.enabled && <Row icon={<ShieldCheck />} label="Monitoring" value="On" />}
            </CardBody>
          </Card>

          {a.liveSession && (
            <Card className={waiting ? 'border-amber-300' : undefined}>
              <CardBody className="space-y-2 text-sm">
                <div className="flex items-center justify-between">
                  <span className="flex items-center gap-2 font-medium text-slate-800">
                    <Radio className="size-4" /> Live session
                  </span>
                  <StatusBadge status={a.sessionState} />
                </div>
                {waiting && <p className="text-slate-600">You're in the waiting room. The quiz opens automatically when your instructor starts the session.</p>}
                <p className="flex items-center gap-2 text-slate-500">
                  <Users className="size-4" /> {participants ?? '—'} student(s) connected
                  <span className="ml-auto text-xs">{socket === 'open' ? 'Live' : socket === 'reconnecting' ? 'Reconnecting…' : ''}</span>
                </p>
              </CardBody>
            </Card>
          )}

          <Card>
            <CardBody className="space-y-3">
              {a.inProgressAttemptId ? (
                <Button asChild className="w-full" size="lg">
                  <Link to={`/student/attempts/${a.inProgressAttemptId}`}>Continue quiz</Link>
                </Button>
              ) : a.canStart ? (
                <>
                  {needsConsent && (
                    <Checkbox checked={consent} onChange={setConsent} label={`I understand that leaving the quiz page is recorded, and that after ${a.proctoring.autoSubmitWarnings} times my quiz is submitted automatically.`} />
                  )}
                  <Button className="w-full" size="lg" onClick={start} loading={starting} disabled={needsConsent && !consent}>
                    {a.attemptsUsed > 0 ? 'Start new attempt' : 'Start quiz'}
                  </Button>
                </>
              ) : (
                <p className="text-center text-sm text-slate-600">{a.blockedReason ?? 'This quiz cannot be started now.'}</p>
              )}
              <ErrorAlert error={startError} />
              {a.latestFinalAttemptId && (
                <Button asChild variant="secondary" className="w-full">
                  <Link to={`/student/attempts/${a.latestFinalAttemptId}/result`}>{a.resultsVisible ? 'View result' : 'Submission status'}</Link>
                </Button>
              )}
            </CardBody>
          </Card>
        </div>
      </div>
    </>
  )
}

function Row({ icon, label, value }: { icon: ReactNode; label: string; value: ReactNode }) {
  return (
    <div className="flex items-center gap-2 text-slate-600">
      <span className="text-slate-400 [&_svg]:size-4">{icon}</span>
      <span>{label}</span>
      <span className="ml-auto text-right font-medium text-slate-800">{value}</span>
    </div>
  )
}

export function Leaderboard({ assignmentId }: { assignmentId: number }) {
  const { data, error, loading } = useApi<LeaderboardEntry[]>(`/api/student/assignments/${assignmentId}/leaderboard`)
  return (
    <Card>
      <CardHeader title="Leaderboard" description="Ordered by score, then earliest submission." />
      {loading ? (
        <LoadingBlock />
      ) : error ? (
        <CardBody>
          <ErrorAlert error={error} />
        </CardBody>
      ) : (
        <Table>
          <THead>
            <tr>
              <Th>Rank</Th>
              <Th>Name</Th>
              <Th className="text-right">Score</Th>
            </tr>
          </THead>
          <tbody>
            {data?.map((e) => (
              <Tr key={e.rank} className={e.me ? 'bg-primary-50/60' : undefined}>
                <Td className="font-medium">#{e.rank}</Td>
                <Td>
                  {e.name} {e.me && <span className="text-xs text-primary-700">(you)</span>}
                </Td>
                <Td className="text-right tabular-nums">
                  {formatMarks(e.score)} / {formatMarks(e.maximumScore)}
                </Td>
              </Tr>
            ))}
          </tbody>
        </Table>
      )}
    </Card>
  )
}
