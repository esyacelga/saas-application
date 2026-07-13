package com.gymadmin.billing.application.service;

import com.gymadmin.billing.domain.model.PeriodoResumen;
import com.gymadmin.billing.domain.port.in.ReporteUseCase;
import com.gymadmin.billing.domain.port.out.ConfigSriRepository;
import com.gymadmin.billing.domain.port.out.ReporteRepository;
import com.gymadmin.billing.infrastructure.adapter.out.xml.AtsXmlBuilder;
import com.gymadmin.billing.infrastructure.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ReporteService implements ReporteUseCase {

    private final ReporteRepository reporteRepository;
    private final ConfigSriRepository configSriRepository;
    private final AtsXmlBuilder atsXmlBuilder;

    // TODO(G9 · Fase 3): incluir notas de crédito (tipo 04) y anulaciones en el
    // ATS mensual. Hoy `findAutorizadosPorMes` filtra `tipo_comprobante = '01'`
    // y `AtsXmlBuilder` hardcodea `tipoComp='01'`. Con G3 (anulación fiscal)
    // ya se emiten NC y se registran ANULADO en `facturacion.comprobantes`;
    // el ATS debe reportarlos para evitar cruces automáticos del SRI. Ver
    // docs/billing-service/pendientes/gap-analysis-sri-2026.md §G9.
    @Override
    public Mono<byte[]> generarAts(Integer idCompania, Integer anio, Integer mes) {
        return configSriRepository.findFirstByCompania(idCompania)
                .switchIfEmpty(Mono.error(new NotFoundException(
                        "Configuración SRI no encontrada para la empresa " + idCompania)))
                .flatMap(configSri ->
                        reporteRepository.findAutorizadosPorMes(idCompania, anio, mes)
                                .collectList()
                                .flatMap(comprobantes -> atsXmlBuilder.buildAts(configSri, comprobantes, anio, mes))
                );
    }

    @Override
    public Mono<PeriodoResumen> resumenPeriodo(Integer idCompania, LocalDate desde, LocalDate hasta) {
        return reporteRepository.resumenPorPeriodo(idCompania, desde, hasta);
    }
}
