package com.gymadmin.billing.domain.model;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Solicitud de anulación fiscal SRI Ecuador — tabla
 * {@code facturacion.anulaciones}.
 * <p>
 * Cubre los dos flujos operativos del SRI:
 * <ul>
 *   <li><b>Flujo A · anulación directa</b>: solicitud → aprobación → confirmación
 *       manual del portal SRI. Sin NC.</li>
 *   <li><b>Flujo B · anulación con nota de crédito</b>: solicitud → aprobación
 *       que dispara la emisión de una NC tipo {@code "04"} (G4). Al llegar la NC
 *       a {@code AUTORIZADO} se completa la anulación y {@link #idComprobanteNc}
 *       apunta al comprobante NC generado.</li>
 * </ul>
 * <p>
 * El flag "generar nota de crédito" no es una columna en el DDL; se codifica en
 * {@link #observacionResolucion} como el prefijo interno {@code [FLUJO_B]} en el
 * momento de la solicitud. Ver {@code AnulacionService} para el manejo.
 */
@Data
@Builder(toBuilder = true)
public class Anulacion {

    private Long id;
    private Integer idCompania;
    private Integer idSucursal;
    /** FK a {@code facturacion.comprobantes.id} — la factura que se solicita anular. */
    private Long idComprobante;
    /** Motivo libre aportado por el solicitante (obligatorio). */
    private String motivo;
    /** Estado dominio; se serializa como {@link Enum#name()} contra la CHECK. */
    private EstadoAnulacion estado;
    /**
     * FK a {@code facturacion.comprobantes.id} de la NC generada (Flujo B).
     * {@code null} para Flujo A o mientras la NC no haya sido emitida aún.
     */
    private Long idComprobanteNc;
    private Integer idUsuarioSolicita;
    private Integer idUsuarioAprueba;
    private OffsetDateTime fechaSolicitud;
    private OffsetDateTime fechaResolucion;
    /**
     * Texto libre asociado a la resolución. Puede contener el prefijo interno
     * {@code [FLUJO_B]} para señalizar al servicio que la aprobación debe emitir
     * NC. La UI no debe exponer ese prefijo.
     */
    private String observacionResolucion;
}
