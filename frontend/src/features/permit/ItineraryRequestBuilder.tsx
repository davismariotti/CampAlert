import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useApiMutation } from '../../hooks/useApiMutation'
import { createPermitSearchRequest } from '../../api/generated/sdk.gen'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { Spinner } from '../../components/ui/Spinner'
import { usePermit } from './usePermit'
import { ItineraryLegPicker } from './ItineraryLegPicker'
import type { PermitItineraryLegBody, PermitSearchResult } from '../../api/generated/types.gen'
import type { AxiosError } from 'axios'

function formatDate(dateStr: string) {
  return new Date(`${dateStr}T12:00:00`).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric'
  })
}

interface Props {
  permit: PermitSearchResult
  onClear: () => void
  onSuccess?: () => void
}

export function ItineraryRequestBuilder({ permit, onClear, onSuccess }: Props) {
  const navigate = useNavigate()
  const { data: permitDetail, isLoading: isLoadingPermit } = usePermit(permit.id)
  const [name, setName] = useState(permit.name)
  const [groupSize, setGroupSize] = useState(1)
  const [startDate, setStartDate] = useState('')
  const [legs, setLegs] = useState<PermitItineraryLegBody[]>([])
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitError, setSubmitError] = useState<{ noPhone?: boolean; message?: string } | null>(null)

  function divisionName(divisionId: string) {
    return permitDetail?.divisions.find((d) => d.id === divisionId)?.name ?? divisionId
  }

  function legConflict(leg: PermitItineraryLegBody): string | null {
    const division = permitDetail?.divisions.find((d) => d.id === leg.divisionId)
    if (division?.maxGroupSize != null && division.maxGroupSize < groupSize) {
      return `Group size (${groupSize}) exceeds this site's limit of ${division.maxGroupSize}`
    }
    return null
  }

  const hasConflict = legs.some((leg) => legConflict(leg) != null)

  const mutation = useApiMutation({
    mutationFn: async () => {
      const result = await createPermitSearchRequest({
        body: {
          name,
          permitId: permit.id,
          permitName: permit.name,
          groupSize,
          searchType: 'ITINERARY',
          itineraryTarget: { legs },
          provider: permit.provider
        }
      })
      if (result.error) throw result
      return result.data!
    },
    onSuccess: () => (onSuccess ? onSuccess() : navigate('/requests')),
    onError: (err: AxiosError<{ code?: string; message?: string }>) => {
      if (err.response?.status === 422 && err.response?.data?.code === 'NO_VERIFIED_PHONE') {
        setSubmitError({ noPhone: true })
      } else if (err.response?.status === 422 && err.response?.data?.code === 'ILLEGAL_LEG_SEQUENCE') {
        setSubmitError({
          message:
            err.response.data.message ??
            'One of the selected legs is not a legal continuation. Please review your itinerary.'
        })
      } else {
        setSubmitError({ message: err.response?.data?.message ?? 'Something went wrong. Please try again.' })
      }
    }
  })

  function validate() {
    const e: Record<string, string> = {}
    if (!name.trim()) e.name = 'Alert name is required'
    if (groupSize < 1) e.groupSize = 'Group size must be at least 1'
    if (legs.length === 0) e.legs = 'Add at least one leg'
    if (hasConflict) e.legs = 'Resolve group-size conflicts on the legs below before submitting'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const canSubmit = name.trim() !== '' && groupSize >= 1 && legs.length > 0 && !hasConflict

  function removeLeg(index: number) {
    // Legs after the removed one were chosen as continuations of it, so they're no longer
    // guaranteed legal once it's gone — drop the whole tail rather than leaving an orphaned one.
    setLegs((prev) => prev.slice(0, index))
  }

  if (isLoadingPermit || !permitDetail) {
    return (
      <div className="mt-4 flex justify-center py-8">
        <Spinner />
      </div>
    )
  }

  return (
    <div className="mt-4 overflow-hidden transition-all duration-250">
      <div className="mb-4 flex items-center justify-between rounded-xl bg-forest-100 px-4 py-2">
        <div>
          <span className="text-sm font-medium text-forest-900">{permit.name}</span>
          {permit.recareaName && <span className="ml-2 text-xs text-forest-500">{permit.recareaName}</span>}
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
            {errors.groupSize && <p className="mt-1 text-xs text-red-600">{errors.groupSize}</p>}
          </div>

          <div>
            <label className="mb-1 block text-xs font-medium text-forest-600">First night</label>
            <Input
              type="date"
              value={startDate}
              disabled={legs.length > 0}
              onChange={(e) => setStartDate(e.target.value)}
            />
          </div>

          <div>
            <label className="mb-1 block text-xs font-medium text-forest-600">
              Itinerary{' '}
              <span className="font-normal text-forest-400">
                ({legs.length} {legs.length === 1 ? 'night' : 'nights'})
              </span>
            </label>

            {legs.length > 0 && (
              <ol className="mb-2 flex flex-col gap-1.5">
                {legs.map((leg, i) => {
                  const conflict = legConflict(leg)
                  return (
                    <li
                      key={`${leg.divisionId}-${leg.date}`}
                      className={`flex items-center justify-between rounded-lg px-3 py-2 text-xs ${
                        conflict ? 'border border-red-200 bg-red-50' : 'bg-forest-50'
                      }`}
                    >
                      <div>
                        <span className="font-medium text-forest-800">
                          Night {i + 1}: {divisionName(leg.divisionId)}
                        </span>
                        <span className="ml-2 text-forest-500">{formatDate(leg.date)}</span>
                        {conflict && <p className="mt-0.5 text-red-600">{conflict}</p>}
                      </div>
                      <button
                        type="button"
                        onClick={() => removeLeg(i)}
                        className="ml-2 shrink-0 text-forest-400 hover:text-red-600"
                      >
                        Remove
                      </button>
                    </li>
                  )
                })}
              </ol>
            )}
            {errors.legs && <p className="mb-2 text-xs text-red-600">{errors.legs}</p>}

            <ItineraryLegPicker
              permit={permitDetail}
              legs={legs}
              groupSize={groupSize}
              startDate={startDate}
              onAddLeg={(leg) => setLegs((prev) => [...prev, leg])}
            />
          </div>

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
