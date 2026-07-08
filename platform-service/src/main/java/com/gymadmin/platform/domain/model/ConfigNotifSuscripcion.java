package com.gymadmin.platform.domain.model;

public class ConfigNotifSuscripcion {

    public enum Canal {
        EMAIL, WHATSAPP, AMBOS
    }

    private Long idCompania;
    private Integer diasAntes;
    private Canal canal;
    private Boolean activo;

    public ConfigNotifSuscripcion() {}

    public ConfigNotifSuscripcion(Long idCompania, Integer diasAntes, Canal canal, Boolean activo) {
        this.idCompania = idCompania;
        this.diasAntes = diasAntes;
        this.canal = canal;
        this.activo = activo;
    }

    public Long getIdCompania() { return idCompania; }
    public void setIdCompania(Long idCompania) { this.idCompania = idCompania; }

    public Integer getDiasAntes() { return diasAntes; }
    public void setDiasAntes(Integer diasAntes) { this.diasAntes = diasAntes; }

    public Canal getCanal() { return canal; }
    public void setCanal(Canal canal) { this.canal = canal; }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
}
