package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.QrTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("QrTokenService — generación de tokens QR seguros")
class QrTokenServiceTest {

    private QrTokenService service;

    @BeforeEach
    void setUp() {
        // tokenLength=32 → el servicio genera tokenLength*2 chars hex y toma los primeros 32
        service = new QrTokenService(32);
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("generateToken — longitud y formato")
    class GenerateToken {

        @Test
        @DisplayName("retorna un string no nulo con longitud igual a tokenLength (32)")
        void retornaStringNoNuloConLongitudCorrecta() {
            String token = service.generateToken();

            assertThat(token).isNotNull();
            assertThat(token).hasSize(32);
        }

        @Test
        @DisplayName("todos los caracteres son hexadecimales en minúscula (0-9, a-f)")
        void soloContieneCaracteresHexadecimales() {
            String token = service.generateToken();

            assertThat(token).matches("[0-9a-f]+");
        }

        @Test
        @DisplayName("no contiene caracteres especiales ni espacios")
        void noContieneCaracteresEspeciales() {
            String token = service.generateToken();

            assertThat(token).doesNotContainAnyWhitespaces();
            // Los únicos caracteres válidos son dígitos y letras a-f
            for (char c : token.toCharArray()) {
                assertThat(Character.isLetterOrDigit(c)).isTrue();
            }
        }

        @Test
        @DisplayName("dos llamadas consecutivas retornan tokens distintos (aleatoriedad)")
        void dosLlamadasRetornanTokensDistintos() {
            String token1 = service.generateToken();
            String token2 = service.generateToken();

            // Aunque es probabilísticamente posible que sean iguales, con 32 chars hex
            // la probabilidad de colisión es 1 / 16^32, negligible en un test.
            assertThat(token1).isNotEqualTo(token2);
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("generateToken — distintas longitudes configuradas")
    class GenerateTokenConLongitudVariable {

        @Test
        @DisplayName("respeta la longitud configurada cuando tokenLength es 16")
        void respetaLongitudDe16() {
            QrTokenService serviceCorto = new QrTokenService(16);
            String token = serviceCorto.generateToken();

            assertThat(token).hasSize(16);
            assertThat(token).matches("[0-9a-f]+");
        }

        @Test
        @DisplayName("respeta la longitud configurada cuando tokenLength es 64")
        void respetaLongitudDe64() {
            QrTokenService serviceLargo = new QrTokenService(64);
            String token = serviceLargo.generateToken();

            assertThat(token).hasSize(64);
            assertThat(token).matches("[0-9a-f]+");
        }
    }
}
