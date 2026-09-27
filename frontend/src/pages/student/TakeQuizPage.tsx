import { AlertTriangle, ChevronLeft, ChevronRight, CloudOff, Flag, Loader2, Maximize, ShieldCheck, Timer, Wifi, WifiOff } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Logo } from '@/components/Logo'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { Alert, ErrorAlert, LoadingBlock } from '@/components/ui/feedback'
import { useQuizSocket, type SocketMessage } from '@/hooks/useQuizSocket'
import { ApiError, api, errorMessage } from '@/lib/api'
import { cn, formatCountdown, formatMarks } from '@/lib/utils'
import type { AttemptView, ProctoringEventResponse, ProctoringEventType, SaveAnswersResponse, SubmitResponse } from '@/types/api'

type Selection = { optionId: number | null; seq: number }
type SaveState = 'saved' | 'saving' | 'offline'

const pendingKey = (attemptId: number) => `quizsphere.pending.${attemptId}`

function readLocalPending(attemptId: number): Record<number, Selection> {
  try {
    return JSON.parse(localStorage.getItem(pendingKey(attemptId)) ?? '{}')
  } catch {
    return {}
  }
}

function writeLocalPending(attemptId: number, pending: Map<number, Selection>) {
  try {
    if (pending.size === 0) localStorage.removeItem(pendingKey(attemptId))
    else localStorage.setItem(pendingKey(attemptId), JSON.stringify(Object.fromEntries(pending)))
  } catch {
    /* storage unavailable: pending answers live in memory only */
  }
}

