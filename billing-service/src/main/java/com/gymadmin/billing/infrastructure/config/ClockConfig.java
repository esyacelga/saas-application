package com.gymadmin.billing.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * G3 · Bean {@link Clock} usado por reglas fiscales que dependen del "hoy"
 * (ventana temporal de anulación SRI). Se aísla del reloj del sistema para
 * poder inyectar un {@code Clock} fijo en tests.
 * <p>
 * Se usa la zona {@code America/Guayaquil} porque el SRI opera en hora Ecuador
 * y la ventana "día 7 del mes siguiente" es día calendario Ecuador, no UTC.
 */
@Configuration
public class ClockConfig {

    private static final ZoneId ZONE_ECUADOR = ZoneId.of("America/Guayaquil");

    @Bean
    public Clock systemClock() {
        return Clock.system(ZONE_ECUADOR);
    }
}
