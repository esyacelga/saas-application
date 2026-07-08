package com.gymadmin.finance.domain.port.in;

import com.gymadmin.finance.domain.model.Egreso;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface EgresoUseCase {

    Mono<EgresoListResult> listar(ListarCommand command);

    Mono<Egreso> registrar(RegistrarCommand command);

    record ListarCommand(
            Integer idCompania,
            LocalDate desde,
            LocalDate hasta,
            Integer idCategoria,
            int page,
            int limit
    ) {}

    record RegistrarCommand(
            Integer idCompania,
            Integer idSucursal,
            Integer idCategoria,
            BigDecimal monto,
            String descripcion,
            LocalDate fecha,
            Integer idUsuarioRegistro
    ) {}

    record EgresoResumen(
            Integer id,
            String categoria,
            BigDecimal monto,
            String descripcion,
            LocalDate fecha
    ) {}

    record EgresoListResult(
            BigDecimal totalPeriodo,
            Long totalRegistros,
            List<EgresoResumen> datos
    ) {}
}
