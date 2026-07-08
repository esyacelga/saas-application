package com.gymadmin.platform.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class QrTokenService {

    private static final String HEX_CHARS = "0123456789abcdef";
    private final SecureRandom secureRandom = new SecureRandom();
    private final int tokenLength;

    public QrTokenService(@Value("${qr-token.length:32}") int tokenLength) {
        this.tokenLength = tokenLength;
    }

    public String generateToken() {
        StringBuilder sb = new StringBuilder(tokenLength * 2);
        byte[] bytes = new byte[tokenLength];
        secureRandom.nextBytes(bytes);
        for (byte b : bytes) {
            sb.append(HEX_CHARS.charAt((b >> 4) & 0xF));
            sb.append(HEX_CHARS.charAt(b & 0xF));
        }
        return sb.substring(0, tokenLength);
    }
}
