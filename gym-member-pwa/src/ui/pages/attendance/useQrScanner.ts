import { useEffect, useRef, useState } from 'react'
import { Html5Qrcode } from 'html5-qrcode'

export const QR_CONTAINER_ID = 'qr-scanner-viewport'

export function useQrScanner(onScan: (text: string) => void, active: boolean) {
  const [cameraError, setCameraError] = useState<string | null>(null)
  const scannerRef = useRef<Html5Qrcode | null>(null)
  // Keep latest callback without restarting the scanner
  const onScanRef = useRef(onScan)
  onScanRef.current = onScan

  useEffect(() => {
    if (!active) return

    const scanner = new Html5Qrcode(QR_CONTAINER_ID)
    scannerRef.current = scanner
    let fired = false

    scanner
      .start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 240, height: 240 } },
        (text) => {
          // Deduplicate — only fire once per scan session
          if (fired) return
          fired = true
          onScanRef.current(text)
        },
        undefined,
      )
      .catch(() =>
        setCameraError('No se pudo acceder a la cámara. Verifica los permisos del navegador.'),
      )

    return () => {
      if (scanner.isScanning) scanner.stop().catch(() => {})
    }
  }, [active])

  return { cameraError }
}
