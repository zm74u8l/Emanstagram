import { Client } from '@stomp/stompjs'
import { create } from 'zustand'
import { freshAccessToken } from './api'

/**
 * One STOMP connection per tab, shared by the whole app.
 *
 * The server pushes every event to /user/queue/events as { type, data }.
 * Components subscribe with {@link onRealtime}; nothing else talks to the
 * socket directly.
 *
 * Authentication happens in the CONNECT frame (browsers can't set headers on
 * the WebSocket handshake), and a fresh token is fetched before every
 * connect and reconnect, so an expired access token never breaks the socket.
 */

export interface RealtimeEvent<T = unknown> {
  type: string
  data: T
}

type Listener = (event: RealtimeEvent) => void

interface RealtimeState {
  status: 'idle' | 'connecting' | 'online' | 'offline'
}

export const useRealtimeStatus = create<RealtimeState>(() => ({ status: 'idle' }))

const listeners = new Set<Listener>()
let client: Client | null = null

function socketUrl(): string {
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${scheme}://${window.location.host}/ws`
}

export function connectRealtime(): void {
  if (client) return
  useRealtimeStatus.setState({ status: 'connecting' })

  client = new Client({
    brokerURL: socketUrl(),
    reconnectDelay: 3000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    beforeConnect: async (c) => {
      const token = await freshAccessToken()
      c.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {}
    },
    onConnect: () => {
      useRealtimeStatus.setState({ status: 'online' })
      client?.subscribe('/user/queue/events', (frame) => {
        try {
          const event = JSON.parse(frame.body) as RealtimeEvent
          listeners.forEach((l) => l(event))
        } catch {
          /* ignore malformed frames */
        }
      })
      // Anything missed while offline is reconciled by refetching.
      listeners.forEach((l) => l({ type: 'reconnected', data: null }))
    },
    onWebSocketClose: () => useRealtimeStatus.setState({ status: 'offline' }),
    onStompError: () => useRealtimeStatus.setState({ status: 'offline' }),
  })
  client.activate()
}

export function disconnectRealtime(): void {
  void client?.deactivate()
  client = null
  useRealtimeStatus.setState({ status: 'idle' })
}

/** Sends to an /app destination. Silently dropped while disconnected (typing is best-effort). */
export function publish(destination: string, body: unknown): void {
  if (client?.connected) {
    client.publish({ destination, body: JSON.stringify(body) })
  }
}

export function onRealtime(listener: Listener): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}
