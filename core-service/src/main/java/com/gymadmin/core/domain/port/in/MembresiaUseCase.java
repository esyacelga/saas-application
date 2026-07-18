package com.gymadmin.core.domain.port.in;

import com.gymadmin.core.domain.model.Membresia;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface MembresiaUseCase {

    Flux<Membresia> historialPorCliente(Long idCliente, Long idCompania);

    Mono<MembresiaDetalleResult> detalle(Long id, Long idCompania);

    Mono<Membresia> vender(Long idCliente, Long idCompania, Long idSucursal, Long idUsuario, VenderCommand command);

    Mono<Void> anular(Long id, Long idCompania, String motivo);

    Mono<ValidarAccesoResult> validarAcceso(Long idPersona, Long idCompania);

    Mono<Membresia> actualizarAsistenciasPrevias(Long id, Long idCompania, Integer cantidad);

    Mono<Membresia> confirmarPago(Long idMembresia, Long idCompania, Long idUsuarioActuante);

    Mono<Membresia> rechazar(Long idMembresia, Long idCompania, Long idUsuarioActuante,
                             Membresia.MotivoEliminacion motivoEliminacion);

    Flux<MembresiaPendienteResult> listarPendientesPorCompania(Long idCompania);

    record VenderCommand(
        Long idTipoMembresia,
        LocalDate fechaInicio,
        Long idMetodoPago,
        BigDecimal descuentoAplicado,
        Membresia.EstadoPago estadoPago
    ) {}

    record MembresiaDetalleResult(
        Membresia membresia,
        String tipoNombre,
        String modoControl,
        Integer diasAccesoUsados,
        Integer diasAccesoRestantes
    ) {}

    record ValidarAccesoResult(
        boolean permitido,
        Long idCliente,
        Long idMembresia,
        String modoControl,
        String tipoNombre,
        Integer diasAccesoRestantes,
        LocalDate fechaFin,
        String razon,
        Integer accesosUsados,
        Integer accesosTotal
    ) {}

    record MembresiaPendienteResult(
        Membresia membresia,
        String tipoNombre,
        String modoControl,
        String nombreCliente
    ) {}
}
