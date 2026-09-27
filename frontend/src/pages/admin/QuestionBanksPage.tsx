import { Library, Plus } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { EmptyState, ErrorAlert, LoadingBlock, PageHeader } from '@/components/ui/feedback'
import { Field, Input, Select } from '@/components/ui/form'
import { Table, THead, Td, Th, Tr } from '@/components/ui/table'
import { useApi } from '@/hooks/useApi'
import { api, errorMessage } from '@/lib/api'
import type { BankSummary, Course } from '@/types/api'

export default function QuestionBanksPage() {
  const { data, error, loading, reload } = useApi<BankSummary[]>('/api/admin/question-banks')
  const [open, setOpen] = useState(false)
  return (
    <>
      <PageHeader
        title="Question banks"
        description="Reusable questions per subject. Copying into a quiz makes an independent copy, so bank edits never change existing quizzes."
        actions={
          <Button onClick={() => setOpen(true)}>
            <Plus /> New bank
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
          <EmptyState icon={<Library />} title="No question banks yet" action={<Button onClick={() => setOpen(true)}>Create a bank</Button>} />
        ) : (
          <Table>
            <THead>
              <tr>
                <Th>Name</Th>
                <Th>Course</Th>
                <Th className="text-right">Questions</Th>
              </tr>
            </THead>
            <tbody>
              {data?.map((b) => (
                <Tr key={b.id}>
                  <Td className="font-medium">
                    <Link className="text-primary-700 hover:underline" to={`/admin/question-banks/${b.id}`}>
                      {b.name}
                    </Link>
                    {b.description && <p className="text-xs font-normal text-slate-500">{b.description}</p>}
                  </Td>
                  <Td>{b.course ? `${b.course.courseCode} · ${b.course.courseName}` : '—'}</Td>
                  <Td className="text-right tabular-nums">{b.questionCount}</Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
      <BankDialog open={open} onOpenChange={setOpen} />
    </>
  )
}

export function BankDialog({ open, onOpenChange, bank, onSaved }: { open: boolean; onOpenChange: (o: boolean) => void; bank?: BankSummary; onSaved?: () => void }) {
  const navigate = useNavigate()
  const courses = useApi<Course[]>('/api/admin/courses')
  const [name, setName] = useState(bank?.name ?? '')
  const [description, setDescription] = useState(bank?.description ?? '')
  const [courseId, setCourseId] = useState(bank?.course ? String(bank.course.id) : '')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const save = async () => {
    setPending(true)
    setError(null)
    const body = { name, description, courseId: courseId ? Number(courseId) : undefined }
    try {
      if (bank) {
        await api.patch(`/api/admin/question-banks/${bank.id}`, body)
        onSaved?.()
        onOpenChange(false)
      } else {
        const b = await api.post<BankSummary>('/api/admin/question-banks', body)
        navigate(`/admin/question-banks/${b.id}`)
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
      title={bank ? 'Edit bank' : 'New question bank'}
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={save} loading={pending} disabled={!name.trim()}>
            Save
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <Field label="Name" required>
          {(id) => <Input id={id} value={name} onChange={(e) => setName(e.target.value)} maxLength={150} />}
        </Field>
        <Field label="Description">
          {(id) => <Input id={id} value={description} onChange={(e) => setDescription(e.target.value)} maxLength={500} />}
        </Field>
        <Field label="Course">
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
        <ErrorAlert error={error} />
      </div>
    </Dialog>
  )
}
