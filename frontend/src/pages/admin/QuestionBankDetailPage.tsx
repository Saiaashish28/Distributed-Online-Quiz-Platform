import { ArrowLeft, Copy, FileUp, Pencil, Plus, Search, Trash2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { QuestionEditorDialog } from '@/components/admin/QuestionEditor'
import { QuestionImportDialog } from '@/components/admin/QuestionImport'
import { QuestionList } from '@/components/admin/QuestionList'
import { Button } from '@/components/ui/button'
import { Card, CardHeader } from '@/components/ui/card'
import { ConfirmDialog, Dialog } from '@/components/ui/dialog'
import { EmptyState, ErrorAlert, LoadingBlock, PageHeader } from '@/components/ui/feedback'
import { Field, Input, Select } from '@/components/ui/form'
import { useToast } from '@/components/ui/toast'
import { useApi } from '@/hooks/useApi'
import { api, errorMessage } from '@/lib/api'
import type { BankDetail, QuestionAdmin, QuizSummary } from '@/types/api'
import { BankDialog } from './QuestionBanksPage'

export default function QuestionBankDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const [search, setSearch] = useState('')
  const [debounced, setDebounced] = useState('')
  useEffect(() => {
    const t = setTimeout(() => setDebounced(search), 300)
    return () => clearTimeout(t)
  }, [search])
  const { data, error, loading, reload } = useApi<BankDetail>(`/api/admin/question-banks/${id}${debounced ? `?q=${encodeURIComponent(debounced)}` : ''}`)
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [editing, setEditing] = useState<QuestionAdmin | null | 'new'>(null)
  const [deleting, setDeleting] = useState<QuestionAdmin | null>(null)
  const [importOpen, setImportOpen] = useState(false)
  const [editBank, setEditBank] = useState(false)
  const [deleteBank, setDeleteBank] = useState(false)
  const [copyOpen, setCopyOpen] = useState(false)

  if (loading && !data) return <LoadingBlock />
  if (error || !data) return <ErrorAlert error={error ?? 'Not found'} onRetry={reload} />
  const toggle = (qid: number) =>
    setSelected((s) => {
      const n = new Set(s)
      if (n.has(qid)) n.delete(qid)
      else n.add(qid)
      return n
    })

  return (
    <>
      <PageHeader
        back={
          <Link to="/admin/question-banks" className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
            <ArrowLeft className="size-4" /> Question banks
          </Link>
        }
        title={data.bank.name}
        description={`${data.bank.questionCount} question(s)${data.bank.course ? ` · ${data.bank.course.courseCode}` : ''}`}
        actions={
          <>
            <Button variant="secondary" onClick={() => setEditBank(true)}>
              <Pencil /> Edit
            </Button>
            <Button variant="ghost" className="text-red-600" onClick={() => setDeleteBank(true)}>
              <Trash2 /> Delete
            </Button>
          </>
        }
      />
      <Card>
        <CardHeader
          title="Questions"
          actions={
            <>
              <Button variant="secondary" size="sm" onClick={() => setImportOpen(true)}>
                <FileUp /> Import
              </Button>
              <Button size="sm" onClick={() => setEditing('new')}>
                <Plus /> Add question
              </Button>
            </>
          }
        />
        <div className="flex flex-wrap items-center gap-3 border-b border-slate-100 px-5 py-3">
          <div className="relative min-w-56 flex-1">
            <Search className="pointer-events-none absolute left-3 top-2.5 size-4 text-slate-400" />
            <Input className="pl-9" placeholder="Search questions" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search questions" />
          </div>
          <Button variant="secondary" size="sm" onClick={() => setSelected(new Set(data.questions.map((q) => q.id)))} disabled={data.questions.length === 0}>
            Select all
          </Button>
          <Button size="sm" onClick={() => setCopyOpen(true)} disabled={selected.size === 0}>
            <Copy /> Copy {selected.size || ''} to quiz
          </Button>
        </div>
        {data.questions.length === 0 ? (
          <EmptyState title={debounced ? 'No matching questions' : 'This bank is empty'} description="Add questions or import them from the template." />
        ) : (
          <QuestionList
            questions={data.questions}
            editable
            selectable
            selected={selected}
            onToggle={toggle}
            onEdit={(q) => setEditing(q)}
            onDelete={(q) => setDeleting(q)}
          />
        )}
      </Card>

      <QuestionEditorDialog
        open={editing !== null}
        onOpenChange={(o) => !o && setEditing(null)}
        question={editing === 'new' ? null : editing}
        createPath={`/api/admin/question-banks/${id}/questions`}
        onSaved={reload}
      />
      <QuestionImportDialog open={importOpen} onOpenChange={setImportOpen} endpoint={`/api/admin/question-banks/${id}/questions/import`} onDone={reload} />
      <BankDialog key={JSON.stringify(data.bank)} open={editBank} onOpenChange={setEditBank} bank={data.bank} onSaved={reload} />
      <CopyDialog
        open={copyOpen}
        onOpenChange={setCopyOpen}
        bankId={Number(id)}
        questionIds={[...selected]}
        onCopied={(quizId, n) => {
          toast(`Copied ${n} question(s)`)
          setSelected(new Set())
          navigate(`/admin/quizzes/${quizId}`)
        }}
      />
      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(o) => !o && setDeleting(null)}
        title="Delete question from bank?"
        description="Quizzes that already copied this question keep their copy."
        confirmLabel="Delete"
        destructive
        onConfirm={async () => {
          await api.del(`/api/admin/questions/${deleting?.id}`)
          reload()
        }}
      />
      <ConfirmDialog
        open={deleteBank}
        onOpenChange={setDeleteBank}
        title="Delete question bank?"
        description="All questions in this bank will be deleted. Quizzes keep their copies."
        confirmLabel="Delete bank"
        destructive
        onConfirm={async () => {
          await api.del(`/api/admin/question-banks/${id}`)
          navigate('/admin/question-banks')
        }}
      />
    </>
  )
}

function CopyDialog({
  open,
  onOpenChange,
  bankId,
  questionIds,
  onCopied,
}: {
  open: boolean
  onOpenChange: (o: boolean) => void
  bankId: number
  questionIds: number[]
  onCopied: (quizId: number, n: number) => void
}) {
  const quizzes = useApi<QuizSummary[]>(open ? '/api/admin/quizzes' : null)
  const drafts = quizzes.data?.filter((q) => q.status === 'DRAFT') ?? []
  const [quizId, setQuizId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const copy = async () => {
    setPending(true)
    setError(null)
    try {
      const r = await api.post<{ copied: number }>(`/api/admin/question-banks/${bankId}/copy-to-quiz`, { quizId: Number(quizId), questionIds })
      onOpenChange(false)
      onCopied(Number(quizId), r.copied)
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
      title={`Copy ${questionIds.length} question(s) to a quiz`}
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={copy} loading={pending} disabled={!quizId}>
            Copy
          </Button>
        </>
      }
    >
      <div className="space-y-3">
        <Field label="Draft quiz" hint="Only draft quizzes can receive questions.">
          {(id) => (
            <Select id={id} value={quizId} onChange={(e) => setQuizId(e.target.value)}>
              <option value="">Select a quiz</option>
              {drafts.map((q) => (
                <option key={q.id} value={q.id}>
                  {q.title}
                </option>
              ))}
            </Select>
          )}
        </Field>
        {quizzes.data && drafts.length === 0 && <p className="text-sm text-slate-500">No draft quizzes. Create one first.</p>}
        <ErrorAlert error={error} />
      </div>
    </Dialog>
  )
}
