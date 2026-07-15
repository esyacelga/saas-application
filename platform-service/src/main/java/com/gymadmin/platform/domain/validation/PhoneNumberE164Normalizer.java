package com.gymadmin.platform.domain.validation;

import java.util.Optional;

/**
 * Normaliza teléfonos ecuatorianos a formato E.164 (p. ej. {@code +593987654321}) antes de
 * enviarlos a Meta WhatsApp Cloud API, que exige el número en E.164 sin el {@code +} literal
 * al armar el payload (aquí se conserva el {@code +} y el adaptador lo quita si hace falta).
 *
 * <p>Reglas (contexto Ecuador):
 * <ul>
 *   <li>Se toleran espacios, guiones y paréntesis: se descartan antes de evaluar.</li>
 *   <li>Ya en E.164 ({@code +593XXXXXXXXX}, 9 dígitos tras el 593) → se acepta tal cual.</li>
 *   <li>Con prefijo internacional sin {@code +} ({@code 593XXXXXXXXX}) → se le antepone {@code +}.</li>
 *   <li>Celular nacional con 0 inicial ({@code 09XXXXXXXX}, 10 dígitos) → {@code +593} + los 9
 *       dígitos tras el 0.</li>
 *   <li>Celular sin 0 ni prefijo ({@code 9XXXXXXXX}, 9 dígitos que empiezan en 9) → {@code +593} + número.</li>
 *   <li>Cualquier otra longitud/forma (fijos, basura, {@code null}) → {@link Optional#empty()}:
 *       el llamador NO debe enviar WhatsApp y registra {@code telefono_invalido}.</li>
 * </ul>
 *
 * <p>Solo cubre celulares ecuatorianos (los únicos que reciben WhatsApp de este flujo). Si en el
 * futuro hay tenants de otros países, esta clase es el único punto a extender.
 */
public final class PhoneNumberE164Normalizer {

    private static final String CODIGO_PAIS = "593";

    private PhoneNumberE164Normalizer() {
    }

    /**
     * @return el número en E.164 con {@code +} (p. ej. {@code +593987654321}), o
     *         {@link Optional#empty()} si no es un celular ecuatoriano normalizable.
     */
    public static Optional<String> normalizar(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        // Descartar todo lo que no sea dígito o un '+' inicial.
        boolean teniaMas = raw.trim().startsWith("+");
        String digitos = raw.replaceAll("[^0-9]", "");
        if (digitos.isEmpty()) {
            return Optional.empty();
        }

        // Caso 1: prefijo internacional 593 (venga con + o sin él) + 9 dígitos de celular (empieza en 9).
        if (digitos.startsWith(CODIGO_PAIS)) {
            String nacional = digitos.substring(CODIGO_PAIS.length());
            return esCelularSinCero(nacional)
                    ? Optional.of("+" + CODIGO_PAIS + nacional)
                    : Optional.empty();
        }

        // Si traía '+' pero el país no es 593, no lo tocamos (fuera de alcance).
        if (teniaMas) {
            return Optional.empty();
        }

        // Caso 2: celular nacional con 0 inicial → 10 dígitos, "09XXXXXXXX".
        if (digitos.length() == 10 && digitos.startsWith("09")) {
            return Optional.of("+" + CODIGO_PAIS + digitos.substring(1));
        }

        // Caso 3: celular sin 0 ni prefijo → 9 dígitos que empiezan en 9.
        if (esCelularSinCero(digitos)) {
            return Optional.of("+" + CODIGO_PAIS + digitos);
        }

        return Optional.empty();
    }

    /** 9 dígitos de un celular ecuatoriano sin el 0 inicial (empieza en 9). */
    private static boolean esCelularSinCero(String n) {
        return n.length() == 9 && n.charAt(0) == '9';
    }
}
