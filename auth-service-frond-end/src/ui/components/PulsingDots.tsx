interface Props {
  size?: 'sm' | 'md' | 'lg'
}

const sizeMap = {
  sm: { dot: 'w-1 h-1',     gap: 'gap-1'   },
  md: { dot: 'w-1.5 h-1.5', gap: 'gap-1.5' },
  lg: { dot: 'w-2 h-2',     gap: 'gap-2'   },
}

export function PulsingDots({ size = 'md' }: Props) {
  const { dot, gap } = sizeMap[size]
  return (
    <span className={`inline-flex items-center ${gap}`} aria-hidden="true">
      <span className={`${dot} rounded-full bg-orange-500 pulse-dot-1`} />
      <span className={`${dot} rounded-full bg-orange-500 pulse-dot-2`} />
      <span className={`${dot} rounded-full bg-orange-500 pulse-dot-3`} />
    </span>
  )
}
