import { createContext } from 'react'

export type ToastEntry = { id: number; message: string; type: 'error' | 'success' }
export type ToastCtx = { showToast: (message: string, type?: ToastEntry['type']) => void }

export const ToastContext = createContext<ToastCtx>({ showToast: () => {} })
