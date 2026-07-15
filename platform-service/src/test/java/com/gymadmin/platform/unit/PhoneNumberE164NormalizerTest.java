package com.gymadmin.platform.unit;

import com.gymadmin.platform.domain.validation.PhoneNumberE164Normalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PhoneNumberE164Normalizer — normalización a E.164 (Ecuador)")
class PhoneNumberE164NormalizerTest {

    private static final String ESPERADO = "+593987654321";

    @Nested
    @DisplayName("celulares ecuatorianos normalizables")
    class Validos {

        @Test
        @DisplayName("celular nacional con 0 inicial → +593…")
        void nacionalConCero() {
            assertEquals(Optional.of(ESPERADO), PhoneNumberE164Normalizer.normalizar("0987654321"));
        }

        @Test
        @DisplayName("celular sin 0 ni prefijo → +593…")
        void sinCeroNiPrefijo() {
            assertEquals(Optional.of(ESPERADO), PhoneNumberE164Normalizer.normalizar("987654321"));
        }

        @Test
        @DisplayName("ya en E.164 con + → se conserva")
        void yaE164() {
            assertEquals(Optional.of(ESPERADO), PhoneNumberE164Normalizer.normalizar("+593987654321"));
        }

        @Test
        @DisplayName("prefijo 593 sin + → se le antepone +")
        void prefijoSinMas() {
            assertEquals(Optional.of(ESPERADO), PhoneNumberE164Normalizer.normalizar("593987654321"));
        }

        @Test
        @DisplayName("tolera espacios, guiones y paréntesis")
        void conSeparadores() {
            assertEquals(Optional.of(ESPERADO), PhoneNumberE164Normalizer.normalizar("+593 98-765 4321"));
            assertEquals(Optional.of(ESPERADO), PhoneNumberE164Normalizer.normalizar("(09) 8765-4321"));
        }
    }

    @Nested
    @DisplayName("no normalizables → Optional.empty()")
    class Invalidos {

        @ParameterizedTest
        @ValueSource(strings = {
                "123",              // muy corto
                "022345678",        // fijo (no empieza en 09)
                "0887654321",       // celular no empieza en 09 (08 no es celular EC)
                "9876543",          // 7 dígitos
                "12345678901234",   // demasiado largo
                "+1202555019",      // otro país (no 593)
                "abcдef",           // basura sin dígitos
                "0",                // un solo dígito
                "   "               // en blanco
        })
        @DisplayName("formatos inválidos")
        void invalidos(String raw) {
            assertTrue(PhoneNumberE164Normalizer.normalizar(raw).isEmpty(),
                    () -> "debería ser empty: " + raw);
        }

        @Test
        @DisplayName("null → empty")
        void nulo() {
            assertTrue(PhoneNumberE164Normalizer.normalizar(null).isEmpty());
        }
    }
}
