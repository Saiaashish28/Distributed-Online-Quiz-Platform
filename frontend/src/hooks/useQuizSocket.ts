import { useEffect, useRef, useState } from 'react'
import { WS_BASE, tokenStore } from '@/lib/api'

export interface SocketMessage<T = Record<string, unknown>> {
  type: string
  assignmentId: number | null
  serverTime: string
  data: T
}

export type SocketStatus = 'connecting' | 'open' | 'reconnecting' | 'closed' | 'forbidden'

/**
 * Subscribes to an assignment's realtime channel. Reconnects with exponential backoff; on every
 * (re)connect the server sends an authoritative `session_snapshot`, so missed events are recovered.
 */
export function useQuizSocket(assignmentId: number | null, onMessage: (msg: SocketMessage) => void) {
  const [status, setStatus] = useState<SocketStatus>('connecting')
  const handler = useRef(onMessage)
  useEffect(() => {
    handler.current = onMessage
  }, [onMessage])

  useEffect(() => {
    if (!assignmentId) return
    let ws: WebSocket | null = null
    let retry = 0
    let stopped = false
    let timer: ReturnType<typeof setTimeout> | undefined
    let ping: ReturnType<typeof setInterval> | undefined

    const connect = () => {
      const token = tokenStore.get()
      if (!token || stopped) return
      setStatus(retry === 0 ? 'connecting' : 'reconnecting')
      ws = new WebSocket(`${WS_BASE}/ws`)
      ws.onopen = () => {
        ws?.send(JSON.stringify({ type: 'subscribe', token, assignmentId }))
      }
      ws.onmessage = (ev) => {
        try {
          const msg = JSON.parse(ev.data) as SocketMessage
          if (msg.type === 'session_snapshot') {
            retry = 0
            setStatus('open')
          }
          if (msg.type !== 'pong') handler.current(msg)
        } catch {
          /* ignore malformed frames */
        }
      }
      ws.onclose = (ev) => {
        clearInterval(ping)
        if (stopped) return
        if (ev.code === 4401 || ev.code === 4403) {
          setStatus('forbidden')
          return
        }
        retry += 1
        setStatus('reconnecting')
        timer = setTimeout(connect, Math.min(30000, 1000 * 2 ** Math.min(retry, 5)))
      }
      ping = setInterval(() => {
        if (ws?.readyState === WebSocket.OPEN) ws.send(JSON.stringify({ type: 'ping' }))
      }, 25000)
    }
    connect()
    return () => {
      stopped = true
      clearTimeout(timer)
      clearInterval(ping)
      ws?.close()
      setStatus('closed')
    }
  }, [assignmentId])

  return status
}
