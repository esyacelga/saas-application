package com.gymadmin.finance.domain.port.in;

import com.gymadmin.finance.domain.model.Ingreso;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface IngresoUseCase {

    Mono<IngresoListResult> listar(ListarCommand command);

    Mono<Ingreso> registrar(RegistrarCommand command);

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
            Integer idMembresia,
            Integer idVenta,
            Integer idUsuarioRegistro
    ) {}

    record IngresoResumen(
            Integer id,
            String categoria,
            BigDecimal monto,
            String descripcion,
            LocalDate fecha,
            String origen,
            Integer idReferencia
    ) {}

    record IngresoListResult(
            BigDecimal totalPeriodo,
            Long totalRegistros,
            List<IngresoResumen> datos
    ) {}
}
