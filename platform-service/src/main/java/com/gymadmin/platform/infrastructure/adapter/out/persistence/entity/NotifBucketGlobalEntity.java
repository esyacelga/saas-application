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
 * Fase 6 (R1) — story GYM-003, {@code saas.notif_buckets_globales}.
 * <p>
 * La PK es {@code destinatario VARCHAR(10)} ('socio'/'dueno'); como {@link ConfigPlataformaEntity},
 * no extiende {@code BaseAuditEntity} (la tabla solo tiene {@code modificado_por}/{@code modificado_at}
 * + {@code creacion_*}). Implementa {@link Persistable} para que R2DBC diferencie INSERT de UPDATE con
 * PK String — el flag {@code nuevo} lo marca el adapter (las 2 filas se seedan en la migración, así que
 * en la práctica siempre es UPDATE).
 */
@Table("saas.notif_buckets_globales")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotifBucketGlobalEntity implements Persistable<String> {

    @Id
    @Column("destinatario")
    private String destinatario;

    @Column("dias_previo")
    private Integer diasPrevio;

    @Column("activo")
    private Boolean activo;

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
        return destinatario;
    }

    @Override
    public boolean isNew() {
        return nuevo;
    }
}
