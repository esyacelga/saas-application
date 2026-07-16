/**
 * Returns the URL to use for a person's avatar.
 *
 * Priority:
 *   1. fotoUrl if non-empty
 *   2. VITE_AVATAR_MUJER_URL  when sexo === 'F'
 *   3. VITE_AVATAR_HOMBRE_URL otherwise (M, O, null)
 */
export function getAvatarUrl(
  fotoUrl: string | null | undefined,
  sexo: 'M' | 'F' | 'O' | null | undefined,
): string {
  if (fotoUrl) return fotoUrl
  if (sexo === 'F') return import.meta.env.VITE_AVATAR_MUJER_URL as string
  return import.meta.env.VITE_AVATAR_HOMBRE_URL as string
}
