package com.gymadmin.platform.domain.model;

import java.time.LocalDateTime;

public class NotificacionSuscripcion {

    private Long id;
    private Long idCompaniaPlan;
    private Integer diasAntes;
    private String canal;
    private String estado;
    private LocalDateTime fechaEnvio;

    public NotificacionSuscripcion() {}

    public NotificacionSuscripcion(Long id, Long idCompaniaPlan, Integer diasAntes,
                                    String canal, String estado, LocalDateTime fechaEnvio) {
        this.id = id;
        this.idCompaniaPlan = idCompaniaPlan;
        this.diasAntes = diasAntes;
        this.canal = canal;
        this.estado = estado;
        this.fechaEnvio = fechaEnvio;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIdCompaniaPlan() { return idCompaniaPlan; }
    public void setIdCompaniaPlan(Long idCompaniaPlan) { this.idCompaniaPlan = idCompaniaPlan; }

    public Integer getDiasAntes() { return diasAntes; }
    public void setDiasAntes(Integer diasAntes) { this.diasAntes = diasAntes; }

    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFechaEnvio() { return fechaEnvio; }
    public void setFechaEnvio(LocalDateTime fechaEnvio) { this.fechaEnvio = fechaEnvio; }
}
