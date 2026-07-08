package com.gymadmin.core.domain.model;

import java.math.BigDecimal;

public class TipoMembresia {

    public enum ModoControl { calendario, accesos }
    public enum DuracionTipo { dias, semanas, meses, años }

    private Long id;
    private Long idCompania;
    private Long idSucursal;
    private String nombre;
    private ModoControl modoControl;
    private DuracionTipo duracionTipo;
    private Integer duracionValor;
    private Integer diasAcceso;
    private BigDecimal precio;
    private Boolean activo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIdCompania() { return idCompania; }
    public void setIdCompania(Long idCompania) { this.idCompania = idCompania; }

    public Long getIdSucursal() { return idSucursal; }
    public void setIdSucursal(Long idSucursal) { this.idSucursal = idSucursal; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public ModoControl getModoControl() { return modoControl; }
    public void setModoControl(ModoControl modoControl) { this.modoControl = modoControl; }

    public DuracionTipo getDuracionTipo() { return duracionTipo; }
    public void setDuracionTipo(DuracionTipo duracionTipo) { this.duracionTipo = duracionTipo; }

    public Integer getDuracionValor() { return duracionValor; }
    public void setDuracionValor(Integer duracionValor) { this.duracionValor = duracionValor; }

    public Integer getDiasAcceso() { return diasAcceso; }
    public void setDiasAcceso(Integer diasAcceso) { this.diasAcceso = diasAcceso; }

    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
}
