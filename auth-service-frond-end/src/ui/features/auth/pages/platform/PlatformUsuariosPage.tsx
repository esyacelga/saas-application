import {useCallback, useEffect, useState} from 'react'
import {format} from 'date-fns'
import {es} from 'date-fns/locale'
import {toast} from 'sonner'
import {isAxiosError} from 'axios'
import {UserCog, UserCircle2} from 'lucide-react'
import {useTranslation} from 'react-i18next'
import {Button} from 'primereact/button'
import {ConfirmDialog} from '@/ui/components/ConfirmDialog'
import {PageHeader} from '@/ui/components/PageHeader'
import {useCurrentUser} from '@/infrastructure/store/auth/auth.store'
import {authRepository} from '@/infrastructure/http/auth/AuthHttpRepository'
import type {OperadorPlataforma} from '@/infrastructure/http/auth/auth.dto'
import type {JwtPayloadPlataforma} from '@/domain/auth/entities/User.entity'
import {CrearOperadorModal} from '../../components/CrearOperadorModal'
import {EditarOperadorModal} from '../../components/EditarOperadorModal'

const ROLE_LABEL: Record<string, string> = {
    super_admin: 'Super Admin',
    soporte: 'Soporte',
    viewer: 'Viewer',
}

const ROLE_STYLE: Record<string, { background: string; color: string; border: string }> = {
    super_admin: {background: 'rgba(249,115,22,0.15)', color: '#f97316', border: '1px solid rgba(249,115,22,0.3)'},
    soporte: {background: 'rgba(59,130,246,0.15)', color: '#60a5fa', border: '1px solid rgba(59,130,246,0.3)'},
    viewer: {background: 'rgba(100,116,139,0.15)', color: '#94a3b8', border: '1px solid rgba(100,116,139,0.3)'},
}

function AvatarCell({ src, nombre }: { src: string | null; nombre: string }) {
    if (src) {
        return (
            <div className="w-[38px] h-[38px] rounded-full flex-shrink-0 overflow-hidden"
                 style={{ border: '2px solid var(--page-border)', boxShadow: '0 0 0 2px var(--page-surface)' }}>
                <img src={src} alt={nombre} className="w-full h-full object-cover rounded-full" />
            </div>
        )
    }
    return <UserCircle2 size={34} style={{ color: 'var(--page-muted)' }} />
}

function TableSkeleton() {
    return (
        <div className="space-y-0">
            {Array.from({length: 3}).map((_, i) => (
                <div key={i} className="flex gap-4 px-6 py-3 animate-pulse"
                     style={{borderBottom: '1px solid var(--page-border)'}}>
                    <div className="h-3 rounded w-1/4" style={{background: 'var(--page-border)'}}/>
                    <div className="h-3 rounded w-1/3" style={{background: 'var(--page-border)'}}/>
                    <div className="h-3 rounded w-20" style={{background: 'var(--page-border)'}}/>
                    <div className="h-3 rounded w-16" style={{background: 'var(--page-border)'}}/>
                    <div className="h-3 rounded w-24" style={{background: 'var(--page-border)'}}/>
                </div>
            ))}
        </div>
    )
}

