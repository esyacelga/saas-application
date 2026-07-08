package com.gymadmin.platform.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CompaniaPlan {

    public enum Estado {
        ACTIVO, EN_GRACIA, VENCIDO, SUSPENDIDO, CANCELADO, PROGRAMADO
    }

    public enum TipoCambio {
        NUEVO, RENOVACION, UPGRADE, DOWNGRADE
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
}
