package com.gymadmin.platform.application.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

@Service
public class CloudinaryService {

    /** REQ-SAAS-001 (RN-08): límite duro 5MB por comprobante. */
    public static final int MAX_COMPROBANTE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_NOMBRE_LEN = 64;

    private final Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public Mono<String> subirLogo(byte[] bytes, Long idCompania) {
        return Mono.fromCallable(() -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                    "folder", "gym-admin/logos",
                    "public_id", "compania-" + idCompania,
                    "overwrite", true,
                    "resource_type", "image"
            ));
            return (String) result.get("secure_url");
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * REQ-SAAS-001 (RN-08): sube un comprobante de pago (JPEG/PNG/PDF) a
     * Cloudinary con acceso authenticated. Aplica validaciones:
     * <ul>
     *   <li>Tamaño ≤ 5MB.</li>
     *   <li>MIME por magic bytes (JPEG {@code FF D8 FF}, PNG {@code 89 50 4E 47 0D 0A 1A 0A}, PDF {@code %PDF}).</li>
     *   <li>Nombre sanitizado (alfanuméricos + '-', 64 chars).</li>
     * </ul>
     * Persiste SHA-256 del contenido para trazabilidad.
     */
    public Mono<ComprobanteSubidoResponse> subirComprobante(byte[] contenido,
                                                              String nombreOriginal,
                                                              Long idCompania) {
        return Mono.fromCallable(() -> {
                    validarTamano(contenido);
                    validarMagicBytes(contenido);
                    String hash = calcularSha256(contenido);
                    String nombreSanitizado = sanitizarNombre(nombreOriginal);

                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = cloudinary.uploader().upload(contenido, ObjectUtils.asMap(
                            "folder", "gym-admin/comprobantes/" + idCompania,
                            "public_id", nombreSanitizado + "-" + hash.substring(0, 12),
                            "overwrite", false,
                            "resource_type", "raw",
                            "access_mode", "authenticated"
                    ));
                    String url = (String) result.get("secure_url");
                    return new ComprobanteSubidoResponse(url, hash);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void validarTamano(byte[] contenido) {
        if (contenido == null || contenido.length == 0) {
            throw new IllegalArgumentException("archivo vacío");
        }
        if (contenido.length > MAX_COMPROBANTE_BYTES) {
            throw new IllegalArgumentException("archivo excede 5MB");
        }
    }

    private void validarMagicBytes(byte[] contenido) {
        if (esJpeg(contenido) || esPng(contenido) || esPdf(contenido)) {
            return;
        }
        throw new IllegalArgumentException("formato no permitido — solo JPEG/PNG/PDF");
    }

    private boolean esJpeg(byte[] c) {
        return c.length >= 3
                && (c[0] & 0xFF) == 0xFF
                && (c[1] & 0xFF) == 0xD8
                && (c[2] & 0xFF) == 0xFF;
    }

    private boolean esPng(byte[] c) {
        return c.length >= 8
                && (c[0] & 0xFF) == 0x89
                && c[1] == 0x50 && c[2] == 0x4E && c[3] == 0x47
                && c[4] == 0x0D && c[5] == 0x0A && c[6] == 0x1A && c[7] == 0x0A;
    }

    private boolean esPdf(byte[] c) {
        return c.length >= 4
                && c[0] == 0x25 && c[1] == 0x50 && c[2] == 0x44 && c[3] == 0x46;
    }

    private String calcularSha256(byte[] contenido) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(contenido);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private String sanitizarNombre(String nombreOriginal) {
        if (nombreOriginal == null || nombreOriginal.isBlank()) {
            return "comprobante";
        }
        int dot = nombreOriginal.lastIndexOf('.');
        String base = dot > 0 ? nombreOriginal.substring(0, dot) : nombreOriginal;
        String sanitized = base
                .replaceAll("[^a-zA-Z0-9-]", "-")
                .replaceAll("-+", "-");
        if (sanitized.isBlank() || sanitized.equals("-")) {
            sanitized = "comprobante";
        }
        if (sanitized.length() > MAX_NOMBRE_LEN) {
            sanitized = sanitized.substring(0, MAX_NOMBRE_LEN);
        }
        return sanitized;
    }

    /**
     * REQ-SAAS-001 (RN-08): respuesta de {@link #subirComprobante(byte[], String, Long)}.
     */
    public record ComprobanteSubidoResponse(String url, String hash) {}

    /** Expuesto para tests unitarios de la validación (no forma parte del contrato público). */
    static String sanitizarParaTest(String nombreOriginal) {
        return new CloudinaryService(null).sanitizarNombre(nombreOriginal);
    }

    /** Expuesto para tests unitarios de la validación. */
    static void validarTamanoParaTest(byte[] contenido) {
        new CloudinaryService(null).validarTamano(contenido);
    }

    /** Expuesto para tests unitarios de la validación. */
    static void validarMagicBytesParaTest(byte[] contenido) {
        new CloudinaryService(null).validarMagicBytes(contenido);
    }

    /** Expuesto para tests unitarios. */
    static String hashParaTest(byte[] contenido) {
        return new CloudinaryService(null).calcularSha256(contenido);
    }

    /** UTF-8 helper (no usado directamente pero disponible por si un test lo requiere). */
    @SuppressWarnings("unused")
    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
