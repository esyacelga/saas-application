import { Copy } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { toast } from 'sonner'

interface Props {
  token: string
  truncate?: boolean
}

export function QrTokenDisplay({ token, truncate = true }: Props) {
  const display = truncate && token.length > 16 ? `${token.slice(0, 16)}…` : token

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(token)
      toast.success('Token copiado al portapapeles')
    } catch {
      toast.error('No se pudo copiar el token')
    }
  }

  return (
    <div className="flex items-center gap-1.5">
      <code className="font-mono text-xs bg-slate-800 text-slate-300 px-2 py-0.5 rounded select-all">
        {display}
      </code>
      <Button variant="ghost" size="icon" className="h-6 w-6" onClick={handleCopy} title="Copiar token">
        <Copy size={12} />
      </Button>
    </div>
  )
}
