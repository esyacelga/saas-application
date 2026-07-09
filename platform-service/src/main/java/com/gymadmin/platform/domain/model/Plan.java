package com.gymadmin.platform.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class Plan {

    private Long id;
    private String nombre;
    private String descripcion;
    private BigDecimal precioMensual;
    private Boolean activo;
    private LocalDateTime createdAt;
    private List<Caracteristica> caracteristicas;

    // REQ-SAAS-001 — Sub-fase 1.2: nuevos atributos del esquema Free / Trial / Premium
    private String codigo;                 // FREE / TRIAL / PREMIUM / LEGACY_GRANDFATHERED (UNIQUE en DB)
    private Integer duracionDias;          // null = permanente (Free); 60 = Trial; 30 = Premium
    private boolean esGratuito;
    private Long planDegradacionId;        // FK auto-referencial: plan destino al vencer
    private Integer maxSucursales;         // null = ilimitado
    private Integer maxClientesActivos;    // null = ilimitado
    private Integer maxStaff;              // null = ilimitado
    private String moneda;                 // default USD
    private boolean esLegacy;

    public Plan() {}

    public Plan(Long id, String nombre, String descripcion, BigDecimal precioMensual,
                Boolean activo, LocalDateTime createdAt, List<Caracteristica> caracteristicas) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precioMensual = precioMensual;
        this.activo = activo;
        this.createdAt = createdAt;
        this.caracteristicas = caracteristicas;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public BigDecimal getPrecioMensual() { return precioMensual; }
    public void setPrecioMensual(BigDecimal precioMensual) { this.precioMensual = precioMensual; }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<Caracteristica> getCaracteristicas() { return caracteristicas; }
    public void setCaracteristicas(List<Caracteristica> caracteristicas) { this.caracteristicas = caracteristicas; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public Integer getDuracionDias() { return duracionDias; }
    public void setDuracionDias(Integer duracionDias) { this.duracionDias = duracionDias; }

    public boolean isEsGratuito() { return esGratuito; }
    public void setEsGratuito(boolean esGratuito) { this.esGratuito = esGratuito; }

    public Long getPlanDegradacionId() { return planDegradacionId; }
    public void setPlanDegradacionId(Long planDegradacionId) { this.planDegradacionId = planDegradacionId; }

    public Integer getMaxSucursales() { return maxSucursales; }
    public void setMaxSucursales(Integer maxSucursales) { this.maxSucursales = maxSucursales; }

    public Integer getMaxClientesActivos() { return maxClientesActivos; }
    public void setMaxClientesActivos(Integer maxClientesActivos) { this.maxClientesActivos = maxClientesActivos; }

    public Integer getMaxStaff() { return maxStaff; }
    public void setMaxStaff(Integer maxStaff) { this.maxStaff = maxStaff; }

    public String getMoneda() { return moneda; }
    public void setMoneda(String moneda) { this.moneda = moneda; }

    public boolean isEsLegacy() { return esLegacy; }
    public void setEsLegacy(boolean esLegacy) { this.esLegacy = esLegacy; }

    /**
     * true si el plan aplica al menos un límite duro sobre sucursales, clientes activos o staff.
     * Consumido por LimiteRecursoService (Sub-fase 1.3) para decidir si evaluar cuotas.
     */
    public boolean tieneLimites() {
        return maxSucursales != null || maxClientesActivos != null || maxStaff != null;
    }
}
