package com.gymadmin.core.domain.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public class Congelamiento {

    public enum Motivo { viaje, lesion, enfermedad, voluntario, otro }

    private Long id;
    private Long idCompania;
    private Long idSucursal;
    private Long idMembresia;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Motivo motivo;
    private String detalle;
    private Boolean retroactivo;
    private String documentoRespaldo;
    private Long aprobadoPor;
    private LocalDate fechaAprobacion;
    private Long idUsuarioRegistro;
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIdCompania() { return idCompania; }
    public void setIdCompania(Long idCompania) { this.idCompania = idCompania; }

    public Long getIdSucursal() { return idSucursal; }
    public void setIdSucursal(Long idSucursal) { this.idSucursal = idSucursal; }

    public Long getIdMembresia() { return idMembresia; }
    public void setIdMembresia(Long idMembresia) { this.idMembresia = idMembresia; }

    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }

    public Motivo getMotivo() { return motivo; }
    public void setMotivo(Motivo motivo) { this.motivo = motivo; }

    public String getDetalle() { return detalle; }
    public void setDetalle(String detalle) { this.detalle = detalle; }

    public Boolean getRetroactivo() { return retroactivo; }
    public void setRetroactivo(Boolean retroactivo) { this.retroactivo = retroactivo; }

    public String getDocumentoRespaldo() { return documentoRespaldo; }
    public void setDocumentoRespaldo(String documentoRespaldo) { this.documentoRespaldo = documentoRespaldo; }

    public Long getAprobadoPor() { return aprobadoPor; }
    public void setAprobadoPor(Long aprobadoPor) { this.aprobadoPor = aprobadoPor; }

    public LocalDate getFechaAprobacion() { return fechaAprobacion; }
    public void setFechaAprobacion(LocalDate fechaAprobacion) { this.fechaAprobacion = fechaAprobacion; }

    public Long getIdUsuarioRegistro() { return idUsuarioRegistro; }
    public void setIdUsuarioRegistro(Long idUsuarioRegistro) { this.idUsuarioRegistro = idUsuarioRegistro; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
