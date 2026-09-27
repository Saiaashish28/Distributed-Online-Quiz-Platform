import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Alert, ErrorAlert, LoadingBlock } from '@/components/ui/feedback'
import { Field, Input, Select } from '@/components/ui/form'
import { Tabs } from '@/components/ui/tabs'
import { useApi } from '@/hooks/useApi'
import { useAuth } from '@/hooks/useAuth'
import { errorMessage } from '@/lib/api'
import type { AuthConfig, Role } from '@/types/api'
import { AuthShell } from './AuthShell'

export default function RegisterPage() {
  const [params, setParams] = useSearchParams()
  const role: Role = params.get('role') === 'admin' ? 'ADMIN' : 'STUDENT'
  const { register } = useAuth()
  const navigate = useNavigate()
  const config = useApi<AuthConfig>('/api/auth/config')
  const [form, setForm] = useState({
    name: '',
    email: '',
    password: '',
    confirm: '',
    inviteCode: '',
    registerNumber: '',
    academicYear: '',
    department: '',
    section: '',
    program: '',
    semester: '',
  })
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [k]: e.target.value }))

  const enabled = role === 'ADMIN' ? config.data?.adminRegistration : config.data?.studentSelfRegistration

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (form.password !== form.confirm) {
      setError('Passwords do not match')
      return
    }
    setPending(true)
    setError(null)
    try {
      const body: Record<string, unknown> = { role, name: form.name, password: form.password, email: form.email || undefined }
      if (role === 'ADMIN') body.inviteCode = form.inviteCode
      else {
        Object.assign(body, {
          registerNumber: form.registerNumber,
          academicYear: Number(form.academicYear),
          department: form.department,
          section: form.section || undefined,
          program: form.program || undefined,
          semester: form.semester ? Number(form.semester) : undefined,
        })
      }
      const user = await register(body)
      navigate(user.role === 'ADMIN' ? '/admin' : '/student')
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setPending(false)
    }
  }

  return (
    <AuthShell
      title="Create an account"
      footer={
        <span>
          Already registered?{' '}
          <Link className="font-medium text-primary-700 hover:underline" to={`/login?role=${role.toLowerCase()}`}>
            Sign in
          </Link>
        </span>
      }
    >
      <Tabs<Role>
        value={role}
        onChange={(r) => setParams({ role: r.toLowerCase() })}
        tabs={[
          { value: 'STUDENT', label: 'Student' },
          { value: 'ADMIN', label: 'Admin / Faculty' },
        ]}
      />
      {config.loading ? (
        <LoadingBlock />
      ) : !enabled ? (
        <Alert tone="info" className="mt-5">
          {role === 'ADMIN'
            ? 'Admin registration is disabled on this server. Ask an existing administrator for access.'
            : 'Self-registration is disabled. Your institution will create your account.'}
        </Alert>
      ) : (
        <form className="mt-5 grid grid-cols-2 gap-4" onSubmit={submit}>
          <Field label="Full name" required className="col-span-2">
            {(id) => <Input id={id} value={form.name} onChange={set('name')} required maxLength={150} />}
          </Field>
          {role === 'STUDENT' && (
            <>
              <Field label="Register number" required className="col-span-2">
                {(id) => <Input id={id} value={form.registerNumber} onChange={set('registerNumber')} required placeholder="e.g. 21CSE001" />}
              </Field>
              <Field label="Academic year" required>
                {(id) => (
                  <Select id={id} value={form.academicYear} onChange={set('academicYear')} required>
                    <option value="">Select</option>
                    {[1, 2, 3, 4, 5].map((y) => (
                      <option key={y} value={y}>{['I', 'II', 'III', 'IV', 'V'][y - 1]} year</option>
                    ))}
                  </Select>
                )}
              </Field>
              <Field label="Department" required>
                {(id) => <Input id={id} value={form.department} onChange={set('department')} required placeholder="CSE" />}
              </Field>
              <Field label="Section">
                {(id) => <Input id={id} value={form.section} onChange={set('section')} placeholder="A" />}
              </Field>
              <Field label="Semester">
                {(id) => <Input id={id} type="number" min={1} max={12} value={form.semester} onChange={set('semester')} />}
              </Field>
              <Field label="Program / branch" className="col-span-2" hint="Optional, e.g. IT">
                {(id) => <Input id={id} value={form.program} onChange={set('program')} />}
              </Field>
            </>
          )}
          <Field label="Email" required={role === 'ADMIN'} className="col-span-2">
            {(id) => <Input id={id} type="email" value={form.email} onChange={set('email')} required={role === 'ADMIN'} />}
          </Field>
          <Field label="Password" required hint="At least 8 characters">
            {(id) => <Input id={id} type="password" autoComplete="new-password" minLength={8} value={form.password} onChange={set('password')} required />}
          </Field>
          <Field label="Confirm password" required>
            {(id) => <Input id={id} type="password" autoComplete="new-password" value={form.confirm} onChange={set('confirm')} required />}
          </Field>
          {role === 'ADMIN' && (
            <Field label="Invite code" required className="col-span-2" hint="Provided by your system administrator">
              {(id) => <Input id={id} value={form.inviteCode} onChange={set('inviteCode')} required />}
            </Field>
          )}
          <div className="col-span-2 space-y-3">
            <ErrorAlert error={error} />
            <Button type="submit" className="w-full" loading={pending}>
              Create account
            </Button>
          </div>
        </form>
      )}
    </AuthShell>
  )
}
