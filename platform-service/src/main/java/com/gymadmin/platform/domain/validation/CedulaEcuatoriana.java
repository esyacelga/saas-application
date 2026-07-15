package com.gymadmin.platform.domain.validation;

/**
 * Validación de cédula de identidad ecuatoriana (algoritmo del Registro Civil / módulo 10).
 *
 * Réplica exacta de la frontera del frontend {@code src/lib/sri/validarCedula.ts}: front y
 * back deben dar el MISMO veredicto para la misma cédula. Si se cambia una, cambiar la otra.
 *
 * Reglas:
 *  - 10 dígitos exactos.
 *  - Los dos primeros dígitos son el código de provincia: 01–24, o 30 (ecuatorianos
 *    registrados en el exterior).
 *  - El tercer dígito es < 6 para personas naturales.
 *  - El décimo dígito es el verificador, calculado con coeficientes 2,1,2,1… sobre los
 *    primeros 9 dígitos (si el producto ≥ 10 se le resta 9).
 */
public final class CedulaEcuatoriana {

    private static final int[] COEFICIENTES = {2, 1, 2, 1, 2, 1, 2, 1, 2};

    private CedulaEcuatoriana() {
    }

    /**
     * @return true solo si la cédula es matemáticamente válida (formato + dígito verificador).
     *         No comprueba que la cédula pertenezca a una persona real.
     */
    public static boolean esValida(String cedula) {
        if (cedula == null || !cedula.matches("\\d{10}")) {
            return false;
        }

        int provincia = Integer.parseInt(cedula.substring(0, 2));
        if ((provincia < 1 || provincia > 24) && provincia != 30) {
            return false;
        }

        int tercerDigito = cedula.charAt(2) - '0';
        if (tercerDigito >= 6) {
            return false;
        }

        int suma = 0;
        for (int i = 0; i < 9; i++) {
            int producto = (cedula.charAt(i) - '0') * COEFICIENTES[i];
            if (producto >= 10) {
                producto -= 9;
            }
            suma += producto;
        }

        int decenaSuperior = ((suma + 9) / 10) * 10;
        int verificador = decenaSuperior - suma;
        if (verificador == 10) {
            verificador = 0;
        }

        return verificador == (cedula.charAt(9) - '0');
    }
}
