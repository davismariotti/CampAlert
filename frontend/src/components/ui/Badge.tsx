interface BadgeProps {
  status: 'watching' | 'done'
}

export function Badge({ status }: BadgeProps) {
  return status === 'watching' ? (
    <span className="rounded-full bg-forest-100 px-2.5 py-0.5 text-xs font-medium text-forest-600">Watching</span>
  ) : (
    <span className="rounded-full bg-neutral-100 px-2.5 py-0.5 text-xs font-medium text-neutral-500">Done</span>
  )
}
