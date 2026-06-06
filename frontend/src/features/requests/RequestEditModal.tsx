import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { updateSearchRequest } from '../../api/generated/sdk.gen'
import { useApiMutation } from '../../hooks/useApiMutation'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { LoopPicker } from './LoopPicker'
import type { SearchRequestResponse } from '../../api/generated/types.gen'

interface Props {
  request: SearchRequestResponse
  onClose: () => void
}

export function RequestEditModal({ request, onClose }: Props) {
  const queryClient = useQueryClient()
  const [name, setName] = useState(request.name)
  const [startDay, setStartDay] = useState(request.startDay)
  const [nights, setNights] = useState(request.nights)
  const [groupSize, setGroupSize] = useState(request.groupSize)
  const [loops, setLoops] = useState<string[] | null>(request.loops ?? null)
  const [completed, setCompleted] = useState(request.completed)
  const [error, setError] = useState<string | null>(null)

  const mutation = useApiMutation({
    mutationFn: async () => {
      const result = await updateSearchRequest({
        path: { id: request.id },
        body: {
          name,
          startDay,
          nights,
          groupSize,
          campsiteId: request.campsiteId,
          loops: loops ?? undefined,
          completed
        }
      })
      if (result.error) throw result
      return result.data!
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['search-requests'] })
      onClose()
    },
    onError: () => setError('Failed to update. Please try again.')
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-md">
        <h2 className="mb-4 font-semibold text-forest-900">Edit alert</h2>

        <div className="flex flex-col gap-4">
          <Input placeholder="Alert name" value={name} onChange={(e) => setName(e.target.value)} />
          <Input type="date" value={startDay} onChange={(e) => setStartDay(e.target.value)} />

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="mb-1 block text-xs font-medium text-forest-600">Nights</label>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => setNights((n) => Math.max(1, n - 1))}
                  className="flex h-8 w-8 items-center justify-center rounded-lg border border-forest-200 text-forest-600 hover:bg-forest-100"
                >
                  −
                </button>
                <span className="w-6 text-center text-sm font-medium">{nights}</span>
                <button
                  type="button"
                  onClick={() => setNights((n) => n + 1)}
                  className="flex h-8 w-8 items-center justify-center rounded-lg border border-forest-200 text-forest-600 hover:bg-forest-100"
                >
                  +
                </button>
              </div>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-forest-600">Group size</label>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => setGroupSize((n) => Math.max(1, n - 1))}
                  className="flex h-8 w-8 items-center justify-center rounded-lg border border-forest-200 text-forest-600 hover:bg-forest-100"
                >
                  −
                </button>
                <span className="w-6 text-center text-sm font-medium">{groupSize}</span>
                <button
                  type="button"
                  onClick={() => setGroupSize((n) => n + 1)}
                  className="flex h-8 w-8 items-center justify-center rounded-lg border border-forest-200 text-forest-600 hover:bg-forest-100"
                >
                  +
                </button>
              </div>
            </div>
          </div>

          <LoopPicker campgroundId={request.campsiteId} selectedLoops={loops} onChange={setLoops} />

          <label className="flex items-center gap-2 text-sm text-forest-700">
            <input
              type="checkbox"
              checked={completed}
              onChange={(e) => setCompleted(e.target.checked)}
              className="rounded border-forest-300"
            />
            Mark as completed
          </label>

          {error && <p className="text-sm text-red-600">{error}</p>}

          <div className="flex justify-end gap-3">
            <Button variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button loading={mutation.isPending} onClick={() => mutation.mutate()}>
              Save
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}
