import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { ErrorAlert } from '@/components/ui/feedback'
import { Field, Input } from '@/components/ui/form'
import { Tabs } from '@/components/ui/tabs'
import { useAuth } from '@/hooks/useAuth'
import { errorMessage } from '@/lib/api'
import type { Role } from '@/types/api'
import { AuthShell } from './AuthShell'

export default function LoginPage() {
  const [params, setParams] = useSearchParams()
  const role: Role = params.get('role') === 'admin' ? 'ADMIN' : 'STUDENT'
  const { login } = useAuth()
  const navigate = useNavigate()
  const [identifier, setIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setPending(true)
    setError(null)
    try {
      const user = await login(role, identifier.trim(), password)
      if (user.mustChangePassword) navigate('/change-password')
      else {
        const next = params.get('next')
        const home = user.role === 'ADMIN' ? '/admin' : '/student'
        navigate(next && next.startsWith(home) ? next : home)
      }
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setPending(false)
    }
  }

  return (
    <AuthShell
      title="Sign in"
      subtitle={role === 'ADMIN' ? 'Faculty and administrators sign in with email.' : 'Students sign in with their register number.'}
      footer={
        <span>
          New here?{' '}
          <Link className="font-medium text-primary-700 hover:underline" to={`/register?role=${role.toLowerCase()}`}>
            Create an account
          </Link>
        </span>
      }
    >
      <Tabs<Role>
        value={role}
        onChange={(r) => {
          setError(null)
          setIdentifier('')
          setParams({ role: r.toLowerCase() })
        }}
        tabs={[
          { value: 'STUDENT', label: 'Student' },
          { value: 'ADMIN', label: 'Admin / Faculty' },
        ]}
      />
      <form className="mt-5 space-y-4" onSubmit={submit}>
        <Field label={role === 'ADMIN' ? 'Email' : 'Register number'} required>
          {(id) => (
            <Input
              id={id}
              autoComplete="username"
              type={role === 'ADMIN' ? 'email' : 'text'}
              placeholder={role === 'ADMIN' ? 'you@college.edu' : 'e.g. 21CSE001'}
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              required
              autoFocus
            />
          )}
        </Field>
        <Field label="Password" required>
          {(id) => (
            <Input id={id} type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          )}
        </Field>
        <ErrorAlert error={error} />
        <Button type="submit" className="w-full" loading={pending}>
          Sign in
        </Button>
        {role === 'STUDENT' && (
          <p className="text-xs text-slate-500">
            Accounts created by your institution start with your register number as the password; you'll be asked to change it.
          </p>
        )}
      </form>
    </AuthShell>
  )
}
