package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.CloudinaryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQ-SAAS-001 (RN-08) — tests unitarios de las validaciones de
 * {@link CloudinaryService#subirComprobante(byte[], String, Long)} (tamaño,
 * magic bytes, sanitización de nombre). No golpea Cloudinary — usa métodos
 * package-private de test.
 */
@DisplayName("CloudinaryService.subirComprobante — validaciones RN-08")
class CloudinaryServiceComprobanteTest {

    private static final byte[] PNG_MAGIC = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00
    };
    private static final byte[] JPEG_MAGIC = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};
    private static final byte[] PDF_MAGIC = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E};

    @Nested
    @DisplayName("validarTamano")
    class Tamano {

        @Test
        @DisplayName("rechaza archivos > 5MB")
        void rechazaArchivosGrandes() {
            byte[] grande = new byte[CloudinaryService.MAX_COMPROBANTE_BYTES + 1];
            assertThatThrownBy(() -> CloudinaryServiceTestHooks.validarTamano(grande))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("excede 5MB");
        }

        @Test
        @DisplayName("rechaza archivos vacíos")
        void rechazaArchivosVacios() {
            assertThatThrownBy(() -> CloudinaryServiceTestHooks.validarTamano(new byte[0]))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("vacío");
        }

        @Test
        @DisplayName("acepta archivos ≤ 5MB")
        void aceptaArchivosPequenos() {
            byte[] pequeno = new byte[1024];
            pequeno[0] = 0x25;
            CloudinaryServiceTestHooks.validarTamano(pequeno); // no lanza
        }
    }

    @Nested
    @DisplayName("validarMagicBytes")
    class MagicBytes {

        @Test
        @DisplayName("acepta JPEG (FF D8 FF)")
        void aceptaJpeg() {
            CloudinaryServiceTestHooks.validarMagicBytes(JPEG_MAGIC);
        }

        @Test
        @DisplayName("acepta PNG (89 50 4E 47 0D 0A 1A 0A)")
        void aceptaPng() {
            CloudinaryServiceTestHooks.validarMagicBytes(PNG_MAGIC);
        }

        @Test
        @DisplayName("acepta PDF (%PDF)")
        void aceptaPdf() {
            CloudinaryServiceTestHooks.validarMagicBytes(PDF_MAGIC);
        }

        @Test
        @DisplayName("rechaza bytes sin magic válido")
        void rechazaBytesInvalidos() {
            byte[] basura = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
            assertThatThrownBy(() -> CloudinaryServiceTestHooks.validarMagicBytes(basura))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("formato no permitido");
        }
    }

    @Nested
    @DisplayName("sanitizarNombre")
    class Nombre {

        @Test
        @DisplayName("reemplaza caracteres especiales por '-'")
        void reemplazaCaracteresEspeciales() {
            String sanitizado = CloudinaryServiceTestHooks.sanitizar("mi archivo #1!.pdf");
            assertThat(sanitizado).matches("[a-zA-Z0-9-]+");
        }

        @Test
        @DisplayName("elimina extensión")
        void quitaExtension() {
            String sanitizado = CloudinaryServiceTestHooks.sanitizar("comprobante.pdf");
            assertThat(sanitizado).doesNotContain(".");
        }

        @Test
        @DisplayName("trunca a 64 caracteres")
        void truncaA64() {
            String nombreLargo = "a".repeat(200) + ".pdf";
            String sanitizado = CloudinaryServiceTestHooks.sanitizar(nombreLargo);
            assertThat(sanitizado.length()).isLessThanOrEqualTo(64);
        }

        @Test
        @DisplayName("nombre vacío → 'comprobante'")
        void nombreVacio() {
            String sanitizado = CloudinaryServiceTestHooks.sanitizar("");
            assertThat(sanitizado).isEqualTo("comprobante");
        }
    }

    /**
     * Adapter estático para exponer los helpers package-private de CloudinaryService
     * en este mismo package (unit).
     */
    private static final class CloudinaryServiceTestHooks {
        static void validarTamano(byte[] c) {
            CloudinaryService.validarTamanoParaTest(c);
        }
        static void validarMagicBytes(byte[] c) {
            CloudinaryService.validarMagicBytesParaTest(c);
        }
        static String sanitizar(String n) {
            return CloudinaryService.sanitizarParaTest(n);
        }
    }
}
