package com.gymadmin.core.domain.port.in;

import com.gymadmin.core.domain.model.Membresia;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface MembresiaUseCase {

    /**
     * Historial de membresías del cliente enriquecido con nombre y modo del tipo, y con
     * conteo de accesos usados/restantes cuando aplica ({@code modo_control = accesos}).
     * Incluye membresías con {@code eliminado = true} — la UI las muestra con badge y motivo.
     */
    Flux<MembresiaHistorialItem> historialPorCliente(Long idCliente, Long idCompania);

    /**
     * Alias de {@link #historialPorCliente(Long, Long)} que resuelve el {@code id_cliente} a
     * partir del {@code id_persona} + {@code id_compania} del JWT del socio autenticado.
     * Usado por la PWA (endpoint {@code /clientes/me/membresias}).
     */
    Flux<MembresiaHistorialItem> historialPorPersona(Long idPersona, Long idCompania);

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

    /**
     * Fila del historial enriquecida con nombre y modo del tipo, monto pagado / saldo
     * derivados del {@code estado_pago} (fuente de verdad única mientras no exista
     * {@code core.pagos} — deuda técnica documentada en {@code docs/gym-administrator/requirements/estado-pago-membresias.md} §7 HU-C), y para membresías por accesos, el conteo de usados/restantes.
     */
    record MembresiaHistorialItem(
        Membresia membresia,
        String tipoNombre,
        String modoControl,
        java.math.BigDecimal montoPagado,
        java.math.BigDecimal saldoPendiente,
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
