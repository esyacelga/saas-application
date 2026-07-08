import { useState } from 'react'
import { Loader2, AlertTriangle } from 'lucide-react'
import { toast } from 'sonner'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { GestionarSucursalUseCase } from '@/application/platform/GestionarSucursal.usecase'
import { QrTokenDisplay } from '../../../components/QrTokenDisplay'
import type { Sucursal } from '@/domain/platform/entities/Plan.entity'

const usecase = new GestionarSucursalUseCase(platformRepository)

interface Props {
  open: boolean
  sucursal: Sucursal | null
  onClose: () => void
  onRenewed: (token: string, expira: string | null) => void
}

export function RenovarQrModal({ open, sucursal, onClose, onRenewed }: Props) {
  const [expiresInHours, setExpiresInHours] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [nuevoToken, setNuevoToken] = useState<string | null>(null)

  const handleRenovar = async () => {
    if (!sucursal) return
    setSubmitting(true)
    try {
      const res = await usecase.renovarQrToken(sucursal.id, {
        expiresInHours: expiresInHours ? Number(expiresInHours) : undefined,
      })
      setNuevoToken(res.qrToken)
      onRenewed(res.qrToken, res.qrTokenExpira)
      toast.success('QR token renovado')
    } catch {
      toast.error('Error al renovar el QR token')
    } finally {
      setSubmitting(false)
    }
  }

  const handleClose = () => {
    setNuevoToken(null)
    setExpiresInHours('')
    onClose()
  }

  if (nuevoToken) {
    return (
      <Dialog open={open} onOpenChange={handleClose}>
        <DialogContent>
          <DialogHeader><DialogTitle>QR Token renovado</DialogTitle></DialogHeader>
          <div className="space-y-3">
            <p className="text-sm text-slate-600">El nuevo token está activo. Guárdalo:</p>
            <QrTokenDisplay token={`${import.meta.env.VITE_CLIENT_APP_URL}/login?qr=${nuevoToken}`} truncate={false} />
          </div>
          <DialogFooter>
            <Button onClick={handleClose}>Cerrar</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    )
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent>
        <DialogHeader><DialogTitle>Renovar QR token — {sucursal?.nombre}</DialogTitle></DialogHeader>
        <div className="space-y-4">
          <Alert>
            <AlertTriangle size={14} className="text-amber-500" />
            <AlertDescription className="text-xs">
              El token anterior quedará inválido inmediatamente.
            </AlertDescription>
          </Alert>
          <div>
            <Label>Expira en horas <span className="text-xs text-slate-400">(dejar vacío = sin expiración)</span></Label>
            <Input
              value={expiresInHours}
              onChange={e => setExpiresInHours(e.target.value)}
              type="number"
              min={1}
              placeholder="24"
              className="mt-1"
            />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={handleClose}>Cancelar</Button>
          <Button onClick={handleRenovar} disabled={submitting}>
            {submitting && <Loader2 size={14} className="animate-spin mr-2" />}
            Renovar QR
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
