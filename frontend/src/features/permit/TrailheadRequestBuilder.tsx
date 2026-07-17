import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useApiMutation } from '../../hooks/useApiMutation'
import { createPermitSearchRequest } from '../../api/generated/sdk.gen'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { TurnstileWidget, type TurnstileWidgetHandle } from '../../components/TurnstileWidget'
import { usePermit } from './usePermit'
import { ZoneDivisionPicker } from './ZoneDivisionPicker'
import type { PermitSearchResult } from '../../api/generated/types.gen'
import type { AxiosError } from 'axios'

interface Props {
  permit: PermitSearchResult
  onClear: () => void
  onSuccess?: () => void
}

export function TrailheadRequestBuilder({ permit, onClear, onSuccess }: Props) {
  const navigate = useNavigate()
  const { data: permitDetail } = usePermit(permit.id)
  const [name, setName] = useState(permit.name)
  const [groupSize, setGroupSize] = useState(1)
  const [divisionIds, setDivisionIds] = useState<string[]>([])
  const [entryDate, setEntryDate] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitError, setSubmitError] = useState<{ noPhone?: boolean; message?: string } | null>(null)
  const [turnstileToken, setTurnstileToken] = useState<string | null>(null)
  const turnstileRef = useRef<TurnstileWidgetHandle>(null)

  const maxGroupSize = permitDetail?.maxGroupSize ?? null
  const groupSizeExceedsMax = maxGroupSize != null && groupSize > maxGroupSize

  const mutation = useApiMutation({
    mutationFn: async () => {
      const result = await createPermitSearchRequest({
        body: {
          name,
          permitId: permit.id,
          permitName: permit.name,
          groupSize,
          searchType: 'TRAILHEAD',
          // Trailhead quota is consumed on the entry date — startDay/endDay model a flexibility
          // window, but date-range flexibility isn't built yet, so both are set to the same date.
          trailheadTarget: { divisionIds, startDay: entryDate, endDay: entryDate },
          provider: permit.provider,
          turnstileToken: turnstileToken!
        }
      })
      if (result.error) throw result
      return result.data!
    },
    onSuccess: () => (onSuccess ? onSuccess() : navigate('/requests')),
    onError: (err: AxiosError<{ code?: string; message?: string }>) => {
      if (err.response?.data?.code === 'TURNSTILE_FAILED') {
        setSubmitError({ message: 'Verification expired. Please try again.' })
        turnstileRef.current?.reset()
      } else if (err.response?.status === 422 && err.response?.data?.code === 'NO_VERIFIED_PHONE') {
        setSubmitError({ noPhone: true })
      } else {
        setSubmitError({ message: err.response?.data?.message ?? 'Something went wrong. Please try again.' })
      }
    }
  })

  function validate() {
    const e: Record<string, string> = {}
    if (!name.trim()) e.name = 'Alert name is required'
    if (groupSize < 1) e.groupSize = 'Group size must be at least 1'
    if (groupSizeExceedsMax) e.groupSize = `Group size exceeds this permit's limit of ${maxGroupSize}`
    if (divisionIds.length === 0) e.divisionIds = 'Select at least one trailhead'
    if (!entryDate) e.entryDate = 'Entry date is required'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const canSubmit =
    name.trim() !== '' &&
    groupSize >= 1 &&
    !groupSizeExceedsMax &&
    divisionIds.length > 0 &&
    entryDate !== '' &&
    turnstileToken !== null

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
              {maxGroupSize != null && <span className="text-xs text-forest-400">Max {maxGroupSize}</span>}
            </div>
            {errors.groupSize && <p className="mt-1 text-xs text-red-600">{errors.groupSize}</p>}
          </div>

          <div>
            <label className="mb-1 block text-xs font-medium text-forest-600">Entry Date</label>
            <Input type="date" value={entryDate} onChange={(e) => setEntryDate(e.target.value)} />
            {errors.entryDate && <p className="mt-1 text-xs text-red-600">{errors.entryDate}</p>}
          </div>

          <div>
            <ZoneDivisionPicker
              permitId={permit.id}
              selectedDivisionIds={divisionIds}
              onChange={setDivisionIds}
              label="Trailheads"
            />
            {errors.divisionIds && <p className="mt-1 text-xs text-red-600">{errors.divisionIds}</p>}
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

          <TurnstileWidget ref={turnstileRef} onToken={setTurnstileToken} />

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
