package com.gymadmin.platform.domain.model;

import java.time.LocalDateTime;

public class Sucursal {

    private Long id;
    private Long idCompania;
    private String nombre;
    private String direccion;
    private Boolean esPrincipal;
    private Boolean activo;
    private String qrToken;
    private LocalDateTime qrTokenExpira;

    public Sucursal() {}

    public Sucursal(Long id, Long idCompania, String nombre, String direccion,
                    Boolean esPrincipal, Boolean activo, String qrToken,
                    LocalDateTime qrTokenExpira) {
        this.id = id;
        this.idCompania = idCompania;
        this.nombre = nombre;
        this.direccion = direccion;
        this.esPrincipal = esPrincipal;
        this.activo = activo;
        this.qrToken = qrToken;
        this.qrTokenExpira = qrTokenExpira;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIdCompania() { return idCompania; }
    public void setIdCompania(Long idCompania) { this.idCompania = idCompania; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public Boolean getEsPrincipal() { return esPrincipal; }
    public void setEsPrincipal(Boolean esPrincipal) { this.esPrincipal = esPrincipal; }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }

    public String getQrToken() { return qrToken; }
    public void setQrToken(String qrToken) { this.qrToken = qrToken; }

    public LocalDateTime getQrTokenExpira() { return qrTokenExpira; }
    public void setQrTokenExpira(LocalDateTime qrTokenExpira) { this.qrTokenExpira = qrTokenExpira; }
}
