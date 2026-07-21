package com.gymadmin.platform.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * REQ-SAAS-001 (RN-03): time-travel testable — todos los servicios que
 * calculan fechas ({@code SubscriptionJobService}, {@code ActivarTrialService}, ...)
 * inyectan {@link Clock} en lugar de usar {@code LocalDate.now()} directamente.
 *
 * <p>Anclado a {@code America/Guayaquil} (2026-07-21, uniformación de zona horaria) para que el
 * "hoy" de negocio ({@code LocalDate.now(clock)} en jobs de suscripción/vencimiento/buckets) sea el
 * día civil de Ecuador y no el de UTC — de noche (19:00–24:00 hora Ecuador) UTC ya está en el día
 * siguiente, lo que adelantaba vencimientos y avisos. Los {@code Instant.now(clock)} no se ven
 * afectados (un instante es absoluto); solo cambia el cálculo de fecha civil.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONA_OPERACION = ZoneId.of("America/Guayaquil");

    @Bean
    public Clock clock() {
        return Clock.system(ZONA_OPERACION);
    }
}
