interface Props {
  title: string
  description?: string
  action?: React.ReactNode
}

export function PageHeader({ title, description, action }: Props) {
  return (
    <div className="flex items-start justify-between px-6 py-5 sticky top-0 z-10 flex-shrink-0"
      style={{ borderBottom: '1px solid var(--page-border)', background: 'var(--page-bg)' }}
    >
      <div>
        <h1 className="text-lg font-bold flex items-center gap-2" style={{ color: 'var(--page-text)' }}>{title}</h1>
        {description && <p className="text-sm mt-0.5" style={{ color: 'var(--page-muted)' }}>{description}</p>}
      </div>
      {action && <div className="ml-4 flex-shrink-0">{action}</div>}
    </div>
  )
}
