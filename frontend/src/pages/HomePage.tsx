import { useState } from 'react'
import { CampgroundSearch } from '../features/campground/CampgroundSearch'
import { RequestBuilder } from '../features/requests/RequestBuilder'
import type { CampgroundSearchResult } from '../api/generated/types.gen'

export function HomePage() {
  const [selected, setSelected] = useState<CampgroundSearchResult | null>(null)

  return (
    <div className="mx-auto w-full max-w-xl px-4 py-12">
      <div className="mb-8 flex flex-col items-center gap-2 text-center">
        <img src="/logo.png" alt="CampAlert" className="h-20 w-20 rounded-2xl" />
        <h1 className="text-3xl font-semibold text-forest-900">CampAlert</h1>
        <p className="text-forest-600">Never miss a campsite opening.</p>
      </div>

      {!selected && (
        <CampgroundSearch onSelect={(cg) => setSelected(cg)} />
      )}

      {selected && (
        <RequestBuilder campground={selected} onClear={() => setSelected(null)} />
      )}
    </div>
  )
}
