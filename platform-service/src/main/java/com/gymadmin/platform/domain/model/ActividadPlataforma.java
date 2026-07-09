package com.gymadmin.platform.domain.model;

import java.time.OffsetDateTime;

/**
 * REQ-SAAS-001 (sección 6bis): eventos auditables de plataforma.
 * <p>
 * Se mantienen los campos históricos ({@code tipoEvento}, {@code modulo}, {@code entidadId},
 * {@code entidadNombre}, {@code detalle}, {@code usuario}, {@code ip}, {@code fecha}) para
 * no romper el `ActividadPlataformaService` ni las lecturas del controller actual.
 * <p>
 * Sub-fase 1.2 agrega los tres campos nuevos que exige la sección 6bis
 * (schema post-migración GYM-003, script 07_alter_saas_actividad_plataforma):
 * <ul>
 *   <li>{@code idCompania}: FK al tenant afectado (NULL para eventos de sistema).</li>
 *   <li>{@code idUsuarioActor}: id del actor humano cuando aplica.</li>
 *   <li>{@code tipoActor}: OWNER / ROOT / STAFF / SISTEMA (default SISTEMA en DB).</li>
 * </ul>
 * <p>
 * El nombre del evento sigue vivo en {@code tipoEvento} (mismo mapping a la columna
 * {@code tipo_evento} — el requerimiento lo llama "evento" en el spec pero la migración
 * conservó el nombre físico anterior para no romper filas existentes).
 * <p>
 * {@code detalleJson} es el mismo campo {@code detalle} mapeado a la columna
 * {@code detalle} (ahora JSONB en DB). Se maneja como String en el dominio; el
 * parseo Jackson vive en la capa de aplicación cuando corresponda.
 */
public class ActividadPlataforma {

    /** REQ-SAAS-001 sección 6bis: clasificación del actor que originó el evento. */
    public enum TipoActor {
        OWNER, ROOT, STAFF, SISTEMA
    }

    private Long id;

    // ── Campos históricos (columnas ya existentes en la tabla) ──────────────
    private String tipoEvento;      // -> tipo_evento
    private String modulo;          // -> modulo
    private Long entidadId;         // -> entidad_id
    private String entidadNombre;   // -> entidad_nombre
    private String detalle;         // -> detalle (ahora JSONB, ver detalleJson)
    private String usuario;         // -> usuario (texto legible)
    private String ip;              // -> ip (ahora INET; se maneja como String)
    private OffsetDateTime fecha;   // -> fecha

    // ── Campos nuevos REQ-SAAS-001 (sección 6bis) ───────────────────────────
    private Long idCompania;
    private Long idUsuarioActor;
    private TipoActor tipoActor;

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

    public Long getIdCompania() { return idCompania; }
    public void setIdCompania(Long idCompania) { this.idCompania = idCompania; }

    public Long getIdUsuarioActor() { return idUsuarioActor; }
    public void setIdUsuarioActor(Long idUsuarioActor) { this.idUsuarioActor = idUsuarioActor; }

    public TipoActor getTipoActor() { return tipoActor; }
    public void setTipoActor(TipoActor tipoActor) { this.tipoActor = tipoActor; }

    /**
     * Alias funcional sobre {@link #getDetalle()}: la columna {@code detalle} ya es
     * {@code JSONB} en DB. Se conserva como String; el parseo/serialización JSON
     * vive en la capa de aplicación cuando se necesite.
     */
    public String getDetalleJson() { return detalle; }
    public void setDetalleJson(String detalleJson) { this.detalle = detalleJson; }
}
