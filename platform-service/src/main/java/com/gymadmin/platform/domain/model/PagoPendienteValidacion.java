package com.gymadmin.platform.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * REQ-SAAS-001 (RN-08): buzón de pagos reportados por el owner pendientes
 * de aprobación manual por root/soporte. Al aprobarse, se materializa una fila
 * en {@code tenant.pagos_suscripcion} (histórico contable) y se activa la
 * suscripción Premium correspondiente.
 * <p>
 * Tabla física: {@code tenant.pagos_pendientes_validacion}.
 */
public class PagoPendienteValidacion {

    public enum Estado {
        PENDIENTE, APROBADO, RECHAZADO
    }

    private Long id;
    private Long idCompania;
    private Long idPlanDestino;

    private BigDecimal monto;
    private String moneda;

    private Instant fechaReporte;
    private LocalDate fechaTransferencia;

    private String comprobanteUrl;
    private String comprobanteHash;

    private String bancoOrigen;
    private String referencia;

    private String hashIdempotencia;

    private Estado estado;
    private String motivoRechazo;

    private Long aprobadoPor;
    private Instant fechaAprobacion;

    /** RN-05: true si es upgrade Trial→Premium agendado — el Premium se activa al vencer el Trial. */
    private boolean activacionProgramada;

    /** Reservado para fase 4 (facturación SRI). NULL en fase 1. */
    private Long facturaEmitidaId;

    // Auditoría estándar (patrón BaseAuditEntity).
    private Boolean eliminado;
    private OffsetDateTime creacionFecha;
    private String creacionUsuario;
    private OffsetDateTime modificaFecha;
    private String modificaUsuario;

    public PagoPendienteValidacion() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIdCompania() { return idCompania; }
    public void setIdCompania(Long idCompania) { this.idCompania = idCompania; }

    public Long getIdPlanDestino() { return idPlanDestino; }
    public void setIdPlanDestino(Long idPlanDestino) { this.idPlanDestino = idPlanDestino; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public String getMoneda() { return moneda; }
    public void setMoneda(String moneda) { this.moneda = moneda; }

    public Instant getFechaReporte() { return fechaReporte; }
    public void setFechaReporte(Instant fechaReporte) { this.fechaReporte = fechaReporte; }

    public LocalDate getFechaTransferencia() { return fechaTransferencia; }
    public void setFechaTransferencia(LocalDate fechaTransferencia) { this.fechaTransferencia = fechaTransferencia; }

    public String getComprobanteUrl() { return comprobanteUrl; }
    public void setComprobanteUrl(String comprobanteUrl) { this.comprobanteUrl = comprobanteUrl; }

    public String getComprobanteHash() { return comprobanteHash; }
    public void setComprobanteHash(String comprobanteHash) { this.comprobanteHash = comprobanteHash; }

    public String getBancoOrigen() { return bancoOrigen; }
    public void setBancoOrigen(String bancoOrigen) { this.bancoOrigen = bancoOrigen; }

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }

    public String getHashIdempotencia() { return hashIdempotencia; }
    public void setHashIdempotencia(String hashIdempotencia) { this.hashIdempotencia = hashIdempotencia; }

    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }

    public String getMotivoRechazo() { return motivoRechazo; }
    public void setMotivoRechazo(String motivoRechazo) { this.motivoRechazo = motivoRechazo; }

    public Long getAprobadoPor() { return aprobadoPor; }
    public void setAprobadoPor(Long aprobadoPor) { this.aprobadoPor = aprobadoPor; }

    public Instant getFechaAprobacion() { return fechaAprobacion; }
    public void setFechaAprobacion(Instant fechaAprobacion) { this.fechaAprobacion = fechaAprobacion; }

    public boolean isActivacionProgramada() { return activacionProgramada; }
    public void setActivacionProgramada(boolean activacionProgramada) { this.activacionProgramada = activacionProgramada; }

    public Long getFacturaEmitidaId() { return facturaEmitidaId; }
    public void setFacturaEmitidaId(Long facturaEmitidaId) { this.facturaEmitidaId = facturaEmitidaId; }

    public Boolean getEliminado() { return eliminado; }
    public void setEliminado(Boolean eliminado) { this.eliminado = eliminado; }

    public OffsetDateTime getCreacionFecha() { return creacionFecha; }
    public void setCreacionFecha(OffsetDateTime creacionFecha) { this.creacionFecha = creacionFecha; }

    public String getCreacionUsuario() { return creacionUsuario; }
    public void setCreacionUsuario(String creacionUsuario) { this.creacionUsuario = creacionUsuario; }

    public OffsetDateTime getModificaFecha() { return modificaFecha; }
    public void setModificaFecha(OffsetDateTime modificaFecha) { this.modificaFecha = modificaFecha; }

    public String getModificaUsuario() { return modificaUsuario; }
    public void setModificaUsuario(String modificaUsuario) { this.modificaUsuario = modificaUsuario; }
}
