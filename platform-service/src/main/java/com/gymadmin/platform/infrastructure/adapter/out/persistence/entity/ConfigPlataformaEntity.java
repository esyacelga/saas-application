package com.gymadmin.platform.infrastructure.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

/**
 * REQ-SAAS-001 (sección 11.4) — script 06_create_table_saas_config_plataforma.sql.
 * <p>
 * La PK es {@code clave VARCHAR(100)}; por eso la entidad no extiende
 * {@link BaseAuditEntity} — la tabla solo tiene {@code modificado_por},
 * {@code modificado_at}, {@code creacion_fecha} y {@code creacion_usuario}
 * (no coincide con el patrón estándar de auditoría).
 * <p>
 * Implementa {@link Persistable} para que R2DBC diferencie INSERT de UPDATE con PK String:
 * el flag {@code nuevo} lo marca el adapter antes de invocar {@code save}.
 */
@Table("saas.config_plataforma")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConfigPlataformaEntity implements Persistable<String> {

    @Id
    @Column("clave")
    private String clave;

    @Column("valor")
    private String valor;

    @Column("descripcion")
    private String descripcion;

    @Column("modificado_por")
    private Long modificadoPor;

    @Column("modificado_at")
    private OffsetDateTime modificadoAt;

    @Column("creacion_fecha")
    private OffsetDateTime creacionFecha;

    @Column("creacion_usuario")
    private String creacionUsuario;

    @Transient
    private boolean nuevo;

    @Override
    public String getId() {
        return clave;
    }

    @Override
    public boolean isNew() {
        return nuevo;
    }
}
