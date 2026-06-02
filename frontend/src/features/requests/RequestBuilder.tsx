import { useState, KeyboardEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { createSearchRequest } from '../../api/generated/sdk.gen'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import type { CampgroundSearchResult } from '../../api/generated/types.gen'

interface Props {
  campground: CampgroundSearchResult
  onClear: () => void
}

export function RequestBuilder({ campground, onClear }: Props) {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [startDay, setStartDay] = useState('')
  const [nights, setNights] = useState(1)
  const [groupSize, setGroupSize] = useState(1)
  const [loops, setLoops] = useState<string[]>([])
  const [loopInput, setLoopInput] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})

  const mutation = useMutation({
    mutationFn: () =>
      createSearchRequest({
        body: {
          name,
          startDay,
          nights,
          groupSize,
          campsiteId: campground.id,
          loops: loops.length > 0 ? loops : null
        }
      }),
    onSuccess: () => navigate('/requests')
  })

  function validate() {
    const e: Record<string, string> = {}
    if (!name.trim()) e.name = 'Alert name is required'
    if (!startDay) e.startDay = 'Start date is required'
    if (nights < 1) e.nights = 'Nights must be at least 1'
    if (groupSize < 1) e.groupSize = 'Group size must be at least 1'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  function addLoop(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter' && loopInput.trim()) {
      e.preventDefault()
      setLoops((prev) => [...prev, loopInput.trim()])
      setLoopInput('')
    }
  }

  const canSubmit = name.trim() !== '' && startDay !== '' && nights >= 1 && groupSize >= 1

  return (
    <div className="mt-4 overflow-hidden transition-all duration-250">
      {/* Selected campground chip */}
      <div className="mb-4 flex items-center justify-between rounded-xl bg-forest-100 px-4 py-2">
        <div>
          <span className="text-sm font-medium text-forest-900">{campground.name}</span>
          <span className="ml-2 text-xs text-forest-500">ID: {campground.id}</span>
        </div>
        <button
          type="button"
          onClick={onClear}
          className="ml-4 text-xs font-medium text-forest-600 hover:text-forest-800"
        >
          Change
        </button>
      </div>

      <div className="rounded-2xl bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-4">
          <div>
            <Input placeholder="Alert name" value={name} onChange={(e) => setName(e.target.value)} />
            {errors.name && <p className="mt-1 text-xs text-red-600">{errors.name}</p>}
          </div>

          <div>
            <Input type="date" value={startDay} onChange={(e) => setStartDay(e.target.value)} />
            {errors.startDay && <p className="mt-1 text-xs text-red-600">{errors.startDay}</p>}
          </div>

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

          <div>
            <label className="mb-1 block text-xs font-medium text-forest-600">
              Loops <span className="font-normal text-forest-400">(optional — press Enter to add)</span>
            </label>
            <div className="flex flex-wrap gap-1 rounded-xl border border-forest-200 bg-white px-3 py-2">
              {loops.map((loop) => (
                <button
                  key={loop}
                  type="button"
                  onClick={() => setLoops((prev) => prev.filter((l) => l !== loop))}
                  className="flex items-center gap-1 rounded-full bg-forest-100 px-2 py-0.5 text-xs font-medium text-forest-700 hover:bg-forest-200"
                >
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

          <Button
            type="button"
            loading={mutation.isPending}
            disabled={!canSubmit}
            onClick={() => {
              if (validate()) mutation.mutate()
            }}
          >
            Set Alert
          </Button>
        </div>
      </div>
    </div>
  )
}
