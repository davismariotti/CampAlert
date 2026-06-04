import { useRef, useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { deleteSearchRequest } from '../../api/generated/sdk.gen'
import { Button } from '../../components/ui/Button'
import { RequestEditModal } from './RequestEditModal'
import { useApiMutation } from '../../hooks/useApiMutation'
import type { SearchRequestResponse } from '../../api/generated/types.gen'

interface Props {
  request: SearchRequestResponse
}

function formatDate(dateStr: string) {
  return new Date(`${dateStr}T12:00:00`).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric'
  })
}

function OverflowMenu({ onEdit, onDelete }: { onEdit: () => void; onDelete: () => void }) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    function handleClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    function handleEscape(e: KeyboardEvent) {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [open])

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="flex h-7 w-7 items-center justify-center rounded-lg text-forest-400 hover:bg-forest-100 hover:text-forest-700"
        aria-label="More options"
      >
        ···
      </button>
      {open && (
        <div className="absolute right-0 top-full z-20 mt-1 w-32 overflow-hidden rounded-xl border border-forest-200 bg-white shadow-md">
          <button
            type="button"
            onClick={() => {
              setOpen(false)
              onEdit()
            }}
            className="flex w-full px-4 py-2.5 text-left text-sm text-forest-700 hover:bg-forest-50"
          >
            Edit
          </button>
          <button
            type="button"
            onClick={() => {
              setOpen(false)
              onDelete()
            }}
            className="flex w-full px-4 py-2.5 text-left text-sm text-red-600 hover:bg-red-50"
          >
            Delete
          </button>
        </div>
      )}
    </div>
  )
}

export function RequestCard({ request }: Props) {
  const [showEdit, setShowEdit] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const queryClient = useQueryClient()

  const deleteMutation = useApiMutation({
    mutationFn: async () => {
      const result = await deleteSearchRequest({ path: { id: request.id } })
      if (result.error) throw result
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['search-requests'] }),
    errorMessage: 'Failed to delete alert. Please try again.'
  })

  const meta = [
    formatDate(request.startDay),
    `${request.nights} night${request.nights !== 1 ? 's' : ''}`,
    `${request.groupSize} ${request.groupSize !== 1 ? 'people' : 'person'}`
  ].join(' · ')

  const watching = !request.completed

  return (
    <>
      <div className="rounded-2xl bg-white p-5 shadow-sm transition-shadow hover:shadow-md">
        {/* Status + overflow menu */}
        <div className="mb-3 flex items-center justify-between">
          <div className="flex items-center gap-1.5">
            <span className={`h-2 w-2 rounded-full ${watching ? 'bg-forest-500' : 'bg-neutral-300'}`} />
            <span className={`text-xs font-medium ${watching ? 'text-forest-600' : 'text-neutral-400'}`}>
              {watching ? 'Watching' : 'Done'}
            </span>
          </div>
          <OverflowMenu onEdit={() => setShowEdit(true)} onDelete={() => setShowConfirm(true)} />
        </div>

        {/* Names */}
        <h3 className="font-semibold text-forest-900">{request.name}</h3>
        {request.campgroundName && <p className="mt-0.5 text-sm text-forest-500">{request.campgroundName}</p>}

        {/* Meta */}
        <p className="mt-2 text-sm text-forest-600">{meta}</p>

        {/* Loops */}
        {request.loops && request.loops.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-1">
            {request.loops.map((loop) => (
              <span key={loop} className="rounded-full bg-forest-100 px-2 py-0.5 text-xs font-medium text-forest-600">
                {loop}
              </span>
            ))}
          </div>
        )}

        {/* Pause warning */}
        {request.pauseReason === 'NO_VERIFIED_PHONE' && (
          <p className="mt-2 text-xs text-amber-700">
            Paused — no verified phone.{' '}
            <Link to="/phone-numbers" className="font-medium underline hover:text-amber-900">
              Add one
            </Link>{' '}
            to resume.
          </p>
        )}
      </div>

      {showEdit && <RequestEditModal request={request} onClose={() => setShowEdit(false)} />}

      {showConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-md">
            <h2 className="mb-2 font-semibold text-forest-900">Delete alert?</h2>
            <p className="mb-6 text-sm text-forest-600">"{request.name}" will be permanently removed.</p>
            <div className="flex justify-end gap-3">
              <Button variant="secondary" onClick={() => setShowConfirm(false)}>
                Cancel
              </Button>
              <Button
                className="bg-red-600 hover:bg-red-700"
                loading={deleteMutation.isPending}
                onClick={() => deleteMutation.mutate()}
              >
                Delete
              </Button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
