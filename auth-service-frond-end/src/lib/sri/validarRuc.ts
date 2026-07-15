import { validarCedula } from './validarCedula'

/**
 * Validación de RUC ecuatoriano (SRI).
 *
 * Frontera `src/lib/sri/`: TypeScript puro, sin dependencias de framework.
 * Se usa en el wizard de configuración de facturación (donde el RUC pasa a ser
 * obligatorio), NO en el registro de gimnasios.
 *
 * Tres tipos de RUC, discriminados por el tercer dígito:
 *  - Persona natural (tercer dígito 0–5): los 10 primeros dígitos son una cédula
 *    válida, seguidos de "001".
 *  - Sociedad pública (tercer dígito 6): verificador módulo 11 con coeficientes
 *    3,2,7,6,5,4,3,2 sobre los primeros 8 dígitos; termina en "0001".
 *  - Sociedad privada / extranjera (tercer dígito 9): verificador módulo 11 con
 *    coeficientes 4,3,2,7,6,5,4,3,2 sobre los primeros 9 dígitos; termina en "001".
 */
export function validarRuc(ruc: string): boolean {
  if (!/^\d{13}$/.test(ruc)) return false

  const provincia = Number(ruc.slice(0, 2))
  if ((provincia < 1 || provincia > 24) && provincia !== 30) return false

  const tercerDigito = Number(ruc[2])

  if (tercerDigito >= 0 && tercerDigito <= 5) {
    // Persona natural: cédula válida + establecimiento "001".
    return validarCedula(ruc.slice(0, 10)) && ruc.slice(10) === '001'
  }

  if (tercerDigito === 6) {
    // Sociedad pública.
    return verificarModulo11(ruc, [3, 2, 7, 6, 5, 4, 3, 2], 8) && ruc.slice(9) === '0001'
  }

  if (tercerDigito === 9) {
    // Sociedad privada / extranjera.
    return verificarModulo11(ruc, [4, 3, 2, 7, 6, 5, 4, 3, 2], 9) && ruc.slice(10) === '001'
  }

  return false
}

function verificarModulo11(ruc: string, coeficientes: number[], posVerificador: number): boolean {
  let suma = 0
  for (let i = 0; i < coeficientes.length; i++) {
    suma += Number(ruc[i]) * coeficientes[i]
  }
  const residuo = suma % 11
  const verificador = residuo === 0 ? 0 : 11 - residuo
  return verificador === Number(ruc[posVerificador])
}
