import { Plus, UsersRound } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { FilterPreview, GroupFilterEditor, isEmptyFilter } from '@/components/admin/GroupFilterEditor'
import { Badge } from '@/components/ui/badge'
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
import type { Course, Group, GroupFilter, GroupType, MembershipMode } from '@/types/api'

export const GROUP_TYPE_LABEL: Record<GroupType, string> = {
  ACADEMIC: 'Academic',
  COURSE_BASED: 'Course-based',
  CUSTOM: 'Custom',
}

export default function GroupsPage() {
  const { data, error, loading, reload } = useApi<Group[]>('/api/admin/groups')
  const [open, setOpen] = useState(false)
  return (
    <>
      <PageHeader
        title="Groups"
        description="Target quizzes at classes (III-CSE), sections (II-ECE-A) or course cohorts (III-CSE-DCC)."
        actions={
          <Button onClick={() => setOpen(true)}>
            <Plus /> New group
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
          <EmptyState icon={<UsersRound />} title="No groups yet" description="Create academic or course-based groups to assign quizzes." action={<Button onClick={() => setOpen(true)}>Create a group</Button>} />
        ) : (
          <Table>
            <THead>
              <tr>
                <Th>Name</Th>
                <Th>Type</Th>
                <Th>Membership</Th>
                <Th>Criteria</Th>
                <Th className="text-right">Members</Th>
                <Th>Owner</Th>
              </tr>
            </THead>
            <tbody>
              {data?.map((g) => (
                <Tr key={g.id}>
                  <Td className="font-medium">
                    <Link className="text-primary-700 hover:underline" to={`/admin/groups/${g.id}`}>
                      {g.name}
                    </Link>
                    {g.description && <p className="text-xs font-normal text-slate-500">{g.description}</p>}
                  </Td>
                  <Td>
                    <Badge tone={g.groupType === 'ACADEMIC' ? 'blue' : g.groupType === 'COURSE_BASED' ? 'purple' : 'gray'}>{GROUP_TYPE_LABEL[g.groupType]}</Badge>
                  </Td>
                  <Td>
                    <Badge tone={g.membershipMode === 'DYNAMIC' ? 'green' : 'amber'}>{g.membershipMode === 'DYNAMIC' ? 'Dynamic' : 'Manual'}</Badge>
                  </Td>
                  <Td className="text-xs text-slate-600">{g.filterSummary || '—'}</Td>
                  <Td className="text-right tabular-nums">{g.memberCount}</Td>
                  <Td className="text-xs text-slate-500">{g.owned ? 'You' : g.ownerName}</Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
      <CreateGroupDialog open={open} onOpenChange={setOpen} onCreated={reload} />
    </>
  )
}

function suggestName(type: GroupType, f: GroupFilter, courses: Course[]) {
  const parts: string[] = []
  if (f.academicYears?.length === 1) parts.push(roman(f.academicYears[0]))
  if (f.departments?.length === 1) parts.push(f.departments[0])
  if (f.programs?.length === 1) parts.push(f.programs[0])
  if (f.sections?.length === 1) parts.push(f.sections[0])
  if (type === 'COURSE_BASED' && f.courseIds?.length === 1) {
    const c = courses.find((x) => x.id === f.courseIds![0])
    if (c) parts.push(c.courseName.replace(/\s+/g, ''))
  }
  return parts.join('-')
}

function CreateGroupDialog({ open, onOpenChange, onCreated }: { open: boolean; onOpenChange: (o: boolean) => void; onCreated: () => void }) {
  const navigate = useNavigate()
  const toast = useToast()
  const courses = useApi<Course[]>('/api/admin/courses')
  const [type, setType] = useState<GroupType>('ACADEMIC')
  const [mode, setMode] = useState<MembershipMode>('DYNAMIC')
  const [filter, setFilter] = useState<GroupFilter>({})
  const [name, setName] = useState('')
  const [nameTouched, setNameTouched] = useState(false)
  const [description, setDescription] = useState('')
  const [seed, setSeed] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  useEffect(() => {
    if (!nameTouched) setName(suggestName(type, filter, courses.data ?? []))
  }, [type, filter, courses.data, nameTouched])

  useEffect(() => {
    if (open) {
      setType('ACADEMIC')
      setMode('DYNAMIC')
      setFilter({})
      setName('')
      setNameTouched(false)
      setDescription('')
      setError(null)
    }
  }, [open])

  const save = async () => {
    setPending(true)
    setError(null)
    try {
      const f = type === 'ACADEMIC' ? { ...filter, courseIds: [] } : filter
      const g = await api.post<Group>('/api/admin/groups', {
        name,
        description,
        groupType: type,
        membershipMode: mode,
        filter: f,
        seedFromFilter: mode === 'MANUAL' && seed,
      })
      toast(`Group ${g.name} created with ${g.memberCount} member(s)`)
      onCreated()
      onOpenChange(false)
      navigate(`/admin/groups/${g.id}`)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setPending(false)
    }
  }

  const needsFilter = mode === 'DYNAMIC'
  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title="New group"
      size="xl"
      footer={
        <>
          <Button variant="secondary" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={save} loading={pending} disabled={!name.trim() || (needsFilter && isEmptyFilter(filter))}>
            Create group
          </Button>
        </>
      }
    >
      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Group type">
              {(id) => (
                <Select id={id} value={type} onChange={(e) => setType(e.target.value as GroupType)}>
                  <option value="ACADEMIC">Academic (year / dept / section / program)</option>
                  <option value="COURSE_BASED">Course-based (enrollment)</option>
                  <option value="CUSTOM">Custom</option>
                </Select>
              )}
            </Field>
            <Field label="Membership">
              {(id) => (
                <Select id={id} value={mode} onChange={(e) => setMode(e.target.value as MembershipMode)}>
                  <option value="DYNAMIC">Dynamic — follows student attributes</option>
                  <option value="MANUAL">Manual — you manage members</option>
                </Select>
              )}
            </Field>
          </div>
          <GroupFilterEditor value={filter} onChange={setFilter} allowCourses={type !== 'ACADEMIC'} />
          {mode === 'MANUAL' && (
            <Checkbox checked={seed} onChange={setSeed} label="Add the matching students as initial members" description="You can add or remove members individually afterwards." />
          )}
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Name" required hint="Suggested from the criteria; edit freely">
              {(id) => (
                <Input
                  id={id}
                  value={name}
                  onChange={(e) => {
                    setNameTouched(true)
                    setName(e.target.value)
                  }}
                  maxLength={100}
                />
              )}
            </Field>
            <Field label="Description">
              {(id) => <Input id={id} value={description} onChange={(e) => setDescription(e.target.value)} maxLength={500} />}
            </Field>
          </div>
          <ErrorAlert error={error} />
        </div>
        <div className="rounded-lg bg-slate-50 p-4">
          <p className="mb-2 text-sm font-semibold text-slate-700">Member preview</p>
          <FilterPreview filter={type === 'ACADEMIC' ? { ...filter, courseIds: [] } : filter} />
        </div>
      </div>
    </Dialog>
  )
}
