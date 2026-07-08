import { useEffect, useRef, useState } from 'react'
import { Printer, QrCode } from 'lucide-react'
import { QRCodeSVG } from 'qrcode.react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import type { Compania, Sucursal } from '@/domain/platform/entities/Plan.entity'

interface Props {
  open: boolean
  onClose: () => void
}

export function PrintQrModal({ open, onClose }: Props) {
  const { t } = useTranslation()
  const [empresa, setEmpresa] = useState<Compania | null>(null)
  const [sucursal, setSucursal] = useState<Sucursal | null>(null)
  const [loading, setLoading] = useState(false)
  const qrRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    setLoading(true)
    Promise.all([platformRepository.getMiEmpresa(), platformRepository.getMiSucursal()])
      .then(([emp, suc]) => { setEmpresa(emp); setSucursal(suc) })
      .catch(() => toast.error(t('layout.printQrError')))
      .finally(() => setLoading(false))
  }, [open, t])

  const handlePrint = () => {
    if (!empresa || !sucursal) return
    const svgEl = qrRef.current?.querySelector('svg')
    if (!svgEl) return

    const svgStr = svgEl.outerHTML
    const footer = t('layout.printQrFooter')
    const html = `<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="utf-8"/>
  <title>QR - ${empresa.nombre}</title>
  <style>
    *{margin:0;padding:0;box-sizing:border-box}
    body{font-family:system-ui,sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh;background:#fff}
    .card{text-align:center;padding:2.5rem}
    h1{font-size:1.4rem;font-weight:700;color:#0f172a;margin-bottom:0.25rem}
    .branch{font-size:0.875rem;color:#64748b;margin-bottom:1.75rem}
    .qr-wrap{display:inline-block;padding:12px;background:#fff;border:2px solid #e2e8f0;border-radius:14px}
    .footer{margin-top:1rem;font-size:0.7rem;color:#94a3b8}
    @media print{@page{margin:1cm}}
  </style>
</head>
<body>
  <div class="card">
    <h1>${empresa.nombre}</h1>
    <p class="branch">${sucursal.nombre}</p>
    <div class="qr-wrap">${svgStr}</div>
    <p class="footer">${footer}</p>
  </div>
  <script>window.onload=()=>{window.print();window.close()}<\/script>
</body>
</html>`

    const win = window.open('', '_blank', 'width=420,height=560,menubar=no,toolbar=no,scrollbars=no')
    if (!win) {
      toast.error(t('layout.printQrPopupBlocked'))
      return
    }
    win.document.write(html)
    win.document.close()
  }

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <QrCode size={18} />
            {t('layout.printQrTitle')}
          </DialogTitle>
        </DialogHeader>

        <div className="flex flex-col items-center gap-4 py-2">
          {loading ? (
            <div
              className="w-48 h-48 animate-pulse rounded-xl"
              style={{ background: 'var(--page-border)' }}
            />
          ) : sucursal?.qrToken ? (
            <>
              <div className="text-center">
                <p className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>
                  {empresa?.nombre}
                </p>
                <p className="text-xs" style={{ color: 'var(--page-muted)' }}>
                  {sucursal.nombre}
                </p>
              </div>
              <div
                ref={qrRef}
                className="p-3 rounded-xl"
                style={{ background: '#ffffff', border: '1px solid #e2e8f0' }}
              >
                <QRCodeSVG
                  value={`${import.meta.env.VITE_CLIENT_APP_URL}/login?qr=${sucursal.qrToken}`}
                  size={180}
                  bgColor="#ffffff"
                  fgColor="#0f172a"
                  level="M"
                />
              </div>
            </>
          ) : (
            <div
              className="w-48 h-48 rounded-xl flex items-center justify-center"
              style={{ background: 'var(--page-border)' }}
            >
              <QrCode size={40} style={{ color: 'var(--page-muted)' }} />
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button onClick={handlePrint} disabled={loading || !sucursal?.qrToken}>
            <Printer size={14} className="mr-2" />
            {t('layout.printQrButton')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
