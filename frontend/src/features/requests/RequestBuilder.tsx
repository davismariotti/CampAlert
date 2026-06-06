import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useApiMutation } from '../../hooks/useApiMutation'
import { createSearchRequest } from '../../api/generated/sdk.gen'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { LoopPicker } from './LoopPicker'
import type { CampgroundSearchResult } from '../../api/generated/types.gen'
import type { AxiosError } from 'axios'

interface Props {
  campground: CampgroundSearchResult
  onClear: () => void
  onSuccess?: () => void
}

function defaultAlertName(campgroundName: string): string {
  const year = new Date().getFullYear()
  const base = campgroundName
    .replace(/\bcampgrounds?\b\s*/gi, '') // remove "Campground(s)" anywhere
    .replace(/\s*\([A-Z]{2}\)\s*$/, '') // strip trailing state abbrevs like (UT)
    .replace(/\s{2,}/g, ' ')
    .trim()
  return `${base} ${year}`
}

export function RequestBuilder({ campground, onClear, onSuccess }: Props) {
  const navigate = useNavigate()
  const [name, setName] = useState(() => defaultAlertName(campground.name))
  const [startDay, setStartDay] = useState('')
  const [nights, setNights] = useState(1)
  const [groupSize, setGroupSize] = useState(1)
  const [loops, setLoops] = useState<string[] | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitError, setSubmitError] = useState<{ noPhone?: boolean; message?: string } | null>(null)

  const mutation = useApiMutation({
    mutationFn: async () => {
      const result = await createSearchRequest({
        body: {
          name,
          startDay,
          nights,
          groupSize,
          campsiteId: campground.id,
          campgroundName: campground.name,
          loops
        }
      })
      if (result.error) throw result
      return result.data!
    },
    onSuccess: () => (onSuccess ? onSuccess() : navigate('/requests')),
    onError: (err: AxiosError<{ code?: string; message?: string }>) => {
      if (err.response?.status === 422 && err.response?.data?.code === 'NO_VERIFIED_PHONE') {
        setSubmitError({ noPhone: true })
      } else {
        setSubmitError({ message: err.response?.data?.message ?? 'Something went wrong. Please try again.' })
      }
    }
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

          <LoopPicker campgroundId={campground.id} selectedLoops={loops} onChange={setLoops} />

          {submitError?.noPhone && (
            <p className="text-sm text-amber-700">
              You need a verified phone number to create alerts.{' '}
              <Link to="/phone-numbers" className="font-medium underline hover:text-amber-900">
                Add one now
              </Link>
              .
            </p>
          )}
          {submitError?.message && <p className="text-sm text-red-600">{submitError.message}</p>}

          <Button
            type="button"
            loading={mutation.isPending}
            disabled={!canSubmit}
            onClick={() => {
              setSubmitError(null)
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
