import { Plus, Send } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Badge, StatusBadge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { EmptyState, ErrorAlert, LoadingBlock, PageHeader } from '@/components/ui/feedback'
import { Table, THead, Td, Th, Tr } from '@/components/ui/table'
import { useApi } from '@/hooks/useApi'
import { formatDateTime } from '@/lib/utils'
import type { AssignmentSummary, AssignmentType } from '@/types/api'

export const TYPE_LABEL: Record<AssignmentType, string> = {
  GROUP: 'Groups',
  INDIVIDUAL: 'Individual',
  CODE: 'Join code',
  OPEN: 'Open to all',
}

export default function AssignmentsPage() {
  const { data, error, loading, reload } = useApi<AssignmentSummary[]>('/api/admin/assignments')
  return (
    <>
      <PageHeader
        title="Assignments"
        description="Who gets which quiz, when, and under what rules."
        actions={
          <Button asChild>
            <Link to="/admin/assignments/new">
              <Plus /> New assignment
            </Link>
          </Button>
        }
      />
      <Card>
        {loading && !data ? (
          <LoadingBlock />
        ) : error ? (
          <div className="p-4">
            <ErrorAlert error={error} onRetry={reload} />
          </div>
        ) : data?.length === 0 ? (
          <EmptyState
            icon={<Send />}
            title="No assignments yet"
            description="Publish a quiz, then assign it to groups, individual students, a join code, or everyone."
            action={
              <Button asChild>
                <Link to="/admin/assignments/new">Create assignment</Link>
              </Button>
            }
          />
        ) : (
          <Table>
            <THead>
              <tr>
                <Th>Assignment</Th>
                <Th>Audience</Th>
                <Th>Window</Th>
                <Th>Session</Th>
                <Th>Status</Th>
                <Th>Results</Th>
              </tr>
            </THead>
            <tbody>
              {data?.map((a) => (
                <Tr key={a.id}>
                  <Td>
                    <Link className="font-medium text-primary-700 hover:underline" to={`/admin/assignments/${a.id}`}>
                      {a.name}
                    </Link>
                    <p className="text-xs text-slate-500">
                      {a.quizTitle}
                      {a.course && ` · ${a.course.courseCode}`}
                    </p>
                  </Td>
                  <Td>
                    <Badge tone="indigo">{TYPE_LABEL[a.type]}</Badge>
                    {a.joinCode && <span className="ml-2 font-mono text-xs">{a.joinCode}</span>}
                  </Td>
                  <Td className="text-xs text-slate-600">
                    {a.availableFrom ? formatDateTime(a.availableFrom) : 'Now'} → {a.deadline ? formatDateTime(a.deadline) : 'No deadline'}
                  </Td>
                  <Td>{a.liveSession ? <StatusBadge status={a.sessionState} /> : <span className="text-xs text-slate-400">Self-paced</span>}</Td>
                  <Td>
                    <StatusBadge status={a.status} />
                  </Td>
                  <Td>{a.resultsVisible ? <Badge tone="green">Visible</Badge> : <Badge>Hidden</Badge>}</Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
    </>
  )
}
