import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { CampgroundSearch } from '../campground/CampgroundSearch'
import { RequestBuilder } from './RequestBuilder'
import { PermitSearch } from '../permit/PermitSearch'
import { PermitRequestBuilder } from '../permit/PermitRequestBuilder'
import { useAuth } from '../auth/useAuth'
import type { CampgroundSearchResult, PermitSearchResult } from '../../api/generated/types.gen'

interface Props {
  onClose: () => void
}

type AlertType = 'campground' | 'permit' | 'globalEntry'

export function AddAlertModal({ onClose }: Props) {
  const [alertType, setAlertType] = useState<AlertType>('campground')
  const [selectedCampground, setSelectedCampground] = useState<CampgroundSearchResult | null>(null)
  const [selectedPermit, setSelectedPermit] = useState<PermitSearchResult | null>(null)
  const queryClient = useQueryClient()
  const { user } = useAuth()
  // The Global Entry tab only exists for users whose effective provider access includes it.
  const globalEntryEnabled = user?.enabledProviders.includes('GLOBAL_ENTRY') ?? false
  const tabs: { type: AlertType; label: string }[] = [
    { type: 'campground', label: 'Campground' },
    { type: 'permit', label: 'Permit' },
    ...(globalEntryEnabled ? [{ type: 'globalEntry' as const, label: 'Global Entry' }] : [])
  ]

  function handleSuccess() {
    queryClient.invalidateQueries({ queryKey: ['search-requests'] })
    queryClient.invalidateQueries({ queryKey: ['permit-search-requests'] })
    onClose()
  }

  const hasSelection = selectedCampground !== null || selectedPermit !== null

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/50 px-4 py-8">
      <div className="w-full max-w-xl rounded-2xl bg-stone-50 p-6 shadow-xl">
        <div className="mb-5 flex items-center justify-between">
          <h2 className="text-xl font-semibold text-forest-900">New Alert</h2>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1.5 text-forest-400 hover:bg-forest-100 hover:text-forest-700"
            aria-label="Close"
          >
            ✕
          </button>
        </div>

        {!hasSelection && (
          <div className="mb-4 flex gap-1 rounded-xl bg-forest-100 p-1">
            {tabs.map((tab) => (
              <button
                key={tab.type}
                type="button"
                onClick={() => setAlertType(tab.type)}
                className={`flex-1 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                  alertType === tab.type
                    ? 'bg-white text-forest-900 shadow-sm'
                    : 'text-forest-500 hover:text-forest-700'
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>
        )}

        {!hasSelection && alertType === 'campground' && (
          <>
            <p className="mb-4 text-sm text-forest-600">Search for a campground to start watching.</p>
            <CampgroundSearch onSelect={(cg) => setSelectedCampground(cg)} />
          </>
        )}

        {!hasSelection && alertType === 'globalEntry' && globalEntryEnabled && (
          <>
            <p className="mb-4 text-sm text-forest-600">Search for an enrollment center to start watching.</p>
            <CampgroundSearch
              provider="GLOBAL_ENTRY"
              minChars={2}
              placeholder="Search by center, city, or state..."
              emptyMessage="No enrollment centers found"
              onSelect={(center) => setSelectedCampground(center)}
            />
          </>
        )}

        {!hasSelection && alertType === 'permit' && (
          <>
            <p className="mb-4 text-sm text-forest-600">Search for a permit to start watching.</p>
            <PermitSearch onSelect={(permit) => setSelectedPermit(permit)} />
          </>
        )}

        {selectedCampground && (
          <RequestBuilder
            campground={selectedCampground}
            onClear={() => setSelectedCampground(null)}
            onSuccess={handleSuccess}
          />
        )}

        {selectedPermit && (
          <PermitRequestBuilder
            permit={selectedPermit}
            onClear={() => setSelectedPermit(null)}
            onSuccess={handleSuccess}
          />
        )}
      </div>
    </div>
  )
}
