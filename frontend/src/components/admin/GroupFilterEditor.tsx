import { useEffect, useState } from 'react'
import { Input } from '@/components/ui/form'
import { useApi } from '@/hooks/useApi'
import { api } from '@/lib/api'
import { cn, roman } from '@/lib/utils'
import type { AttributeOptions, Course, GroupFilter, Student } from '@/types/api'

function Chips<T extends string | number>({
  options,
  value,
  onChange,
  render = (v) => String(v),
}: {
  options: T[]
  value: T[]
  onChange: (v: T[]) => void
  render?: (v: T) => string
}) {
  return (
    <div className="flex flex-wrap gap-1.5">
      {options.map((o) => {
        const on = value.includes(o)
        return (
          <button
            key={String(o)}
            type="button"
            aria-pressed={on}
            onClick={() => onChange(on ? value.filter((v) => v !== o) : [...value, o])}
            className={cn(
              'rounded-md border px-2.5 py-1 text-xs font-medium cursor-pointer',
              on ? 'border-primary-500 bg-primary-50 text-primary-700' : 'border-slate-300 text-slate-600 hover:bg-slate-50',
            )}
          >
            {render(o)}
          </button>
        )
      })}
    </div>
  )
}

/** Free-text values plus quick chips for values already present in the roster. */
function ValueList({ label, known, value, onChange }: { label: string; known: string[]; value: string[]; onChange: (v: string[]) => void }) {
  const [text, setText] = useState('')
  const options = Array.from(new Set([...known, ...value]))
  return (
    <div>
      <p className="mb-1 text-sm font-medium text-slate-700">{label}</p>
      {options.length > 0 && <Chips options={options} value={value} onChange={onChange} />}
      <Input
        className="mt-1.5 h-8 text-xs"
        placeholder={`Add ${label.toLowerCase()} and press Enter`}
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') {
            e.preventDefault()
            const v = text.trim().toUpperCase()
            if (v && !value.includes(v)) onChange([...value, v])
            setText('')
          }
        }}
      />
    </div>
  )
}

export function GroupFilterEditor({
  value,
  onChange,
  allowAcademic = true,
  allowCourses = true,
}: {
  value: GroupFilter
  onChange: (f: GroupFilter) => void
  allowAcademic?: boolean
  allowCourses?: boolean
}) {
  const attrs = useApi<AttributeOptions>('/api/admin/students/attributes')
  const courses = useApi<Course[]>('/api/admin/courses')
  const set = (patch: Partial<GroupFilter>) => onChange({ ...value, ...patch })
  return (
    <div className="space-y-4">
      {allowAcademic && (
        <>
          <div>
            <p className="mb-1 text-sm font-medium text-slate-700">Academic year</p>
            <Chips options={[1, 2, 3, 4]} value={value.academicYears ?? []} onChange={(v) => set({ academicYears: v })} render={(y) => `${roman(y)} year`} />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <ValueList label="Department" known={attrs.data?.departments ?? []} value={value.departments ?? []} onChange={(v) => set({ departments: v })} />
            <ValueList label="Section" known={attrs.data?.sections ?? []} value={value.sections ?? []} onChange={(v) => set({ sections: v })} />
            <ValueList label="Program / branch" known={attrs.data?.programs ?? []} value={value.programs ?? []} onChange={(v) => set({ programs: v })} />
            <div>
              <p className="mb-1 text-sm font-medium text-slate-700">Semester</p>
              <Chips options={[1, 2, 3, 4, 5, 6, 7, 8]} value={value.semesters ?? []} onChange={(v) => set({ semesters: v })} />
            </div>
          </div>
        </>
      )}
      {allowCourses && (
        <div>
          <div className="mb-1 flex items-center justify-between">
            <p className="text-sm font-medium text-slate-700">Courses</p>
            {(value.courseIds?.length ?? 0) > 1 && (
              <select
                aria-label="Course match"
                className="rounded border border-slate-300 px-1 py-0.5 text-xs"
                value={value.courseMatch ?? 'ANY'}
                onChange={(e) => set({ courseMatch: e.target.value as 'ANY' | 'ALL' })}
              >
                <option value="ANY">Enrolled in any</option>
                <option value="ALL">Enrolled in all</option>
              </select>
            )}
          </div>
          {courses.data?.length ? (
            <Chips
              options={courses.data.map((c) => c.id)}
              value={value.courseIds ?? []}
              onChange={(v) => set({ courseIds: v })}
              render={(id) => {
                const c = courses.data?.find((x) => x.id === id)
                return c ? `${c.courseCode} · ${c.courseName}` : String(id)
              }}
            />
          ) : (
            <p className="text-xs text-slate-500">No courses yet — create courses first.</p>
          )}
        </div>
      )}
    </div>
  )
}

export function isEmptyFilter(f: GroupFilter) {
  return !f.academicYears?.length && !f.departments?.length && !f.sections?.length && !f.programs?.length && !f.semesters?.length && !f.courseIds?.length
}

/** Live preview of the students matching a filter. */
export function FilterPreview({ filter }: { filter: GroupFilter }) {
  const [state, setState] = useState<{ count: number; students: Student[] } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const key = JSON.stringify(filter)
  useEffect(() => {
    if (isEmptyFilter(filter)) {
      setState(null)
      return
    }
    const t = setTimeout(() => {
      api
        .post<{ count: number; students: Student[] }>('/api/admin/groups/preview', { filter })
        .then((r) => {
          setState(r)
          setError(null)
        })
        .catch((e) => setError(e.message))
    }, 300)
    return () => clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])

  if (error) return <p className="text-sm text-red-600">{error}</p>
  if (!state) return <p className="text-sm text-slate-500">Choose criteria to preview members.</p>
  return (
    <div>
      <p className="text-sm font-medium text-slate-800">{state.count} matching student(s)</p>
      <ul className="mt-2 max-h-56 divide-y divide-slate-100 overflow-auto rounded-lg border border-slate-200 text-sm">
        {state.students.map((s) => (
          <li key={s.id} className="flex justify-between px-3 py-1.5">
            <span>
              <span className="font-mono text-xs">{s.registerNumber}</span> · {s.fullName}
            </span>
            <span className="text-xs text-slate-500">
              {roman(s.academicYear)}-{s.department}
              {s.section && `-${s.section}`}
            </span>
          </li>
        ))}
        {state.count === 0 && <li className="px-3 py-3 text-slate-500">Nobody matches yet.</li>}
      </ul>
    </div>
  )
}
