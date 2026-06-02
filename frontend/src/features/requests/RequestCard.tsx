import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { deleteSearchRequest } from '../../api/generated/sdk.gen'
import { Badge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { RequestEditModal } from './RequestEditModal'
import type { SearchRequestResponse } from '../../api/generated/types.gen'

interface Props {
  request: SearchRequestResponse
}

export function RequestCard({ request }: Props) {
  const [showEdit, setShowEdit] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const queryClient = useQueryClient()

  const deleteMutation = useMutation({
    mutationFn: () => deleteSearchRequest({ path: { id: request.id } }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['search-requests'] }),
  })

  const dateRange = `${request.startDay} · ${request.nights} night${request.nights !== 1 ? 's' : ''}`

  return (
    <>
      <div className="relative rounded-2xl bg-white p-6 shadow-sm">
        <div className="absolute right-4 top-4 flex gap-1">
          <button
            type="button"
            onClick={() => setShowEdit(true)}
            className="rounded-lg p-1.5 text-forest-400 hover:bg-forest-100 hover:text-forest-700"
            aria-label="Edit"
          >
            ✏️
          </button>
          <button
            type="button"
            onClick={() => setShowConfirm(true)}
            className="rounded-lg p-1.5 text-forest-400 hover:bg-red-50 hover:text-red-600"
            aria-label="Delete"
          >
            🗑️
          </button>
        </div>

        <div className="flex flex-col gap-2">
          <div className="flex items-start justify-between pr-16">
            <h3 className="font-semibold text-forest-900">{request.name}</h3>
            <Badge status={request.completed ? 'done' : 'watching'} />
          </div>
          <p className="text-sm text-forest-600">Campground ID: {request.campsiteId}</p>
          <p className="text-sm text-forest-600">{dateRange}</p>
          <p className="text-sm text-forest-600">Group: {request.groupSize}</p>
          {request.loops && request.loops.length > 0 && (
            <div className="flex flex-wrap gap-1">
              {request.loops.map((loop) => (
                <span
                  key={loop}
                  className="rounded-full bg-forest-100 px-2 py-0.5 text-xs font-medium text-forest-600"
                >
                  {loop}
                </span>
              ))}
            </div>
          )}
        </div>
      </div>

      {showEdit && (
        <RequestEditModal request={request} onClose={() => setShowEdit(false)} />
      )}

      {showConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-md">
            <h2 className="mb-2 font-semibold text-forest-900">Delete alert?</h2>
            <p className="mb-6 text-sm text-forest-600">
              "{request.name}" will be permanently removed.
            </p>
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
