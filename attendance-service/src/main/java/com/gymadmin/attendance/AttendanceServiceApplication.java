package com.gymadmin.attendance;

import com.gymadmin.attendance.infrastructure.config.AppProperties;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
public class AttendanceServiceApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Guayaquil"));
        cargarDotEnv();
        SpringApplication.run(AttendanceServiceApplication.class, args);
    }

    /**
     * Carga las variables del `.env` de la raíz del proyecto como System properties (incl. META_*
     * e INTERNAL_SECRET que consumen MetaWhatsAppAdapter/CoreServiceClient vía @Value). No pisa
     * variables ya definidas en el entorno real (prod/Docker), donde el .env no existe y se ignora.
     */
    private static void cargarDotEnv() {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();
        dotenv.entries(Dotenv.Filter.DECLARED_IN_ENV_FILE).forEach(entry -> {
            if (System.getProperty(entry.getKey()) == null && System.getenv(entry.getKey()) == null) {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        });
    }
}
