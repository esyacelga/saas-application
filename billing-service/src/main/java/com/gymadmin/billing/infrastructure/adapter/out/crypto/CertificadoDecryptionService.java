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
 */
@Service
public class CertificadoDecryptionService {

    private static final int GCM_TAG_LENGTH_BITS = 128;

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

    public byte[] decrypt(byte[] ciphertext, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Error al descifrar el certificado P12", e);
        }
    }

    public String decryptString(String base64Ciphertext, byte[] iv) {
        byte[] decrypted = decrypt(Base64.getDecoder().decode(base64Ciphertext), iv);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}
