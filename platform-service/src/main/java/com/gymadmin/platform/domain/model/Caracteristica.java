package com.gymadmin.platform.domain.model;

public class Caracteristica {

    private Long id;
    private String codigo;
    private String nombre;
    private String modulo;
    private Boolean activo;

    public Caracteristica() {}

    public Caracteristica(Long id, String codigo, String nombre, String modulo, Boolean activo) {
        this.id = id;
        this.codigo = codigo;
        this.nombre = nombre;
        this.modulo = modulo;
        this.activo = activo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getModulo() { return modulo; }
    public void setModulo(String modulo) { this.modulo = modulo; }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
}
