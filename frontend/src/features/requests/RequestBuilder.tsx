import { useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useApiMutation } from '../../hooks/useApiMutation'
import { createSearchRequest, getCampground } from '../../api/generated/sdk.gen'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { TurnstileWidget, type TurnstileWidgetHandle } from '../../components/TurnstileWidget'
import { LoopPicker } from './LoopPicker'
import { EquipmentTypeFilter } from './EquipmentTypeFilter'
import { AmenityFilter } from './AmenityFilter'
import { CampsitePickerModal } from './CampsitePickerModal'
import { DateWindowFields } from './DateWindowFields'
import type { CampgroundResponse, CampgroundSearchResult } from '../../api/generated/types.gen'
import type { AxiosError } from 'axios'
import { validateDateWindow, type DateMode } from '../../utils/dateWindow'

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
  const [dateMode, setDateMode] = useState<DateMode>('exact')
  const [startDay, setStartDay] = useState('')
  const [latestStartDay, setLatestStartDay] = useState('')
  const [nights, setNights] = useState(1)
  const [groupSize, setGroupSize] = useState(1)
  const [loops, setLoops] = useState<string[] | null>(null)
  const [siteIds, setSiteIds] = useState<string[] | null>(null)
  const [equipmentType, setEquipmentType] = useState<string | null>(null)
  const [amenityIds, setAmenityIds] = useState<number[] | null>(null)
  const [showCampsiteModal, setShowCampsiteModal] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitError, setSubmitError] = useState<{ noPhone?: boolean; message?: string } | null>(null)
  const [turnstileToken, setTurnstileToken] = useState<string | null>(null)
  const turnstileRef = useRef<TurnstileWidgetHandle>(null)

  // Only fetched for providers that might expose equipment-type data (CampLife) — Recreation.gov
  // requests never need this extra catalog fetch alongside the loops fetch LoopPicker already makes.
  const { data: catalog } = useQuery({
    queryKey: ['campground-catalog', campground.id, campground.provider.type],
    queryFn: async () => {
      const result = await getCampground({ path: { id: campground.id }, query: { provider: campground.provider.type } })
      if (result.error) throw result
      return result.data as CampgroundResponse
    },
    enabled: campground.provider.type === 'CAMPLIFE',
    staleTime: 5 * 60 * 1000
  })

  const equipmentTypes = catalog?.equipmentTypes ?? []
  const amenities = catalog?.amenities ?? []

  const allowedGroupings = useMemo(() => {
    if (!equipmentType || !catalog) return null
    const groupings = new Set(
      Object.values(catalog.campsites)
        .filter((s) => (s.equipmentTypes ?? []).includes(equipmentType))
        .map((s) => s.loop)
    )
    return Array.from(groupings)
  }, [equipmentType, catalog])

  const dateWindowError = validateDateWindow({
    mode: dateMode,
    startDay,
    nights,
    latestStartDay,
    providerType: campground.provider.type
  })

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
          loops,
          siteIds,
          amenityIds,
          provider: campground.provider,
          latestStartDay: dateMode === 'flexible' ? latestStartDay : undefined,
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
    if (dateWindowError) e.startDay = dateWindowError
    if (groupSize < 1) e.groupSize = 'Group size must be at least 1'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const canSubmit = name.trim() !== '' && !dateWindowError && groupSize >= 1 && turnstileToken !== null

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

          <DateWindowFields
            mode={dateMode}
            onModeChange={setDateMode}
            startDay={startDay}
            onStartDayChange={setStartDay}
            latestStartDay={latestStartDay}
            onLatestStartDayChange={setLatestStartDay}
            nights={nights}
            providerType={campground.provider.type}
            error={dateWindowError}
          />

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

          <EquipmentTypeFilter equipmentTypes={equipmentTypes} selected={equipmentType} onChange={setEquipmentType} />

          <AmenityFilter amenities={amenities} selected={amenityIds} onChange={setAmenityIds} />

          <LoopPicker
            campgroundId={campground.id}
            provider={campground.provider}
            selectedLoops={loops}
            onChange={setLoops}
            allowedGroupings={allowedGroupings}
          />

          <div>
            <div className="flex items-center justify-between">
              <label className="block text-xs font-medium text-forest-600">Specific campsites</label>
              <button
                type="button"
                onClick={() => setShowCampsiteModal(true)}
                className="text-xs font-medium text-forest-600 hover:text-forest-800"
              >
                Choose specific campsites
              </button>
            </div>
            {siteIds && siteIds.length > 0 ? (
              <div className="mt-1.5 flex flex-wrap gap-1.5">
                {siteIds.map((id) => (
                  <span
                    key={id}
                    className="flex items-center gap-1 rounded-full bg-forest-700 px-2.5 py-0.5 text-xs font-medium text-white"
                  >
                    {id}
                    <button
                      type="button"
                      onClick={() =>
                        setSiteIds((prev) => {
                          const next = (prev ?? []).filter((s) => s !== id)
                          return next.length === 0 ? null : next
                        })
                      }
                      aria-label={`Remove site ${id}`}
                      className="hover:text-forest-200"
                    >
                      ×
                    </button>
                  </span>
                ))}
              </div>
            ) : (
              <p className="mt-1 text-xs text-forest-400">No specific sites selected — watching by grouping above</p>
            )}
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

      {showCampsiteModal && (
        <CampsitePickerModal
          campgroundId={campground.id}
          provider={campground.provider}
          selectedSiteIds={siteIds}
          onConfirm={(ids) => {
            setSiteIds(ids)
            setShowCampsiteModal(false)
          }}
          onClose={() => setShowCampsiteModal(false)}
        />
      )}
    </div>
  )
}
