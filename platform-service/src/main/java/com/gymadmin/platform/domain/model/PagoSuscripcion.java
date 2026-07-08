package com.gymadmin.platform.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PagoSuscripcion {

    public enum MetodoPago {
        EFECTIVO, TRANSFERENCIA, TARJETA
    }

    public enum TipoPago {
        PAGO_COMPLETO, DIFERENCIA_UPGRADE, CREDITO_DOWNGRADE, RENOVACION
    }

    public enum EstadoPago {
        PAGADO, FALLIDO, PENDIENTE
    }

    private Long id;
    private Long idCompaniaPlan;
    private BigDecimal monto;
    private LocalDate fechaPago;
    private LocalDate periodoDesde;
    private LocalDate periodoHasta;
    private MetodoPago metodoPago;
    private TipoPago tipoPago;
    private EstadoPago estado;
    private String referencia;

    public PagoSuscripcion() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIdCompaniaPlan() { return idCompaniaPlan; }
    public void setIdCompaniaPlan(Long idCompaniaPlan) { this.idCompaniaPlan = idCompaniaPlan; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public LocalDate getFechaPago() { return fechaPago; }
    public void setFechaPago(LocalDate fechaPago) { this.fechaPago = fechaPago; }

    public LocalDate getPeriodoDesde() { return periodoDesde; }
    public void setPeriodoDesde(LocalDate periodoDesde) { this.periodoDesde = periodoDesde; }

    public LocalDate getPeriodoHasta() { return periodoHasta; }
    public void setPeriodoHasta(LocalDate periodoHasta) { this.periodoHasta = periodoHasta; }

    public MetodoPago getMetodoPago() { return metodoPago; }
    public void setMetodoPago(MetodoPago metodoPago) { this.metodoPago = metodoPago; }

    public TipoPago getTipoPago() { return tipoPago; }
    public void setTipoPago(TipoPago tipoPago) { this.tipoPago = tipoPago; }

    public EstadoPago getEstado() { return estado; }
    public void setEstado(EstadoPago estado) { this.estado = estado; }

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }
}
