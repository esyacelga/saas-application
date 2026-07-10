package com.gymadmin.platform.infrastructure.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * REQ-SAAS-001 (Sub-fase 1.5): mapea {@code tenant.notificaciones_suscripcion}.
 * <p>
 * La columna de estado en DB se llama {@code estado} (histórico). El requerimiento
 * la referencia como {@code estado_envio} — misma semántica.
 */
@Table("tenant.notificaciones_suscripcion")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class NotificacionSuscripcionEntity extends BaseAuditEntity {

    @Id
    private Long id;

    @Column("id_compania")
    private Long idCompania;

    @Column("id_compania_plan")
    private Long idCompaniaPlan;

    @Column("tipo")
    private String tipo;

    @Column("dias_antes")
    private Integer diasAntes;

    @Column("canal")
    private String canal;

    @Column("estado")
    private String estado;

    @Column("intentos")
    private Integer intentos;

    @Column("ultimo_error")
    private String ultimoError;

    @Column("proximo_intento")
    private OffsetDateTime proximoIntento;

    @Column("descartado_at")
    private OffsetDateTime descartadoAt;

    @Column("fecha_envio")
    private LocalDateTime fechaEnvio;

}
