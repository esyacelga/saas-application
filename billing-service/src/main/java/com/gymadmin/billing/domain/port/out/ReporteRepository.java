package com.gymadmin.billing.domain.port.out;

import com.gymadmin.billing.domain.model.AtsPagoComprobante;
import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.model.PeriodoResumen;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface ReporteRepository {

    Flux<Comprobante> findAutorizadosPorMes(Integer idCompania, Integer anio, Integer mes);

    /**
     * G9 · Formas de pago de los comprobantes autorizados del mes, para el nodo
     * {@code formasDePago} del ATS. Se consulta aparte de
     * {@link #findAutorizadosPorMes} porque la relación es 1:N (pago mixto).
     */
    Flux<AtsPagoComprobante> findFormasPagoAutorizadasPorMes(Integer idCompania, Integer anio, Integer mes);

    /**
     * G9 · Comprobantes anulados del mes, para el nodo {@code anulados} del ATS.
     * El SRI los reporta por separado de las ventas, no como una venta en negativo.
     */
    Flux<Comprobante> findAnuladosPorMes(Integer idCompania, Integer anio, Integer mes);

    Mono<PeriodoResumen> resumenPorPeriodo(Integer idCompania, LocalDate desde, LocalDate hasta);
}
