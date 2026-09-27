import { useCallback, useEffect, useRef, useState } from 'react'
import { api, errorMessage } from '@/lib/api'

/** Loads a GET endpoint; `reload()` refetches. Pass null to skip loading. */
export function useApi<T>(path: string | null) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(path !== null)
  const requestId = useRef(0)

  const load = useCallback(async () => {
    if (!path) return
    const id = ++requestId.current
    setLoading(true)
    setError(null)
    try {
      const result = await api.get<T>(path)
      if (id === requestId.current) setData(result)
    } catch (e) {
      if (id === requestId.current) setError(errorMessage(e))
    } finally {
      if (id === requestId.current) setLoading(false)
    }
  }, [path])

  useEffect(() => {
    load()
  }, [load])

  return { data, setData, error, loading, reload: load }
}

/** Wraps an async action with pending/error state. */
export function useAction<A extends unknown[], R>(fn: (...args: A) => Promise<R>) {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const run = useCallback(
    async (...args: A): Promise<R | undefined> => {
      setPending(true)
      setError(null)
      try {
        return await fn(...args)
      } catch (e) {
        setError(errorMessage(e))
        return undefined
      } finally {
        setPending(false)
      }
    },
    [fn],
  )
  return { run, pending, error, setError }
}