export function PlatformUsuariosPage() {
    const {t} = useTranslation()
    const rawUser = useCurrentUser()
    const currentUser = rawUser?.tipo === 'plataforma' ? (rawUser as JwtPayloadPlataforma) : null
    const esSuperAdmin = currentUser?.rol_plataforma === 'super_admin'

    const [operadores, setOperadores] = useState<OperadorPlataforma[]>([])
    const [loading, setLoading] = useState(true)
    const [crearOpen, setCrearOpen] = useState(false)
    const [editarOperador, setEditarOperador] = useState<OperadorPlataforma | null>(null)
    const [confirmDesactivar, setConfirmDesactivar] = useState<OperadorPlataforma | null>(null)

    const cargar = useCallback(async () => {
        setLoading(true)
        try {
            const data = await authRepository.getOperadoresPlataforma()
            setOperadores(data)
        } catch {
            toast.error(t('operators.toastLoadError'))
        } finally {
            setLoading(false)
        }
    }, [t])

    useEffect(() => {
        cargar()
    }, [cargar])

    const handleDesactivar = async (op: OperadorPlataforma) => {
        try {
            await authRepository.desactivarOperadorPlataforma(op.id)
            toast.success(t('operators.toastDeactivated', {name: op.nombre}))
            cargar()
        } catch (err) {
            if (isAxiosError(err) && err.response?.status === 409) {
                toast.error(t('operators.toastLastAdminError'))
            } else {
                toast.error(t('operators.toastDeactivateError'))
            }
        } finally {
            setConfirmDesactivar(null)
        }
    }

    const handleActualizado = (updated: OperadorPlataforma) => {
        setOperadores(prev => prev.map(op => op.id === updated.id ? updated : op))
        if (editarOperador?.id === updated.id) setEditarOperador(updated)
    }

    const formatFecha = (fecha: string | null) => {
        if (!fecha) return '—'
        try {
            return format(new Date(fecha), 'd MMM, HH:mm', {locale: es})
        } catch {
            return '—'
        }
    }

    const TABLE_HEADERS = [
        '',
        t('operators.colName'),
        t('operators.colEmail'),
        t('operators.colRole'),
        t('operators.colStatus'),
        t('operators.colLastAccess'),
        t('operators.colActions'),
    ]

    return (
        <div className="flex flex-col h-full" style={{color: 'var(--page-text)'}}>
            <PageHeader
                title={t('operators.title')}
                description={t('operators.subtitle')}
                action={
                    esSuperAdmin ? (
                        <Button
                            label={t('operators.newOperator')}
                            icon="pi pi-user-plus"
                            severity="warning"
                            size="small"
                            onClick={() => setCrearOpen(true)}
                        />
                    ) : undefined
                }
            />

            {/* Stats bar */}
            {!loading && operadores.length > 0 && (
                <div className="flex items-center gap-6 px-6 py-3 flex-shrink-0"
                     style={{borderBottom: '1px solid var(--page-border)'}}>
                    <div className="flex items-center gap-2">
                        <span className="text-2xl font-bold"
                              style={{color: 'var(--page-text)'}}>{operadores.length}</span>
                        <span className="text-xs" style={{color: 'var(--page-muted)'}}>operadores totales</span>
                    </div>
                    <div className="h-4 w-px" style={{background: 'var(--page-border)'}}/>
                    <div className="flex items-center gap-2">
                        <span className="text-2xl font-bold"
                              style={{color: 'var(--page-text)'}}>{operadores.filter(o => o.activo).length}</span>
                        <span className="text-xs" style={{color: 'var(--page-muted)'}}>activos</span>
                    </div>
                    <div className="h-4 w-px" style={{background: 'var(--page-border)'}}/>
                    <div className="flex items-center gap-2">
                        <span className="text-2xl font-bold"
                              style={{color: 'var(--page-text)'}}>{operadores.filter(o => o.rol_plataforma === 'super_admin').length}</span>
                        <span className="text-xs" style={{color: 'var(--page-muted)'}}>super admins</span>
                    </div>
                </div>
            )}

            <div className="p-6 flex flex-col flex-1 overflow-auto">
                {loading ? (
                    <TableSkeleton/>
                ) : operadores.length === 0 ? (
                    <div className="flex flex-col items-center justify-center py-16 text-center gap-3">
                        <div className="w-16 h-16 rounded-full flex items-center justify-center"
                             style={{background: 'var(--page-surface)'}}>
                            <UserCog size={28} style={{color: 'var(--page-muted)'}}/>
                        </div>
                        <p className="text-sm font-medium"
                           style={{color: 'var(--page-muted)'}}>{t('operators.noOperators')}</p>
                    </div>
                ) : (
                    <div className="rounded-lg overflow-hidden" style={{border: '1px solid var(--page-border)'}}>
                        <table className="w-full table-dense">
                            <thead>
                            <tr style={{background: 'var(--page-surface)', borderBottom: '1px solid var(--page-border)'}}>
                                {TABLE_HEADERS.map(h => (
                                    <th key={h} className="text-left uppercase whitespace-nowrap"
                                        style={{color: 'var(--page-muted)'}}>
                                        {h}
                                    </th>
                                ))}
                            </tr>
                            </thead>
                            <tbody>
                            {operadores.map((op, i) => (
                                <tr key={op.id} style={{
                                    background: i % 2 === 0 ? 'var(--page-surface)' : 'var(--page-bg)',
                                    borderBottom: '1px solid var(--page-border)',
                                }}>
                                    <td><AvatarCell src={op.foto_url} nombre={op.nombre} /></td>
                                    <td className="font-medium whitespace-nowrap" style={{color: 'var(--page-text)'}}>
                                        {op.nombre}
                                    </td>
                                    <td className="font-mono whitespace-nowrap"
                                        style={{color: 'var(--page-muted)', fontSize: '0.6rem'}}>
                                        {op.correo}
                                    </td>
                                    <td className="whitespace-nowrap">
                                        <span className="px-1.5 py-0.5 rounded font-semibold"
                                              style={{fontSize: '0.55rem', ...(ROLE_STYLE[op.rol_plataforma] ?? ROLE_STYLE.viewer)}}>
                                            {ROLE_LABEL[op.rol_plataforma] ?? op.rol_plataforma}
                                        </span>
                                    </td>
                                    <td className="whitespace-nowrap">
                                        <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded font-semibold"
                                              style={{
                                                  fontSize: '0.55rem',
                                                  background: op.activo ? 'rgba(34,197,94,0.15)' : 'rgba(239,68,68,0.15)',
                                                  color: op.activo ? '#16a34a' : '#ef4444',
                                                  border: `1px solid ${op.activo ? 'rgba(34,197,94,0.3)' : 'rgba(239,68,68,0.3)'}`,
                                              }}>
                                            <span className="w-1 h-1 rounded-full" style={{background: op.activo ? '#16a34a' : '#ef4444'}}/>
                                            {op.activo ? t('common.active') : t('common.inactive')}
                                        </span>
                                    </td>
                                    <td className="whitespace-nowrap font-mono"
                                        style={{color: 'var(--page-muted)', fontSize: '0.6rem'}}>
                                        {formatFecha(op.ultimo_acceso)}
                                    </td>
                                    <td className="whitespace-nowrap">
                                        <div className="flex items-center gap-0.5">
                                            {esSuperAdmin && (
                                                <Button
                                                    icon="pi pi-pencil"
                                                    text
                                                    severity="secondary"
                                                    tooltip={t('operators.edit')}
                                                    tooltipOptions={{position: 'top'}}
                                                    onClick={() => setEditarOperador(op)}
                                                    pt={{root: {className: '!py-0.5 !px-1'}}}
                                                />
                                            )}
                                            {esSuperAdmin && op.activo && (
                                                <Button
                                                    icon="pi pi-ban"
                                                    text
                                                    severity="danger"
                                                    tooltip={t('operators.deactivate')}
                                                    tooltipOptions={{position: 'top'}}
                                                    onClick={() => setConfirmDesactivar(op)}
                                                    pt={{root: {className: '!py-0.5 !px-1'}}}
                                                />
                                            )}
                                        </div>
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>

            <CrearOperadorModal
                open={crearOpen}
                onClose={() => setCrearOpen(false)}
                onCreado={() => {
                    setCrearOpen(false);
                    cargar()
                }}
            />

            {editarOperador && (
                <EditarOperadorModal
                    open={editarOperador !== null}
                    operador={editarOperador}
                    onClose={() => setEditarOperador(null)}
                    onActualizado={handleActualizado}
                />
            )}

            <ConfirmDialog
                open={confirmDesactivar !== null}
                title={t('operators.confirmDeactivateTitle')}
                description={t('operators.confirmDeactivateDesc', {name: confirmDesactivar?.nombre})}
                onConfirm={() => confirmDesactivar && handleDesactivar(confirmDesactivar)}
                onCancel={() => setConfirmDesactivar(null)}
                destructive
            />
        </div>
    )
}
