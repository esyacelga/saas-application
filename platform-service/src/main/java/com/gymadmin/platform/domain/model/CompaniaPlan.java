package com.gymadmin.platform.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CompaniaPlan {

    public enum Estado {
        ACTIVO, EN_GRACIA, VENCIDO, SUSPENDIDO, CANCELADO, PROGRAMADO,
        // REQ-SAAS-001 (RN-10): la ACTIVA/EN_GRACIA pasa a REEMPLAZADA cuando se activa un PROGRAMADO.
        // Indica "vencida por upgrade" (no por vencimiento). Es un estado terminal.
        REEMPLAZADA
    }

    public enum TipoCambio {
        NUEVO, RENOVACION, UPGRADE, DOWNGRADE,
        // REQ-SAAS-001 (RN-03): degradación automática Trial/Premium → Free por vencimiento.
        DEGRADACION_AUTO,
        // REQ-SAAS-001 (RN-09): cancelación voluntaria iniciada por el owner.
        CANCELACION,
        // REQ-SAAS-001 (RN-09): suspensión administrativa iniciada por root.
        SUSPENSION
    }

    /**
     * REQ-SAAS-001: causa de la última degradación automática registrada en la fila.
     * Se persiste en {@code compania_planes.causa_degradacion} (minúsculas en DB).
     */
    public enum CausaDegradacion {
        VENCIMIENTO, PAGO_RECHAZADO, CANCELACION_MANUAL, SUSPENSION_ROOT
    }

    private Long id;
    private Long idCompania;
    private Long idPlan;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Integer diasGracia;
    private LocalDate fechaUltimoPago;
    private String motivoSuspension;
    private Estado estado;
    private TipoCambio tipoCambio;
    private Long idCompaniaPlanOrig;
    private BigDecimal creditoMonto;

    // REQ-SAAS-001 — Sub-fase 1.2 (RN-06): modo sobre-límite tras degradación a Free.
    private boolean sobreLimite;
    private LocalDate sobreLimiteHasta;
    // Persistido como String para poder guardar NULL sin sentinel; se mapea a CausaDegradacion en la capa de dominio cuando aplica.
    private String causaDegradacion;

    public CompaniaPlan() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIdCompania() { return idCompania; }
    public void setIdCompania(Long idCompania) { this.idCompania = idCompania; }

    public Long getIdPlan() { return idPlan; }
    public void setIdPlan(Long idPlan) { this.idPlan = idPlan; }

    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }

    public Integer getDiasGracia() { return diasGracia; }
    public void setDiasGracia(Integer diasGracia) { this.diasGracia = diasGracia; }

    public LocalDate getFechaUltimoPago() { return fechaUltimoPago; }
    public void setFechaUltimoPago(LocalDate fechaUltimoPago) { this.fechaUltimoPago = fechaUltimoPago; }

    public String getMotivoSuspension() { return motivoSuspension; }
    public void setMotivoSuspension(String motivoSuspension) { this.motivoSuspension = motivoSuspension; }

    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }

    public TipoCambio getTipoCambio() { return tipoCambio; }
    public void setTipoCambio(TipoCambio tipoCambio) { this.tipoCambio = tipoCambio; }

    public Long getIdCompaniaPlanOrig() { return idCompaniaPlanOrig; }
    public void setIdCompaniaPlanOrig(Long idCompaniaPlanOrig) { this.idCompaniaPlanOrig = idCompaniaPlanOrig; }

    public BigDecimal getCreditoMonto() { return creditoMonto; }
    public void setCreditoMonto(BigDecimal creditoMonto) { this.creditoMonto = creditoMonto; }

    public boolean isSobreLimite() { return sobreLimite; }
    public void setSobreLimite(boolean sobreLimite) { this.sobreLimite = sobreLimite; }

    public LocalDate getSobreLimiteHasta() { return sobreLimiteHasta; }
    public void setSobreLimiteHasta(LocalDate sobreLimiteHasta) { this.sobreLimiteHasta = sobreLimiteHasta; }

    public String getCausaDegradacion() { return causaDegradacion; }
    public void setCausaDegradacion(String causaDegradacion) { this.causaDegradacion = causaDegradacion; }
}
