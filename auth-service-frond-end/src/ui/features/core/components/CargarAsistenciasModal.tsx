import { useState } from 'react'
import { toast } from 'sonner'
import { Loader2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import {
  Dialog, DialogContent, DialogHeader,
  DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { coreRepository } from '@/infrastructure/http/core/CoreRepository'

interface Props {
  open: boolean
  onClose: () => void
  idMembresia: number
  valorActual: number
  onActualizado: (nuevaCantidad: number) => void
}

export function CargarAsistenciasModal({ open, onClose, idMembresia, valorActual, onActualizado }: Props) {
  const { t } = useTranslation()
  const [cantidad, setCantidad] = useState(String(valorActual))
  const [submitting, setSubmitting] = useState(false)

  const handleOpen = () => {
    setCantidad(String(valorActual))
  }

  const handleSubmit = async () => {
    const parsed = parseInt(cantidad, 10)
    if (isNaN(parsed) || parsed < 0) {
      toast.error(t('membresias.asistPreviasInvalid'))
      return
    }
    setSubmitting(true)
    try {
      await coreRepository.actualizarAsistenciasPrevias(idMembresia, parsed)
      onActualizado(parsed)
      toast.success(t('membresias.asistPreviasSuccess'))
      onClose()
    } catch {
      toast.error(t('membresias.asistPreviasError'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={v => { if (!v) onClose(); else handleOpen() }}>
      <DialogContent className="w-full max-w-xs">
        <DialogHeader>
          <DialogTitle>{t('membresias.asistPreviasTitle')}</DialogTitle>
          <p className="text-xs" style={{ color: 'var(--page-muted)' }}>
            {t('membresias.asistPreviasDesc')}
          </p>
        </DialogHeader>

        <div className="py-2">
          <label className="block text-xs font-semibold uppercase mb-1.5" style={{ color: 'var(--page-muted)' }}>
            {t('membresias.asistPreviasLabel')}
          </label>
          <input
            type="number"
            min={0}
            value={cantidad}
            onChange={e => setCantidad(e.target.value)}
            className="w-full px-3 py-2 rounded-lg text-sm font-sans focus:outline-none focus:ring-2 focus:ring-orange-500"
            style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
          />
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={submitting} style={{ fontSize: '0.75rem' }}>
            {t('confirmDialog.cancel')}
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={submitting}
            className="bg-orange-500 hover:bg-orange-600 text-white"
            style={{ fontSize: '0.75rem' }}
          >
            {submitting && <Loader2 size={14} className="mr-2 animate-spin" />}
            {t('membresias.asistPreviasSubmit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
