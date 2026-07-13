import type { Provider } from '../api/generated/types.gen'

// No "list providers" endpoint exists — this mirrors the backend's Provider enum for UI purposes
// (offering search options before any catalog result exists to read a provider off of).
export const AVAILABLE_PROVIDERS: Provider[] = [{ type: 'RECREATION_GOV', name: 'Recreation.gov' }]

export const DEFAULT_PROVIDER: Provider = AVAILABLE_PROVIDERS[0]
