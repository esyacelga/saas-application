/**
 * Validación de cédula de identidad ecuatoriana (algoritmo del Registro Civil / módulo 10).
 *
 * Frontera `src/lib/sri/`: TypeScript puro, sin dependencias de React/axios/PrimeReact.
 * Replicable tal cual a otros SaaS ecuatorianos (ver docs facturacion-diseno.md §14).
 *
 * Reglas:
 *  - 10 dígitos exactos.
 *  - Los dos primeros dígitos son el código de provincia: 01–24, o 30 (ecuatorianos
 *    registrados en el exterior).
 *  - El tercer dígito es < 6 para personas naturales.
 *  - El décimo dígito es el verificador, calculado con coeficientes 2,1,2,1… sobre
 *    los primeros 9 dígitos (si el producto ≥ 10 se le resta 9).
 */
export function validarCedula(cedula: string): boolean {
  if (!/^\d{10}$/.test(cedula)) return false

  const provincia = Number(cedula.slice(0, 2))
  if ((provincia < 1 || provincia > 24) && provincia !== 30) return false

  const tercerDigito = Number(cedula[2])
  if (tercerDigito >= 6) return false

  const coeficientes = [2, 1, 2, 1, 2, 1, 2, 1, 2]
  let suma = 0
  for (let i = 0; i < 9; i++) {
    let producto = Number(cedula[i]) * coeficientes[i]
    if (producto >= 10) producto -= 9
    suma += producto
  }

  const decenaSuperior = Math.ceil(suma / 10) * 10
  let verificador = decenaSuperior - suma
  if (verificador === 10) verificador = 0

  return verificador === Number(cedula[9])
}
