import { GraduationCap, Plus } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { EmptyState, ErrorAlert, LoadingBlock, PageHeader } from '@/components/ui/feedback'
import { Field, Input } from '@/components/ui/form'
import { Table, THead, Td, Th, Tr } from '@/components/ui/table'
import { useToast } from '@/components/ui/toast'
import { useApi } from '@/hooks/useApi'
import { api, errorMessage } from '@/lib/api'
import type { Course } from '@/types/api'

export default function CoursesPage() {
  const { data, error, loading, reload } = useApi<Course[]>('/api/admin/courses')
  const [open, setOpen] = useState(false)
  return (
    <>
      <PageHeader
        title="Courses"
        description="Courses power enrollment and course-based groups such as III-CSE-DCC."
        actions={
          <Button onClick={() => setOpen(true)}>
            <Plus /> New course
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
          <EmptyState icon={<GraduationCap />} title="No courses yet" action={<Button onClick={() => setOpen(true)}>Create a course</Button>} />
        ) : (
          <Table>
            <THead>
              <tr>
                <Th>Code</Th>
                <Th>Name</Th>
                <Th>Semester</Th>
                <Th className="text-right">Students</Th>
              </tr>
            </THead>
            <tbody>
              {data?.map((c) => (
                <Tr key={c.id}>
                  <Td className="font-mono text-xs font-semibold">
                    <Link className="text-primary-700 hover:underline" to={`/admin/courses/${c.id}`}>
                      {c.courseCode}
                    </Link>
                  </Td>
                  <Td>{c.courseName}</Td>
                  <Td>{c.semester ?? '—'}</Td>
                  <Td className="text-right tabular-nums">{c.studentCount}</Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
      <CourseDialog open={open} onOpenChange={setOpen} onSaved={reload} />
    </>
  )
}

export function CourseDialog({
  open,
  onOpenChange,
  course,
  onSaved,
}: {
  open: boolean
  onOpenChange: (o: boolean) => void
  course?: Course
  onSaved: () => void
}) {
  const [code, setCode] = useState(course?.courseCode ?? '')
  const [name, setName] = useState(course?.courseName ?? '')
  const [semester, setSemester] = useState(course?.semester ? String(course.semester) : '')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const toast = useToast()

  const save = async () => {
    setPending(true)
    setError(null)
    try {
      const sem = semester ? Number(semester) : undefined
      if (course) await api.patch(`/api/admin/courses/${course.id}`, { courseName: name, semester: sem })
      else await api.post('/api/admin/courses', { courseCode: code, courseName: name, semester: sem })
      toast(course ? 'Course updated' : 'Course created')
      onSaved()
      onOpenChange(false)
      if (!course) {
        setCode('')
        setName('')
        setSemester('')
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
      title={course ? 'Edit course' : 'New course'}
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={save} loading={pending} disabled={!name.trim() || (!course && !code.trim())}>
            Save
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <Field label="Course code" required hint="Unique, e.g. CS301">
          {(id) => <Input id={id} value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} disabled={!!course} maxLength={30} />}
        </Field>
        <Field label="Course name" required>
          {(id) => <Input id={id} value={name} onChange={(e) => setName(e.target.value)} maxLength={150} />}
        </Field>
        <Field label="Semester">
          {(id) => <Input id={id} type="number" min={1} max={12} value={semester} onChange={(e) => setSemester(e.target.value)} />}
        </Field>
        <ErrorAlert error={error} />
      </div>
    </Dialog>
  )
}
