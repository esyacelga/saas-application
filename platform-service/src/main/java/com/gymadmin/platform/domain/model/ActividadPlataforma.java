package com.gymadmin.platform.domain.model;

import java.time.OffsetDateTime;

public class ActividadPlataforma {

    private Long id;
    private String tipoEvento;
    private String modulo;
    private Long entidadId;
    private String entidadNombre;
    private String detalle;
    private String usuario;
    private String ip;
    private OffsetDateTime fecha;

    public ActividadPlataforma() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTipoEvento() { return tipoEvento; }
    public void setTipoEvento(String tipoEvento) { this.tipoEvento = tipoEvento; }

    public String getModulo() { return modulo; }
    public void setModulo(String modulo) { this.modulo = modulo; }

    public Long getEntidadId() { return entidadId; }
    public void setEntidadId(Long entidadId) { this.entidadId = entidadId; }

    public String getEntidadNombre() { return entidadNombre; }
    public void setEntidadNombre(String entidadNombre) { this.entidadNombre = entidadNombre; }

    public String getDetalle() { return detalle; }
    public void setDetalle(String detalle) { this.detalle = detalle; }

    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public OffsetDateTime getFecha() { return fecha; }
    public void setFecha(OffsetDateTime fecha) { this.fecha = fecha; }
}
