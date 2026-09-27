export const API_BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '') ?? 'http://localhost:8080'
export const WS_BASE =
  (import.meta.env.VITE_WS_BASE_URL as string | undefined)?.replace(/\/$/, '') ??
  API_BASE.replace(/^http/, 'ws')

const TOKEN_KEY = 'quizsphere.token'

export const tokenStore = {
  get(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },
  set(token: string | null) {
    try {
      if (token) localStorage.setItem(TOKEN_KEY, token)
      else localStorage.removeItem(TOKEN_KEY)
    } catch {
      /* storage unavailable: session-only login */
    }
  },
}

export class ApiError extends Error {
  status: number
  fieldErrors?: Record<string, string>
  details?: unknown

  constructor(status: number, message: string, fieldErrors?: Record<string, string>, details?: unknown) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
    this.details = details
  }
}

/** Called on 401 so the app can drop the session. */
let onUnauthorized: (() => void) | null = null
export function setUnauthorizedHandler(fn: (() => void) | null) {
  onUnauthorized = fn
}

async function parseError(res: Response): Promise<ApiError> {
  let body: { message?: string; fieldErrors?: Record<string, string>; details?: unknown } = {}
  try {
    body = await res.json()
  } catch {
    /* non-JSON error */
  }
  let message = body.message ?? `Request failed (${res.status})`
  if (body.fieldErrors) {
    const first = Object.entries(body.fieldErrors)[0]
    if (first) message = `${message}: ${first[0]} ${first[1]}`
  }
  return new ApiError(res.status, message, body.fieldErrors, body.details)
}

async function request<T>(method: string, path: string, body?: unknown, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = {}
  const token = tokenStore.get()
  if (token) headers.Authorization = `Bearer ${token}`
  let payload: BodyInit | undefined
  if (body instanceof FormData) payload = body
  else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  let res: Response
  try {
    res = await fetch(`${API_BASE}${path}`, { method, headers, body: payload, ...init })
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check your connection and try again.')
  }
  if (!res.ok) {
    const err = await parseError(res)
    if (res.status === 401 && token && onUnauthorized) onUnauthorized()
    throw err
  }
  if (res.status === 204) return undefined as T
  const type = res.headers.get('content-type') ?? ''
  return (type.includes('json') ? await res.json() : undefined) as T
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  patch: <T>(path: string, body?: unknown) => request<T>('PATCH', path, body),
  del: <T = void>(path: string) => request<T>('DELETE', path),
  upload: <T>(path: string, file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    return request<T>('POST', path, fd)
  },
}

/** Downloads a binary response (e.g. an .xlsx export) using the server-provided filename. */
export async function download(path: string, fallbackName: string) {
  const token = tokenStore.get()
  const res = await fetch(`${API_BASE}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  }).catch(() => {
    throw new ApiError(0, 'Cannot reach the server.')
  })
  if (!res.ok) throw await parseError(res)
  const disposition = res.headers.get('content-disposition') ?? ''
  // Prefer the RFC 5987 filename* parameter; fall back to the plain filename.
  const extended = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  const plain = /filename="?([^";]+)"?/i.exec(disposition)
  const name = extended ? decodeURIComponent(extended[1]) : plain ? plain[1] : fallbackName
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function errorMessage(e: unknown) {
  if (e instanceof ApiError) return e.message
  if (e instanceof Error) return e.message
  return 'Something went wrong'
}
