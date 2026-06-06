import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { CampgroundSearch } from '../campground/CampgroundSearch'
import { RequestBuilder } from './RequestBuilder'
import type { CampgroundSearchResult } from '../../api/generated/types.gen'

interface Props {
  onClose: () => void
}

export function AddAlertModal({ onClose }: Props) {
  const [selected, setSelected] = useState<CampgroundSearchResult | null>(null)
  const queryClient = useQueryClient()

  function handleSuccess() {
    queryClient.invalidateQueries({ queryKey: ['search-requests'] })
    onClose()
  }

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

        {!selected && (
          <>
            <p className="mb-4 text-sm text-forest-600">Search for a campground to start watching.</p>
            <CampgroundSearch onSelect={(cg) => setSelected(cg)} />
          </>
        )}

        {selected && (
          <RequestBuilder campground={selected} onClear={() => setSelected(null)} onSuccess={handleSuccess} />
        )}
      </div>
    </div>
  )
}
