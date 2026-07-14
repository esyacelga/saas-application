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

    /**
     * G9 · ATS mensual. Reúne las tres fuentes que exige el esquema del SRI y delega
     * el armado en {@link AtsXmlBuilder}: las ventas autorizadas (facturas y notas de
     * crédito, que el builder agrupa por cliente y tipo), sus formas de pago (relación
     * 1:N, va en el nodo {@code formasDePago}) y los comprobantes anulados (nodo
     * {@code anulados}, separado de las ventas).
     */
    @Override
    public Mono<byte[]> generarAts(Integer idCompania, Integer anio, Integer mes) {
        return configSriRepository.findFirstByCompania(idCompania)
                .switchIfEmpty(Mono.error(new NotFoundException(
                        "Configuración SRI no encontrada para la empresa " + idCompania)))
                .flatMap(configSri -> Mono.zip(
                        reporteRepository.findAutorizadosPorMes(idCompania, anio, mes).collectList(),
                        reporteRepository.findFormasPagoAutorizadasPorMes(idCompania, anio, mes).collectList(),
                        reporteRepository.findAnuladosPorMes(idCompania, anio, mes).collectList()
                ).flatMap(t -> atsXmlBuilder.buildAts(
                        configSri, t.getT1(), t.getT2(), t.getT3(), anio, mes)));
    }

    @Override
    public Mono<PeriodoResumen> resumenPeriodo(Integer idCompania, LocalDate desde, LocalDate hasta) {
        return reporteRepository.resumenPorPeriodo(idCompania, desde, hasta);
    }
}
