import { CheckCircle2, Clock } from 'lucide-react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ErrorAlert, LoadingBlock } from '@/components/ui/feedback'
import { useApi } from '@/hooks/useApi'
import { formatDateTime } from '@/lib/utils'
import type { ResultView } from '@/types/api'

export default function SubmittedPage() {
  const { id } = useParams()
  const proctoring = (useLocation().state as { reason?: string } | null)?.reason === 'proctoring'
  const { data, error, loading, reload } = useApi<ResultView>(`/api/student/attempts/${id}/result`)
  if (loading && !data) return <LoadingBlock />
  if (error || !data) return <ErrorAlert error={error ?? 'Not found'} onRetry={reload} />
  const auto = data.status === 'AUTO_SUBMITTED'
  return (
    <div className="mx-auto max-w-lg py-10">
      <Card className="p-8 text-center">
        {auto ? <Clock className="mx-auto size-12 text-amber-500" /> : <CheckCircle2 className="mx-auto size-12 text-emerald-500" />}
        <h1 className="mt-4 text-xl font-semibold text-slate-900">{auto ? 'Your quiz was submitted automatically' : 'Quiz submitted'}</h1>
        <p className="mt-2 text-sm text-slate-600">
          {data.assignmentName} · {data.quizTitle}
        </p>
        <p className="mt-1 text-sm text-slate-500">Recorded at {formatDateTime(data.submittedAt)}</p>
        {auto && (
          <p className="mt-3 text-sm text-slate-600">
            {proctoring
              ? 'You left the quiz page 3 times, so your quiz was submitted automatically.'
              : 'Time ran out, the session ended, or the page-leave limit was reached.'}{' '}
            Every answer you saved has been graded; unanswered questions score zero.
          </p>
        )}
        <p className="mt-4 text-sm text-slate-600">
          {data.released ? 'Your result is available now.' : 'Your result will be available when your instructor releases it.'}
        </p>
        <div className="mt-6 flex flex-wrap justify-center gap-2">
          <Button variant="secondary" asChild>
            <Link to="/student">Back to dashboard</Link>
          </Button>
          {data.released && (
            <Button asChild>
              <Link to={`/student/attempts/${id}/result`}>View result</Link>
            </Button>
          )}
        </div>
      </Card>
    </div>
  )
}
