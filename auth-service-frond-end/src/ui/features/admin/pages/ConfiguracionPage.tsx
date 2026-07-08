import { useState, useEffect, useRef, useCallback } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { isAxiosError } from 'axios'
import {
  Building2, MapPin, QrCode, Upload, Copy, Check,
  RefreshCw, Camera, Settings,
} from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { QRCodeSVG } from 'qrcode.react'
import { PageHeader } from '@/ui/components/PageHeader'
import { ConfirmDialog } from '@/ui/components/ConfirmDialog'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import type { Compania, Sucursal } from '@/domain/platform/entities/Plan.entity'
import { cn } from '@/lib/utils'

// ── Schemas ───────────────────────────────────────────────────────────────────

const empresaSchema = z.object({
  nombre:   z.string().min(1, 'required'),
  telefono: z.string().optional(),
  whatsapp: z.string().optional(),
  correo:   z.string().email('emailInvalid').optional().or(z.literal('')),
})

const sucursalSchema = z.object({
  nombre:    z.string().min(1, 'required'),
  direccion: z.string().optional(),
})

type EmpresaForm   = z.infer<typeof empresaSchema>
type SucursalForm  = z.infer<typeof sucursalSchema>
type Tab = 'empresa' | 'sucursal'

// ── Subcomponentes ────────────────────────────────────────────────────────────

function FieldLabel({ label, required }: { label: string; required?: boolean }) {
  return (
    <label className="block text-xs font-medium mb-1" style={{ color: 'var(--page-muted)' }}>
      {label}{required && <span className="text-red-400 ml-0.5">*</span>}
    </label>
  )
}

function TextInput({ error, ...props }: React.InputHTMLAttributes<HTMLInputElement> & { error?: string }) {
  return (
    <div>
      <input
        {...props}
        className={cn(
          'w-full px-3 py-2 text-sm rounded-lg focus:outline-none focus:ring-2 focus:ring-orange-500 transition-colors',
          props.readOnly && 'opacity-60 cursor-not-allowed',
        )}
        style={{
          background: 'var(--input-bg)',
          border: `1px solid ${error ? '#f87171' : 'var(--input-border)'}`,
          color: 'var(--input-text)',
        }}
      />
      {error && <p className="text-xs text-red-400 mt-1">{error}</p>}
    </div>
  )
}

function SectionCard({ title, icon, children }: { title: string; icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="rounded-xl p-5" style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
      <div className="flex items-center gap-2 mb-4">
        <span style={{ color: '#f97316' }}>{icon}</span>
        <h3 className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>{title}</h3>
      </div>
      {children}
    </div>
  )
}

// ── Tab: Mi Empresa ───────────────────────────────────────────────────────────

