import type { Provider } from '../api/generated/types.gen'

// No "list providers" endpoint exists — this mirrors the backend's Provider enum for UI purposes.
export const ALL_PROVIDERS: Provider[] = [
  { type: 'RECREATION_GOV', name: 'Recreation.gov' },
  { type: 'CAMPLIFE', name: 'CampLife' }
]

// Permit search only ever supports Recreation.gov (CampLife permit support is out of scope) — kept
// as its own list so permit search's provider selector doesn't grow a CampLife option it can't serve.
// Campground search itself is unscoped by default (see CampgroundSearch/useCampgroundSearch) and
// doesn't use this list at all.
export const PERMIT_PROVIDERS: Provider[] = ALL_PROVIDERS.filter((p) => p.type === 'RECREATION_GOV')

export const DEFAULT_PERMIT_PROVIDER: Provider = PERMIT_PROVIDERS[0]
