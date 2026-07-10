package com.gymadmin.platform.domain.model;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * REQ-SAAS-001 (Sub-fase 1.5): notificación de vencimiento (canal email o banner)
 * persistida en {@code tenant.notificaciones_suscripcion}.
 *
 * <p>El nombre físico de la columna de estado en DB es {@code estado} (histórico);
 * el requerimiento la referencia como {@code estado_envio}. Se mantiene el nombre
 * físico para no romper compatibilidad; en el dominio se llama {@code estado}
 * pero con la semántica de "estado de envío" que define el requerimiento:
 * {@code pendiente}, {@code enviado}, {@code fallido}, {@code reintentar}.
 */
public class NotificacionSuscripcion {

    public static final String CANAL_EMAIL = "email";
    public static final String CANAL_BANNER = "banner";
    public static final String CANAL_WHATSAPP = "whatsapp";

    public static final String ESTADO_PENDIENTE = "pendiente";
    public static final String ESTADO_ENVIADO = "enviado";
    public static final String ESTADO_FALLIDO = "fallido";
    public static final String ESTADO_REINTENTAR = "reintentar";

    private Long id;
    private Long idCompania;
    private Long idCompaniaPlan;
    private String tipo;
    private Integer diasAntes;
    private String canal;
    private String estado;
    private Integer intentos;
    private String ultimoError;
    private OffsetDateTime proximoIntento;
    private OffsetDateTime descartadoAt;
    private LocalDateTime fechaEnvio;

    public NotificacionSuscripcion() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIdCompania() { return idCompania; }
    public void setIdCompania(Long idCompania) { this.idCompania = idCompania; }

    public Long getIdCompaniaPlan() { return idCompaniaPlan; }
    public void setIdCompaniaPlan(Long idCompaniaPlan) { this.idCompaniaPlan = idCompaniaPlan; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Integer getDiasAntes() { return diasAntes; }
    public void setDiasAntes(Integer diasAntes) { this.diasAntes = diasAntes; }

    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public Integer getIntentos() { return intentos; }
    public void setIntentos(Integer intentos) { this.intentos = intentos; }

    public String getUltimoError() { return ultimoError; }
    public void setUltimoError(String ultimoError) { this.ultimoError = ultimoError; }

    public OffsetDateTime getProximoIntento() { return proximoIntento; }
    public void setProximoIntento(OffsetDateTime proximoIntento) { this.proximoIntento = proximoIntento; }

    public OffsetDateTime getDescartadoAt() { return descartadoAt; }
    public void setDescartadoAt(OffsetDateTime descartadoAt) { this.descartadoAt = descartadoAt; }

    public LocalDateTime getFechaEnvio() { return fechaEnvio; }
    public void setFechaEnvio(LocalDateTime fechaEnvio) { this.fechaEnvio = fechaEnvio; }
}
