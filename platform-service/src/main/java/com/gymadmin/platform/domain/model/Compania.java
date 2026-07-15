package com.gymadmin.platform.domain.model;

import java.time.Instant;

public class Compania {

    private Long id;
    private String nombre;
    private String ruc;
    private String logoUrl;
    private String telefono;
    private String whatsapp;
    private String correo;
    private Boolean activo;

    // REQ-SAAS-001 — Sub-fase 1.2 (RN-01): Trial único e irrevocable por tenant.
    private boolean trialUsado;
    private Instant fechaTrialUsado;

    // GYM-002: opt-in del dueño para avisos de vencimiento por WhatsApp.
    private boolean aceptaWhatsapp;
    private Instant fechaConsentimientoWa;

    public Compania() {}

    public Compania(Long id, String nombre, String ruc, String logoUrl, String telefono,
                    String whatsapp, String correo, Boolean activo) {
        this.id = id;
        this.nombre = nombre;
        this.ruc = ruc;
        this.logoUrl = logoUrl;
        this.telefono = telefono;
        this.whatsapp = whatsapp;
        this.correo = correo;
        this.activo = activo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getRuc() { return ruc; }
    public void setRuc(String ruc) { this.ruc = ruc; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getWhatsapp() { return whatsapp; }
    public void setWhatsapp(String whatsapp) { this.whatsapp = whatsapp; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }

    public boolean isTrialUsado() { return trialUsado; }
    public void setTrialUsado(boolean trialUsado) { this.trialUsado = trialUsado; }

    public Instant getFechaTrialUsado() { return fechaTrialUsado; }
    public void setFechaTrialUsado(Instant fechaTrialUsado) { this.fechaTrialUsado = fechaTrialUsado; }

    public boolean isAceptaWhatsapp() { return aceptaWhatsapp; }
    public void setAceptaWhatsapp(boolean aceptaWhatsapp) { this.aceptaWhatsapp = aceptaWhatsapp; }

    public Instant getFechaConsentimientoWa() { return fechaConsentimientoWa; }
    public void setFechaConsentimientoWa(Instant fechaConsentimientoWa) { this.fechaConsentimientoWa = fechaConsentimientoWa; }
}
