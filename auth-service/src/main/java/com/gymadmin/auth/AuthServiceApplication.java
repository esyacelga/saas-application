package com.gymadmin.auth;

import com.gymadmin.auth.infrastructure.config.AppProperties;
import com.gymadmin.auth.infrastructure.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, AppProperties.class})
@EnableAsync
public class AuthServiceApplication {
    public static void main(String[] args) {
        // Zona de operación Ecuador (UTC-5) — uniforma la zona por defecto de la JVM con el resto de
        // microservicios, de modo que LocalDate/LocalTime.now() y la serialización de fechas reflejen
        // hora local de Ecuador (2026-07-21).
        TimeZone.setDefault(TimeZone.getTimeZone("America/Guayaquil"));
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
