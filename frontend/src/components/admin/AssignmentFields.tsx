import { Checkbox, Field, Input, Select } from '@/components/ui/form'
import type { ProctoringSettings, ReleaseMode } from '@/types/api'

export interface TimingValue {
  availableFrom: string
  deadline: string
  durationMinutes: string
  maxAttempts: string
}

export function TimingFields({ value, onChange }: { value: TimingValue; onChange: (v: TimingValue) => void }) {
  const set = (k: keyof TimingValue) => (e: { target: { value: string } }) => onChange({ ...value, [k]: e.target.value })
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <Field label="Available from" hint="Leave empty to open immediately">
        {(id) => <Input id={id} type="datetime-local" value={value.availableFrom} onChange={set('availableFrom')} />}
      </Field>
      <Field label="Deadline" hint="Attempts end no later than this">
        {(id) => <Input id={id} type="datetime-local" value={value.deadline} onChange={set('deadline')} />}
      </Field>
      <Field label="Duration (minutes)" required>
        {(id) => <Input id={id} type="number" min={1} max={600} value={value.durationMinutes} onChange={set('durationMinutes')} />}
      </Field>
      <Field label="Maximum attempts" required hint="Best attempt counts for marks">
        {(id) => <Input id={id} type="number" min={1} max={10} value={value.maxAttempts} onChange={set('maxAttempts')} />}
      </Field>
    </div>
  )
}

export function ReleaseFields({
  mode,
  leaderboard,
  onMode,
  onLeaderboard,
}: {
  mode: ReleaseMode
  leaderboard: boolean
  onMode: (m: ReleaseMode) => void
  onLeaderboard: (v: boolean) => void
}) {
  return (
    <div className="space-y-3">
      <Field label="Results release">
        {(id) => (
          <Select id={id} value={mode} onChange={(e) => onMode(e.target.value as ReleaseMode)}>
            <option value="MANUAL">Manually, when I release them</option>
            <option value="AFTER_DEADLINE">Automatically after the deadline / session end</option>
            <option value="IMMEDIATE">Immediately after each submission</option>
          </Select>
        )}
      </Field>
      <Checkbox checked={leaderboard} onChange={onLeaderboard} label="Show a leaderboard" description="Visible to students once results are released (names and scores)." />
    </div>
  )
}

export function ProctoringFields({ value, onChange }: { value: ProctoringSettings; onChange: (v: ProctoringSettings) => void }) {
  const set = (patch: Partial<ProctoringSettings>) => onChange({ ...value, ...patch })
  return (
    <div className="space-y-3">
      <Checkbox
        checked={value.enabled}
        onChange={(enabled) => set({ enabled })}
        label="Enable browser monitoring"
        description="Records when the quiz page loses focus or exits fullscreen. Students see a clear notice. After 3 such events the attempt is submitted automatically; saved answers are graded and nothing is deducted."
      />
      {value.enabled && (
        <div className="ml-6 space-y-3 border-l border-slate-200 pl-4">
          <Field label="Warning threshold" hint="Warnings before an attempt is flagged for review (auto-submit always happens at 3)">
            {(id) => (
              <Input id={id} className="w-28" type="number" min={1} max={100} value={value.warningThreshold} onChange={(e) => set({ warningThreshold: Number(e.target.value) || 1 })} />
            )}
          </Field>
          <Checkbox checked={value.showWarnings} onChange={(showWarnings) => set({ showWarnings })} label="Show warnings to the student" />
          <Checkbox checked={value.flagForReview} onChange={(flagForReview) => set({ flagForReview })} label="Flag attempts for review at the threshold" />
          <Checkbox checked={value.requireFullscreen} onChange={(requireFullscreen) => set({ requireFullscreen })} label="Require fullscreen" description="Each exit from fullscreen counts as a warning (3 warnings auto-submit) and starts a 10-second countdown to return; if it runs out the quiz is submitted automatically. Browsers without fullscreen support (e.g. iPhone Safari) are not affected." />
        </div>
      )}
    </div>
  )
}
