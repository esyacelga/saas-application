package com.gymadmin.platform.infrastructure.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

/**
 * REQ-SAAS-001 (sección 6bis) — schema post-migración GYM-003
 * (script 07_alter_saas_actividad_plataforma.sql).
 * <p>
 * Columnas nuevas:
 * <ul>
 *   <li>{@code id_compania} INT NULL — FK a tenant afectado (NULL para eventos de sistema).</li>
 *   <li>{@code id_usuario_actor} INT NULL — id del actor humano cuando aplica.</li>
 *   <li>{@code tipo_actor} VARCHAR(20) NOT NULL DEFAULT 'SISTEMA' — OWNER/ROOT/STAFF/SISTEMA.</li>
 * </ul>
 * <p>
 * Notas:
 * <ul>
 *   <li>{@code detalle} sigue mapeado a String — la columna ahora es JSONB en DB pero el
 *       driver R2DBC recibe/envía texto JSON sin transformación adicional. Serialización
 *       Jackson vive en la capa de aplicación cuando se necesite.</li>
 *   <li>{@code ip} sigue mapeado a String — la columna ahora es INET en DB. Postgres acepta
 *       el cast implícito desde texto en INSERT.</li>
 *   <li>{@code tipo_actor} en DB usa MAYÚSCULAS (OWNER/ROOT/STAFF/SISTEMA — ver DDL 07).</li>
 * </ul>
 */
@Table("saas.actividad_plataforma")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActividadPlataformaEntity {

    @Id
    private Long id;

    @Column("tipo_evento")
    private String tipoEvento;

    @Column("modulo")
    private String modulo;

    @Column("entidad_id")
    private Long entidadId;

    @Column("entidad_nombre")
    private String entidadNombre;

    @Column("detalle")
    private String detalle;

    @Column("usuario")
    private String usuario;

    @Column("ip")
    private String ip;

    @Column("fecha")
    private OffsetDateTime fecha;

    // ── Nuevas columnas REQ-SAAS-001 (sección 6bis, script 07) ────────────────

    @Column("id_compania")
    private Long idCompania;

    @Column("id_usuario_actor")
    private Long idUsuarioActor;

    /** Valores permitidos (MAYÚSCULAS, ver DDL 07): OWNER / ROOT / STAFF / SISTEMA. */
    @Column("tipo_actor")
    private String tipoActor;
}
