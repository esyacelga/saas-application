package com.gymadmin.billing.application.service;

import com.gymadmin.billing.domain.port.out.CertificadoRepository;
import com.gymadmin.billing.domain.port.out.ConfigSriRepository;
import com.gymadmin.billing.domain.port.out.EmailNotificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CertificadoAlertaService {

    private static final int DIAS_ALERTA = 30;

    private final CertificadoRepository certificadoRepository;
    private final ConfigSriRepository configSriRepository;
    private final EmailNotificationPort emailNotificationPort;

    @Scheduled(cron = "0 0 8 * * *")
    public void verificarVencimientos() {
        log.info("Iniciando verificación de vencimientos de certificados digitales");

        certificadoRepository.findProximosAVencer(DIAS_ALERTA)
                .flatMap(cert -> configSriRepository.findFirstByCompania(cert.idCompania())
                        .flatMap(config -> {
                            String emailDestino = config.getEmailNotificacion();
                            if (emailDestino == null || emailDestino.isBlank()) {
                                log.warn("Sin email de notificación configurado para empresa {}", cert.idCompania());
                                return reactor.core.publisher.Mono.empty();
                            }
                            return emailNotificationPort.enviarAlertaVencimientoCertificado(
                                    emailDestino,
                                    config.getRuc(),
                                    config.getRazonSocial(),
                                    cert.fechaVencimiento()
                            ).doOnSuccess(v -> log.info(
                                    "Alerta de vencimiento enviada para empresa {} (vence {})",
                                    cert.idCompania(), cert.fechaVencimiento()
                            ));
                        })
                        .onErrorResume(ex -> {
                            log.error("Error al procesar alerta para empresa {}: {}",
                                    cert.idCompania(), ex.getMessage());
                            return reactor.core.publisher.Mono.empty();
                        })
                )
                .subscribe(
                        null,
                        ex -> log.error("Error en verificación de vencimientos: {}", ex.getMessage()),
                        () -> log.info("Verificación de vencimientos completada")
                );
    }
}
