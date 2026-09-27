import { ArrowDown, ArrowUp, Check, Pencil, Trash2 } from 'lucide-react'
import type { ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { cn, formatMarks } from '@/lib/utils'
import type { QuestionAdmin } from '@/types/api'

/** Renders questions with the correct option highlighted (admin view). */
export function QuestionList({
  questions,
  editable,
  onEdit,
  onDelete,
  onMove,
  selectable,
  selected,
  onToggle,
  highlight,
}: {
  questions: QuestionAdmin[]
  editable?: boolean
  onEdit?: (q: QuestionAdmin) => void
  onDelete?: (q: QuestionAdmin) => void
  onMove?: (index: number, dir: -1 | 1) => void
  selectable?: boolean
  selected?: Set<number>
  onToggle?: (id: number) => void
  highlight?: Set<number>
}) {
  return (
    <ol className="divide-y divide-slate-100">
      {questions.map((q, i) => (
        <li key={q.id} className={cn('flex gap-3 px-5 py-4', highlight?.has(q.id) && 'bg-red-50/60')}>
          {selectable && (
            <input
              type="checkbox"
              aria-label={`Select question ${i + 1}`}
              className="mt-1 size-4 accent-primary-600"
              checked={selected?.has(q.id) ?? false}
              onChange={() => onToggle?.(q.id)}
            />
          )}
          <div className="min-w-0 flex-1">
            <div className="flex items-start justify-between gap-3">
              <p className="font-medium text-slate-900">
                <span className="mr-2 text-slate-400">{i + 1}.</span>
                <span className="whitespace-pre-line">{q.text}</span>
              </p>
              <span className="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium tabular-nums text-slate-600">
                {formatMarks(q.points)} mk
              </span>
            </div>
            <ul className="mt-2 grid gap-1 text-sm sm:grid-cols-2">
              {q.options.map((o, j) => (
                <li key={o.id} className={cn('flex items-center gap-1.5 rounded px-2 py-1', o.correct ? 'bg-emerald-50 text-emerald-800' : 'text-slate-600')}>
                  <span className="font-semibold">{String.fromCharCode(65 + j)}.</span> {o.text}
                  {o.correct && <Check className="ml-auto size-3.5" aria-label="Correct" />}
                </li>
              ))}
            </ul>
            {q.explanation && <p className="mt-2 text-xs text-slate-500">Explanation: {q.explanation}</p>}
          </div>
          {editable && (
            <div className="flex shrink-0 flex-col gap-1 sm:flex-row sm:items-start">
              {onMove && (
                <>
                  <IconBtn label="Move up" disabled={i === 0} onClick={() => onMove(i, -1)}>
                    <ArrowUp />
                  </IconBtn>
                  <IconBtn label="Move down" disabled={i === questions.length - 1} onClick={() => onMove(i, 1)}>
                    <ArrowDown />
                  </IconBtn>
                </>
              )}
              <IconBtn label="Edit" onClick={() => onEdit?.(q)}>
                <Pencil />
              </IconBtn>
              <IconBtn label="Delete" onClick={() => onDelete?.(q)}>
                <Trash2 className="text-red-600" />
              </IconBtn>
            </div>
          )}
        </li>
      ))}
    </ol>
  )
}

function IconBtn({ label, onClick, disabled, children }: { label: string; onClick: () => void; disabled?: boolean; children: ReactNode }) {
  return (
    <Button variant="ghost" size="icon" aria-label={label} title={label} onClick={onClick} disabled={disabled}>
      {children}
    </Button>
  )
}
