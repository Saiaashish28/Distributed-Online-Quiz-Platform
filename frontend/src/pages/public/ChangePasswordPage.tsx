import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Alert, ErrorAlert, LoadingBlock } from '@/components/ui/feedback'
import { Field, Input } from '@/components/ui/form'
import { useToast } from '@/components/ui/toast'
import { useAuth } from '@/hooks/useAuth'
import { api, errorMessage } from '@/lib/api'
import { AuthShell } from './AuthShell'

export default function ChangePasswordPage() {
  const { user, loading, refresh } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  if (loading) return <LoadingBlock />
  if (!user) return <Navigate to="/login" replace />
  const home = user.role === 'ADMIN' ? '/admin' : '/student'

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (next !== confirm) {
      setError('New passwords do not match')
      return
    }
    setPending(true)
    setError(null)
    try {
      await api.post('/api/auth/change-password', { currentPassword: current, newPassword: next })
      await refresh()
      toast('Password updated')
      navigate(home)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setPending(false)
    }
  }

  return (
    <AuthShell title="Change password" subtitle={user.student ? `${user.student.fullName} · ${user.student.registerNumber}` : user.email}>
      {user.mustChangePassword && (
        <Alert tone="warning" className="mb-4">
          Your account uses an initial password. Please choose a new one to continue.
        </Alert>
      )}
      <form className="space-y-4" onSubmit={submit}>
        <Field label="Current password" required>
          {(id) => <Input id={id} type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} required />}
        </Field>
        <Field label="New password" required hint="8-72 characters">
          {(id) => <Input id={id} type="password" autoComplete="new-password" minLength={8} maxLength={72} value={next} onChange={(e) => setNext(e.target.value)} required />}
        </Field>
        <Field label="Confirm new password" required>
          {(id) => <Input id={id} type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />}
        </Field>
        <ErrorAlert error={error} />
        <div className="flex gap-2">
          {!user.mustChangePassword && (
            <Button type="button" variant="secondary" onClick={() => navigate(home)}>
              Cancel
            </Button>
          )}
          <Button type="submit" className="flex-1" loading={pending}>
            Update password
          </Button>
        </div>
      </form>
    </AuthShell>
  )
}
