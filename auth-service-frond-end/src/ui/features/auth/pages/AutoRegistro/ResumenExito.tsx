import { useState } from 'react'
import { CheckCircle2, Copy, Check } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

interface Props {
  nombreGimnasio: string
  qrToken: string
}

export function ResumenExito({ nombreGimnasio, qrToken }: Props) {
  const navigate = useNavigate()
  const [copiado, setCopiado] = useState(false)

  const handleCopiar = async () => {
    await navigator.clipboard.writeText(qrToken)
    setCopiado(true)
    setTimeout(() => setCopiado(false), 2000)
  }

  return (
    <div className="flex flex-col items-center gap-5 py-2 text-center">
      <CheckCircle2 size={56} className="text-green-500" />

      <div>
        <h2 className="text-xl font-bold" style={{ color: 'var(--page-text)' }}>
          ¡Tu gimnasio está listo!
        </h2>
        <p className="text-sm mt-1" style={{ color: 'var(--page-muted)' }}>
          <span className="font-medium" style={{ color: 'var(--page-text)' }}>{nombreGimnasio}</span> ha sido
          registrado exitosamente.
        </p>
      </div>

      <div
        className="w-full rounded-xl p-4 text-left"
        style={{ border: '1px solid var(--page-border)', background: 'var(--page-surface)' }}
      >
        <p className="text-xs font-semibold mb-2" style={{ color: 'var(--page-text)' }}>
          Tu token QR de asistencia
        </p>
        <div className="flex items-center gap-2">
          <code
            className="flex-1 font-mono text-xs break-all rounded-md px-3 py-2"
            style={{ background: 'var(--page-bg)', color: 'var(--page-text)', border: '1px solid var(--page-border)' }}
          >
            {qrToken}
          </code>
          <button
            type="button"
            onClick={handleCopiar}
            className="flex-shrink-0 flex items-center gap-1.5 text-xs font-medium px-3 py-2 rounded-lg transition-all"
            style={{
              background: copiado ? '#f0fdf4' : 'var(--page-bg)',
              color: copiado ? '#16a34a' : 'var(--page-muted)',
              border: `1px solid ${copiado ? '#bbf7d0' : 'var(--page-border)'}`,
            }}
          >
            {copiado ? <Check size={12} /> : <Copy size={12} />}
            {copiado ? '¡Copiado!' : 'Copiar'}
          </button>
        </div>
        <p className="text-xs mt-2" style={{ color: 'var(--page-muted)' }}>
          Úsalo para que tus clientes registren su asistencia con el QR.
        </p>
      </div>

      <button
        type="button"
        onClick={() => navigate('/login', { replace: true })}
        className="w-full py-2.5 rounded-lg font-semibold text-sm transition-colors"
        style={{ background: '#f97316', color: '#fff' }}
        onMouseOver={e => (e.currentTarget.style.background = '#ea6c10')}
        onMouseOut={e => (e.currentTarget.style.background = '#f97316')}
      >
        Ir a iniciar sesión →
      </button>

      <p className="text-xs" style={{ color: 'var(--page-muted)' }}>
        Usa el correo y contraseña que acabas de crear.
      </p>
    </div>
  )
}
