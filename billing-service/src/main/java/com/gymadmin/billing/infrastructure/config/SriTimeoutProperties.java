package com.gymadmin.billing.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * G2 · Transmisión inmediata. Configuración del timeout aplicado al pipeline
 * síncrono {@code firma → envío → autorización} disparado desde
 * {@code POST /api/v1/comprobantes/facturas}.
 * <p>
 * Property:
 * <ul>
 *     <li>{@code sri.timeout.envio-seconds} — default {@code 15}.</li>
 * </ul>
 */
@Configuration
@ConfigurationProperties(prefix = "sri.timeout")
@Getter
@Setter
public class SriTimeoutProperties {

    /** Timeout en segundos del pipeline síncrono de emisión inmediata al SRI. */
    private int envioSeconds = 15;

    public Duration getEnvioDuration() {
        return Duration.ofSeconds(envioSeconds);
    }
}
