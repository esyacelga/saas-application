import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { isAxiosError } from 'axios'
import { useTranslation } from 'react-i18next'
import { Dialog } from 'primereact/dialog'
import { InputText } from 'primereact/inputtext'
import { InputTextarea } from 'primereact/inputtextarea'
import { Dropdown } from 'primereact/dropdown'
import { Button } from 'primereact/button'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { CompaniaBasica, SucursalBasica } from '@/infrastructure/http/auth/auth.dto'

interface Props {
  open: boolean
  companias: CompaniaBasica[]
  onClose: () => void
  onCreado: (idPermiso: number) => void
}

const ID_SUCURSAL_DEFAULT = 1

export function CrearPermisoModal({ open, companias, onClose, onCreado }: Props) {
  const { t } = useTranslation()
  const [nombre, setNombre] = useState('')
  const [modulo, setModulo] = useState('')
  const [descripcion, setDescripcion] = useState('')
  const [idCompania, setIdCompania] = useState<number | null>(null)
  const [idSucursal, setIdSucursal] = useState<number | null>(null)
  const [sucursales, setSucursales] = useState<SucursalBasica[]>([])
  const [loadingSucursales, setLoadingSucursales] = useState(false)
  const [loading, setLoading] = useState(false)
  const [errores, setErrores] = useState<Record<string, string>>({})

  useEffect(() => {
    if (idCompania === null) {
      setSucursales([])
      setIdSucursal(null)
      return
    }
    let cancelled = false
    setLoadingSucursales(true)
    authRepository.getSucursalesByCompania(idCompania)
      .then(data => {
        if (cancelled) return
        setSucursales(data)
        setIdSucursal(data.length === 0 ? ID_SUCURSAL_DEFAULT : null)
      })
      .catch(() => {
        if (!cancelled) {
          setSucursales([])
          setIdSucursal(ID_SUCURSAL_DEFAULT)
        }
      })
      .finally(() => { if (!cancelled) setLoadingSucursales(false) })
    return () => { cancelled = true }
  }, [idCompania])

  const resetForm = () => {
    setNombre('')
    setModulo('')
    setDescripcion('')
    setIdCompania(null)
    setIdSucursal(null)
    setSucursales([])
    setErrores({})
  }

  const handleClose = () => {
    resetForm()
    onClose()
  }

  const validate = (): boolean => {
    const e: Record<string, string> = {}
    if (!nombre.trim()) e.nombre = t('permisosPlataforma.crear.nameRequired')
    if (!modulo.trim()) e.modulo = t('permisosPlataforma.crear.moduleRequired')
    if (idCompania === null) e.idCompania = t('permisosPlataforma.crear.companyRequired')
    if (sucursales.length > 0 && idSucursal === null) e.idSucursal = t('permisosPlataforma.crear.branchRequired')
    setErrores(e)
    return Object.keys(e).length === 0
  }

  const handleSubmit = async () => {
    if (!validate()) return
    setLoading(true)
    try {
      const permiso = await authRepository.crearPermisoPlataforma({
        nombre: nombre.trim(),
        modulo: modulo.trim(),
        descripcion: descripcion.trim() || undefined,
        id_compania: idCompania!,
        id_sucursal: idSucursal ?? ID_SUCURSAL_DEFAULT,
      })
      toast.success(t('permisosPlataforma.crear.createSuccess', { name: permiso.nombre }))
      resetForm()
      onCreado(permiso.id)
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        setErrores({ nombre: t('permisosPlataforma.crear.duplicateName') })
      } else {
        toast.error(t('permisosPlataforma.crear.createError'))
      }
    } finally {
      setLoading(false)
    }
  }

  const footer = (
    <div className="flex justify-end gap-2">
      <Button label={t('common.cancel')} outlined onClick={handleClose} disabled={loading} />
      <Button
        label={loading ? t('permisosPlataforma.crear.creating') : t('permisosPlataforma.crear.submit')}
        icon={loading ? 'pi pi-spin pi-spinner' : 'pi pi-check'}
        severity="warning"
        onClick={handleSubmit}
        disabled={loading}
      />
    </div>
  )

  return (
    <Dialog
      header={
        <div className="flex items-center gap-2">
          <i className="pi pi-plus text-orange-500" />
          <span>{t('permisosPlataforma.crear.title')}</span>
        </div>
      }
      visible={open}
      onHide={handleClose}
      style={{ width: '520px', maxWidth: '95vw' }}
      footer={footer}
      modal
      draggable={false}
      resizable={false}
    >
      <div className="flex flex-col gap-4">
        <div className="flex gap-3">
          <div className="flex flex-col gap-1.5 flex-1">
            <label className="text-sm font-medium">
              {t('permisosPlataforma.crear.nameLabel')} <span className="text-red-400">*</span>
            </label>
            <InputText
              value={nombre}
              onChange={e => setNombre(e.target.value)}
              placeholder={t('permisosPlataforma.crear.namePlaceholder')}
              className={`w-full ${errores.nombre ? 'p-invalid' : ''}`}
              autoFocus
            />
            {errores.nombre && <small className="text-red-400 text-xs">{errores.nombre}</small>}
          </div>

          <div className="flex flex-col gap-1.5 w-40">
            <label className="text-sm font-medium">
              {t('permisosPlataforma.crear.moduleLabel')} <span className="text-red-400">*</span>
            </label>
            <InputText
              value={modulo}
              onChange={e => setModulo(e.target.value)}
              placeholder={t('permisosPlataforma.crear.modulePlaceholder')}
              className={`w-full ${errores.modulo ? 'p-invalid' : ''}`}
            />
            {errores.modulo && <small className="text-red-400 text-xs">{errores.modulo}</small>}
          </div>
        </div>

        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">
            {t('permisosPlataforma.crear.companyLabel')} <span className="text-red-400">*</span>
          </label>
          <Dropdown
            value={idCompania}
            options={companias}
            optionLabel="nombre"
            optionValue="id"
            onChange={e => setIdCompania(e.value)}
            placeholder={t('permisosPlataforma.crear.companyPlaceholder')}
            className={`w-full ${errores.idCompania ? 'p-invalid' : ''}`}
            filter
            filterPlaceholder={t('permisosPlataforma.crear.companyFilter')}
            emptyMessage={t('permisosPlataforma.crear.noResults')}
          />
          {errores.idCompania && <small className="text-red-400 text-xs">{errores.idCompania}</small>}
        </div>

        {idCompania !== null && sucursales.length > 0 && (
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium">
              {t('permisosPlataforma.crear.branchLabel')} <span className="text-red-400">*</span>
            </label>
            <Dropdown
              value={idSucursal}
              options={sucursales}
              optionLabel="nombre"
              optionValue="id"
              onChange={e => setIdSucursal(e.value)}
              placeholder={loadingSucursales ? t('permisosPlataforma.crear.branchLoading') : t('permisosPlataforma.crear.branchPlaceholder')}
              disabled={loadingSucursales}
              className={`w-full ${errores.idSucursal ? 'p-invalid' : ''}`}
              filter
              filterPlaceholder={t('permisosPlataforma.crear.branchFilter')}
              emptyMessage={t('permisosPlataforma.crear.noResults')}
            />
            {errores.idSucursal && <small className="text-red-400 text-xs">{errores.idSucursal}</small>}
          </div>
        )}

        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">
            {t('permisosPlataforma.crear.descLabel')}{' '}
            <span className="text-xs font-normal opacity-50">{t('common.optional')}</span>
          </label>
          <InputTextarea
            value={descripcion}
            onChange={e => setDescripcion(e.target.value)}
            placeholder={t('permisosPlataforma.crear.descPlaceholder')}
            rows={3}
            autoResize
            className="w-full"
          />
        </div>

        <p className="text-xs text-slate-400 bg-slate-50 rounded-md px-3 py-2 border border-slate-100">
          <i className="pi pi-info-circle mr-1" />
          {t('permisosPlataforma.crear.autoAssignHint')}
        </p>
      </div>
    </Dialog>
  )
}