function EmpresaTab({ empresa, onUpdate }: { empresa: Compania; onUpdate: (c: Compania) => void }) {
  const { t } = useTranslation()
  const fileRef = useRef<HTMLInputElement>(null)
  const [uploadingLogo, setUploadingLogo] = useState(false)
  const [logoPreview, setLogoPreview] = useState<string | null>(empresa.logoUrl)

  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<EmpresaForm>({
    resolver: zodResolver(empresaSchema),
    defaultValues: {
      nombre:   empresa.nombre,
      telefono: empresa.telefono,
      whatsapp: empresa.whatsapp,
      correo:   empresa.correo,
    },
  })

  const handleLogoChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    if (file.size > 5 * 1024 * 1024) {
      toast.error(t('configuracion.logoSizeError'))
      return
    }
    setLogoPreview(URL.createObjectURL(file))
    setUploadingLogo(true)
    try {
      const updated = await platformRepository.subirLogoEmpresa(file)
      onUpdate(updated)
      toast.success(t('configuracion.logoSuccess'))
    } catch {
      toast.error(t('configuracion.logoError'))
      setLogoPreview(empresa.logoUrl)
    } finally {
      setUploadingLogo(false)
      e.target.value = ''
    }
  }

  const onSubmit = async (values: EmpresaForm) => {
    try {
      const updated = await platformRepository.actualizarMiEmpresa(values)
      onUpdate(updated)
      toast.success(t('configuracion.empresaSuccess'))
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        toast.error(t('configuracion.empresaConflict'))
      } else {
        toast.error(t('configuracion.empresaError'))
      }
    }
  }

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      {/* Logo */}
      <SectionCard title={t('configuracion.logoTitle')} icon={<Camera size={16} />}>
        <div className="flex flex-col items-center gap-4 py-2">
          <div
            className="relative w-28 h-28 rounded-2xl overflow-hidden flex items-center justify-center flex-shrink-0"
            style={{ background: 'var(--page-border)' }}
          >
            {logoPreview ? (
              <img src={logoPreview} alt="logo" className="w-full h-full object-cover" />
            ) : (
              <span className="text-4xl font-bold" style={{ color: 'var(--page-muted)' }}>
                {empresa.nombre?.[0]?.toUpperCase() ?? 'G'}
              </span>
            )}
            {uploadingLogo && (
              <div className="absolute inset-0 flex items-center justify-center" style={{ background: 'rgba(0,0,0,0.5)' }}>
                <RefreshCw size={20} className="text-white animate-spin" />
              </div>
            )}
          </div>

          <div className="text-center">
            <button
              type="button"
              onClick={() => fileRef.current?.click()}
              disabled={uploadingLogo}
              className="flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-medium transition-colors disabled:opacity-50"
              style={{ background: 'rgba(249,115,22,0.12)', color: '#f97316', border: '1px solid rgba(249,115,22,0.3)' }}
            >
              <Upload size={13} />
              {uploadingLogo ? t('configuracion.logoUploading') : t('configuracion.logoUpload')}
            </button>
            <p className="text-xs mt-2" style={{ color: 'var(--page-muted)' }}>{t('configuracion.logoHint')}</p>
          </div>

          <input
            ref={fileRef}
            type="file"
            accept="image/png,image/jpeg,image/webp"
            className="hidden"
            onChange={handleLogoChange}
          />
        </div>
      </SectionCard>

      {/* Datos empresa */}
      <SectionCard title={t('configuracion.datosTitle')} icon={<Building2 size={16} />}>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-3">
          <div>
            <FieldLabel label={t('configuracion.fieldNombre')} required />
            <TextInput {...register('nombre')} error={errors.nombre && t(`configuracion.${errors.nombre.message}`)} />
          </div>

          <div>
            <FieldLabel label={t('configuracion.fieldRuc')} />
            <TextInput value={empresa.ruc} readOnly />
          </div>

          <div>
            <FieldLabel label={t('configuracion.fieldTelefono')} />
            <TextInput {...register('telefono')} type="tel" />
          </div>

          <div>
            <FieldLabel label={t('configuracion.fieldWhatsapp')} />
            <TextInput {...register('whatsapp')} type="tel" placeholder="+593..." />
          </div>

          <div>
            <FieldLabel label={t('configuracion.fieldCorreo')} />
            <TextInput
              {...register('correo')}
              type="email"
              error={errors.correo && t(`configuracion.${errors.correo.message}`)}
            />
          </div>

          <div className="pt-1 flex justify-end">
            <button
              type="submit"
              disabled={isSubmitting}
              className="px-4 py-2 rounded-lg text-xs font-semibold text-white transition-colors disabled:opacity-60"
              style={{ background: isSubmitting ? '#9ca3af' : '#f97316' }}
            >
              {isSubmitting ? t('configuracion.guardando') : t('configuracion.guardar')}
            </button>
          </div>
        </form>
      </SectionCard>
    </div>
  )
}

// ── Tab: Mi Sucursal ──────────────────────────────────────────────────────────

