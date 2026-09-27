import { BookOpen, Plus } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { StatusBadge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { EmptyState, ErrorAlert, LoadingBlock, PageHeader } from '@/components/ui/feedback'
import { Checkbox, Field, Input, Select, Textarea } from '@/components/ui/form'
import { Table, THead, Td, Th, Tr } from '@/components/ui/table'
import { useApi } from '@/hooks/useApi'
import { api, errorMessage } from '@/lib/api'
import { formatDateTime, formatMarks } from '@/lib/utils'
import type { Course, QuizDetail, QuizSummary } from '@/types/api'

export default function QuizzesPage() {
  const { data, error, loading, reload } = useApi<QuizSummary[]>('/api/admin/quizzes')
  const [open, setOpen] = useState(false)
  return (
    <>
      <PageHeader
        title="Quizzes"
        description="Author questions, then publish a quiz to assign it."
        actions={
          <Button onClick={() => setOpen(true)}>
            <Plus /> New quiz
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
          <EmptyState icon={<BookOpen />} title="No quizzes yet" action={<Button onClick={() => setOpen(true)}>Create your first quiz</Button>} />
        ) : (
          <Table>
            <THead>
              <tr>
                <Th>Title</Th>
                <Th>Course</Th>
                <Th>Status</Th>
                <Th className="text-right">Questions</Th>
                <Th className="text-right">Marks</Th>
                <Th className="text-right">Duration</Th>
                <Th>Updated</Th>
              </tr>
            </THead>
            <tbody>
              {data?.map((q) => (
                <Tr key={q.id}>
                  <Td className="font-medium">
                    <Link className="text-primary-700 hover:underline" to={`/admin/quizzes/${q.id}`}>
                      {q.title}
                    </Link>
                  </Td>
                  <Td>{q.course?.courseCode ?? '—'}</Td>
                  <Td>
                    <StatusBadge status={q.status} />
                  </Td>
                  <Td className="text-right tabular-nums">{q.questionCount}</Td>
                  <Td className="text-right tabular-nums">{formatMarks(q.totalPoints)}</Td>
                  <Td className="text-right tabular-nums">{q.durationMinutes} min</Td>
                  <Td className="whitespace-nowrap text-slate-500">{formatDateTime(q.updatedAt)}</Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
      <QuizSettingsDialog open={open} onOpenChange={setOpen} />
    </>
  )
}

export function QuizSettingsDialog({
  open,
  onOpenChange,
  quiz,
  onSaved,
}: {
  open: boolean
  onOpenChange: (o: boolean) => void
  quiz?: QuizDetail
  onSaved?: (q: QuizDetail) => void
}) {
  const navigate = useNavigate()
  const courses = useApi<Course[]>('/api/admin/courses')
  const [title, setTitle] = useState(quiz?.title ?? '')
  const [description, setDescription] = useState(quiz?.description ?? '')
  const [instructions, setInstructions] = useState(quiz?.instructions ?? '')
  const [courseId, setCourseId] = useState(quiz?.course ? String(quiz.course.id) : '')
  const [duration, setDuration] = useState(String(quiz?.durationMinutes ?? 30))
  const [shuffleQuestions, setShuffleQuestions] = useState(quiz?.shuffleQuestions ?? false)
  const [shuffleOptions, setShuffleOptions] = useState(quiz?.shuffleOptions ?? false)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  const save = async () => {
    setPending(true)
    setError(null)
    const body = {
      title,
      description,
      instructions,
      durationMinutes: Number(duration),
      shuffleQuestions,
      shuffleOptions,
      courseId: courseId ? Number(courseId) : undefined,
    }
    try {
      if (quiz) {
        const q = await api.patch<QuizDetail>(`/api/admin/quizzes/${quiz.id}`, { ...body, clearCourse: !courseId })
        onSaved?.(q)
        onOpenChange(false)
      } else {
        const q = await api.post<QuizDetail>('/api/admin/quizzes', body)
        navigate(`/admin/quizzes/${q.id}`)
      }
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setPending(false)
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={quiz ? 'Quiz settings' : 'New quiz'}
      size="lg"
      footer={
        <>
          <Button variant="secondary" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={save} loading={pending} disabled={!title.trim() || !Number(duration)}>
            {quiz ? 'Save' : 'Create & add questions'}
          </Button>
        </>
      }
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Title" required className="sm:col-span-2">
          {(id) => <Input id={id} value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} />}
        </Field>
        <Field label="Course / subject">
          {(id) => (
            <Select id={id} value={courseId} onChange={(e) => setCourseId(e.target.value)}>
              <option value="">None</option>
              {courses.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.courseCode} · {c.courseName}
                </option>
              ))}
            </Select>
          )}
        </Field>
        <Field label="Default duration (minutes)" required>
          {(id) => <Input id={id} type="number" min={1} max={600} value={duration} onChange={(e) => setDuration(e.target.value)} />}
        </Field>
        <Field label="Description" className="sm:col-span-2">
          {(id) => <Textarea id={id} rows={2} value={description} onChange={(e) => setDescription(e.target.value)} />}
        </Field>
        <Field label="Instructions for students" className="sm:col-span-2">
          {(id) => <Textarea id={id} rows={3} value={instructions} onChange={(e) => setInstructions(e.target.value)} />}
        </Field>
        <Checkbox checked={shuffleQuestions} onChange={setShuffleQuestions} label="Shuffle question order" description="Each attempt gets its own stable order" />
        <Checkbox checked={shuffleOptions} onChange={setShuffleOptions} label="Shuffle option order" />
        <div className="sm:col-span-2">
          <ErrorAlert error={error} />
        </div>
      </div>
    </Dialog>
  )
}
