import { create } from 'zustand'

export interface Toast {
  id: number
  message: string
  tone: 'neutral' | 'error'
  action?: { label: string; onClick: () => void }
}

interface ToastState {
  toasts: Toast[]
  show: (message: string, options?: { tone?: Toast['tone']; action?: Toast['action']; durationMs?: number }) => void
  dismiss: (id: number) => void
}

let nextId = 1

/** Small pill notices at the bottom of the screen ("Link copied", errors). */
export const useToasts = create<ToastState>((set, get) => ({
  toasts: [],
  show: (message, options = {}) => {
    const id = nextId++
    const toast: Toast = { id, message, tone: options.tone ?? 'neutral', action: options.action }
    // Keep at most three on screen; the newest wins.
    set({ toasts: [...get().toasts.slice(-2), toast] })
    window.setTimeout(() => get().dismiss(id), options.durationMs ?? 3200)
  },
  dismiss: (id) => set({ toasts: get().toasts.filter((t) => t.id !== id) }),
}))

export const toast = (message: string) => useToasts.getState().show(message)
export const toastError = (message: string) => useToasts.getState().show(message, { tone: 'error', durationMs: 4500 })