function SucursalTab({ sucursal, onUpdate }: { sucursal: Sucursal; onUpdate: (s: Sucursal) => void }) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)
  const [confirmRenovar, setConfirmRenovar] = useState(false)
  const [renovando, setRenovando] = useState(false)

  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<SucursalForm>({
    resolver: zodResolver(sucursalSchema),
    defaultValues: { nombre: sucursal.nombre, direccion: sucursal.direccion },
  })

  const onSubmit = async (values: SucursalForm) => {
    try {
      const updated = await platformRepository.actualizarMiSucursal(values)
      onUpdate(updated)
      toast.success(t('configuracion.sucursalSuccess'))
    } catch {
      toast.error(t('configuracion.sucursalError'))
    }
  }

  const handleCopy = () => {
    navigator.clipboard.writeText(qrUrl)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const handleRenovar = async () => {
    setRenovando(true)
    try {
      const updated = await platformRepository.renovarMiQr()
      onUpdate(updated)
      toast.success(t('configuracion.qrRenovarSuccess'))
    } catch {
      toast.error(t('configuracion.qrRenovarError'))
    } finally {
      setRenovando(false)
      setConfirmRenovar(false)
    }
  }

  const expiraTexto = sucursal.qrTokenExpira
    ? new Date(sucursal.qrTokenExpira).toLocaleDateString()
    : t('configuracion.qrNoExpira')

  const qrUrl = sucursal.qrToken
    ? `${import.meta.env.VITE_CLIENT_APP_URL}/login?qr=${sucursal.qrToken}`
    : ''

  const urlCorta = qrUrl
    ? `${qrUrl.slice(0, 30)}...${qrUrl.slice(-10)}`
    : '—'

  return (
    <>
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Datos sucursal */}
        <SectionCard title={t('configuracion.sucursalDatos')} icon={<MapPin size={16} />}>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-3">
            <div>
              <FieldLabel label={t('configuracion.sucursalNombre')} required />
              <TextInput {...register('nombre')} error={errors.nombre && t(`configuracion.${errors.nombre.message}`)} />
            </div>

            <div>
              <FieldLabel label={t('configuracion.sucursalDireccion')} />
              <TextInput {...register('direccion')} />
            </div>

            <div>
              <FieldLabel label={t('configuracion.sucursalTipo')} />
              <span
                className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium"
                style={{
                  background: sucursal.esPrincipal ? 'rgba(249,115,22,0.12)' : 'rgba(148,163,184,0.12)',
                  color: sucursal.esPrincipal ? '#f97316' : 'var(--page-muted)',
                }}
              >
                <span className="w-1.5 h-1.5 rounded-full" style={{ background: 'currentColor' }} />
                {sucursal.esPrincipal ? t('configuracion.sucursalPrincipal') : t('configuracion.sucursalSecundaria')}
              </span>
            </div>

            <div className="pt-1 flex justify-end">
              <button
                type="submit"
                disabled={isSubmitting}
                className="px-4 py-2 rounded-lg text-xs font-semibold text-white transition-colors disabled:opacity-60"
                style={{ background: isSubmitting ? '#9ca3af' : '#f97316' }}
              >
                {isSubmitting ? t('configuracion.guardando') : t('configuracion.guardar')}
              </button>
            </div>
          </form>
        </SectionCard>

        {/* QR Code */}
        <SectionCard title={t('configuracion.qrTitle')} icon={<QrCode size={16} />}>
          <div className="flex flex-col items-center gap-4">
            <p className="text-xs text-center" style={{ color: 'var(--page-muted)' }}>
              {t('configuracion.qrDescription')}
            </p>

            {sucursal.qrToken ? (
              <div
                className="p-3 rounded-xl"
                style={{ background: '#ffffff' }}
              >
                <QRCodeSVG
                  value={`${import.meta.env.VITE_CLIENT_APP_URL}/login?qr=${sucursal.qrToken}`}
                  size={160}
                  bgColor="#ffffff"
                  fgColor="#0f172a"
                  level="M"
                />
              </div>
            ) : (
              <div
                className="w-44 h-44 rounded-xl flex items-center justify-center"
                style={{ background: 'var(--page-border)' }}
              >
                <QrCode size={40} style={{ color: 'var(--page-muted)' }} />
              </div>
            )}

            {/* Token */}
            <div className="w-full space-y-2">
              <div
                className="flex items-center justify-between gap-2 px-3 py-2 rounded-lg"
                style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)' }}
              >
                <span className="text-xs font-mono truncate" style={{ color: 'var(--page-muted)' }}>
                  {urlCorta}
                </span>
                <button
                  type="button"
                  onClick={handleCopy}
                  className="flex-shrink-0 p-1 rounded transition-colors"
                  style={{ color: copied ? '#22c55e' : 'var(--page-muted)' }}
                  title={t('configuracion.qrCopy')}
                >
                  {copied ? <Check size={14} /> : <Copy size={14} />}
                </button>
              </div>

              <div className="flex items-center justify-between text-xs" style={{ color: 'var(--page-muted)' }}>
                <span>{t('configuracion.qrExpira')}: <strong>{expiraTexto}</strong></span>
              </div>

              <button
                type="button"
                onClick={() => setConfirmRenovar(true)}
                disabled={renovando}
                className="flex items-center justify-center gap-2 w-full px-3 py-2 rounded-lg text-xs font-medium transition-colors disabled:opacity-50"
                style={{ border: '1px solid var(--page-border)', color: 'var(--page-muted)' }}
              >
                <RefreshCw size={12} className={renovando ? 'animate-spin' : ''} />
                {renovando ? t('configuracion.qrRenovando') : t('configuracion.qrRenovar')}
              </button>
            </div>
          </div>
        </SectionCard>
      </div>

      <ConfirmDialog
        open={confirmRenovar}
        title={t('configuracion.qrRenovarConfirmTitle')}
        description={t('configuracion.qrRenovarConfirmDesc')}
        onConfirm={handleRenovar}
        onCancel={() => setConfirmRenovar(false)}
        destructive
      />
    </>
  )
}

