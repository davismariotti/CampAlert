import type { Provider } from '../../api/generated/types.gen'

interface ProviderSelectProps {
  providers: Provider[]
  selected: Provider
  onChange: (provider: Provider) => void
}

export function ProviderSelect({ providers, selected, onChange }: ProviderSelectProps) {
  if (providers.length <= 1) return null

  return (
    <div className="mb-4 flex gap-1 rounded-xl bg-forest-100 p-1">
      {providers.map((provider) => (
        <button
          key={provider.type}
          type="button"
          onClick={() => onChange(provider)}
          className={`flex-1 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
            provider.type === selected.type
              ? 'bg-white text-forest-900 shadow-sm'
              : 'text-forest-500 hover:text-forest-700'
          }`}
        >
          {provider.name}
        </button>
      ))}
    </div>
  )
}
