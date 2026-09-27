import { ArrowLeft, Pencil, RefreshCw, Trash2, UserMinus, UserPlus, Users } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { FilterPreview, GroupFilterEditor } from '@/components/admin/GroupFilterEditor'
import { StudentPicker } from '@/components/admin/StudentPicker'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { ConfirmDialog, Dialog } from '@/components/ui/dialog'
import { Alert, EmptyState, ErrorAlert, LoadingBlock, PageHeader } from '@/components/ui/feedback'
import { Field, Input } from '@/components/ui/form'
import { Table, THead, Td, Th, Tr } from '@/components/ui/table'
import { useToast } from '@/components/ui/toast'
import { useApi } from '@/hooks/useApi'
import { api, errorMessage } from '@/lib/api'
import { roman } from '@/lib/utils'
import type { GroupDetail, GroupFilter, MembershipResult, Student, StudentRef } from '@/types/api'
import { GROUP_TYPE_LABEL } from './GroupsPage'

export default function GroupDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const { data, error, loading, reload } = useApi<GroupDetail>(`/api/admin/groups/${id}`)
  const [picked, setPicked] = useState<StudentRef[]>([])
  const [removing, setRemoving] = useState<Student | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [editing, setEditing] = useState(false)
  const [pending, setPending] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  if (loading && !data) return <LoadingBlock />
  if (error || !data) return <ErrorAlert error={error ?? 'Not found'} onRetry={reload} />
  const g = data.group
  const manual = g.membershipMode === 'MANUAL'

  const addMembers = async () => {
    setPending(true)
    setActionError(null)
    try {
      const r = await api.post<MembershipResult>(`/api/admin/groups/${id}/members`, { studentIds: picked.map((p) => p.id) })
      toast(`${r.added} added${r.alreadyPresent ? `, ${r.alreadyPresent} were already members` : ''}`)
      setPicked([])
      reload()
    } catch (e) {
      setActionError(errorMessage(e))
    } finally {
      setPending(false)
    }
  }

  return (
    <>
      <PageHeader
        back={
          <Link to="/admin/groups" className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
            <ArrowLeft className="size-4" /> Groups
          </Link>
        }
        title={g.name}
        description={g.description}
        actions={
          <>
            <Badge tone="blue">{GROUP_TYPE_LABEL[g.groupType]}</Badge>
            <Badge tone={manual ? 'amber' : 'green'}>{manual ? 'Manual' : 'Dynamic'}</Badge>
            {!manual && (
              <Button variant="secondary" onClick={() => api.post(`/api/admin/groups/${id}/refresh`).then(reload)}>
                <RefreshCw /> Refresh
              </Button>
            )}
            {g.owned && (
              <>
                <Button variant="secondary" onClick={() => setEditing(true)}>
                  <Pencil /> Edit
                </Button>
                <Button variant="destructive" onClick={() => setDeleting(true)} disabled={g.usedByAssignments} title={g.usedByAssignments ? 'Used by assignments' : undefined}>
                  <Trash2 /> Delete
                </Button>
              </>
            )}
          </>
        }
      />
      {g.filterSummary && (
        <Alert tone="info" className="mb-4" title={manual ? 'Created from criteria' : 'Membership criteria'}>
          {g.filterSummary}
          {!manual && <span className="block text-xs">Members are recomputed from current student attributes and enrollments.</span>}
        </Alert>
      )}
      <div className={manual && g.owned ? 'grid gap-6 lg:grid-cols-[1fr_340px]' : ''}>
        <Card>
          <CardHeader title={`Members (${data.members.length})`} />
          {data.members.length === 0 ? (
            <EmptyState icon={<Users />} title="No members" />
          ) : (
            <Table>
              <THead>
                <tr>
                  <Th>Register no.</Th>
                  <Th>Name</Th>
                  <Th>Class</Th>
                  <Th>Courses</Th>
                  {manual && g.owned && <Th />}
                </tr>
              </THead>
              <tbody>
                {data.members.map((s) => (
                  <Tr key={s.id}>
                    <Td className="font-mono text-xs">{s.registerNumber}</Td>
                    <Td>
                      {s.fullName} {!s.active && <Badge>Inactive</Badge>}
                    </Td>
                    <Td>
                      {roman(s.academicYear)}-{s.department}
                      {s.program && `-${s.program}`}
                      {s.section && `-${s.section}`}
                    </Td>
                    <Td className="text-xs">{s.courses.map((c) => c.courseCode).join(', ')}</Td>
                    {manual && g.owned && (
                      <Td className="text-right">
                        <Button variant="ghost" size="icon" aria-label={`Remove ${s.registerNumber}`} onClick={() => setRemoving(s)}>
                          <UserMinus className="text-red-600" />
                        </Button>
                      </Td>
                    )}
                  </Tr>
                ))}
              </tbody>
            </Table>
          )}
        </Card>
        {manual && g.owned && (
          <Card className="h-fit">
            <CardHeader title="Add members" description="Duplicates are ignored." />
            <CardBody className="space-y-3">
              <StudentPicker value={picked} onChange={setPicked} />
              <ErrorAlert error={actionError} />
              <Button className="w-full" onClick={addMembers} disabled={picked.length === 0} loading={pending}>
                <UserPlus /> Add {picked.length || ''}
              </Button>
            </CardBody>
          </Card>
        )}
      </div>

      <EditGroupDialog key={JSON.stringify(g)} open={editing} onOpenChange={setEditing} detail={data} onSaved={reload} />
      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(o) => !o && setRemoving(null)}
        title="Remove member?"
        description={`${removing?.registerNumber} · ${removing?.fullName} will be removed from ${g.name}.`}
        confirmLabel="Remove"
        destructive
        onConfirm={async () => {
          await api.del(`/api/admin/groups/${id}/members/${removing?.id}`)
          reload()
        }}
      />
      <ConfirmDialog
        open={deleting}
        onOpenChange={setDeleting}
        title="Delete group?"
        description={`${g.name} will be deleted. Students are not affected.`}
        confirmLabel="Delete group"
        destructive
        onConfirm={async () => {
          await api.del(`/api/admin/groups/${id}`)
          navigate('/admin/groups')
        }}
      />
    </>
  )
}

