package com.gymadmin.billing.application.service;

import com.gymadmin.billing.domain.port.out.ColaEnvioRepository;
import com.gymadmin.billing.domain.port.out.ComprobanteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetrySchedulerService {

    private final ColaEnvioRepository colaEnvioRepository;
    private final ComprobanteRepository comprobanteRepository;
    private final EnvioSriService envioSriService;

    @Scheduled(fixedDelay = 60000)
    public void procesarPendientes() {
        colaEnvioRepository.findPendientes(10)
                .flatMap(cola -> comprobanteRepository.findById(cola.getIdComprobante())
                        .flatMap(comprobante -> envioSriService.procesarComprobante(
                                comprobante.getId(),
                                comprobante.getIdCompania(),
                                comprobante.getIdSucursal()
                        ))
                        .onErrorResume(e -> {
                            log.error("Error procesando reintento cola {}: {}", cola.getId(), e.getMessage());
                            return Mono.empty();
                        })
                )
                .subscribe();
    }
}
