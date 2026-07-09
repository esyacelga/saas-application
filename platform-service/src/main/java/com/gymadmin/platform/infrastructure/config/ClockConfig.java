package com.gymadmin.platform.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * REQ-SAAS-001 (RN-03): time-travel testable — todos los servicios que
 * calculan fechas ({@code SubscriptionJobService}, {@code ActivarTrialService}, ...)
 * inyectan {@link Clock} en lugar de usar {@code LocalDate.now()} directamente.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
