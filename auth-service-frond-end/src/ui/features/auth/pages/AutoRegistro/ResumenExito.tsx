import { CheckCircle2, LogIn, Settings, CreditCard, Clock } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

interface Props {
  nombreGimnasio: string
  planCodigo: string | null
}

interface Paso {
  icon: React.ReactNode
  texto: string
}

const PASOS: Paso[] = [
  { icon: <LogIn size={13} style={{ color: '#f97316' }} />, texto: 'Inicia sesión y revisa tu dashboard' },
  { icon: <Settings size={13} style={{ color: '#f97316' }} />, texto: 'Configura nombre comercial, logo y horarios' },
  { icon: <CreditCard size={13} style={{ color: '#f97316' }} />, texto: 'Crea tu primer tipo de membresía' },
]

export function ResumenExito({ nombreGimnasio, planCodigo }: Props) {
  const navigate = useNavigate()
  const esTrial = planCodigo === 'TRIAL'

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
        <p className="text-xs font-semibold mb-3" style={{ color: 'var(--page-text)' }}>
          Empieza a configurar tu gimnasio
        </p>
        <ul className="space-y-1.5">
          {PASOS.map((paso, i) => (
            <li key={i} className="flex items-start gap-2.5 py-1.5">
              <span className="flex-shrink-0 mt-0.5">{paso.icon}</span>
              <span className="text-xs" style={{ color: 'var(--page-muted)' }}>{paso.texto}</span>
            </li>
          ))}
        </ul>
      </div>

      {esTrial && (
        <div className="flex items-center gap-1.5 text-xs" style={{ color: 'var(--page-muted)' }}>
          <Clock size={12} />
          <span>Tienes 60 días de prueba gratuita para explorar todas las funciones.</span>
        </div>
      )}

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
