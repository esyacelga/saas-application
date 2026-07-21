package com.gymadmin.billing;

import com.gymadmin.billing.infrastructure.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
public class BillingServiceApplication {

    public static void main(String[] args) {
        // Zona de operación Ecuador (UTC-5) — uniforma la zona por defecto de la JVM con el resto de
        // microservicios (2026-07-21). El SRI opera en hora Ecuador; la lógica fiscal ya usa el Clock
        // inyectable (ClockConfig, también America/Guayaquil).
        TimeZone.setDefault(TimeZone.getTimeZone("America/Guayaquil"));
        SpringApplication.run(BillingServiceApplication.class, args);
    }
}
