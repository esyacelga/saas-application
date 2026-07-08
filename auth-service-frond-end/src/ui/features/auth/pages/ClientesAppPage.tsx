import { useState, useCallback, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { AlertTriangle, Loader2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { Persona } from '@/infrastructure/http/auth/auth.dto'
import type { CrearPersonaRequest } from '@/infrastructure/http/auth/auth.dto'
import type { CrearAppUsuarioFormData } from '../schemas/persona.schema'
import { PageHeader } from '@/ui/components/PageHeader'
import { coreRepository } from '@/infrastructure/http/core/CoreRepository'
import { BuscarPersonaStep } from '../components/BuscarPersonaStep'
import { CrearPersonaStep } from '../components/CrearPersonaStep'
import { CrearAppUsuarioStep } from '../components/CrearAppUsuarioStep'
import { SeleccionarMembresiaStep } from '../components/SeleccionarMembresiaStep'

type Paso = 'buscar' | 'crear-persona' | 'crear-app-usuario' | 'membresia'

export function ClientesAppPage() {
  const { t } = useTranslation()
  const [paso, setPaso] = useState<Paso>('buscar')
  const [ciPendiente, setCiPendiente] = useState('')
  const [personaSeleccionada, setPersonaSeleccionada] = useState<Persona | null>(null)
  const [personaFormData, setPersonaFormData] = useState<CrearPersonaRequest | null>(null)
  const [appUsuarioDatos, setAppUsuarioDatos] = useState<CrearAppUsuarioFormData | null>(null)
  const [sinTiposMembresia, setSinTiposMembresia] = useState<boolean | null>(null)

  useEffect(() => {
    coreRepository.getTiposMembresia()
      .then(tipos => setSinTiposMembresia(tipos.filter(t => t.activo).length === 0))
      .catch(() => setSinTiposMembresia(false))
  }, [])

  const PASOS: { key: Paso; label: string }[] = [
    { key: 'buscar',            label: t('appAccounts.stepSearch') },
    { key: 'crear-persona',     label: t('appAccounts.stepRegister') },
    { key: 'crear-app-usuario', label: t('appAccounts.stepCreateAccount') },
    { key: 'membresia',         label: t('appAccounts.stepMembresia') },
  ]

  const reiniciar = useCallback(() => {
    setPaso('buscar')
    setCiPendiente('')
    setPersonaSeleccionada(null)
    setPersonaFormData(null)
    setAppUsuarioDatos(null)
  }, [])

  const idxActual = PASOS.findIndex(p => p.key === paso)

  // ── Verificando configuración ──────────────────────────────────────────────
  if (sinTiposMembresia === null) {
    return (
      <div className="flex flex-col h-full">
        <PageHeader title={t('appAccounts.title')} description={t('appAccounts.description')} />
        <div className="flex items-center justify-center flex-1">
          <Loader2 size={24} className="animate-spin text-slate-300" />
        </div>
      </div>
    )
  }

  // ── Sin tipos de membresía: bloquear el wizard ─────────────────────────────
  if (sinTiposMembresia) {
    return (
      <div className="flex flex-col h-full">
        <PageHeader title={t('appAccounts.title')} description={t('appAccounts.description')} />
        <div className="mx-6 mt-4 flex items-start gap-3 bg-amber-50 border border-amber-200 rounded-lg p-4">
          <AlertTriangle size={18} className="text-amber-500 flex-shrink-0 mt-0.5" />
          <div className="flex-1 text-sm">
            <p className="font-semibold text-amber-800">{t('appAccounts.noMembershipTypesTitle')}</p>
            <p className="text-amber-700 mt-0.5">{t('appAccounts.noMembershipTypesDesc')}</p>
            <Link
              to="/admin/tipos-membresia"
              className="inline-block mt-1.5 text-amber-700 font-semibold underline hover:text-amber-900 transition-colors text-xs"
            >
              {t('appAccounts.noMembershipTypesLink')}
            </Link>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full">
      <PageHeader
        title={t('appAccounts.title')}
        description={t('appAccounts.description')}
      />

      <div className="px-6 py-4 border-b bg-white">
        <div className="flex items-center gap-2 text-sm flex-wrap">
          {PASOS.map((s, i) => {
            const activo = s.key === paso
            const completado = i < idxActual
            return (
              <div key={s.key} className="flex items-center gap-2">
                {i > 0 && <span className="text-slate-300">›</span>}
                <span className={
                  activo      ? 'text-orange-600 font-semibold' :
                  completado  ? 'text-green-600' :
                               'text-slate-400'
                }>
                  {completado ? '✓ ' : ''}{s.label}
                </span>
              </div>
            )
          })}
        </div>
      </div>

      <div className="flex-1 overflow-auto p-6">
        {paso === 'buscar' && (
          <BuscarPersonaStep
            onPersonaEncontrada={persona => {
              setPersonaSeleccionada(persona)
              setPaso('crear-app-usuario')
            }}
            onPersonaNoExiste={ci => {
              setCiPendiente(ci)
              setPaso('crear-persona')
            }}
          />
        )}

        {paso === 'crear-persona' && (
          <CrearPersonaStep
            ci={ciPendiente}
            onDatosPersona={payload => {
              setPersonaFormData(payload)
              setPaso('crear-app-usuario')
            }}
            onVolver={reiniciar}
          />
        )}

        {paso === 'crear-app-usuario' && (
          <CrearAppUsuarioStep
            nombre={personaSeleccionada?.nombre ?? personaFormData?.nombre ?? ''}
            correo={personaSeleccionada?.correo ?? personaFormData?.correo ?? null}
            onDatosUsuario={datos => {
              setAppUsuarioDatos(datos)
              setPaso('membresia')
            }}
            onVolver={reiniciar}
          />
        )}

        {paso === 'membresia' && appUsuarioDatos && (
          <SeleccionarMembresiaStep
            personaExistente={personaSeleccionada}
            personaFormData={personaFormData}
            appUsuarioDatos={appUsuarioDatos}
            onCompletado={reiniciar}
          />
        )}
      </div>
    </div>
  )
}
