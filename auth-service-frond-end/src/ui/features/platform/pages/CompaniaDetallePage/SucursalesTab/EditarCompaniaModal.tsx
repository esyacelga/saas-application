import { useEffect, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2, Building2, Upload, RefreshCw } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { GestionarCompaniaUseCase } from '@/application/platform/GestionarCompania.usecase'
import type { Compania } from '@/domain/platform/entities/Plan.entity'
import { PhoneInputE164Controller, parsePhoneToE164 } from '@/ui/components/PhoneInputE164'

const usecase = new GestionarCompaniaUseCase(platformRepository)

const schema = z.object({
  nombre: z.string().min(2, 'Nombre requerido'),
  /* Campo telefono oculto — se conserva en el schema y DTO por compatibilidad */
  telefono: z.string().optional(),
  whatsapp: z.string()
    .optional()
    .refine((v) => !v || /^\+[1-9]\d{6,14}$/.test(v), 'Número de WhatsApp inválido'),
  correo: z.string().email('Correo no válido').optional().or(z.literal('')),
})
type Form = z.infer<typeof schema>

interface Props {
  open: boolean
  compania: Compania | null
  onClose: () => void
  onUpdated: (c: Compania) => void
}

export function EditarCompaniaModal({ open, compania, onClose, onUpdated }: Props) {
  const { t } = useTranslation()
  const { register, handleSubmit, reset, control, formState: { errors, isSubmitting } } = useForm<Form>({
    resolver: zodResolver(schema),
  })

  const fileRef = useRef<HTMLInputElement>(null)
  const [logoPreview, setLogoPreview] = useState<string | null>(null)
  const [uploadingLogo, setUploadingLogo] = useState(false)
  const [whatsappLegacy, setWhatsappLegacy] = useState(false)

  useEffect(() => {
    if (compania) {
      const whatsappParsed = parsePhoneToE164(compania.whatsapp)
      setWhatsappLegacy(!whatsappParsed.parsedOk)
      reset({
        nombre: compania.nombre,
        telefono: compania.telefono,
        whatsapp: whatsappParsed.value,
        correo: compania.correo,
      })
      setLogoPreview(compania.logoUrl)
    }
  }, [compania, reset])

  const handleLogoChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file || !compania) return
    if (file.size > 5 * 1024 * 1024) {
      toast.error('El archivo no debe superar los 5 MB')
      return
    }
    setLogoPreview(URL.createObjectURL(file))
    setUploadingLogo(true)
    try {
      const updated = await platformRepository.subirLogoCompania(compania.id, file)
      onUpdated(updated)
      setLogoPreview(updated.logoUrl)
      toast.success('Logo actualizado')
    } catch {
      toast.error('Error al subir el logo')
      setLogoPreview(compania.logoUrl)
    } finally {
      setUploadingLogo(false)
      e.target.value = ''
    }
  }

  const onSubmit = async (values: Form) => {
    if (!compania) return
    try {
      const updated = await usecase.actualizarCompania(compania.id, {
        nombre: values.nombre,
        telefono: values.telefono || undefined,
        whatsapp: values.whatsapp || undefined,
        correo: values.correo || undefined,
      })
      toast.success('Datos actualizados')
      onUpdated(updated)
      onClose()
    } catch {
      toast.error('Error al actualizar los datos')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader><DialogTitle>Editar datos del gimnasio</DialogTitle></DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">

          {/* Logo upload */}
          <div className="flex items-center gap-4">
            <div
              className="relative w-16 h-16 rounded-xl overflow-hidden flex items-center justify-center flex-shrink-0"
              style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}
            >
              {logoPreview
                ? <img src={logoPreview} alt="logo" className="w-full h-full object-cover" onError={e => { (e.target as HTMLImageElement).style.display = 'none' }} />
                : <Building2 size={20} style={{ color: 'var(--page-muted)' }} />
              }
              {uploadingLogo && (
                <div className="absolute inset-0 flex items-center justify-center" style={{ background: 'rgba(0,0,0,0.5)' }}>
                  <RefreshCw size={14} className="text-white animate-spin" />
                </div>
              )}
            </div>
            <div>
              <button
                type="button"
                onClick={() => fileRef.current?.click()}
                disabled={uploadingLogo}
                className="flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors disabled:opacity-50"
                style={{ background: 'rgba(249,115,22,0.12)', color: '#f97316', border: '1px solid rgba(249,115,22,0.3)' }}
              >
                <Upload size={12} />
                {uploadingLogo ? 'Subiendo...' : logoPreview ? 'Cambiar logo' : 'Subir logo'}
              </button>
              <p className="text-xs mt-1" style={{ color: 'var(--page-muted)' }}>PNG, JPG o WebP · máx. 5 MB</p>
            </div>
            <input
              ref={fileRef}
              type="file"
              accept="image/png,image/jpeg,image/webp"
              className="hidden"
              onChange={handleLogoChange}
            />
          </div>

          <div>
            <Label>Nombre</Label>
            <Input {...register('nombre')} className="mt-1" />
            {errors.nombre && <p className="text-xs text-red-500 mt-1">{errors.nombre.message}</p>}
          </div>
          {/* Campo telefono oculto — se conserva en el schema y DTO por compatibilidad */}
          <div>
            <Label>WhatsApp</Label>
            <PhoneInputE164Controller
              name="whatsapp"
              control={control}
              defaultCountry="EC"
              placeholder={t('phoneInput.placeholder')}
            />
            {whatsappLegacy && (
              <p className="text-xs text-amber-500 mt-1">{t('phoneInput.legacyFormat')}</p>
            )}
            {errors.whatsapp && (
              <p className="text-xs text-red-500 mt-1">{t('phoneInput.invalid')}</p>
            )}
          </div>
          <div>
            <Label>Correo</Label>
            <Input {...register('correo')} type="email" className="mt-1" />
            {errors.correo && <p className="text-xs text-red-500 mt-1">{errors.correo.message}</p>}
          </div>
          <p className="text-xs text-slate-400">El RUC no es editable.</p>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
            <Button type="submit" disabled={isSubmitting || uploadingLogo}>
              {isSubmitting && <Loader2 size={14} className="animate-spin mr-2" />}
              Guardar cambios
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
