import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, setUnauthorizedHandler, tokenStore } from '@/lib/api'
import type { AuthResponse, Role, UserInfo } from '@/types/api'

interface AuthState {
  user: UserInfo | null
  loading: boolean
  login: (role: Role, identifier: string, password: string) => Promise<UserInfo>
  register: (body: Record<string, unknown>) => Promise<UserInfo>
  logout: () => void
  refresh: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(null)
  const [loading, setLoading] = useState(() => tokenStore.get() !== null)

  const logout = useCallback(() => {
    tokenStore.set(null)
    setUser(null)
  }, [])

  const refresh = useCallback(async () => {
    if (!tokenStore.get()) {
      setUser(null)
      return
    }
    try {
      setUser(await api.get<UserInfo>('/api/auth/me'))
    } catch {
      logout()
    }
  }, [logout])

  useEffect(() => {
    setUnauthorizedHandler(logout)
    refresh().finally(() => setLoading(false))
    return () => setUnauthorizedHandler(null)
  }, [logout, refresh])

  const login = useCallback(async (role: Role, identifier: string, password: string) => {
    const res = await api.post<AuthResponse>('/api/auth/login', { role, identifier, password })
    tokenStore.set(res.token)
    setUser(res.user)
    return res.user
  }, [])

  const register = useCallback(async (body: Record<string, unknown>) => {
    const res = await api.post<AuthResponse>('/api/auth/register', body)
    tokenStore.set(res.token)
    setUser(res.user)
    return res.user
  }, [])

  const value = useMemo(() => ({ user, loading, login, register, logout, refresh }), [user, loading, login, register, logout, refresh])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