// ── Página principal ──────────────────────────────────────────────────────────

export function ConfiguracionPage() {
  const { t } = useTranslation()
  const [activeTab, setActiveTab] = useState<Tab>('empresa')
  const [empresa, setEmpresa] = useState<Compania | null>(null)
  const [sucursal, setSucursal] = useState<Sucursal | null>(null)
  const [loading, setLoading] = useState(true)

  const cargar = useCallback(async () => {
    setLoading(true)
    try {
      const [emp, suc] = await Promise.all([
        platformRepository.getMiEmpresa(),
        platformRepository.getMiSucursal(),
      ])
      setEmpresa(emp)
      setSucursal(suc)
    } catch {
      toast.error(t('configuracion.loadError'))
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => { cargar() }, [cargar])

  const TABS: { key: Tab; label: string; icon: React.ReactNode }[] = [
    { key: 'empresa',   label: t('configuracion.tabEmpresa'),  icon: <Building2 size={14} /> },
    { key: 'sucursal',  label: t('configuracion.tabSucursal'), icon: <MapPin size={14} /> },
  ]

  const tabStyle = (key: Tab) => ({
    background: activeTab === key ? 'var(--page-bg)' : 'transparent',
    color: activeTab === key ? 'var(--page-text)' : 'var(--page-muted)',
    borderBottom: activeTab === key ? '2px solid #f97316' : '2px solid transparent',
  })

  return (
    <div className="flex flex-col h-full" style={{ color: 'var(--page-text)' }}>
      <PageHeader
        title={t('configuracion.title')}
        description={t('configuracion.description')}
        action={<Settings size={18} style={{ color: 'var(--page-muted)' }} />}
      />

      {/* Tabs */}
      <div
        className="flex items-end gap-1 px-6 pt-3 flex-shrink-0"
        style={{ borderBottom: '1px solid var(--page-border)', background: 'var(--page-surface)' }}
      >
        {TABS.map(tab => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className="flex items-center gap-2 px-4 py-2.5 text-xs font-medium transition-colors rounded-t-lg"
            style={tabStyle(tab.key)}
          >
            {tab.icon}
            {tab.label}
          </button>
        ))}
      </div>

      {/* Content */}
      <div className="flex-1 overflow-auto p-4">
        {loading ? (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {[1, 2].map(i => (
              <div
                key={i}
                className="rounded-xl p-5 h-64 animate-pulse"
                style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}
              />
            ))}
          </div>
        ) : (
          <>
            {activeTab === 'empresa' && empresa && (
              <EmpresaTab empresa={empresa} onUpdate={setEmpresa} />
            )}
            {activeTab === 'sucursal' && sucursal && (
              <SucursalTab sucursal={sucursal} onUpdate={setSucursal} />
            )}
          </>
        )}
      </div>
    </div>
  )
}
