package com.gymadmin.platform.domain.model;

import java.time.Instant;

/**
 * REQ-SAAS-001 (sección 11.4): configuración runtime editable por root
 * (datos bancarios, textos, umbrales). No requiere redeploy.
 * <p>
 * Tabla física: {@code saas.config_plataforma} (PK = clave).
 */
public class ConfigPlataforma {

    private String clave;
    private String valor;
    private String descripcion;
    private Long modificadoPor;
    private Instant modificadoAt;

    public ConfigPlataforma() {}

    public ConfigPlataforma(String clave, String valor, String descripcion,
                             Long modificadoPor, Instant modificadoAt) {
        this.clave = clave;
        this.valor = valor;
        this.descripcion = descripcion;
        this.modificadoPor = modificadoPor;
        this.modificadoAt = modificadoAt;
    }

    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }

    public String getValor() { return valor; }
    public void setValor(String valor) { this.valor = valor; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public Long getModificadoPor() { return modificadoPor; }
    public void setModificadoPor(Long modificadoPor) { this.modificadoPor = modificadoPor; }

    public Instant getModificadoAt() { return modificadoAt; }
    public void setModificadoAt(Instant modificadoAt) { this.modificadoAt = modificadoAt; }
}
