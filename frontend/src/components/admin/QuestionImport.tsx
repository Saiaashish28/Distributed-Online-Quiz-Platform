import { StatusBadge } from '@/components/ui/badge'
import { useToast } from '@/components/ui/toast'
import { Table, THead, Td, Th, Tr } from '@/components/ui/table'
import { formatMarks } from '@/lib/utils'
import type { QuestionImportResult } from '@/types/api'
import { ImportDialog } from './ImportDialog'

export function QuestionImportDialog({
  open,
  onOpenChange,
  endpoint,
  onDone,
}: {
  open: boolean
  onOpenChange: (o: boolean) => void
  endpoint: string
  onDone: () => void
}) {
  const toast = useToast()
  return (
    <ImportDialog<QuestionImportResult>
      open={open}
      onOpenChange={onOpenChange}
      title="Import questions"
      endpoint={endpoint}
      templatePath="/api/admin/templates/questions"
      templateName="QuizSphere_Question_Template"
      summarize={(r) => ({ valid: r.validRows, invalid: r.invalidRows, label: 'questions' })}
      onCommitted={(r) => {
        toast(`Imported ${r.imported} question(s)`)
        onDone()
      }}
      renderPreview={(r) => (
        <Table>
          <THead>
            <tr>
              <Th>Row</Th>
              <Th>Status</Th>
              <Th>Question</Th>
              <Th>Options</Th>
              <Th>Answer</Th>
              <Th>Marks</Th>
              <Th>Problems</Th>
            </tr>
          </THead>
          <tbody>
            {r.rows.map((row) => (
              <Tr key={row.rowNumber} className={row.errors.length ? 'bg-red-50/50' : undefined}>
                <Td className="tabular-nums text-slate-500">{row.rowNumber}</Td>
                <Td>
                  <StatusBadge status={row.errors.length ? 'INVALID' : 'CREATE'} />
                </Td>
                <Td className="max-w-xs">{row.question ?? <span className="text-slate-400">—</span>}</Td>
                <Td className="max-w-xs text-xs text-slate-600">
                  {row.options.map((o, i) => (
                    <div key={i}>
                      {String.fromCharCode(65 + i)}. {o}
                    </div>
                  ))}
                </Td>
                <Td className="font-medium">{row.correctAnswer ?? '—'}</Td>
                <Td className="tabular-nums">{formatMarks(row.marks)}</Td>
                <Td className="text-xs">
                  {row.errors.map((e) => (
                    <p key={e} className="text-red-700">{e}</p>
                  ))}
                  {row.warnings.map((w) => (
                    <p key={w} className="text-amber-700">{w}</p>
                  ))}
                </Td>
              </Tr>
            ))}
          </tbody>
        </Table>
      )}
    />
  )
}
