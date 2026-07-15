package com.gymadmin.attendance.domain.validation;

import java.util.Optional;

/**
 * Normaliza teléfonos ecuatorianos a formato E.164 (p. ej. {@code +593987654321}) antes de
 * enviarlos a Meta WhatsApp Cloud API. Duplicado pragmático (v1) del normalizador de
 * platform-service; en attendance el teléfono del socio llega desde {@code identidad.personas}
 * (vía el endpoint interno de core) y hay que normalizarlo aquí (core lo entrega sin normalizar).
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

        boolean teniaMas = raw.trim().startsWith("+");
        String digitos = raw.replaceAll("[^0-9]", "");
        if (digitos.isEmpty()) {
            return Optional.empty();
        }

        if (digitos.startsWith(CODIGO_PAIS)) {
            String nacional = digitos.substring(CODIGO_PAIS.length());
            return esCelularSinCero(nacional)
                    ? Optional.of("+" + CODIGO_PAIS + nacional)
                    : Optional.empty();
        }

        if (teniaMas) {
            return Optional.empty();
        }

        if (digitos.length() == 10 && digitos.startsWith("09")) {
            return Optional.of("+" + CODIGO_PAIS + digitos.substring(1));
        }

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
