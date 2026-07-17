import {useEffect, useState} from 'react'
import {useForm} from 'react-hook-form'
import {zodResolver} from '@hookform/resolvers/zod'
import {z} from 'zod'
import {Link, useNavigate, useSearchParams} from 'react-router-dom'
import {toast} from 'sonner'
import {GoogleLogin} from '@react-oauth/google'
import {useTranslation} from 'react-i18next'
import {authRepository} from '@/infrastructure/http/AuthHttpRepository'
import {coreRepository} from '@/infrastructure/http/CoreHttpRepository'
import {useAuthStore} from '@/infrastructure/store/auth.store'
import {useThemeStore} from '@/infrastructure/store/theme.store'
import {LangToggle} from '@/ui/components/LangToggle'
import {CompletarRegistroOAuth} from './CompletarRegistroOAuth'

// ── Schemas ──────────────────────────────────────────────────────────────────

const loginSchema = z.object({
    login: z.string().min(1, 'Requerido'),
    password: z.string().min(1, 'Requerido'),
    id_compania: z.number().optional(),
})
type LoginData = z.infer<typeof loginSchema>

const registerSchema = z
    .object({
        nombre: z.string().min(2, 'Mínimo 2 caracteres'),
        correo: z.string().email('Correo inválido'),
        password: z.string().min(8, 'Mínimo 8 caracteres'),
        confirmPassword: z.string().min(1, 'Requerido'),
    })
    .refine((d) => d.password === d.confirmPassword, {
        message: 'Las contraseñas no coinciden',
        path: ['confirmPassword'],
    })
type RegisterData = z.infer<typeof registerSchema>

type Mode = 'login' | 'register'

type AuthStep =
    | { step: 'form' }
    | { step: 'completar_oauth'; provider: 'google' | 'facebook'; token: string; email: string; nombre: string }

// ── Inline icon components ───────────────────────────────────────────────────

function IcoEnvelope() {
    return (
        <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round"
                  d="M21.75 6.75v10.5a2.25 2.25 0 01-2.25 2.25h-15a2.25 2.25 0 01-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25m19.5 0v.243a2.25 2.25 0 01-1.07 1.916l-7.5 4.615a2.25 2.25 0 01-2.36 0L3.32 8.91a2.25 2.25 0 01-1.07-1.916V6.75"/>
        </svg>
    )
}

function IcoUser() {
    return (
        <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round"
                  d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z"/>
        </svg>
    )
}

function IcoLock() {
    return (
        <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round"
                  d="M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z"/>
        </svg>
    )
}

function IcoBuilding() {
    return (
        <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round"
                  d="M3.75 21h16.5M4.5 3h15M5.25 3v18m13.5-18v18M9 6.75h1.5m-1.5 3h1.5m-1.5 3h1.5m3-6H15m-1.5 3H15m-1.5 3H15M9 21v-3.375c0-.621.504-1.125 1.125-1.125h3.75c.621 0 1.125.504 1.125 1.125V21"/>
        </svg>
    )
}

function IcoEye({show}: { show: boolean }) {
    return show ? (
        <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round"
                  d="M3.98 8.223A10.477 10.477 0 001.934 12C3.226 16.338 7.244 19.5 12 19.5c.993 0 1.953-.138 2.863-.395M6.228 6.228A10.45 10.45 0 0112 4.5c4.756 0 8.773 3.162 10.065 7.498a10.523 10.523 0 01-4.293 5.774M6.228 6.228L3 3m3.228 3.228l3.65 3.65m7.894 7.894L21 21m-3.228-3.228l-3.65-3.65m0 0a3 3 0 10-4.243-4.243m4.242 4.242L9.88 9.88"/>
        </svg>
    ) : (
        <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round"
                  d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z"/>
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
        </svg>
    )
}

function IcoSpinner() {
    return (
        <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/>
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
        </svg>
    )
}

// ── Background floating icons ─────────────────────────────────────────────────

type BgIconData = {
    icon: 'dumbbell' | 'lightning' | 'flame' | 'pulse'
    top?: string
    bottom?: string
    left?: string
    right?: string
    rotate: string
    size: string
    dur: string
    delay: string
}

