package com.gymadmin.core.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public class Membresia {

    public enum Estado { activa, vencida, congelada, anulada }

    public enum EstadoPago { PENDIENTE, PAGADO }

    public enum MotivoEliminacion {
        SOCIO_CAMBIO_OPINION, ERROR_DE_VENTA, DUPLICADA, DATOS_INCORRECTOS, OTRO
    }

    private Long id;
    private Long idCompania;
    private Long idSucursal;
    private Long idCliente;
    private Long idTipoMembresia;
    private Long idMetodoPago;
    private Long idUsuarioRegistro;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Integer diasAccesoTotal;
    private BigDecimal precioPagado;
    private BigDecimal descuentoAplicado;
    private Estado estado;
    private Integer asistenciasPrevias;
    private OffsetDateTime createdAt;
    private EstadoPago estadoPago;
    private Boolean eliminado;
    private OffsetDateTime fechaEliminacion;
    private Integer eliminadoPor;
    private MotivoEliminacion motivoEliminacion;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIdCompania() { return idCompania; }
    public void setIdCompania(Long idCompania) { this.idCompania = idCompania; }

    public Long getIdSucursal() { return idSucursal; }
    public void setIdSucursal(Long idSucursal) { this.idSucursal = idSucursal; }

    public Long getIdCliente() { return idCliente; }
    public void setIdCliente(Long idCliente) { this.idCliente = idCliente; }

    public Long getIdTipoMembresia() { return idTipoMembresia; }
    public void setIdTipoMembresia(Long idTipoMembresia) { this.idTipoMembresia = idTipoMembresia; }

    public Long getIdMetodoPago() { return idMetodoPago; }
    public void setIdMetodoPago(Long idMetodoPago) { this.idMetodoPago = idMetodoPago; }

    public Long getIdUsuarioRegistro() { return idUsuarioRegistro; }
    public void setIdUsuarioRegistro(Long idUsuarioRegistro) { this.idUsuarioRegistro = idUsuarioRegistro; }

    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }

    public Integer getDiasAccesoTotal() { return diasAccesoTotal; }
    public void setDiasAccesoTotal(Integer diasAccesoTotal) { this.diasAccesoTotal = diasAccesoTotal; }

    public BigDecimal getPrecioPagado() { return precioPagado; }
    public void setPrecioPagado(BigDecimal precioPagado) { this.precioPagado = precioPagado; }

    public BigDecimal getDescuentoAplicado() { return descuentoAplicado; }
    public void setDescuentoAplicado(BigDecimal descuentoAplicado) { this.descuentoAplicado = descuentoAplicado; }

    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }

    public Integer getAsistenciasPrevias() { return asistenciasPrevias; }
    public void setAsistenciasPrevias(Integer asistenciasPrevias) { this.asistenciasPrevias = asistenciasPrevias; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public EstadoPago getEstadoPago() { return estadoPago; }
    public void setEstadoPago(EstadoPago estadoPago) { this.estadoPago = estadoPago; }

    public Boolean getEliminado() { return eliminado; }
    public void setEliminado(Boolean eliminado) { this.eliminado = eliminado; }

    public OffsetDateTime getFechaEliminacion() { return fechaEliminacion; }
    public void setFechaEliminacion(OffsetDateTime fechaEliminacion) { this.fechaEliminacion = fechaEliminacion; }

    public Integer getEliminadoPor() { return eliminadoPor; }
    public void setEliminadoPor(Integer eliminadoPor) { this.eliminadoPor = eliminadoPor; }

    public MotivoEliminacion getMotivoEliminacion() { return motivoEliminacion; }
    public void setMotivoEliminacion(MotivoEliminacion motivoEliminacion) { this.motivoEliminacion = motivoEliminacion; }
}
