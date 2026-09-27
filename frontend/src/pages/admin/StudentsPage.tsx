import { ChevronLeft, ChevronRight, FileUp, Pencil, Plus, Search, Users } from 'lucide-react'
import { useEffect, useState } from 'react'
import { ImportDialog } from '@/components/admin/ImportDialog'
import { Badge, StatusBadge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { EmptyState, ErrorAlert, LoadingBlock, PageHeader } from '@/components/ui/feedback'
import { Checkbox, Field, Input, Select } from '@/components/ui/form'
import { Table, THead, Td, Th, Tr } from '@/components/ui/table'
import { useToast } from '@/components/ui/toast'
import { useApi } from '@/hooks/useApi'
import { api, errorMessage } from '@/lib/api'
import { roman } from '@/lib/utils'
import type { AttributeOptions, Course, Page, RosterImportResult, Student } from '@/types/api'

export default function StudentsPage() {
  const [q, setQ] = useState('')
  const [debounced, setDebounced] = useState('')
  const [year, setYear] = useState('')
  const [department, setDepartment] = useState('')
  const [section, setSection] = useState('')
  const [active, setActive] = useState('true')
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<Student | 'new' | null>(null)
  const [importOpen, setImportOpen] = useState(false)
  const [updateExisting, setUpdateExisting] = useState(false)
  const toast = useToast()

  useEffect(() => {
    const t = setTimeout(() => {
      setDebounced(q)
      setPage(0)
    }, 300)
    return () => clearTimeout(t)
  }, [q])

  const params = new URLSearchParams({ page: String(page), size: '25' })
  if (debounced.trim()) params.set('q', debounced.trim())
  if (year) params.set('year', year)
  if (department) params.set('department', department)
  if (section) params.set('section', section)
  if (active) params.set('active', active)
  const { data, error, loading, reload } = useApi<Page<Student>>(`/api/admin/students?${params}`)
  const attrs = useApi<AttributeOptions>('/api/admin/students/attributes')
  const courses = useApi<Course[]>('/api/admin/courses')

  return (
    <>
      <PageHeader
        title="Students"
        description="Roster of registered students. Register numbers are unique."
        actions={
          <>
            <Button variant="secondary" onClick={() => setImportOpen(true)}>
              <FileUp /> Import roster
            </Button>
            <Button onClick={() => setEditing('new')}>
              <Plus /> Add student
            </Button>
          </>
        }
      />
      <Card>
        <div className="flex flex-wrap items-end gap-3 border-b border-slate-100 p-4">
          <div className="relative min-w-56 flex-1">
            <Search className="pointer-events-none absolute left-3 top-2.5 size-4 text-slate-400" />
            <Input className="pl-9" placeholder="Search register number or name" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search students" />
          </div>
          <Select aria-label="Year" className="w-28" value={year} onChange={(e) => (setYear(e.target.value), setPage(0))}>
            <option value="">All years</option>
            {[1, 2, 3, 4].map((y) => (
              <option key={y} value={y}>{roman(y)} year</option>
            ))}
          </Select>
          <Select aria-label="Department" className="w-36" value={department} onChange={(e) => (setDepartment(e.target.value), setPage(0))}>
            <option value="">All depts</option>
            {attrs.data?.departments.map((d) => <option key={d}>{d}</option>)}
          </Select>
          <Select aria-label="Section" className="w-32" value={section} onChange={(e) => (setSection(e.target.value), setPage(0))}>
            <option value="">All sections</option>
            {attrs.data?.sections.map((d) => <option key={d}>{d}</option>)}
          </Select>
          <Select aria-label="Status" className="w-32" value={active} onChange={(e) => (setActive(e.target.value), setPage(0))}>
            <option value="true">Active</option>
            <option value="false">Inactive</option>
            <option value="">All</option>
          </Select>
        </div>
        {error ? (
          <div className="p-4">
            <ErrorAlert error={error} onRetry={reload} />
          </div>
        ) : loading && !data ? (
          <LoadingBlock />
        ) : data && data.content.length === 0 ? (
          <EmptyState icon={<Users />} title="No students found" description="Add students one by one or import a roster spreadsheet." />
        ) : (
          <>
            <Table>
              <THead>
                <tr>
                  <Th>Register no.</Th>
                  <Th>Name</Th>
                  <Th>Year</Th>
                  <Th>Dept / Section</Th>
                  <Th>Sem</Th>
                  <Th>Courses</Th>
                  <Th>Status</Th>
                  <Th />
                </tr>
              </THead>
              <tbody>
                {data?.content.map((s) => (
                  <Tr key={s.id}>
                    <Td className="font-mono text-xs">{s.registerNumber}</Td>
                    <Td>
                      <p className="font-medium text-slate-800">{s.fullName}</p>
                      {s.email && <p className="text-xs text-slate-500">{s.email}</p>}
                    </Td>
                    <Td>{roman(s.academicYear)}</Td>
                    <Td>
                      {s.department}
                      {s.program && `-${s.program}`}
                      {s.section && ` · ${s.section}`}
                    </Td>
                    <Td>{s.semester ?? '—'}</Td>
                    <Td>
                      <div className="flex flex-wrap gap-1">
                        {s.courses.map((c) => (
                          <Badge key={c.id} tone="indigo">{c.courseCode}</Badge>
                        ))}
                      </div>
                    </Td>
                    <Td>{s.active ? <StatusBadge status="ACTIVE" /> : <Badge>Inactive</Badge>}</Td>
                    <Td className="text-right">
                      <Button variant="ghost" size="icon" aria-label={`Edit ${s.registerNumber}`} onClick={() => setEditing(s)}>
                        <Pencil />
                      </Button>
                    </Td>
                  </Tr>
                ))}
              </tbody>
            </Table>
            {data && (
              <div className="flex items-center justify-between border-t border-slate-100 px-4 py-3 text-sm text-slate-600">
                <span>{data.totalElements} student(s)</span>
                <div className="flex items-center gap-2">
                  <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)} aria-label="Previous page">
                    <ChevronLeft />
                  </Button>
                  <span>
                    Page {data.page + 1} of {Math.max(1, data.totalPages)}
                  </span>
                  <Button variant="secondary" size="sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)} aria-label="Next page">
                    <ChevronRight />
                  </Button>
                </div>
              </div>
            )}
          </>
        )}
      </Card>

      <StudentDialog
        student={editing}
        courses={courses.data ?? []}
        onClose={() => setEditing(null)}
        onSaved={() => {
          reload()
          attrs.reload()
        }}
      />

      <ImportDialog<RosterImportResult>
        open={importOpen}
        onOpenChange={setImportOpen}
        title="Import student roster"
        endpoint="/api/admin/students/import"
        templatePath="/api/admin/templates/students"
        templateName="QuizSphere_Student_Roster_Template"
        extraParams={{ updateExisting }}
        extraOptions={
          <div className="space-y-2 rounded-lg bg-slate-50 p-3 text-sm text-slate-600">
            <Checkbox
              checked={updateExisting}
              onChange={setUpdateExisting}
              label="Update students that already exist"
              description="Off by default: existing register numbers are skipped, never silently overwritten. Course enrollments are only added, never removed."
            />
            <p className="text-xs">Columns: Register Number, Student Name, Email, Academic Year (1-4 or I-IV), Department, Section, Semester, Course Codes (separated by ;), Program, Password. Without a password, the initial password is the register number and must be changed at first login.</p>
          </div>
        }
        summarize={(r) => ({ valid: r.toCreate + r.toUpdate, invalid: r.invalid, label: 'students' })}
        onCommitted={(r) => {
          toast(`Imported: ${r.toCreate} created, ${r.toUpdate} updated, ${r.skippedExisting} skipped`)
          reload()
          attrs.reload()
        }}
        renderPreview={(r) => (
          <Table>
            <THead>
              <tr>
                <Th>Row</Th>
                <Th>Action</Th>
                <Th>Register no.</Th>
                <Th>Name</Th>
                <Th>Year / Dept / Sec</Th>
                <Th>Courses</Th>
                <Th>Notes</Th>
              </tr>
            </THead>
            <tbody>
              {r.rows.map((row) => (
                <Tr key={row.rowNumber} className={row.action === 'INVALID' ? 'bg-red-50/50' : undefined}>
                  <Td className="text-slate-500">{row.rowNumber}</Td>
                  <Td>
                    <StatusBadge status={row.action} />
                  </Td>
                  <Td className="font-mono text-xs">{row.registerNumber ?? '—'}</Td>
                  <Td>{row.fullName ?? '—'}</Td>
                  <Td className="text-xs">
                    {row.values.academicyear} / {row.values.department} / {row.values.section}
                  </Td>
                  <Td className="text-xs">{row.values.coursecodes}</Td>
                  <Td className="text-xs">
                    {row.errors.map((e) => <p key={e} className="text-red-700">{e}</p>)}
                    {row.warnings.map((w) => <p key={w} className="text-amber-700">{w}</p>)}
                  </Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
      />
    </>
  )
}

function StudentDialog({
  student,
  courses,
  onClose,
  onSaved,
}: {
  student: Student | 'new' | null
  courses: Course[]
  onClose: () => void
  onSaved: () => void
}) {
  const isNew = student === 'new'
  const s = student && student !== 'new' ? student : null
  const toast = useToast()
  const [form, setForm] = useState<Record<string, string>>({})
  const [courseIds, setCourseIds] = useState<number[]>([])
  const [active, setActive] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  useEffect(() => {
    if (!student) return
    setError(null)
    setForm({
      registerNumber: s?.registerNumber ?? '',
      fullName: s?.fullName ?? '',
      email: s?.email ?? '',
      academicYear: String(s?.academicYear ?? ''),
      department: s?.department ?? '',
      section: s?.section ?? '',
      program: s?.program ?? '',
      semester: s?.semester ? String(s.semester) : '',
      password: '',
    })
    setCourseIds(s?.courses.map((c) => c.id) ?? [])
    setActive(s?.active ?? true)
  }, [student, s])

  const set = (k: string) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [k]: e.target.value }))

  const save = async () => {
    setPending(true)
    setError(null)
    const common = {
      fullName: form.fullName,
      email: form.email,
      academicYear: Number(form.academicYear),
      department: form.department,
      section: form.section,
      program: form.program,
      semester: form.semester ? Number(form.semester) : undefined,
      courseIds,
    }
    try {
      if (isNew) {
        await api.post('/api/admin/students', { ...common, registerNumber: form.registerNumber, password: form.password || undefined })
        toast('Student added')
      } else if (s) {
        await api.patch(`/api/admin/students/${s.id}`, { ...common, active, resetPassword: form.password || undefined })
        toast('Student updated')
      }
      onSaved()
      onClose()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setPending(false)
    }
  }

  return (
    <Dialog
      open={student !== null}
      onOpenChange={(o) => !o && onClose()}
      title={isNew ? 'Add student' : `Edit ${s?.registerNumber}`}
      size="lg"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={save} loading={pending}>
            Save
          </Button>
        </>
      }
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Register number" required hint={isNew ? 'Unique; cannot be changed later' : undefined}>
          {(id) => <Input id={id} value={form.registerNumber ?? ''} onChange={set('registerNumber')} disabled={!isNew} />}
        </Field>
        <Field label="Full name" required>
          {(id) => <Input id={id} value={form.fullName ?? ''} onChange={set('fullName')} />}
        </Field>
        <Field label="Email">
          {(id) => <Input id={id} type="email" value={form.email ?? ''} onChange={set('email')} />}
        </Field>
        <Field label="Academic year" required>
          {(id) => (
            <Select id={id} value={form.academicYear ?? ''} onChange={set('academicYear')}>
              <option value="">Select</option>
              {[1, 2, 3, 4].map((y) => (
                <option key={y} value={y}>{roman(y)}</option>
              ))}
            </Select>
          )}
        </Field>
        <Field label="Department" required>
          {(id) => <Input id={id} value={form.department ?? ''} onChange={set('department')} placeholder="CSE" />}
        </Field>
        <Field label="Section">
          {(id) => <Input id={id} value={form.section ?? ''} onChange={set('section')} placeholder="A" />}
        </Field>
        <Field label="Program / branch">
          {(id) => <Input id={id} value={form.program ?? ''} onChange={set('program')} placeholder="IT" />}
        </Field>
        <Field label="Semester">
          {(id) => <Input id={id} type="number" min={1} max={12} value={form.semester ?? ''} onChange={set('semester')} />}
        </Field>
        <Field
          label={isNew ? 'Initial password' : 'Reset password'}
          hint={isNew ? 'Leave blank to use the register number. The student must change it at first login.' : 'Leave blank to keep the current password.'}
          className="sm:col-span-2"
        >
          {(id) => <Input id={id} type="password" autoComplete="new-password" value={form.password ?? ''} onChange={set('password')} minLength={8} />}
        </Field>
        <div className="sm:col-span-2">
          <p className="mb-1 text-sm font-medium text-slate-700">Course enrollments</p>
          {courses.length === 0 ? (
            <p className="text-sm text-slate-500">No courses yet.</p>
          ) : (
            <div className="flex flex-wrap gap-2">
              {courses.map((c) => {
                const on = courseIds.includes(c.id)
                return (
                  <button
                    key={c.id}
                    type="button"
                    aria-pressed={on}
                    onClick={() => setCourseIds((ids) => (on ? ids.filter((x) => x !== c.id) : [...ids, c.id]))}
                    className={`rounded-md border px-3 py-1 text-xs font-medium ${on ? 'border-primary-500 bg-primary-50 text-primary-700' : 'border-slate-300 text-slate-600 hover:bg-slate-50'}`}
                  >
                    {c.courseCode} · {c.courseName}
                  </button>
                )
              })}
            </div>
          )}
        </div>
        {!isNew && (
          <div className="sm:col-span-2">
            <Checkbox checked={active} onChange={setActive} label="Active" description="Deactivated students cannot sign in and lose access to all quizzes. Their past results are kept." />
          </div>
        )}
        <div className="sm:col-span-2">
          <ErrorAlert error={error} />
        </div>
      </div>
    </Dialog>
  )
}