const BG_ICONS: BgIconData[] = [
    {icon: 'dumbbell', top: '6%', left: '4%', rotate: '-22deg', size: 'h-10 w-10', dur: '9s', delay: '0s'},
    {icon: 'lightning', top: '13%', right: '6%', rotate: '18deg', size: 'h-8 w-8', dur: '11s', delay: '1.5s'},
    {icon: 'flame', top: '46%', left: '2%', rotate: '10deg', size: 'h-12 w-12', dur: '13s', delay: '3s'},
    {icon: 'pulse', top: '52%', right: '3%', rotate: '-14deg', size: 'h-9 w-9', dur: '10s', delay: '0.5s'},
    {icon: 'lightning', top: '78%', left: '5%', rotate: '-8deg', size: 'h-7 w-7', dur: '8s', delay: '4s'},
    {icon: 'dumbbell', top: '88%', right: '7%', rotate: '28deg', size: 'h-9 w-9', dur: '12s', delay: '2s'},
    {icon: 'flame', top: '27%', right: '2%', rotate: '-18deg', size: 'h-6 w-6', dur: '7s', delay: '5s'},
    {icon: 'pulse', top: '65%', left: '1%', rotate: '12deg', size: 'h-8 w-8', dur: '14s', delay: '2.5s'},
]

// ── OAuth availability ────────────────────────────────────────────────────────

const GOOGLE_ENABLED = !!import.meta.env.VITE_GOOGLE_CLIENT_ID
const FB_ENABLED = !!import.meta.env.VITE_FACEBOOK_APP_ID

// ── Input class helper ────────────────────────────────────────────────────────

const inputCls = (withToggle = false) =>
    `w-full rounded-xl bg-slate-800/60 border border-slate-700/50 pl-11 ${
        withToggle ? 'pr-11' : 'pr-4'
    } py-3.5 text-sm text-slate-50 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-accent-500/50 focus:border-transparent transition-all`

// ── Component ─────────────────────────────────────────────────────────────────