export default function TakeQuizPage() {
  const { id } = useParams()
  const attemptId = Number(id)
  const navigate = useNavigate()

  const [view, setView] = useState<AttemptView | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [answers, setAnswers] = useState<Record<number, Selection>>({})
  const [saveState, setSaveState] = useState<SaveState>('saved')
  const [current, setCurrent] = useState(0)
  const [flagged, setFlagged] = useState<Set<number>>(new Set())
  const [offset, setOffset] = useState(0) // server clock minus client clock
  const [now, setNow] = useState(() => Date.now())
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [warning, setWarning] = useState<string | null>(null)
  const [warningCount, setWarningCount] = useState(0)
  const [needFullscreen, setNeedFullscreen] = useState(false)

  const seqRef = useRef(0)
  const pendingRef = useRef(new Map<number, Selection>())
  const inflightRef = useRef(false)
  const flushTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const retryDelay = useRef(1000)
  const finishedRef = useRef(false)

  const goSubmitted = useCallback(
    (reason?: 'proctoring') => {
      finishedRef.current = true
      clearTimeout(flushTimer.current)
      if (document.fullscreenElement) document.exitFullscreen().catch(() => undefined)
      navigate(`/student/attempts/${attemptId}/submitted`, { replace: true, state: reason ? { reason } : undefined })
    },
    [attemptId, navigate],
  )

  // ------------------------------------------------------------------ load / resume
  useEffect(() => {
    let cancelled = false
    api
      .get<AttemptView>(`/api/student/attempts/${attemptId}`)
      .then((v) => {
        if (cancelled) return
        if (v.status !== 'IN_PROGRESS') {
          goSubmitted()
          return
        }
        setOffset(new Date(v.serverTime).getTime() - Date.now())
        const map: Record<number, Selection> = {}
        let maxSeq = 0
        for (const a of v.answers) {
          map[a.questionId] = { optionId: a.optionId ?? null, seq: a.seq }
          maxSeq = Math.max(maxSeq, a.seq)
        }
        // Answers chosen before a refresh/crash that never reached the server are re-applied.
        const local = readLocalPending(attemptId)
        const validQuestions = new Set(v.questions.map((q) => q.id))
        for (const [qid, sel] of Object.entries(local)) {
          const q = Number(qid)
          if (validQuestions.has(q) && sel.seq > (map[q]?.seq ?? 0)) {
            map[q] = sel
            pendingRef.current.set(q, sel)
            maxSeq = Math.max(maxSeq, sel.seq)
          }
        }
        seqRef.current = maxSeq
        setAnswers(map)
        setWarningCount(v.warningCount)
        setView(v)
        if (v.proctoring.requireFullscreen && document.fullscreenEnabled && !document.fullscreenElement) setNeedFullscreen(true)
        if (pendingRef.current.size > 0) scheduleFlush(0)
      })
      .catch((e) => !cancelled && setLoadError(errorMessage(e)))
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [attemptId])

  // ------------------------------------------------------------------------ autosave
  const flush = useCallback(async () => {
    if (inflightRef.current || finishedRef.current) return
    const items = [...pendingRef.current.entries()]
    if (items.length === 0) {
      setSaveState('saved')
      return
    }
    inflightRef.current = true
    setSaveState('saving')
    try {
      const res = await api.put<SaveAnswersResponse>(`/api/student/attempts/${attemptId}/answers`, {
        answers: items.map(([questionId, s]) => ({ questionId, optionId: s.optionId, seq: s.seq })),
      })
      for (const [qid, sent] of items) {
        const pending = pendingRef.current.get(qid)
        if (pending && pending.seq <= sent.seq) pendingRef.current.delete(qid)
      }
      writeLocalPending(attemptId, pendingRef.current)
      setOffset(new Date(res.serverTime).getTime() - Date.now())
      retryDelay.current = 1000
      setSaveState(pendingRef.current.size ? 'saving' : 'saved')
      if (pendingRef.current.size) scheduleFlush(200)
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        goSubmitted() // time is up or already submitted: the server finalizes with saved answers
        return
      }
      if (e instanceof ApiError && e.status === 400) {
        for (const [qid] of items) pendingRef.current.delete(qid)
        writeLocalPending(attemptId, pendingRef.current)
        setSaveState('saved')
        return
      }
      setSaveState('offline')
      scheduleFlush(retryDelay.current)
      retryDelay.current = Math.min(retryDelay.current * 2, 15000)
    } finally {
      inflightRef.current = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [attemptId, goSubmitted])

  function scheduleFlush(delay: number) {
    clearTimeout(flushTimer.current)
    flushTimer.current = setTimeout(() => void flush(), delay)
  }

  useEffect(() => {
    const online = () => scheduleFlush(0)
    window.addEventListener('online', online)
    return () => {
      window.removeEventListener('online', online)
      clearTimeout(flushTimer.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [flush])

  const select = (questionId: number, optionId: number | null) => {
    if (finishedRef.current) return
    // Monotonic sequence number: the server ignores any write older than one it already has.
    const seq = Math.max(Date.now(), seqRef.current + 1)
    seqRef.current = seq
    const sel = { optionId, seq }
    setAnswers((a) => ({ ...a, [questionId]: sel }))
    pendingRef.current.set(questionId, sel)
    writeLocalPending(attemptId, pendingRef.current)
    setSaveState('saving')
    scheduleFlush(250)
  }

  // ---------------------------------------------------------------------- submission
  const submit = useCallback(async () => {
    if (finishedRef.current && submitting) return
    setSubmitting(true)
    setSubmitError(null)
    clearTimeout(flushTimer.current)
    try {
      // Best effort: push unsaved answers first. The server also finalizes from what it has.
      for (let i = 0; i < 3 && pendingRef.current.size > 0; i++) {
        while (inflightRef.current) await new Promise((r) => setTimeout(r, 100))
        await flush()
      }
      finishedRef.current = true
      await api.post<SubmitResponse>(`/api/student/attempts/${attemptId}/submit`)
      writeLocalPending(attemptId, new Map())
      goSubmitted()
    } catch (e) {
      finishedRef.current = false
      setSubmitError(errorMessage(e))
      setSubmitting(false)
    }
  }, [attemptId, flush, goSubmitted, submitting])

  // ---------------------------------------------------------------------------- timer
  const endsAt = view ? new Date(view.endsAt).getTime() : 0
  const remaining = view ? endsAt - (now + offset) : 0
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 500)
    return () => clearInterval(t)
  }, [])
  useEffect(() => {
    if (view && remaining <= 0 && !finishedRef.current && !submitting) void submit()
  }, [view, remaining, submit, submitting])

  // ------------------------------------------------------------------------ realtime
  const onMessage = useCallback(
    (msg: SocketMessage) => {
      const d = msg.data as { attemptId?: number; attempt?: { id: number }; reason?: string }
      if (msg.type === 'attempt_finalized' && d.attemptId === attemptId) goSubmitted(d.reason === 'PROCTORING' ? 'proctoring' : undefined)
      if (msg.type === 'session_ended') goSubmitted()
      if (msg.type === 'session_snapshot' && view && !d.attempt) {
        // Reconnected and the server has no running attempt for us any more.
        api.get<AttemptView>(`/api/student/attempts/${attemptId}`).then((v) => v.status !== 'IN_PROGRESS' && goSubmitted()).catch(() => undefined)
      }
    },
    [attemptId, goSubmitted, view],
  )
  const socket = useQuizSocket(view?.assignmentId ?? null, onMessage)

  // ---------------------------------------------------------------------- proctoring
  const proctoring = view?.proctoring
  useEffect(() => {
    if (!proctoring?.enabled) return
    let awaySince: number | null = null
    const failed: { eventType: ProctoringEventType; metadata?: Record<string, unknown>; clientTimestamp: string }[] = []

    const send = async (eventType: ProctoringEventType, metadata?: Record<string, unknown>) => {
      if (finishedRef.current) return
      const body = { eventType, metadata, clientTimestamp: new Date().toISOString() }
      try {
        const res = await api.post<ProctoringEventResponse>(`/api/student/attempts/${attemptId}/proctoring-events`, body)
        setWarningCount(res.warningCount)
        if (res.autoSubmitted) {
          goSubmitted('proctoring')
          return
        }
        if (res.showWarning && res.message) setWarning(res.message)
        while (failed.length) await api.post(`/api/student/attempts/${attemptId}/proctoring-events`, failed.shift())
      } catch (e) {
        if (!(e instanceof ApiError) || e.status === 0) failed.push(body)
      }
    }
    const lost = () => {
      if (awaySince !== null) return
      awaySince = Date.now()
      void send('FOCUS_LOST', { visibilityState: document.visibilityState })
    }
    const back = () => {
      if (awaySince === null) return
      const awayMs = Date.now() - awaySince
      awaySince = null
      void send('FOCUS_RETURNED', { awayMs })
    }
    const onVisibility = () => (document.hidden ? lost() : back())
    const onFullscreen = () => {
      if (!proctoring.requireFullscreen) return
      if (document.fullscreenElement) {
        setNeedFullscreen(false)
        void send('FULLSCREEN_ENTER')
      } else if (!finishedRef.current) {
        setNeedFullscreen(true)
        void send('FULLSCREEN_EXIT', { fullscreenSupported: document.fullscreenEnabled })
      }
    }
    document.addEventListener('visibilitychange', onVisibility)
    window.addEventListener('blur', lost)
    window.addEventListener('focus', back)
    document.addEventListener('fullscreenchange', onFullscreen)
    return () => {
      document.removeEventListener('visibilitychange', onVisibility)
      window.removeEventListener('blur', lost)
      window.removeEventListener('focus', back)
      document.removeEventListener('fullscreenchange', onFullscreen)
    }
  }, [attemptId, proctoring, goSubmitted])

  // ---------------------------------------------------------------------------- render
  if (loadError)
    return (
      <div className="mx-auto max-w-lg p-6">
        <ErrorAlert error={loadError} />
        <Button asChild variant="secondary" className="mt-4">
          <Link to="/student">Back to dashboard</Link>
        </Button>
      </div>
    )
  if (!view) return <LoadingBlock label="Loading your quiz…" />

  const q = view.questions[current]
  const answeredCount = view.questions.filter((x) => answers[x.id]?.optionId != null).length
  const unanswered = view.questions.length - answeredCount
  const lowTime = remaining < 60_000

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-4 gap-y-2 px-4 py-2.5 sm:px-6">
          <div className="hidden sm:block">
            <Logo to="#" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate font-semibold text-slate-900">{view.quizTitle}</p>
            <p className="truncate text-xs text-slate-500">
              {view.student.fullName} · {view.student.registerNumber} · Attempt {view.attemptNumber}
            </p>
          </div>
          <SaveIndicator state={saveState} />
          <span className="hidden items-center gap-1 text-xs text-slate-500 sm:flex" title="Live connection">
            {socket === 'open' ? <Wifi className="size-3.5 text-emerald-600" /> : <WifiOff className="size-3.5 text-amber-600" />}
            {socket === 'open' ? 'Connected' : 'Reconnecting'}
          </span>
          <div
            className={cn(
              'flex items-center gap-1.5 rounded-lg px-3 py-1.5 font-mono text-lg font-semibold tabular-nums',
              lowTime ? 'bg-red-50 text-red-700' : 'bg-slate-100 text-slate-800',
            )}
            role="timer"
            aria-live={lowTime ? 'assertive' : 'off'}
            aria-label="Time remaining"
          >
            <Timer className="size-4" /> {formatCountdown(remaining)}
          </div>
        </div>
      </header>

      <main className="mx-auto grid max-w-6xl gap-6 px-4 py-6 sm:px-6 lg:grid-cols-[1fr_280px]">
        <div className="space-y-4">
          {view.proctoring.enabled && (
            <div className="flex items-center gap-2 rounded-lg border border-sky-200 bg-sky-50 px-3 py-2 text-xs text-sky-900">
              <ShieldCheck className="size-4 shrink-0" />
              Browser monitoring is on: leaving this page is recorded. After {view.proctoring.autoSubmitWarnings} times your quiz is submitted automatically.
              {warningCount > 0 && (
                <span className="ml-auto whitespace-nowrap font-medium">
                  Recorded: {warningCount} of {view.proctoring.autoSubmitWarnings}
                </span>
              )}
            </div>
          )}
          {warning && (
            <Alert tone="warning" title="Please stay on the quiz page">
              {warning}
              <button type="button" className="ml-2 font-medium underline" onClick={() => setWarning(null)}>
                Dismiss
              </button>
            </Alert>
          )}
          {needFullscreen && (
            <Alert tone="warning" title="Fullscreen is required for this quiz">
              <Button size="sm" className="mt-2" onClick={() => document.documentElement.requestFullscreen().catch(() => undefined)}>
                <Maximize /> Return to fullscreen
              </Button>
            </Alert>
          )}
          {saveState === 'offline' && (
            <Alert tone="warning" title="You're offline">
              Your latest answers are kept on this device and will be saved automatically when the connection returns.
            </Alert>
          )}

          <Card className="p-5 sm:p-6">
            <div className="mb-4 flex items-center justify-between gap-2">
              <p className="text-sm font-medium text-slate-500">
                Question {q.number} of {view.questions.length}
              </p>
              <div className="flex items-center gap-2">
                <span className="text-xs text-slate-500">{formatMarks(q.points)} mark{q.points === 1 ? '' : 's'}</span>
                <Button
                  variant={flagged.has(q.id) ? 'secondary' : 'ghost'}
                  size="sm"
                  aria-pressed={flagged.has(q.id)}
                  onClick={() =>
                    setFlagged((f) => {
                      const n = new Set(f)
                      if (n.has(q.id)) n.delete(q.id)
                      else n.add(q.id)
                      return n
                    })
                  }
                >
                  <Flag className={flagged.has(q.id) ? 'fill-amber-400 text-amber-500' : ''} /> {flagged.has(q.id) ? 'Flagged' : 'Flag'}
                </Button>
              </div>
            </div>
            <fieldset>
              <legend className="whitespace-pre-line text-lg font-medium leading-relaxed text-slate-900">{q.text}</legend>
              <div className="mt-5 space-y-2.5">
                {q.options.map((o, i) => {
                  const checked = answers[q.id]?.optionId === o.id
                  return (
                    <label
                      key={o.id}
                      className={cn(
                        'flex cursor-pointer items-start gap-3 rounded-lg border px-4 py-3 transition-colors',
                        checked ? 'border-primary-500 bg-primary-50 ring-1 ring-primary-500' : 'border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50',
                      )}
                    >
                      <input
                        type="radio"
                        name={`q-${q.id}`}
                        className="mt-1 size-4 accent-primary-600"
                        checked={checked}
                        onChange={() => select(q.id, o.id)}
                      />
                      <span className="text-sm text-slate-800">
                        <span className="mr-2 font-semibold text-slate-500">{String.fromCharCode(65 + i)}.</span>
                        {o.text}
                      </span>
                    </label>
                  )
                })}
              </div>
            </fieldset>
            {answers[q.id]?.optionId != null && (
              <button type="button" className="mt-3 text-xs text-slate-500 underline hover:text-slate-800" onClick={() => select(q.id, null)}>
                Clear selection
              </button>
            )}
            <div className="mt-6 flex items-center justify-between">
              <Button variant="secondary" onClick={() => setCurrent((c) => c - 1)} disabled={current === 0}>
                <ChevronLeft /> Previous
              </Button>
              {current < view.questions.length - 1 ? (
                <Button onClick={() => setCurrent((c) => c + 1)}>
                  Next <ChevronRight />
                </Button>
              ) : (
                <Button variant="success" onClick={() => setConfirmOpen(true)}>
                  Review & submit
                </Button>
              )}
            </div>
          </Card>
        </div>

        <aside className="space-y-4 lg:sticky lg:top-20 lg:h-fit">
          <Card className="p-4">
            <p className="text-sm font-medium text-slate-700">
              Answered {answeredCount} of {view.questions.length}
            </p>
            <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100">
              <div className="h-full bg-primary-600 transition-all" style={{ width: `${(answeredCount / view.questions.length) * 100}%` }} />
            </div>
            <nav className="mt-4 grid grid-cols-6 gap-1.5 sm:grid-cols-8 lg:grid-cols-5" aria-label="Questions">
              {view.questions.map((x, i) => {
                const done = answers[x.id]?.optionId != null
                return (
                  <button
                    key={x.id}
                    type="button"
                    onClick={() => setCurrent(i)}
                    aria-current={i === current}
                    aria-label={`Question ${x.number}${done ? ', answered' : ''}${flagged.has(x.id) ? ', flagged' : ''}`}
                    className={cn(
                      'relative h-9 rounded-md border text-sm font-medium tabular-nums cursor-pointer',
                      done ? 'border-primary-600 bg-primary-600 text-white' : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50',
                      i === current && 'ring-2 ring-primary-300 ring-offset-1',
                    )}
                  >
                    {x.number}
                    {flagged.has(x.id) && <span className="absolute -right-1 -top-1 size-2.5 rounded-full border border-white bg-amber-400" />}
                  </button>
                )
              })}
            </nav>
            <Button variant="success" className="mt-4 w-full" onClick={() => setConfirmOpen(true)}>
              Submit quiz
            </Button>
          </Card>
        </aside>
      </main>

      <Dialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title="Submit your quiz?"
        size="sm"
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirmOpen(false)} disabled={submitting}>
              Keep working
            </Button>
            <Button variant="success" onClick={submit} loading={submitting}>
              Submit now
            </Button>
          </>
        }
      >
        <div className="space-y-3 text-sm text-slate-700">
          <p>
            You answered <strong>{answeredCount}</strong> of {view.questions.length} questions.
          </p>
          {unanswered > 0 && (
            <p className="flex items-center gap-2 text-amber-700">
              <AlertTriangle className="size-4" /> {unanswered} unanswered question(s) will score zero.
            </p>
          )}
          {flagged.size > 0 && <p>{flagged.size} question(s) are flagged for review.</p>}
          <p>After submitting you can't change your answers.</p>
          <ErrorAlert error={submitError} />
        </div>
      </Dialog>

      {submitting && !confirmOpen && (
        <div className="fixed inset-0 z-50 grid place-items-center bg-white/80">
          <p className="flex items-center gap-2 text-slate-700">
            <Loader2 className="size-5 animate-spin" /> Submitting your answers…
          </p>
          {submitError && <ErrorAlert error={submitError} onRetry={submit} />}
        </div>
      )}
    </div>
  )
}

function SaveIndicator({ state }: { state: SaveState }) {
  return (
    <span className="flex items-center gap-1 text-xs text-slate-500" aria-live="polite">
      {state === 'saving' && (
        <>
          <Loader2 className="size-3.5 animate-spin" /> Saving…
        </>
      )}
      {state === 'saved' && <>All answers saved</>}
      {state === 'offline' && (
        <span className="flex items-center gap-1 text-amber-700">
          <CloudOff className="size-3.5" /> Not saved — retrying
        </span>
      )}
    </span>
  )
}
