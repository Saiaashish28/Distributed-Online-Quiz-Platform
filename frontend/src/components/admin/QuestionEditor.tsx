import { Plus, Trash2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { ErrorAlert } from '@/components/ui/feedback'
import { Field, Input, Textarea } from '@/components/ui/form'
import { api, errorMessage } from '@/lib/api'
import type { QuestionAdmin } from '@/types/api'

interface Draft {
  text: string
  points: string
  explanation: string
  options: { text: string; correct: boolean }[]
}

const empty = (): Draft => ({
  text: '',
  points: '1',
  explanation: '',
  options: [
    { text: '', correct: true },
    { text: '', correct: false },
    { text: '', correct: false },
    { text: '', correct: false },
  ],
})

/** Create (POST to createPath) or edit (PATCH /api/admin/questions/:id) an MCQ question. */
export function QuestionEditorDialog({
  open,
  onOpenChange,
  question,
  createPath,
  onSaved,
}: {
  open: boolean
  onOpenChange: (o: boolean) => void
  question?: QuestionAdmin | null
  createPath: string
  onSaved: () => void
}) {
  const [draft, setDraft] = useState<Draft>(empty)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  useEffect(() => {
    if (!open) return
    setError(null)
    setDraft(
      question
        ? {
            text: question.text,
            points: String(question.points),
            explanation: question.explanation ?? '',
            options: question.options.map((o) => ({ text: o.text, correct: o.correct })),
          }
        : empty(),
    )
  }, [open, question])

  const setOption = (i: number, patch: Partial<Draft['options'][number]>) =>
    setDraft((d) => ({
      ...d,
      options: d.options.map((o, j) => (j === i ? { ...o, ...patch } : patch.correct ? { ...o, correct: false } : o)),
    }))

  const save = async () => {
    const options = draft.options.filter((o) => o.text.trim())
    if (options.length < 2) return setError('Add at least two options')
    if (options.filter((o) => o.correct).length !== 1) return setError('Mark exactly one non-empty option as correct')
    setPending(true)
    setError(null)
    const body = { text: draft.text, points: Number(draft.points), explanation: draft.explanation || undefined, options }
    try {
      if (question) await api.patch(`/api/admin/questions/${question.id}`, body)
      else await api.post(createPath, body)
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
      title={question ? 'Edit question' : 'Add question'}
      description="Multiple choice with exactly one correct answer."
      size="lg"
      footer={
        <>
          <Button variant="secondary" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={save} loading={pending} disabled={!draft.text.trim()}>
            Save question
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <Field label="Question" required>
          {(id) => <Textarea id={id} rows={3} value={draft.text} onChange={(e) => setDraft({ ...draft, text: e.target.value })} maxLength={4000} />}
        </Field>
        <div>
          <p className="mb-1 text-sm font-medium text-slate-700">Options — select the correct one</p>
          <div className="space-y-2">
            {draft.options.map((o, i) => (
              <div key={i} className="flex items-center gap-2">
                <input
                  type="radio"
                  name="correct"
                  aria-label={`Option ${String.fromCharCode(65 + i)} is correct`}
                  className="size-4 accent-emerald-600"
                  checked={o.correct}
                  onChange={() => setOption(i, { correct: true })}
                />
                <span className="w-5 text-sm font-semibold text-slate-500">{String.fromCharCode(65 + i)}.</span>
                <Input value={o.text} onChange={(e) => setOption(i, { text: e.target.value })} placeholder={`Option ${String.fromCharCode(65 + i)}`} maxLength={1000} />
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label="Remove option"
                  disabled={draft.options.length <= 2}
                  onClick={() => setDraft((d) => ({ ...d, options: d.options.filter((_, j) => j !== i) }))}
                >
                  <Trash2 />
                </Button>
              </div>
            ))}
          </div>
          {draft.options.length < 6 && (
            <Button variant="ghost" size="sm" className="mt-2" onClick={() => setDraft((d) => ({ ...d, options: [...d.options, { text: '', correct: false }] }))}>
              <Plus /> Add option
            </Button>
          )}
        </div>
        <div className="grid gap-4 sm:grid-cols-[140px_1fr]">
          <Field label="Marks" required>
            {(id) => <Input id={id} type="number" min={0.01} max={100} step={0.5} value={draft.points} onChange={(e) => setDraft({ ...draft, points: e.target.value })} />}
          </Field>
          <Field label="Explanation" hint="Shown to students after results are released">
            {(id) => <Input id={id} value={draft.explanation} onChange={(e) => setDraft({ ...draft, explanation: e.target.value })} maxLength={4000} />}
          </Field>
        </div>
        <ErrorAlert error={error} />
      </div>
    </Dialog>
  )
}
