import { useState, useEffect } from 'react'
import { toast } from 'sonner'
import { Loader2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Button } from 'primereact/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { coreRepository } from '@/infrastructure/http/core/CoreRepository'
import { getApiErrorStatus } from '@/lib/api-error'

const MOTIVOS = [
  { value: 'SOCIO_CAMBIO_OPINION', labelKey: 'ventasPendientes.motivo.socioCambioOpinion' },
  { value: 'ERROR_DE_VENTA',       labelKey: 'ventasPendientes.motivo.errorDeVenta' },
  { value: 'DUPLICADA',            labelKey: 'ventasPendientes.motivo.duplicada' },
  { value: 'DATOS_INCORRECTOS',    labelKey: 'ventasPendientes.motivo.datosIncorrectos' },
  { value: 'OTRO',                 labelKey: 'ventasPendientes.motivo.otro' },
] as const

interface Props {
  open: boolean
  idMembresia: number
  nombreCliente: string | null
  onClose: () => void
  onRechazada: () => void
}

export function RechazarVentaPendienteModal({ open, idMembresia, nombreCliente, onClose, onRechazada }: Props) {
  const { t } = useTranslation()
  const [motivo, setMotivo] = useState('')
  const [loading, setLoading] = useState(false)
  const [errorMsg, setErrorMsg] = useState('')

  useEffect(() => {
    if (!open) {
      setMotivo('')
      setErrorMsg('')
    }
  }, [open])

  const handleSubmit = async () => {
    if (!motivo) return
    setLoading(true)
    setErrorMsg('')
    try {
      await coreRepository.rechazarMembresia(idMembresia, motivo)
      toast.success(t('ventasPendientes.rechazadaSuccess'))
      onRechazada()
    } catch (err) {
      if (getApiErrorStatus(err) === 409) {
        toast.error(t('ventasPendientes.rechazarError409'))
        onRechazada()
      } else {
        setErrorMsg(t('ventasPendientes.accionError'))
      }
    } finally {
      setLoading(false)
    }
  }

  const inputCls = 'w-full px-3 py-2 text-xs rounded-md font-sans focus:outline-none focus:ring-2 focus:ring-orange-500'
  const inputStyle = { background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }
  const labelCls = 'block text-xs font-medium mb-1'
  const labelStyle = { color: 'var(--page-muted)' }

  return (
    <Dialog open={open} onOpenChange={v => !v && onClose()}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle style={{ color: 'var(--page-text)', fontSize: '0.875rem' }}>
            {t('ventasPendientes.rechazarTitle', { nombre: nombreCliente ?? '' })}
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-3 py-2">
          <p className="text-xs" style={{ color: 'var(--page-muted)' }}>
            {t('ventasPendientes.rechazarAviso')}
          </p>

          <div>
            <label className={labelCls} style={labelStyle}>
              {t('ventasPendientes.rechazarMotivoLabel')}
            </label>
            <select
              value={motivo}
              onChange={e => setMotivo(e.target.value)}
              className={inputCls}
              style={inputStyle}
            >
              <option value="" disabled>
                {t('ventasPendientes.rechazarMotivoPlaceholder')}
              </option>
              {MOTIVOS.map(m => (
                <option key={m.value} value={m.value}>
                  {t(m.labelKey)}
                </option>
              ))}
            </select>
          </div>

          {errorMsg && <p className="text-xs text-red-500">{errorMsg}</p>}
        </div>

        <DialogFooter>
          <Button
            label={t('common.cancel')}
            text
            size="small"
            onClick={onClose}
            disabled={loading}
            style={{ color: 'var(--page-muted)' }}
          />
          <Button
            severity="danger"
            size="small"
            disabled={!motivo || loading}
            onClick={handleSubmit}
            label={loading ? t('common.saving') : t('ventasPendientes.rechazarSubmit')}
            icon={loading ? <Loader2 size={12} className="animate-spin mr-1" /> : undefined}
          />
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
