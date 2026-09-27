import { Archive, ArrowLeft, Eye, FileSpreadsheet, FileUp, Lock, Plus, Send, Settings, Trash2, Undo2 } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { QuestionEditorDialog } from '@/components/admin/QuestionEditor'
import { QuestionImportDialog } from '@/components/admin/QuestionImport'
import { QuestionList } from '@/components/admin/QuestionList'
import { StatusBadge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardHeader } from '@/components/ui/card'
import { ConfirmDialog, Dialog } from '@/components/ui/dialog'
import { Alert, EmptyState, ErrorAlert, LoadingBlock, PageHeader } from '@/components/ui/feedback'
import { useToast } from '@/components/ui/toast'
import { useApi } from '@/hooks/useApi'
import { ApiError, api, download, errorMessage } from '@/lib/api'
import { browserTimeZone, formatMarks } from '@/lib/utils'
import type { QuestionAdmin, QuizDetail, ValidationProblem } from '@/types/api'
import { QuizSettingsDialog } from './QuizzesPage'

export default function QuizEditorPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const { data: quiz, setData, error, loading, reload } = useApi<QuizDetail>(`/api/admin/quizzes/${id}`)
  const [editing, setEditing] = useState<QuestionAdmin | null | 'new'>(null)
  const [deleting, setDeleting] = useState<QuestionAdmin | null>(null)
  const [importOpen, setImportOpen] = useState(false)
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [previewOpen, setPreviewOpen] = useState(false)
  const [deleteQuiz, setDeleteQuiz] = useState(false)
  const [problems, setProblems] = useState<ValidationProblem[]>([])
  const [actionError, setActionError] = useState<string | null>(null)
  const [busy, setBusy] = useState<string | null>(null)

  if (loading && !quiz) return <LoadingBlock />
  if (error || !quiz) return <ErrorAlert error={error ?? 'Not found'} onRetry={reload} />

  const act = async (name: string, fn: () => Promise<QuizDetail | void>, success: string) => {
    setBusy(name)
    setActionError(null)
    setProblems([])
    try {
      const r = await fn()
      if (r) setData(r)
      toast(success)
    } catch (e) {
      if (e instanceof ApiError && Array.isArray(e.details)) setProblems(e.details as ValidationProblem[])
      setActionError(errorMessage(e))
    } finally {
      setBusy(null)
    }
  }

  const move = (index: number, dir: -1 | 1) => {
    const ids = quiz.questions.map((q) => q.id)
    const j = index + dir
    ;[ids[index], ids[j]] = [ids[j], ids[index]]
    void act('reorder', () => api.post<QuizDetail>(`/api/admin/quizzes/${quiz.id}/questions/reorder`, { questionIds: ids }), 'Order updated')
  }

  const highlight = new Set(problems.map((p) => p.questionId).filter((x): x is number => x != null))

  return (
    <>
      <PageHeader
        back={
          <Link to="/admin/quizzes" className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
            <ArrowLeft className="size-4" /> Quizzes
          </Link>
        }
        title={
          <span className="flex flex-wrap items-center gap-2">
            {quiz.title} <StatusBadge status={quiz.status} />
          </span>
        }
        description={`${quiz.questions.length} questions · ${formatMarks(quiz.totalPoints)} marks · ${quiz.durationMinutes} min${quiz.course ? ` · ${quiz.course.courseCode}` : ''}`}
        actions={
          <>
            <Button variant="secondary" onClick={() => setPreviewOpen(true)} disabled={quiz.questions.length === 0}>
              <Eye /> Preview
            </Button>
            {quiz.status !== 'ARCHIVED' && (
              <Button variant="secondary" onClick={() => setSettingsOpen(true)}>
                <Settings /> Settings
              </Button>
            )}
            {quiz.status === 'DRAFT' && (
              <Button onClick={() => act('publish', () => api.post<QuizDetail>(`/api/admin/quizzes/${quiz.id}/publish`), 'Quiz published')} loading={busy === 'publish'}>
                <Send /> Publish
              </Button>
            )}
            {quiz.status === 'PUBLISHED' && (
              <>
                <Button variant="secondary" onClick={() => act('unpublish', () => api.post<QuizDetail>(`/api/admin/quizzes/${quiz.id}/unpublish`), 'Moved back to draft')} loading={busy === 'unpublish'}>
                  <Undo2 /> Back to draft
                </Button>
                <Button asChild>
                  <Link to={`/admin/assignments/new?quizId=${quiz.id}`}>Assign</Link>
                </Button>
              </>
            )}
          </>
        }
      />

      {quiz.status !== 'DRAFT' && (
        <Alert tone="info" className="mb-4" title={quiz.status === 'ARCHIVED' ? 'Archived quiz' : 'Published quiz is locked'}>
          <span className="flex items-center gap-1">
            <Lock className="size-3.5" /> Questions can't change while students may be taking it. Move it back to draft to edit (only possible before anyone has attempted it).
          </span>
        </Alert>
      )}
      <ErrorAlert error={actionError} />
      {problems.length > 0 && (
        <Alert tone="error" className="mt-2" title="Fix these before publishing">
          <ul className="list-disc pl-4">
            {problems.map((p, i) => (
              <li key={i}>
                {p.position ? `Question ${p.position}: ` : ''}
                {p.message}
              </li>
            ))}
          </ul>
        </Alert>
      )}

      <Card className="mt-4">
        <CardHeader
          title="Questions"
          description="MCQ with a single correct answer. The answer key is never sent to students before results are released."
          actions={
            quiz.editable && (
              <>
                <Button variant="secondary" size="sm" onClick={() => setImportOpen(true)}>
                  <FileUp /> Import
                </Button>
                <Button variant="secondary" size="sm" asChild>
                  <Link to="/admin/question-banks">From bank</Link>
                </Button>
                <Button size="sm" onClick={() => setEditing('new')}>
                  <Plus /> Add question
                </Button>
              </>
            )
          }
        />
        {quiz.questions.length === 0 ? (
          <EmptyState
            title="No questions yet"
            description="Add questions manually, import them from the spreadsheet template, or copy them from a question bank."
            action={quiz.editable && <Button onClick={() => setEditing('new')}>Add a question</Button>}
          />
        ) : (
          <QuestionList
            questions={quiz.questions}
            editable={quiz.editable}
            onEdit={(q) => setEditing(q)}
            onDelete={(q) => setDeleting(q)}
            onMove={move}
            highlight={highlight}
          />
        )}
      </Card>

      <div className="mt-6 flex flex-wrap justify-between gap-2">
        <div className="flex gap-2">
          {quiz.status !== 'ARCHIVED' && (
            <Button variant="ghost" onClick={() => act('archive', () => api.post<QuizDetail>(`/api/admin/quizzes/${quiz.id}/archive`), 'Quiz archived')}>
              <Archive /> Archive
            </Button>
          )}
          {!quiz.hasAssignments && (
            <Button variant="ghost" className="text-red-600" onClick={() => setDeleteQuiz(true)}>
              <Trash2 /> Delete quiz
            </Button>
          )}
        </div>
        {quiz.hasAssignments && (
          <Button
            variant="secondary"
            onClick={() =>
              download(`/api/admin/quizzes/${quiz.id}/export/marksheet?tz=${encodeURIComponent(browserTimeZone())}`, 'marksheet.xlsx').catch((e) =>
                setActionError(errorMessage(e)),
              )
            }
          >
            <FileSpreadsheet /> Quiz marksheet (all assignments)
          </Button>
        )}
      </div>

      <QuestionEditorDialog
        open={editing !== null}
        onOpenChange={(o) => !o && setEditing(null)}
        question={editing === 'new' ? null : editing}
        createPath={`/api/admin/quizzes/${quiz.id}/questions`}
        onSaved={reload}
      />
      <QuestionImportDialog open={importOpen} onOpenChange={setImportOpen} endpoint={`/api/admin/quizzes/${quiz.id}/questions/import`} onDone={reload} />
      <QuizSettingsDialog key={quiz.updatedAt} open={settingsOpen} onOpenChange={setSettingsOpen} quiz={quiz} onSaved={setData} />
      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(o) => !o && setDeleting(null)}
        title="Delete question?"
        description={deleting?.text}
        confirmLabel="Delete"
        destructive
        onConfirm={async () => {
          await api.del(`/api/admin/questions/${deleting?.id}`)
          reload()
        }}
      />
      <ConfirmDialog
        open={deleteQuiz}
        onOpenChange={setDeleteQuiz}
        title="Delete quiz?"
        description={`"${quiz.title}" and its questions will be permanently deleted.`}
        confirmLabel="Delete quiz"
        destructive
        onConfirm={async () => {
          await api.del(`/api/admin/quizzes/${quiz.id}`)
          navigate('/admin/quizzes')
        }}
      />
      <Dialog open={previewOpen} onOpenChange={setPreviewOpen} title={`Preview: ${quiz.title}`} description="What students see (without the answer key)." size="lg">
        <div className="space-y-5">
          {quiz.instructions && <p className="whitespace-pre-line rounded-lg bg-slate-50 p-3 text-sm text-slate-700">{quiz.instructions}</p>}
          {quiz.questions.map((q, i) => (
            <div key={q.id}>
              <p className="font-medium text-slate-900">
                {i + 1}. {q.text} <span className="text-xs font-normal text-slate-500">({formatMarks(q.points)} mk)</span>
              </p>
              <div className="mt-2 space-y-1.5">
                {q.options.map((o, j) => (
                  <label key={o.id} className="flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-sm">
                    <input type="radio" name={`p-${q.id}`} className="accent-primary-600" />
                    <span className="font-semibold text-slate-500">{String.fromCharCode(65 + j)}.</span> {o.text}
                  </label>
                ))}
              </div>
            </div>
          ))}
        </div>
      </Dialog>
    </>
  )
}
