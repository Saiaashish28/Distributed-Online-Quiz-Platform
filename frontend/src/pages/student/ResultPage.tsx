import { ArrowLeft, Check, Hourglass, X } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { StatusBadge } from '@/components/ui/badge'
import { Card, CardBody } from '@/components/ui/card'
import { EmptyState, ErrorAlert, LoadingBlock, PageHeader } from '@/components/ui/feedback'
import { useApi } from '@/hooks/useApi'
import { cn, formatDateTime, formatMarks } from '@/lib/utils'
import type { ResultView, ReviewedQuestion } from '@/types/api'
import { Leaderboard } from './AssignmentPage'

export default function ResultPage() {
  const { id } = useParams()
  const { data, error, loading, reload } = useApi<ResultView>(`/api/student/attempts/${id}/result`)
  if (loading && !data) return <LoadingBlock />
  if (error || !data) return <ErrorAlert error={error ?? 'Not found'} onRetry={reload} />

  return (
    <>
      <PageHeader
        back={
          <Link to="/student" className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
            <ArrowLeft className="size-4" /> Dashboard
          </Link>
        }
        title={data.assignmentName}
        description={`${data.quizTitle} · Attempt ${data.attemptNumber} · submitted ${formatDateTime(data.submittedAt)}`}
        actions={<StatusBadge status={data.status} />}
      />
      {!data.released ? (
        <Card>
          <EmptyState icon={<Hourglass />} title="Results not released yet" description="Your submission is safely recorded. Your instructor will release results later." />
        </Card>
      ) : (
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-3">
            <Card className="p-5 sm:col-span-1">
              <p className="text-sm text-slate-500">Score</p>
              <p className="mt-1 text-3xl font-bold tabular-nums text-slate-900">
                {formatMarks(data.score)} <span className="text-lg font-medium text-slate-400">/ {formatMarks(data.maximumScore)}</span>
              </p>
              <p className="mt-1 text-sm text-slate-600">{data.percentage}%</p>
            </Card>
            <Card className="p-5">
              <p className="text-sm text-slate-500">Correct answers</p>
              <p className="mt-1 text-3xl font-bold tabular-nums text-emerald-600">
                {data.correctCount} <span className="text-lg font-medium text-slate-400">/ {data.questionCount}</span>
              </p>
            </Card>
            <Card className="p-5">
              <p className="text-sm text-slate-500">Status</p>
              <div className="mt-2">
                <StatusBadge status={data.status} />
              </div>
            </Card>
          </div>
          {data.leaderboardEnabled && <Leaderboard assignmentId={data.assignmentId} />}
          <div className="space-y-3">
            <h2 className="text-lg font-semibold text-slate-900">Answer review</h2>
            {data.questions.map((q) => (
              <ReviewCard key={q.questionId} q={q} />
            ))}
          </div>
        </div>
      )}
    </>
  )
}

export function ReviewCard({ q }: { q: ReviewedQuestion }) {
  const unanswered = q.selectedOptionId == null
  return (
    <Card>
      <CardBody>
        <div className="flex items-start justify-between gap-3">
          <p className="font-medium text-slate-900">
            <span className="mr-2 text-slate-400">{q.number}.</span>
            <span className="whitespace-pre-line">{q.text}</span>
          </p>
          <span
            className={cn(
              'shrink-0 rounded-full px-2 py-0.5 text-xs font-semibold tabular-nums',
              q.correct ? 'bg-emerald-50 text-emerald-700' : unanswered ? 'bg-slate-100 text-slate-600' : 'bg-red-50 text-red-700',
            )}
          >
            {formatMarks(q.marksAwarded)} / {formatMarks(q.points)}
          </span>
        </div>
        <ul className="mt-3 space-y-1.5">
          {q.options.map((o, i) => {
            const selected = o.id === q.selectedOptionId
            return (
              <li
                key={o.id}
                className={cn(
                  'flex items-center gap-2 rounded-md border px-3 py-2 text-sm',
                  o.correct ? 'border-emerald-300 bg-emerald-50' : selected ? 'border-red-300 bg-red-50' : 'border-slate-200',
                )}
              >
                <span className="font-semibold text-slate-500">{String.fromCharCode(65 + i)}.</span>
                <span className="flex-1">{o.text}</span>
                {o.correct && <Check className="size-4 text-emerald-600" aria-label="Correct answer" />}
                {selected && !o.correct && <X className="size-4 text-red-600" aria-label="Your answer" />}
                {selected && <span className="text-xs text-slate-500">Your answer</span>}
              </li>
            )
          })}
        </ul>
        {unanswered && <p className="mt-2 text-xs text-slate-500">Not answered</p>}
        {q.explanation && <p className="mt-3 rounded-md bg-slate-50 px-3 py-2 text-sm text-slate-600">{q.explanation}</p>}
      </CardBody>
    </Card>
  )
}
