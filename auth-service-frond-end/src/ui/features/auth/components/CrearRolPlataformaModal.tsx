import {useEffect, useState} from 'react'
import {toast} from 'sonner'
import {isAxiosError} from 'axios'
import {useTranslation} from 'react-i18next'
import {Dialog} from 'primereact/dialog'
import {InputText} from 'primereact/inputtext'
import {InputTextarea} from 'primereact/inputtextarea'
import {Dropdown} from 'primereact/dropdown'
import {Button} from 'primereact/button'
import {authRepository} from '@/infrastructure/http/auth/AuthHttpRepository'
import type {CompaniaBasica, SucursalBasica} from '@/infrastructure/http/auth/auth.dto'

interface Props {
    open: boolean
    companias: CompaniaBasica[]
    onClose: () => void
    onCreado: () => void
}

const ID_SUCURSAL_DEFAULT = 1

export function CrearRolPlataformaModal({open, companias, onClose, onCreado}: Props) {
    const { t } = useTranslation()
    const [nombre, setNombre] = useState('')
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
                if (cancelled) return
                setSucursales([])
                setIdSucursal(ID_SUCURSAL_DEFAULT)
            })
            .finally(() => {
                if (!cancelled) setLoadingSucursales(false)
            })
        return () => {
            cancelled = true
        }
    }, [idCompania])

    const resetForm = () => {
        setNombre('')
        setDescripcion('')
        setIdCompania(null)
        setIdSucursal(null)
        setSucursales([])
        setErrores({})
    }

    const handleClose = () => {
        resetForm();
        onClose()
    }

    const validate = (): boolean => {
        const e: Record<string, string> = {}
        if (!nombre.trim()) e.nombre = t('rolPlataformaModal.nameRequired')
        if (idCompania === null) e.idCompania = t('rolPlataformaModal.companyRequired')
        if (sucursales.length > 0 && idSucursal === null) e.idSucursal = t('rolPlataformaModal.branchRequired')
        setErrores(e)
        return Object.keys(e).length === 0
    }

    const handleSubmit = async () => {
        if (!validate()) return
        setLoading(true)
        try {
            await authRepository.crearRolPlataforma({
                nombre: nombre.trim(),
                descripcion: descripcion.trim() || undefined,
                id_compania: idCompania!,
                id_sucursal: idSucursal ?? ID_SUCURSAL_DEFAULT,
            })
            toast.success(t('rolPlataformaModal.createSuccess'))
            resetForm()
            onCreado()
        } catch (err) {
            if (isAxiosError(err) && err.response?.status === 409) {
                setErrores({nombre: t('rolPlataformaModal.duplicateName')})
            } else {
                toast.error(t('rolPlataformaModal.createError'))
            }
        } finally {
            setLoading(false)
        }
    }

    const footer = (
        <div className="flex justify-end gap-2">
            <Button label={t('common.cancel')} outlined onClick={handleClose} disabled={loading}/>
            <Button
                label={loading ? t('rolPlataformaModal.creating') : t('rolPlataformaModal.submit')}
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
                    <i className="pi pi-shield text-orange-500"/>
                    <span>{t('rolPlataformaModal.createTitle')}</span>
                </div>
            }
            visible={open}
            onHide={handleClose}
            style={{width: '480px', maxWidth: '95vw'}}
            footer={footer}
            modal
            draggable={false}
            resizable={false}
        >
            <div className="flex flex-col gap-4">
                <div className="flex flex-col gap-1.5">
                    <label className="text-sm font-medium">
                        {t('rolPlataformaModal.nameLabel')} <span className="text-red-400">*</span>
                    </label>
                    <InputText
                        value={nombre}
                        onChange={e => setNombre(e.target.value)}
                        placeholder="Ej: Administrador de clientes"
                        className={`w-full ${errores.nombre ? 'p-invalid' : ''}`}
                        autoFocus
                    />
                    {errores.nombre && <small className="text-red-400 text-xs">{errores.nombre}</small>}
                </div>

                <div className="flex flex-col gap-1.5">
                    <label className="text-sm font-medium">
                        {t('rolPlataformaModal.companyLabel')} <span className="text-red-400">*</span>
                    </label>
                    <Dropdown
                        value={idCompania}
                        options={companias}
                        optionLabel="nombre"
                        optionValue="id"
                        onChange={e => setIdCompania(e.value)}
                        placeholder={t('rolPlataformaModal.companyPlaceholder')}
                        className={`w-full ${errores.idCompania ? 'p-invalid' : ''}`}
                        filter
                        filterPlaceholder={t('rolPlataformaModal.companyFilter')}
                        emptyMessage={t('rolPlataformaModal.noResults')}
                    />
                    {errores.idCompania && <small className="text-red-400 text-xs">{errores.idCompania}</small>}
                </div>

                {idCompania !== null && sucursales.length > 0 && (
                    <div className="flex flex-col gap-1.5">
                        <label className="text-sm font-medium">
                            {t('rolPlataformaModal.branchLabel')} <span className="text-red-400">*</span>
                        </label>
                        <Dropdown
                            value={idSucursal}
                            options={sucursales}
                            optionLabel="nombre"
                            optionValue="id"
                            onChange={e => setIdSucursal(e.value)}
                            placeholder={loadingSucursales ? t('rolPlataformaModal.branchLoading') : t('rolPlataformaModal.branchPlaceholder')}
                            disabled={loadingSucursales}
                            className={`w-full ${errores.idSucursal ? 'p-invalid' : ''}`}
                            filter
                            filterPlaceholder={t('rolPlataformaModal.branchFilter')}
                            emptyMessage={t('rolPlataformaModal.noResults')}
                        />
                        {errores.idSucursal && <small className="text-red-400 text-xs">{errores.idSucursal}</small>}
                    </div>
                )}

                <div className="flex flex-col gap-1.5">
                    <label className="text-sm font-medium">
                        {t('rolPlataformaModal.descLabel')} <span className="text-xs font-normal opacity-50">{t('common.optional')}</span>
                    </label>
                    <InputTextarea
                        value={descripcion}
                        onChange={e => setDescripcion(e.target.value)}
                        placeholder={t('rolPlataformaModal.descPlaceholder')}
                        rows={3}
                        autoResize
                        className="w-full"
                    />
                </div>
            </div>
        </Dialog>
    )
}
