import { BookOpen, GraduationCap, Radio, Send, ShieldAlert, Users, UsersRound } from 'lucide-react'
import { Link } from 'react-router-dom'
import { StatusBadge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardHeader, StatCard } from '@/components/ui/card'
import { EmptyState, ErrorAlert, LoadingBlock, PageHeader } from '@/components/ui/feedback'
import { Table, THead, Td, Th, Tr } from '@/components/ui/table'
import { useApi } from '@/hooks/useApi'
import { formatDateTime, formatMarks } from '@/lib/utils'
import type { AdminDashboard as Dash } from '@/types/api'

export default function AdminDashboard() {
  const { data, error, loading, reload } = useApi<Dash>('/api/admin/dashboard')
  if (loading && !data) return <LoadingBlock />
  if (error || !data) return <ErrorAlert error={error} onRetry={reload} />
  return (
    <>
      <PageHeader
        title="Dashboard"
        description="Overview of your rosters, quizzes and assignments."
        actions={
          <>
            <Button variant="secondary" asChild>
              <Link to="/admin/quizzes">New quiz</Link>
            </Button>
            <Button asChild>
              <Link to="/admin/assignments/new">Assign a quiz</Link>
            </Button>
          </>
        }
      />
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Students" value={data.totalStudents} hint={`${data.activeStudents} active`} icon={<Users />} />
        <StatCard label="Courses / groups" value={`${data.courses} / ${data.groups}`} icon={<GraduationCap />} />
        <StatCard label="Quizzes" value={data.publishedQuizzes} hint={`${data.draftQuizzes} draft(s)`} icon={<BookOpen />} />
        <StatCard label="Active assignments" value={data.activeAssignments} hint={`${data.liveSessions} live now`} icon={<Send />} />
      </div>
      {(data.liveSessions > 0 || data.pendingProctoringReviews > 0) && (
        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          {data.liveSessions > 0 && (
            <Card className="flex items-center gap-3 border-red-200 p-4">
              <Radio className="size-5 text-red-600" />
              <p className="text-sm text-slate-700">
                <strong>{data.liveSessions}</strong> live session(s) running.
              </p>
              <Button asChild size="sm" variant="secondary" className="ml-auto">
                <Link to="/admin/assignments">Monitor</Link>
              </Button>
            </Card>
          )}
          {data.pendingProctoringReviews > 0 && (
            <Card className="flex items-center gap-3 border-amber-200 p-4">
              <ShieldAlert className="size-5 text-amber-600" />
              <p className="text-sm text-slate-700">
                <strong>{data.pendingProctoringReviews}</strong> monitoring event(s) awaiting review.
              </p>
              <Button asChild size="sm" variant="secondary" className="ml-auto">
                <Link to="/admin/assignments">Review</Link>
              </Button>
            </Card>
          )}
        </div>
      )}
      <Card className="mt-6">
        <CardHeader title="Recent submissions" />
        {data.recentSubmissions.length === 0 ? (
          <EmptyState icon={<UsersRound />} title="No submissions yet" description="Submissions from your assignments will appear here." />
        ) : (
          <Table>
            <THead>
              <tr>
                <Th>Student</Th>
                <Th>Assignment</Th>
                <Th>Status</Th>
                <Th className="text-right">Score</Th>
                <Th>Submitted</Th>
              </tr>
            </THead>
            <tbody>
              {data.recentSubmissions.map((s) => (
                <Tr key={s.attemptId}>
                  <Td>
                    <span className="font-mono text-xs">{s.registerNumber}</span> · {s.fullName}
                  </Td>
                  <Td>
                    <Link className="text-primary-700 hover:underline" to={`/admin/assignments/${s.assignmentId}`}>
                      {s.assignmentName}
                    </Link>
                    <span className="block text-xs text-slate-500">{s.quizTitle}</span>
                  </Td>
                  <Td>
                    <StatusBadge status={s.status} />
                  </Td>
                  <Td className="text-right tabular-nums">
                    {formatMarks(s.score)} / {formatMarks(s.maximumScore)}
                  </Td>
                  <Td className="whitespace-nowrap text-slate-500">{formatDateTime(s.submittedAt)}</Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
    </>
  )
}
