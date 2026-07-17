import { useRef, useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { deleteSearchRequest, deletePermitSearchRequest } from '../../api/generated/sdk.gen'
import { Button } from '../../components/ui/Button'
import { RequestEditModal } from './RequestEditModal'
import { PermitRequestEditModal } from '../permit/PermitRequestEditModal'
import { usePermit } from '../permit/usePermit'
import { useApiMutation } from '../../hooks/useApiMutation'
import { formatShortDate } from '../../utils/dateWindow'
import type {
  SearchRequestResponse,
  SearchRequestStats,
  PermitSearchRequestResponse
} from '../../api/generated/types.gen'

type AnyRequest = SearchRequestResponse | PermitSearchRequestResponse

interface Props {
  request: AnyRequest
}

function isPermitRequest(request: AnyRequest): request is PermitSearchRequestResponse {
  return 'searchType' in request
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

function formatPercent(value: number | null | undefined) {
  if (value === null || value === undefined) return 'Not checked yet'
  return `${Math.round(value * 100)}%`
}

function formatMinutes(value: number) {
  if (value < 1) return '< 1 min'
  return `${Math.round(value)} min`
}

function StatsModal({
  request,
  stats,
  onClose
}: {
  request: AnyRequest
  stats: SearchRequestStats
  onClose: () => void
}) {
  const hasHistory = stats.totalChecks > 0

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
      <div className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-md">
        <div className="mb-4 flex items-start justify-between gap-4">
          <div>
            <h2 className="font-semibold text-forest-900">Alert stats</h2>
            <p className="mt-0.5 text-sm text-forest-500">{request.name}</p>
            {!isPermitRequest(request) && request.latestStartDay && (
              <p className="mt-0.5 text-xs text-forest-400">
                Any {request.nights} night{request.nights !== 1 ? 's' : ''}, arriving{' '}
                {formatShortDate(request.startDay)}-{formatShortDate(request.latestStartDay)}
                {request.matchedStartDay && request.matchedEndDay
                  ? ` · Matched ${formatShortDate(request.matchedStartDay)}–${formatShortDate(request.matchedEndDay)}`
                  : ''}
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="flex h-8 w-8 items-center justify-center rounded-lg text-forest-400 hover:bg-forest-100 hover:text-forest-700"
            aria-label="Close stats"
          >
            x
          </button>
        </div>

        {!hasHistory ? (
          <p className="rounded-xl bg-forest-50 px-4 py-3 text-sm text-forest-700">
            This alert has not been checked yet. Stats will appear after the next availability scan.
          </p>
        ) : (
          <dl className="grid grid-cols-2 gap-3">
            <div className="rounded-xl bg-forest-50 p-3">
              <dt className="text-xs font-medium text-forest-500">Total checks</dt>
              <dd className="mt-1 text-lg font-semibold text-forest-900">{stats.totalChecks}</dd>
            </div>
            <div className="rounded-xl bg-forest-50 p-3">
              <dt className="text-xs font-medium text-forest-500">Available checks</dt>
              <dd className="mt-1 text-lg font-semibold text-forest-900">{stats.availableChecks}</dd>
            </div>
            <div className="rounded-xl bg-forest-50 p-3">
              <dt className="text-xs font-medium text-forest-500">Availability rate</dt>
              <dd className="mt-1 text-lg font-semibold text-forest-900">{formatPercent(stats.availabilityRate)}</dd>
            </div>
            <div className="rounded-xl bg-forest-50 p-3">
              <dt className="text-xs font-medium text-forest-500">Avg window</dt>
              <dd className="mt-1 text-lg font-semibold text-forest-900">
                {formatMinutes(stats.avgAvailabilityWindowMinutes)}
              </dd>
            </div>
            <div className="col-span-2 rounded-xl bg-forest-50 p-3">
              <dt className="text-xs font-medium text-forest-500">Missed quiet-hours windows</dt>
              <dd className="mt-1 text-lg font-semibold text-forest-900">{stats.missedQuietHoursWindows}</dd>
            </div>
          </dl>
        )}
      </div>
    </div>
  )
}

export function RequestCard({ request }: Props) {
  const [showEdit, setShowEdit] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const [showStats, setShowStats] = useState(false)
  const queryClient = useQueryClient()
  const isPermit = isPermitRequest(request)

  const { data: permitDetail } = usePermit(isPermit ? request.permitId : undefined)

  function divisionName(divisionId: string) {
    return permitDetail?.divisions.find((d) => d.id === divisionId)?.name ?? divisionId
  }

  const deleteMutation = useApiMutation({
    mutationFn: async () => {
      if (isPermit) {
        const result = await deletePermitSearchRequest({ path: { id: request.id } })
        if (result.error) throw result
      } else {
        const result = await deleteSearchRequest({ path: { id: request.id } })
        if (result.error) throw result
      }
    },
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: isPermit ? ['permit-search-requests'] : ['search-requests'] }),
    errorMessage: 'Failed to delete alert. Please try again.'
  })

  const meta = isPermit
    ? request.searchType === 'ZONE' && request.zoneTarget
      ? [
          `${request.zoneTarget.divisionIds.length} zone${request.zoneTarget.divisionIds.length !== 1 ? 's' : ''}`,
          request.zoneTarget.startDay === request.zoneTarget.endDay
            ? formatDate(request.zoneTarget.startDay)
            : `${formatDate(request.zoneTarget.startDay)} – ${formatDate(request.zoneTarget.endDay)}`,
          `${request.groupSize} ${request.groupSize !== 1 ? 'people' : 'person'}`
        ].join(' · ')
      : request.searchType === 'TRAILHEAD' && request.trailheadTarget
        ? [
            `${request.trailheadTarget.divisionIds.length} trailhead${request.trailheadTarget.divisionIds.length !== 1 ? 's' : ''}`,
            request.trailheadTarget.startDay === request.trailheadTarget.endDay
              ? formatDate(request.trailheadTarget.startDay)
              : `${formatDate(request.trailheadTarget.startDay)} – ${formatDate(request.trailheadTarget.endDay)}`,
            `${request.groupSize} ${request.groupSize !== 1 ? 'people' : 'person'}`
          ].join(' · ')
        : request.itineraryTarget
          ? [
              `${request.itineraryTarget.legs.length} night${request.itineraryTarget.legs.length !== 1 ? 's' : ''}`,
              `${request.groupSize} ${request.groupSize !== 1 ? 'people' : 'person'}`
            ].join(' · ')
          : ''
    : request.latestStartDay
      ? [
          `Any ${request.nights} night${request.nights !== 1 ? 's' : ''}`,
          `arriving ${formatShortDate(request.startDay)}-${formatShortDate(request.latestStartDay)}`,
          `${request.groupSize} ${request.groupSize !== 1 ? 'people' : 'person'}`
        ].join(' · ')
      : [
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
          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={() => setShowStats(true)}
              className="rounded-lg px-2 py-1 text-xs font-medium text-forest-600 hover:bg-forest-100 hover:text-forest-800"
            >
              Stats
            </button>
            <OverflowMenu onEdit={() => setShowEdit(true)} onDelete={() => setShowConfirm(true)} />
          </div>
        </div>

        {/* Names */}
        <div className="flex items-center gap-2">
          <h3 className="font-semibold text-forest-900">{request.name}</h3>
          <span className="rounded-full bg-neutral-100 px-2 py-0.5 text-xs font-medium text-neutral-500">
            {request.provider.name}
          </span>
        </div>
        {!isPermit && request.campgroundName && (
          <p className="mt-0.5 text-sm text-forest-500">{request.campgroundName}</p>
        )}
        {isPermit && <p className="mt-0.5 text-sm text-forest-500">{request.permitName}</p>}

        {/* Meta */}
        <p className="mt-2 text-sm text-forest-600">{meta}</p>

        {/* Specific campsites take precedence over loops for matching, so surface them distinctly */}
        {!isPermit && request.siteIds && request.siteIds.length > 0 ? (
          <div className="mt-2 flex flex-wrap gap-1">
            <span className="rounded-full bg-forest-700 px-2 py-0.5 text-xs font-medium text-white">
              {request.siteIds.length} specific site{request.siteIds.length !== 1 ? 's' : ''}
            </span>
          </div>
        ) : (
          !isPermit &&
          request.loops &&
          request.loops.length > 0 && (
            <div className="mt-2 flex flex-wrap gap-1">
              {request.loops.map((loop) => (
                <span key={loop} className="rounded-full bg-forest-100 px-2 py-0.5 text-xs font-medium text-forest-600">
                  {loop}
                </span>
              ))}
            </div>
          )
        )}

        {/* Flexible campground match state */}
        {!isPermit && request.latestStartDay && request.matchedStartDay && request.matchedEndDay && (
          <p className="mt-2 text-xs font-medium text-forest-700">
            Matched {formatShortDate(request.matchedStartDay)}–{formatShortDate(request.matchedEndDay)}
          </p>
        )}

        {/* Permit match/blocking state */}
        {isPermit && request.searchType === 'ZONE' && request.zoneTarget?.matchedDivisionId && (
          <p className="mt-2 text-xs text-forest-600">
            Matches <span className="font-medium">{divisionName(request.zoneTarget.matchedDivisionId)}</span>
            {request.zoneTarget.matchedDate && ` on ${formatDate(request.zoneTarget.matchedDate)}`}
          </p>
        )}
        {isPermit && request.searchType === 'TRAILHEAD' && request.trailheadTarget?.matchedDivisionId && (
          <p className="mt-2 text-xs text-forest-600">
            Matches <span className="font-medium">{divisionName(request.trailheadTarget.matchedDivisionId)}</span>
            {request.trailheadTarget.matchedDate && ` on ${formatDate(request.trailheadTarget.matchedDate)}`}
          </p>
        )}
        {isPermit && request.searchType === 'ITINERARY' && request.itineraryTarget?.blockingDivisionId && (
          <p className="mt-2 text-xs text-amber-700">
            Blocked at <span className="font-medium">{divisionName(request.itineraryTarget.blockingDivisionId)}</span>
            {request.itineraryTarget.blockingDate && ` on ${formatDate(request.itineraryTarget.blockingDate)}`}
          </p>
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

      {showEdit &&
        (isPermit ? (
          <PermitRequestEditModal request={request} onClose={() => setShowEdit(false)} />
        ) : (
          <RequestEditModal request={request} onClose={() => setShowEdit(false)} />
        ))}

      {showStats && <StatsModal request={request} stats={request.stats} onClose={() => setShowStats(false)} />}

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
