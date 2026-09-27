import { ArrowLeft, Pencil, UserMinus, UserPlus, Users } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { StudentPicker } from '@/components/admin/StudentPicker'
import { Button } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { ConfirmDialog } from '@/components/ui/dialog'
import { EmptyState, ErrorAlert, LoadingBlock, PageHeader } from '@/components/ui/feedback'
import { Table, THead, Td, Th, Tr } from '@/components/ui/table'
import { useToast } from '@/components/ui/toast'
import { useApi } from '@/hooks/useApi'
import { api, errorMessage } from '@/lib/api'
import { roman } from '@/lib/utils'
import type { Course, MembershipResult, Student, StudentRef } from '@/types/api'
import { CourseDialog } from './CoursesPage'

export default function CourseDetailPage() {
  const { id } = useParams()
  const courses = useApi<Course[]>('/api/admin/courses')
  const roster = useApi<Student[]>(`/api/admin/courses/${id}/students`)
  const course = courses.data?.find((c) => c.id === Number(id))
  const [editOpen, setEditOpen] = useState(false)
  const [picked, setPicked] = useState<StudentRef[]>([])
  const [removing, setRemoving] = useState<Student | null>(null)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const toast = useToast()

  const enroll = async () => {
    setPending(true)
    setError(null)
    try {
      const r = await api.post<MembershipResult>(`/api/admin/courses/${id}/students`, { studentIds: picked.map((p) => p.id) })
      toast(`${r.added} enrolled${r.alreadyPresent ? `, ${r.alreadyPresent} already enrolled` : ''}`)
      setPicked([])
      roster.reload()
      courses.reload()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setPending(false)
    }
  }

  if (courses.loading && !courses.data) return <LoadingBlock />
  if (!course) return <ErrorAlert error={courses.error ?? 'Course not found'} />

  return (
    <>
      <PageHeader
        back={
          <Link to="/admin/courses" className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
            <ArrowLeft className="size-4" /> Courses
          </Link>
        }
        title={`${course.courseCode} · ${course.courseName}`}
        description={`${course.studentCount} enrolled${course.semester ? ` · Semester ${course.semester}` : ''}`}
        actions={
          <Button variant="secondary" onClick={() => setEditOpen(true)}>
            <Pencil /> Edit
          </Button>
        }
      />
      <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
        <Card>
          <CardHeader title="Roster" />
          {roster.loading && !roster.data ? (
            <LoadingBlock />
          ) : roster.data?.length === 0 ? (
            <EmptyState icon={<Users />} title="No students enrolled" description="Enroll students here, or include the course code in a roster import." />
          ) : (
            <Table>
              <THead>
                <tr>
                  <Th>Register no.</Th>
                  <Th>Name</Th>
                  <Th>Class</Th>
                  <Th />
                </tr>
              </THead>
              <tbody>
                {roster.data?.map((s) => (
                  <Tr key={s.id}>
                    <Td className="font-mono text-xs">{s.registerNumber}</Td>
                    <Td>{s.fullName}</Td>
                    <Td>
                      {roman(s.academicYear)}-{s.department}
                      {s.section && `-${s.section}`}
                    </Td>
                    <Td className="text-right">
                      <Button variant="ghost" size="icon" aria-label={`Unenroll ${s.registerNumber}`} onClick={() => setRemoving(s)}>
                        <UserMinus className="text-red-600" />
                      </Button>
                    </Td>
                  </Tr>
                ))}
              </tbody>
            </Table>
          )}
        </Card>
        <Card className="h-fit">
          <CardHeader title="Enroll students" />
          <CardBody className="space-y-3">
            <StudentPicker value={picked} onChange={setPicked} />
            <ErrorAlert error={error} />
            <Button className="w-full" onClick={enroll} disabled={picked.length === 0} loading={pending}>
              <UserPlus /> Enroll {picked.length || ''}
            </Button>
          </CardBody>
        </Card>
      </div>
      <CourseDialog open={editOpen} onOpenChange={setEditOpen} course={course} onSaved={courses.reload} />
      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(o) => !o && setRemoving(null)}
        title="Unenroll student?"
        description={`${removing?.registerNumber} · ${removing?.fullName} will be removed from ${course.courseCode}. Dynamic course-based groups update immediately.`}
        confirmLabel="Unenroll"
        destructive
        onConfirm={async () => {
          await api.del(`/api/admin/courses/${id}/students/${removing?.id}`)
          roster.reload()
          courses.reload()
        }}
      />
    </>
  )
}
