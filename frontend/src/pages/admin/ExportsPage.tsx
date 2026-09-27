import { FileSpreadsheet } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { EmptyState, ErrorAlert, LoadingBlock, PageHeader } from '@/components/ui/feedback'
import { Field, Select } from '@/components/ui/form'
import { useApi } from '@/hooks/useApi'
import { download, errorMessage } from '@/lib/api'
import { browserTimeZone, cn } from '@/lib/utils'
import type { AssignmentSummary, Group } from '@/types/api'

export default function ExportsPage() {
  const assignments = useApi<AssignmentSummary[]>('/api/admin/assignments')
  const groups = useApi<Group[]>('/api/admin/groups')
  const [selected, setSelected] = useState<number[]>([])
  const [groupId, setGroupId] = useState('')
  const [mode, setMode] = useState<'BLANK' | 'ZERO'>('BLANK')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const run = async () => {
    setPending(true)
    setError(null)
    try {
      const p = new URLSearchParams({ assignmentIds: selected.join(','), nonSubmitterMarks: mode, tz: browserTimeZone() })
      if (groupId) p.set('groupId', groupId)
      await download(`/api/admin/exports/consolidated?${p}`, 'consolidated.xlsx')
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setPending(false)
    }
  }

  return (
    <>
      <PageHeader
        title="Excel exports"
        description="Consolidated marks across several assignments. Per-assignment marksheets and responses are on each assignment's Export tab."
      />
      <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
        <Card>
          <CardHeader title="Choose assignments" description={`${selected.length} selected`} />
          {assignments.loading && !assignments.data ? (
            <LoadingBlock />
          ) : assignments.data?.length === 0 ? (
            <EmptyState icon={<FileSpreadsheet />} title="No assignments yet" />
          ) : (
            <ul className="divide-y divide-slate-100">
              {assignments.data?.map((a) => {
                const on = selected.includes(a.id)
                return (
                  <li key={a.id}>
                    <label className={cn('flex cursor-pointer items-center gap-3 px-5 py-3', on && 'bg-primary-50/50')}>
                      <input
                        type="checkbox"
                        className="size-4 accent-primary-600"
                        checked={on}
                        onChange={() => setSelected((s) => (on ? s.filter((x) => x !== a.id) : [...s, a.id]))}
                      />
                      <span className="min-w-0">
                        <span className="block font-medium text-slate-800">{a.name}</span>
                        <span className="block text-xs text-slate-500">
                          {a.quizTitle}
                          {a.course && ` · ${a.course.courseCode}`}
                        </span>
                      </span>
                    </label>
                  </li>
                )
              })}
            </ul>
          )}
        </Card>
        <Card className="h-fit">
          <CardHeader title="Options" />
          <CardBody className="space-y-4">
            <Field label="Limit to group" hint="Produces a group marksheet">
              {(id) => (
                <Select id={id} value={groupId} onChange={(e) => setGroupId(e.target.value)}>
                  <option value="">All assigned students</option>
                  {groups.data?.map((g) => (
                    <option key={g.id} value={g.id}>
                      {g.name}
                    </option>
                  ))}
                </Select>
              )}
            </Field>
            <Field label="Marks for non-submitters">
              {(id) => (
                <Select id={id} value={mode} onChange={(e) => setMode(e.target.value as 'BLANK' | 'ZERO')}>
                  <option value="BLANK">Show NOT_SUBMITTED</option>
                  <option value="ZERO">Record as zero</option>
                </Select>
              )}
            </Field>
            <ErrorAlert error={error} />
            <Button className="w-full" onClick={run} loading={pending} disabled={selected.length === 0}>
              <FileSpreadsheet /> Download consolidated (.xlsx)
            </Button>
          </CardBody>
        </Card>
      </div>
    </>
  )
}
