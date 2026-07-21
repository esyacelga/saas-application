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

    /**
     * Variante del flujo de validación de acceso que resuelve el cliente por su propio
     * {@code id_cliente} (PK de {@code core.clientes}) en lugar de por {@code id_persona}.
     * Usado por el flujo de "asistencia manual" del heatmap admin, que sólo conoce el
     * {@code id_cliente}. Reutiliza exactamente la misma lógica de resolución de acceso que
     * {@link #validarAcceso(Long, Long)}.
     */
    Mono<ValidarAccesoResult> validarAccesoPorCliente(Long idCliente, Long idCompania);

    Mono<Membresia> actualizarAsistenciasPrevias(Long id, Long idCompania, Integer cantidad);

    Mono<Membresia> confirmarPago(Long idMembresia, Long idCompania, Long idUsuarioActuante,
                                   ConfirmarPagoCommand command);

    Mono<Membresia> rechazar(Long idMembresia, Long idCompania, Long idUsuarioActuante,
                             Membresia.MotivoEliminacion motivoEliminacion);

    Flux<MembresiaPendienteResult> listarPendientesPorCompania(Long idCompania);

    /**
     * Cliente PWA envía una solicitud de membresía. Crea una fila
     * {@code estado_pago=PENDIENTE}, {@code origen=cliente}, fechas NULL, {@code precio_pagado=0}
     * (placeholder), {@code descuento=0}, {@code id_metodo_pago=NULL}. Resuelve el
     * {@code id_cliente} y el {@code id_sucursal} a partir del registro del cliente
     * ({@code core.clientes}) — la solicitud queda bajo la misma sucursal donde el
     * cliente está registrado.
     */
    Mono<Membresia> solicitarMembresia(Long idPersona, Long idCompania, Long idTipoMembresia);

    /**
     * Conteo de membresías pendientes agrupado por origen. Usado por el badge del dashboard
     * staff para llamar la atención sobre solicitudes autoservicio del cliente.
     */
    Mono<ContadorPendientesResult> contarPendientesPorCompania(Long idCompania);

    record VenderCommand(
        Long idTipoMembresia,
        LocalDate fechaInicio,
        Long idMetodoPago,
        BigDecimal descuentoAplicado,
        Membresia.EstadoPago estadoPago
    ) {}

    /**
     * Datos opcionales del body de {@code confirmar-pago}. Requeridos cuando la
     * membresía es {@code origen='cliente'} (venta autoservicio) porque el cliente no
     * ingresó nada al solicitar. Ignorados cuando la membresía es {@code origen='staff'}
     * (venta directa staff-PENDIENTE ya trae precio, descuento y método al vender).
     * Todos pueden venir en {@code null} si el body no viene.
     */
    record ConfirmarPagoCommand(
        Long idMetodoPago,
        BigDecimal precioPagado,
        BigDecimal descuentoAplicado,
        LocalDate fechaInicio
    ) {
        public static ConfirmarPagoCommand empty() {
            return new ConfirmarPagoCommand(null, null, null, null);
        }
    }

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

    /**
     * Resultado del badge del dashboard: cantidad total de membresías PENDIENTE
     * vivas de la compañía y desglose por origen. Ambas cuentas de origen son 0
     * cuando no hay pendientes de ese origen.
     */
    record ContadorPendientesResult(long total, long porOrigenCliente, long porOrigenStaff) {}
}
