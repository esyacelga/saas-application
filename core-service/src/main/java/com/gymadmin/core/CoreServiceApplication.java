package com.gymadmin.core;

import com.gymadmin.core.infrastructure.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class CoreServiceApplication {

    public static void main(String[] args) {
        // Zona de operación Ecuador (UTC-5) — uniforma la zona por defecto de la JVM con el resto de
        // microservicios, de modo que LocalDate/LocalTime.now() y la serialización de fechas reflejen
        // hora local de Ecuador (2026-07-21).
        TimeZone.setDefault(TimeZone.getTimeZone("America/Guayaquil"));
        SpringApplication.run(CoreServiceApplication.class, args);
    }
}
