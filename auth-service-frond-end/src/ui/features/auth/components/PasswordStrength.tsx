import { Check, Circle } from 'lucide-react'
import { useTranslation } from 'react-i18next'

interface Props {
  password: string
}

const CHECK_CONFIGS = [
  { key: 'passwordStrength.minChars', test: (p: string) => p.length >= 8 },
  { key: 'passwordStrength.uppercase', test: (p: string) => /[A-Z]/.test(p) },
  { key: 'passwordStrength.number',   test: (p: string) => /[0-9]/.test(p) },
]

export function PasswordStrength({ password }: Props) {
  const { t } = useTranslation()

  if (!password) return null

  return (
    <ul className="space-y-1.5 mt-2">
      {CHECK_CONFIGS.map(({ key, test }) => {
        const ok = test(password)
        return (
          <li
            key={key}
            className={`flex items-center gap-2 text-xs transition-colors ${
              ok ? 'text-green-600' : 'text-slate-400'
            }`}
          >
            {ok
              ? <Check size={13} className="flex-shrink-0" />
              : <Circle size={13} className="flex-shrink-0" />
            }
            {t(key)}
          </li>
        )
      })}
    </ul>
  )
}
