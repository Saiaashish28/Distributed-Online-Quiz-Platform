import { Download, FileUp } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Alert, ErrorAlert } from '@/components/ui/feedback'
import { Checkbox } from '@/components/ui/form'
import { ApiError, api, download, errorMessage } from '@/lib/api'

/**
 * Two-step spreadsheet import: upload -> server preview (nothing saved) -> explicit confirm.
 * The same file is re-sent with mode=commit, so the server re-validates it.
 */
export function ImportDialog<R extends { headerErrors: string[] }>({
  open,
  onOpenChange,
  title,
  endpoint,
  templatePath,
  templateName,
  extraOptions,
  extraParams = {},
  renderPreview,
  summarize,
  onCommitted,
}: {
  open: boolean
  onOpenChange: (o: boolean) => void
  title: string
  endpoint: string
  templatePath: string
  templateName: string
  extraOptions?: ReactNode
  extraParams?: Record<string, boolean>
  renderPreview: (r: R) => ReactNode
  summarize: (r: R) => { valid: number; invalid: number; label: string }
  onCommitted: (r: R) => void
}) {
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<R | null>(null)
  const [skipInvalid, setSkipInvalid] = useState(false)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const query = (mode: 'preview' | 'commit') => {
    const p = new URLSearchParams({ mode, skipInvalid: String(skipInvalid) })
    for (const [k, v] of Object.entries(extraParams)) p.set(k, String(v))
    return `${endpoint}?${p}`
  }

  const reset = () => {
    setFile(null)
    setPreview(null)
    setSkipInvalid(false)
    setError(null)
  }

  const runPreview = async (f: File) => {
    setPending(true)
    setError(null)
    try {
      setPreview(await api.upload<R>(query('preview'), f))
    } catch (e) {
      setPreview(null)
      setError(errorMessage(e))
    } finally {
      setPending(false)
    }
  }

  const commit = async () => {
    if (!file) return
    setPending(true)
    setError(null)
    try {
      const r = await api.upload<R>(query('commit'), file)
      onCommitted(r)
      reset()
      onOpenChange(false)
    } catch (e) {
      if (e instanceof ApiError && e.details && typeof e.details === 'object' && 'rows' in (e.details as object)) {
        setPreview(e.details as R)
      }
      setError(errorMessage(e))
    } finally {
      setPending(false)
    }
  }

  // Options such as "update existing" change the preview, so re-validate when they change.
  const paramsKey = JSON.stringify(extraParams)
  useEffect(() => {
    if (file) void runPreview(file)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [paramsKey])

  const s = preview ? summarize(preview) : null
  const blocked = !preview || preview.headerErrors.length > 0 || !s || s.valid === 0 || (s.invalid > 0 && !skipInvalid)

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => {
        if (!o) reset()
        onOpenChange(o)
      }}
      title={title}
      description="Upload a .xlsx or .csv file. Nothing is saved until you confirm."
      size="xl"
      footer={
        <>
          <Button variant="secondary" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={commit} disabled={blocked} loading={pending && !!preview}>
            {s ? `Confirm import (${s.valid} ${s.label})` : 'Confirm import'}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <div className="flex flex-wrap items-center gap-3">
          <label className="inline-flex cursor-pointer items-center gap-2 rounded-lg border border-dashed border-slate-300 bg-slate-50 px-4 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-100">
            <FileUp className="size-4" />
            {file ? file.name : 'Choose file…'}
            <input
              type="file"
              accept=".xlsx,.csv"
              className="sr-only"
              onChange={(e) => {
                const f = e.target.files?.[0]
                e.target.value = ''
                if (f) {
                  setFile(f)
                  void runPreview(f)
                }
              }}
            />
          </label>
          <Button variant="ghost" size="sm" onClick={() => download(`${templatePath}?format=xlsx`, `${templateName}.xlsx`).catch((e) => setError(errorMessage(e)))}>
            <Download /> Template (.xlsx)
          </Button>
          <Button variant="ghost" size="sm" onClick={() => download(`${templatePath}?format=csv`, `${templateName}.csv`).catch((e) => setError(errorMessage(e)))}>
            <Download /> Template (.csv)
          </Button>
        </div>
        {extraOptions}
        <ErrorAlert error={error} />
        {pending && !preview && <p className="text-sm text-slate-500">Validating file…</p>}
        {preview && preview.headerErrors.length > 0 && (
          <Alert tone="error" title="The file's columns don't match the template">
            <ul className="list-disc pl-4">
              {preview.headerErrors.map((h) => (
                <li key={h}>{h}</li>
              ))}
            </ul>
          </Alert>
        )}
        {preview && s && preview.headerErrors.length === 0 && (
          <>
            <div className="flex flex-wrap items-center gap-3 text-sm">
              <span className="font-medium text-emerald-700">{s.valid} ready</span>
              <span className={s.invalid ? 'font-medium text-red-700' : 'text-slate-500'}>{s.invalid} invalid</span>
              {s.invalid > 0 && (
                <div className="ml-auto">
                  <Checkbox
                    checked={skipInvalid}
                    onChange={setSkipInvalid}
                    label={`Skip the ${s.invalid} invalid row(s) and import the rest`}
                  />
                </div>
              )}
            </div>
            <div className="max-h-[50vh] overflow-auto rounded-lg border border-slate-200">{renderPreview(preview)}</div>
          </>
        )}
      </div>
    </Dialog>
  )
}
