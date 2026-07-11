package com.gymadmin.billing.infrastructure.adapter.out.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Decrypts P12 certificate content and password using AES-256-GCM.
 * The symmetric key is loaded once at startup from the CERT_ENCRYPTION_KEY env var.
 * <p>
 * Storage layout for both {@code facturacion.certificados.p12_cifrado} and
 * {@code facturacion.certificados.password_cifrado}: {@code [IV(12 bytes) || ciphertext(includes GCM tag)]}.
 * The DDL does not store the IV in a separate column — it is prepended to the ciphertext blob.
 */
@Service
public class CertificadoDecryptionService {

    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private final SecretKey secretKey;

    public CertificadoDecryptionService(
            @Value("${facturacion.cert.encryption-key}") String encryptionKeyBase64) {
        byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "CERT_ENCRYPTION_KEY must decode to exactly 32 bytes (256 bits). Got: " + keyBytes.length);
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public byte[] decrypt(byte[] ivAndCiphertext) {
        if (ivAndCiphertext == null || ivAndCiphertext.length <= GCM_IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("Blob cifrado inválido: debe contener IV(12) + ciphertext");
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            System.arraycopy(ivAndCiphertext, 0, iv, 0, GCM_IV_LENGTH_BYTES);
            byte[] ciphertext = new byte[ivAndCiphertext.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(ivAndCiphertext, GCM_IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Error al descifrar el certificado P12", e);
        }
    }

    public String decryptString(byte[] ivAndCiphertext) {
        return new String(decrypt(ivAndCiphertext), StandardCharsets.UTF_8);
    }
}
