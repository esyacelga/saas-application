package com.gymadmin.core.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * REQ-SAAS-001 (Fase 4, issue C4): {@link Clock} inyectable anclado a la zona horaria de operación
 * ({@code America/Guayaquil}), para que el "hoy" de negocio (p. ej. la {@code fechaCorte} de
 * "clientes por vencer") no dependa de la zona del proceso JVM.
 *
 * <p>{@code @ConditionalOnMissingBean} permite que los tests inyecten un {@code Clock.fixed(...)}
 * vía {@code @TestConfiguration} para verificar el cálculo de fecha de forma determinista.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONA_OPERACION = ZoneId.of("America/Guayaquil");

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.system(ZONA_OPERACION);
    }
}