function EditGroupDialog({ open, onOpenChange, detail, onSaved }: { open: boolean; onOpenChange: (o: boolean) => void; detail: GroupDetail; onSaved: () => void }) {
  const g = detail.group
  const [name, setName] = useState(g.name)
  const [description, setDescription] = useState(g.description ?? '')
  const [filter, setFilter] = useState<GroupFilter>(g.filter ?? {})
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const dynamic = g.membershipMode === 'DYNAMIC'

  const save = async () => {
    setPending(true)
    setError(null)
    try {
      await api.patch(`/api/admin/groups/${g.id}`, { name, description, filter: dynamic ? filter : undefined })
      onSaved()
      onOpenChange(false)
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
      title="Edit group"
      size={dynamic ? 'xl' : 'md'}
      footer={
        <>
          <Button variant="secondary" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={save} loading={pending}>
            Save
          </Button>
        </>
      }
    >
      <div className={dynamic ? 'grid gap-6 lg:grid-cols-[1fr_320px]' : ''}>
        <div className="space-y-4">
          <Field label="Name" required>
            {(id) => <Input id={id} value={name} onChange={(e) => setName(e.target.value)} />}
          </Field>
          <Field label="Description">
            {(id) => <Input id={id} value={description} onChange={(e) => setDescription(e.target.value)} />}
          </Field>
          {dynamic && <GroupFilterEditor value={filter} onChange={setFilter} allowCourses={g.groupType !== 'ACADEMIC'} />}
          <ErrorAlert error={error} />
        </div>
        {dynamic && (
          <div className="rounded-lg bg-slate-50 p-4">
            <FilterPreview filter={filter} />
          </div>
        )}
      </div>
    </Dialog>
  )
}
