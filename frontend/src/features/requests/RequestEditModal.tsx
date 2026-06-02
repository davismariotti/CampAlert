import { useState, KeyboardEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { updateSearchRequest } from '../../api/generated/sdk.gen'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
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
  const [loops, setLoops] = useState<string[]>(request.loops ?? [])
  const [loopInput, setLoopInput] = useState('')
  const [completed, setCompleted] = useState(request.completed)
  const [error, setError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: () =>
      updateSearchRequest({
        path: { id: request.id },
        body: { name, startDay, nights, groupSize, campsiteId: request.campsiteId, loops, completed },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['search-requests'] })
      onClose()
    },
    onError: () => setError('Failed to update. Please try again.'),
  })

  function addLoop(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter' && loopInput.trim()) {
      e.preventDefault()
      setLoops((prev) => [...prev, loopInput.trim()])
      setLoopInput('')
    }
  }

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
                <button type="button" onClick={() => setNights((n) => Math.max(1, n - 1))}
                  className="flex h-8 w-8 items-center justify-center rounded-lg border border-forest-200 text-forest-600 hover:bg-forest-100">−</button>
                <span className="w-6 text-center text-sm font-medium">{nights}</span>
                <button type="button" onClick={() => setNights((n) => n + 1)}
                  className="flex h-8 w-8 items-center justify-center rounded-lg border border-forest-200 text-forest-600 hover:bg-forest-100">+</button>
              </div>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-forest-600">Group size</label>
              <div className="flex items-center gap-2">
                <button type="button" onClick={() => setGroupSize((n) => Math.max(1, n - 1))}
                  className="flex h-8 w-8 items-center justify-center rounded-lg border border-forest-200 text-forest-600 hover:bg-forest-100">−</button>
                <span className="w-6 text-center text-sm font-medium">{groupSize}</span>
                <button type="button" onClick={() => setGroupSize((n) => n + 1)}
                  className="flex h-8 w-8 items-center justify-center rounded-lg border border-forest-200 text-forest-600 hover:bg-forest-100">+</button>
              </div>
            </div>
          </div>

          <div>
            <label className="mb-1 block text-xs font-medium text-forest-600">Loops (press Enter to add)</label>
            <div className="flex flex-wrap gap-1 rounded-xl border border-forest-200 bg-white px-3 py-2">
              {loops.map((loop) => (
                <button key={loop} type="button"
                  onClick={() => setLoops((prev) => prev.filter((l) => l !== loop))}
                  className="flex items-center gap-1 rounded-full bg-forest-100 px-2 py-0.5 text-xs font-medium text-forest-700 hover:bg-forest-200">
                  {loop} ×
                </button>
              ))}
              <input
                className="min-w-24 flex-1 bg-transparent text-sm text-forest-900 placeholder:text-forest-300 focus:outline-none"
                placeholder={loops.length === 0 ? 'e.g. Loop A' : ''}
                value={loopInput}
                onChange={(e) => setLoopInput(e.target.value)}
                onKeyDown={addLoop}
              />
            </div>
          </div>

          <label className="flex items-center gap-2 text-sm text-forest-700">
            <input type="checkbox" checked={completed} onChange={(e) => setCompleted(e.target.checked)}
              className="rounded border-forest-300" />
            Mark as completed
          </label>

          {error && <p className="text-sm text-red-600">{error}</p>}

          <div className="flex justify-end gap-3">
            <Button variant="secondary" onClick={onClose}>Cancel</Button>
            <Button loading={mutation.isPending} onClick={() => mutation.mutate()}>Save</Button>
          </div>
        </div>
      </div>
    </div>
  )
}
