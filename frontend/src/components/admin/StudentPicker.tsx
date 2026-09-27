import { Search, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Input } from '@/components/ui/form'
import { api } from '@/lib/api'
import type { Page, Student, StudentRef } from '@/types/api'

/** Search students by register number / name and build a selection. */
export function StudentPicker({ value, onChange }: { value: StudentRef[]; onChange: (v: StudentRef[]) => void }) {
  const [q, setQ] = useState('')
  const [results, setResults] = useState<Student[]>([])
  const [bulk, setBulk] = useState('')

  useEffect(() => {
    if (q.trim().length < 2) {
      setResults([])
      return
    }
    const t = setTimeout(() => {
      api
        .get<Page<Student>>(`/api/admin/students?active=true&size=10&q=${encodeURIComponent(q.trim())}`)
        .then((p) => setResults(p.content))
        .catch(() => setResults([]))
    }, 250)
    return () => clearTimeout(t)
  }, [q])

  const add = (s: StudentRef) => {
    if (!value.some((v) => v.id === s.id)) onChange([...value, { id: s.id, registerNumber: s.registerNumber, fullName: s.fullName }])
  }

  const addBulk = async () => {
    const regs = bulk.split(/[\s,;]+/).map((r) => r.trim().toUpperCase()).filter(Boolean)
    const found: StudentRef[] = []
    for (const r of regs) {
      const page = await api.get<Page<Student>>(`/api/admin/students?size=5&q=${encodeURIComponent(r)}`).catch(() => null)
      const s = page?.content.find((x) => x.registerNumber === r)
      if (s) found.push({ id: s.id, registerNumber: s.registerNumber, fullName: s.fullName })
    }
    const merged = [...value]
    for (const f of found) if (!merged.some((m) => m.id === f.id)) merged.push(f)
    onChange(merged)
    setBulk(regs.filter((r) => !found.some((f) => f.registerNumber === r)).join(', '))
  }

  return (
    <div className="space-y-2">
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-2.5 size-4 text-slate-400" />
        <Input className="pl-9" placeholder="Search by register number or name" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search students" />
        {results.length > 0 && (
          <ul className="absolute z-10 mt-1 max-h-60 w-full overflow-auto rounded-lg border border-slate-200 bg-white shadow-lg">
            {results.map((s) => (
              <li key={s.id}>
                <button
                  type="button"
                  className="flex w-full items-center justify-between px-3 py-2 text-left text-sm hover:bg-slate-50"
                  onClick={() => {
                    add(s)
                    setQ('')
                  }}
                >
                  <span>
                    <span className="font-mono">{s.registerNumber}</span> · {s.fullName}
                  </span>
                  <span className="text-xs text-slate-500">
                    {s.department} {s.section}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
      <div className="flex gap-2">
        <Input placeholder="Or paste register numbers (comma/space separated)" value={bulk} onChange={(e) => setBulk(e.target.value)} aria-label="Paste register numbers" />
        <button type="button" className="shrink-0 rounded-lg border border-slate-300 px-3 text-sm hover:bg-slate-50" onClick={addBulk} disabled={!bulk.trim()}>
          Add
        </button>
      </div>
      {bulk && <p className="text-xs text-slate-500">Unmatched register numbers stay in the box above.</p>}
      {value.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {value.map((s) => (
            <span key={s.id} className="inline-flex items-center gap-1 rounded-full bg-primary-50 px-2.5 py-1 text-xs text-primary-800">
              <span className="font-mono">{s.registerNumber}</span> {s.fullName}
              <button type="button" aria-label={`Remove ${s.registerNumber}`} onClick={() => onChange(value.filter((v) => v.id !== s.id))}>
                <X className="size-3" />
              </button>
            </span>
          ))}
        </div>
      )}
    </div>
  )
}