export function LoginPage() {
    const {t} = useTranslation()
    const navigate = useNavigate()
    const [searchParams] = useSearchParams()
    const setTokens = useAuthStore((s) => s.setTokens)
    const gymInfo = useAuthStore((s) => s.gymInfo)
    const setGymInfo = useAuthStore((s) => s.setGymInfo)
    const initTheme = useThemeStore((s) => s.initTheme)

    const qrParam = searchParams.get('qr')

    const initialized = useAuthStore((s) => s.initialized)
    const accessToken = useAuthStore((s) => s.accessToken)

    const [loading, setLoading] = useState(false)
    const [mode, setMode] = useState<Mode>('login')
    const [authStep, setAuthStep] = useState<AuthStep>({step: 'form'})
    const [showLoginPwd, setShowLoginPwd] = useState(false)
    const [showRegPwd, setShowRegPwd] = useState(false)
    const [showRegConfirmPwd, setShowRegConfirmPwd] = useState(false)
    const [gymName, setGymName] = useState<string | null>(gymInfo?.nombre_compania ?? null)
    const [gymBranch, setGymBranch] = useState<string | null>(gymInfo?.nombre_sucursal ?? null)
    const [gymLogo, setGymLogo] = useState<string | null>(gymInfo?.logo_url ?? null)
    const [gymInfoAvailable, setGymInfoAvailable] = useState(!!gymInfo?.id_compania)
    const [pendingQrToken] = useState<string | null>(qrParam)

    // Auto-redirect when the user already has a valid session
    useEffect(() => {
        if (!initialized || !accessToken) return
        if (qrParam) {
            authRepository
                .getGymByQr(qrParam)
                .then((data) => {
                    setGymInfo({
                        id_compania: data.id_compania,
                        logo_url: data.logo_url,
                        nombre_compania: data.nombre_compania,
                        nombre_sucursal: data.nombre_sucursal,
                        id_sucursal: data.id_sucursal,
                    })
                    navigate('/check-in', {replace: true, state: {autoQrToken: qrParam}})
                })
                .catch(() => navigate('/check-in', {replace: true}))
        } else {
            navigate('/check-in', {replace: true})
        }
    }, [initialized, accessToken])

    const loginForm = useForm<LoginData>({
        resolver: zodResolver(loginSchema),
        defaultValues: {id_compania: gymInfo?.id_compania ?? undefined},
    })

    const registerForm = useForm<RegisterData>({resolver: zodResolver(registerSchema)})

    useEffect(() => {
        if (!qrParam || accessToken) return
        authRepository
            .getGymByQr(qrParam)
            .then((data) => {
                loginForm.setValue('id_compania', data.id_compania)
                setGymName(data.nombre_compania)
                setGymBranch(data.nombre_sucursal)
                setGymLogo(data.logo_url)
                setGymInfo({
                    id_compania: data.id_compania,
                    logo_url: data.logo_url,
                    nombre_compania: data.nombre_compania,
                    nombre_sucursal: data.nombre_sucursal,
                    id_sucursal: data.id_sucursal,
                })
                setGymInfoAvailable(true)
            })
            .catch(() => toast.error(t('login.errors.invalidQr')))
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const resolvedCompanyId = () => gymInfo?.id_compania ?? loginForm.getValues('id_compania')

    const afterLogin = (qrToken: string | null) => {
        initTheme(useAuthStore.getState().user?.sexo ?? null)
        navigate('/check-in', {
            replace: true,
            state: qrToken ? {autoQrToken: qrToken} : undefined,
        })
    }

    const onLoginSubmit = async (data: LoginData) => {
        const id_compania = resolvedCompanyId()
        if (!id_compania) {
            toast.error(t('login.errors.gymIdRequired'));
            return
        }
        setLoading(true)
        try {
            const res = await authRepository.loginManual({...data, id_compania})
            setTokens(res.access_token, res.refresh_token)
            afterLogin(pendingQrToken)
        } catch {
            toast.error(t('login.errors.invalidCredentials'))
        } finally {
            setLoading(false)
        }
    }

    const registrarClienteConReintentos = async (idSucursal?: number) => {
        for (let i = 0; i < 3; i++) {
            try {
                await coreRepository.registrarComoCliente(idSucursal)
                return
            } catch {
                if (i < 2) await new Promise<void>((r) => setTimeout(r, 3000))
            }
        }
    }

    const onRegisterSubmit = async (data: RegisterData) => {
        const id_compania = resolvedCompanyId()
        if (!id_compania) {
            toast.error(t('login.errors.gymIdRequired'));
            return
        }
        setLoading(true)
        try {
            const res = await authRepository.registrar({
                nombre: data.nombre,
                correo: data.correo,
                password: data.password,
                id_compania,
            })
            setTokens(res.access_token, res.refresh_token)
            registrarClienteConReintentos(gymInfo?.id_sucursal ?? undefined).catch(() => {
            })
            toast.success(t('registro.success'))
            afterLogin(pendingQrToken)
        } catch {
            toast.error(t('registro.errors.error'))
        } finally {
            setLoading(false)
        }
    }

    const handleGoogleSuccess = async (credential: string) => {
        const id_compania = resolvedCompanyId()
        if (!id_compania) {
            toast.error(t('login.errors.gymIdRequired'));
            return
        }
        setLoading(true)
        try {
            const res = await authRepository.loginGoogle({id_token: credential, id_compania})
            if (res.status === 'registro_pendiente') {
                setAuthStep({
                    step: 'completar_oauth',
                    provider: 'google',
                    token: credential,
                    email: res.email!,
                    nombre: res.nombre ?? '',
                })
            } else {
                setTokens(res.access_token!, res.refresh_token!)
                afterLogin(pendingQrToken)
            }
        } catch {
            toast.error(t('login.errors.googleError'))
        } finally {
            setLoading(false)
        }
    }

    const handleFacebookLogin = () => {
        const id_compania = resolvedCompanyId()
        if (!id_compania) {
            toast.error(t('login.errors.gymIdRequired'));
            return
        }
        if (!window.FB) {
            toast.error(t('login.errors.facebookError'));
            return
        }
        window.FB.login(
            (response) => {
                // eslint-disable-next-line no-console
                console.log('[FB.login] respuesta completa:', response)
                // eslint-disable-next-line no-console
                console.log('[FB.login] status:', response.status, '| authResponse:', response.authResponse)
                if (!response.authResponse?.accessToken) {
                    // eslint-disable-next-line no-console
                    console.warn('[FB.login] SIN accessToken. status =', response.status,
                        '— si es "unknown" en http://, Facebook bloqueó el login por no ser HTTPS.')
                    toast.error(t('login.errors.facebookError'))
                    return
                }
                const fbToken = response.authResponse.accessToken
                // Imprime la info del perfil de Facebook para depurar
                window.FB.api('/me', {fields: 'id,name,email,picture'}, (me: unknown) => {
                    // eslint-disable-next-line no-console
                    console.log('[FB.api /me] perfil de Facebook:', me)
                })
                setLoading(true)
                authRepository
                    .loginFacebook({access_token: fbToken, id_compania})
                    .then((res) => {
                        if (res.status === 'registro_pendiente') {
                            setAuthStep({
                                step: 'completar_oauth',
                                provider: 'facebook',
                                token: fbToken,
                                email: res.email!,
                                nombre: res.nombre ?? '',
                            })
                        } else {
                            setTokens(res.access_token!, res.refresh_token!)
                            afterLogin(pendingQrToken)
                        }
                    })
                    .catch(() => toast.error(t('login.errors.facebookError')))
                    .finally(() => setLoading(false))
            },
            {scope: 'email'},
        )
    }

    return (
        <div
            className="relative flex min-h-svh flex-col items-center justify-center px-5 py-10 overflow-hidden bg-slate-950">

            {/* ── Language toggle ─────────────────────────────────────────────── */}
            <div className="fixed top-4 right-4 z-20">
                <LangToggle/>
            </div>

            {/* ── Animated background ─────────────────────────────────────────── */}
            <div className="fixed inset-0 overflow-hidden pointer-events-none" aria-hidden="true">

                {/* Radial glow from accent color */}
                <div
                    className="absolute -top-24 left-1/2 -translate-x-1/2 w-[600px] h-[600px] rounded-full blur-3xl"
                    style={{
                        background: 'radial-gradient(circle, var(--accent-700) 0%, transparent 65%)',
                        opacity: 0.28
                    }}
                />

                {/* Floating fitness icons */}
                {BG_ICONS.map((item, i) => (
                    <div
                        key={i}
                        className="absolute pointer-events-none"
                        style={{
                            top: item.top,
                            bottom: item.bottom,
                            left: item.left,
                            right: item.right,
                            transform: `rotate(${item.rotate})`,
                        }}
                    >
                        <svg
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth={2}
                            className={`${item.size} text-accent-400`}
                            style={{
                                animation: `floatFitness ${item.dur} ease-in-out infinite`,
                                animationDelay: item.delay,
                                animationFillMode: 'backwards',
                            }}
                            aria-hidden="true"
                        >
                            {item.icon === 'dumbbell' && (
                                <>
                                    <line x1="8" y1="12" x2="16" y2="12" strokeWidth="2" strokeLinecap="round"/>
                                    <line x1="5" y1="9" x2="5" y2="15" strokeWidth="3" strokeLinecap="round"/>
                                    <line x1="2.5" y1="10" x2="2.5" y2="14" strokeWidth="3.5" strokeLinecap="round"/>
                                    <line x1="19" y1="9" x2="19" y2="15" strokeWidth="3" strokeLinecap="round"/>
                                    <line x1="21.5" y1="10" x2="21.5" y2="14" strokeWidth="3.5" strokeLinecap="round"/>
                                </>
                            )}
                            {item.icon === 'lightning' && (
                                <path strokeLinecap="round" strokeLinejoin="round"
                                      d="M3.75 13.5l10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75z"/>
                            )}
                            {item.icon === 'flame' && (
                                <>
                                    <path strokeLinecap="round" strokeLinejoin="round"
                                          d="M15.362 5.214A8.252 8.252 0 0112 21 8.25 8.25 0 016.038 7.048 8.287 8.287 0 009 9.6a8.983 8.983 0 013.361-6.867 8.21 8.21 0 003 2.48z"/>
                                    <path strokeLinecap="round" strokeLinejoin="round"
                                          d="M12 18a3.75 3.75 0 00.495-7.468 5.99 5.99 0 00-1.925 3.546 5.975 5.975 0 01-2.133-1A3.75 3.75 0 0012 18z"/>
                                </>
                            )}
                            {item.icon === 'pulse' && (
                                <path strokeLinecap="round" strokeLinejoin="round" d="M3 12h3.5l2-7 4 14 2.5-7H21"/>
                            )}
                        </svg>
                    </div>
                ))}
            </div>

            {/* ── Card area ────────────────────────────────────────────────────── */}
            <div className="relative z-10 w-full max-w-sm">

            {/* ── Completar registro OAuth step ──────────────────────────────── */}
            {authStep.step === 'completar_oauth' && (
                <CompletarRegistroOAuth
                    provider={authStep.provider}
                    token={authStep.token}
                    email={authStep.email}
                    nombre={authStep.nombre}
                    idCompania={resolvedCompanyId()!}
                    onCancelar={() => setAuthStep({step: 'form'})}
                    onRegistrado={(accessToken, refreshToken) => {
                        setTokens(accessToken, refreshToken)
                        afterLogin(pendingQrToken)
                    }}
                />
            )}

            {/* ── Normal login/register card ─────────────────────────────────── */}
            {authStep.step === 'form' && (
                <div
                    className="rounded-2xl bg-slate-900/75 backdrop-blur-xl ring-1 ring-white/8 shadow-2xl shadow-black/60 px-7 py-8 space-y-6">

                    {/* Header */}
                    <div className="text-center space-y-3">
                        {gymLogo ? (
                            <img
                                src={gymLogo}
                                alt={gymName ?? ''}
                                className="mx-auto h-20 w-20 rounded-2xl object-cover shadow-xl shadow-black/50 ring-1 ring-white/10"
                            />
                        ) : (
                            <div
                                className="mx-auto h-20 w-20 rounded-2xl bg-slate-800 ring-1 ring-white/8 flex items-center justify-center shadow-lg">
                                <svg className="h-10 w-10 text-accent-500" fill="none" viewBox="0 0 24 24"
                                     stroke="currentColor" strokeWidth={1.5} aria-hidden="true">
                                    <path strokeLinecap="round" strokeLinejoin="round"
                                          d="M3.75 13.5l10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75z"/>
                                </svg>
                            </div>
                        )}
                        <div>
                            <h1 className="text-2xl font-black text-slate-50 tracking-tight">
                                {gymName ?? t('login.title')}
                            </h1>
                            {gymBranch && (
                                <p className="mt-0.5 text-[11px] font-bold uppercase tracking-widest text-accent-400">
                                    {gymBranch}
                                </p>
                            )}
                            <p className="mt-1.5 text-sm text-slate-400">
                                {mode === 'register' ? t('registro.subtitle') : t('login.subtitle')}
                            </p>
                        </div>
                    </div>

                    {/* Mode tabs */}
                    {gymInfoAvailable && (
                        <div className="flex rounded-xl bg-slate-800/50 p-1 gap-1 ring-1 ring-white/6">
                            {(['login', 'register'] as Mode[]).map((m) => (
                                <button
                                    key={m}
                                    type="button"
                                    onClick={() => setMode(m)}
                                    className={`flex-1 rounded-lg py-2.5 text-sm font-semibold transition-all ${
                                        mode === m
                                            ? 'bg-accent-600 text-white shadow-sm'
                                            : 'text-slate-400 hover:text-slate-200'
                                    }`}
                                >
                                    {m === 'login' ? t('login.tabs.login') : t('login.tabs.register')}
                                </button>
                            ))}
                        </div>
                    )}

                    {/* ── Login form ─────────────────────────────────────────────── */}
                    {mode === 'login' && (
                        <form noValidate onSubmit={loginForm.handleSubmit(onLoginSubmit)} className="space-y-3">

                            {/* Gym ID — solo visible si no hay gymInfo */}
                            {!gymInfoAvailable && (
                                <div>
                                    <div className="relative">
                    <span className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none">
                      <IcoBuilding/>
                    </span>
                                        <input
                                            {...loginForm.register('id_compania')}
                                            type="text"
                                            inputMode="numeric"
                                            pattern="[0-9]*"
                                            placeholder={t('login.fields.gymId')}
                                            className={inputCls()}
                                        />
                                    </div>
                                    <p className="mt-1.5 text-xs text-slate-500 pl-1">{t('login.fields.gymIdHint')}</p>
                                </div>
                            )}

                            <div>
                                <div className="relative">
                  <span className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none">
                    <IcoEnvelope/>
                  </span>
                                    <input
                                        {...loginForm.register('login')}
                                        autoComplete="username"
                                        placeholder={t('login.fields.login')}
                                        className={inputCls()}
                                    />
                                </div>
                                {loginForm.formState.errors.login && (
                                    <p className="mt-1 text-xs text-red-400 pl-1">{loginForm.formState.errors.login.message}</p>
                                )}
                            </div>

                            <div>
                                <div className="relative">
                  <span className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none">
                    <IcoLock/>
                  </span>
                                    <input
                                        {...loginForm.register('password')}
                                        type={showLoginPwd ? 'text' : 'password'}
                                        autoComplete="current-password"
                                        placeholder={t('login.fields.password')}
                                        className={inputCls(true)}
                                    />
                                    <button
                                        type="button"
                                        onClick={() => setShowLoginPwd((v) => !v)}
                                        className="absolute right-4 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 transition"
                                        aria-label={showLoginPwd ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                                    >
                                        <IcoEye show={showLoginPwd}/>
                                    </button>
                                </div>
                                {loginForm.formState.errors.password && (
                                    <p className="mt-1 text-xs text-red-400 pl-1">{loginForm.formState.errors.password.message}</p>
                                )}
                            </div>

                            <div className="flex justify-end">
                                <Link
                                    to="/forgot-password"
                                    className="inline-flex items-center min-h-[44px] px-1 text-xs text-accent-400 hover:text-accent-300 transition"
                                >
                                    {t('login.links.forgotPassword')}
                                </Link>
                            </div>

                            <button
                                type="submit"
                                disabled={loading}
                                aria-busy={loading}
                                className="w-full rounded-xl bg-accent-600 py-3.5 text-sm font-semibold text-white transition hover:bg-accent-500 active:scale-[0.98] disabled:opacity-50"
                            >
                                {loading ? (
                                    <span className="flex items-center justify-center gap-2">
                    <IcoSpinner/>{t('login.buttons.submitting')}
                  </span>
                                ) : t('login.buttons.submit')}
                            </button>

                            {/* Social logins — solo si al menos uno está configurado */}
                            {(GOOGLE_ENABLED || FB_ENABLED) && (
                                <>
                                    <div className="flex items-center gap-3 py-1">
                                        <div className="flex-1 h-px bg-slate-700/60"/>
                                        <span
                                            className="text-xs text-slate-500 whitespace-nowrap">{t('login.separator')}</span>
                                        <div className="flex-1 h-px bg-slate-700/60"/>
                                    </div>

                                    {GOOGLE_ENABLED && (
                                        <div className="flex justify-center">
                                            <GoogleLogin
                                                onSuccess={(cr) => { if (cr.credential) handleGoogleSuccess(cr.credential) }}
                                                onError={() => toast.error(t('login.errors.googleError'))}
                                                theme="filled_black"
                                                size="large"
                                                text="continue_with"
                                                width={368}
                                            />
                                        </div>
                                    )}

                                    {FB_ENABLED && (
                                        <button
                                            type="button"
                                            onClick={handleFacebookLogin}
                                            disabled={loading}
                                            className="w-full flex items-center justify-center gap-3 rounded-xl bg-[#1877F2] py-3.5 text-sm font-semibold text-white transition hover:bg-[#166FE5] active:scale-[0.98] disabled:opacity-50"
                                        >
                                            <svg className="h-5 w-5 shrink-0" fill="currentColor" viewBox="0 0 24 24"
                                                 aria-hidden="true">
                                                <path
                                                    d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z"/>
                                            </svg>
                                            {t('login.buttons.facebook')}
                                        </button>
                                    )}
                                </>
                            )}
                        </form>
                    )}

                    {/* ── Register form ───────────────────────────────────────────── */}
                    {mode === 'register' && (
                        <form noValidate onSubmit={registerForm.handleSubmit(onRegisterSubmit)} className="space-y-3">

                            <div>
                                <div className="relative">
                  <span className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none">
                    <IcoUser/>
                  </span>
                                    <input
                                        {...registerForm.register('nombre')}
                                        autoComplete="name"
                                        placeholder={t('registro.fields.nombre')}
                                        className={inputCls()}
                                    />
                                </div>
                                {registerForm.formState.errors.nombre && (
                                    <p className="mt-1 text-xs text-red-400 pl-1">{registerForm.formState.errors.nombre.message}</p>
                                )}
                            </div>

                            <div>
                                <div className="relative">
                  <span className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none">
                    <IcoEnvelope/>
                  </span>
                                    <input
                                        {...registerForm.register('correo')}
                                        type="email"
                                        autoComplete="email"
                                        placeholder={t('registro.fields.correo')}
                                        className={inputCls()}
                                    />
                                </div>
                                {registerForm.formState.errors.correo && (
                                    <p className="mt-1 text-xs text-red-400 pl-1">{registerForm.formState.errors.correo.message}</p>
                                )}
                            </div>

                            <div>
                                <div className="relative">
                  <span className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none">
                    <IcoLock/>
                  </span>
                                    <input
                                        {...registerForm.register('password')}
                                        type={showRegPwd ? 'text' : 'password'}
                                        autoComplete="new-password"
                                        placeholder={t('registro.fields.password')}
                                        className={inputCls(true)}
                                    />
                                    <button
                                        type="button"
                                        onClick={() => setShowRegPwd((v) => !v)}
                                        className="absolute right-4 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 transition"
                                        aria-label={showRegPwd ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                                    >
                                        <IcoEye show={showRegPwd}/>
                                    </button>
                                </div>
                                {registerForm.formState.errors.password && (
                                    <p className="mt-1 text-xs text-red-400 pl-1">{registerForm.formState.errors.password.message}</p>
                                )}
                            </div>

                            <div>
                                <div className="relative">
                  <span className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none">
                    <IcoLock/>
                  </span>
                                    <input
                                        {...registerForm.register('confirmPassword')}
                                        type={showRegConfirmPwd ? 'text' : 'password'}
                                        autoComplete="new-password"
                                        placeholder={t('registro.fields.confirmPassword')}
                                        className={inputCls(true)}
                                    />
                                    <button
                                        type="button"
                                        onClick={() => setShowRegConfirmPwd((v) => !v)}
                                        className="absolute right-4 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 transition"
                                        aria-label={showRegConfirmPwd ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                                    >
                                        <IcoEye show={showRegConfirmPwd}/>
                                    </button>
                                </div>
                                {registerForm.formState.errors.confirmPassword && (
                                    <p className="mt-1 text-xs text-red-400 pl-1">{registerForm.formState.errors.confirmPassword.message}</p>
                                )}
                            </div>

                            <button
                                type="submit"
                                disabled={loading}
                                aria-busy={loading}
                                className="w-full rounded-xl bg-accent-600 py-3.5 text-sm font-semibold text-white transition hover:bg-accent-500 active:scale-[0.98] disabled:opacity-50 mt-1"
                            >
                                {loading ? (
                                    <span className="flex items-center justify-center gap-2">
                    <IcoSpinner/>{t('registro.buttons.submitting')}
                  </span>
                                ) : t('registro.buttons.submit')}
                            </button>
                        </form>
                    )}

                </div>
            )}

            </div>
        </div>
    )
}
