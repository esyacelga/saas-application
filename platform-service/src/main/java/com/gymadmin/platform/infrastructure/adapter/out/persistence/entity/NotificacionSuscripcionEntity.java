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

@Table("tenant.notificaciones_suscripcion")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class NotificacionSuscripcionEntity extends BaseAuditEntity {

    @Id
    private Long id;

    @Column("id_compania_plan")
    private Long idCompaniaPlan;

    @Column("dias_antes")
    private Integer diasAntes;

    @Column("canal")
    private String canal;

    @Column("estado")
    private String estado;

    @Column("fecha_envio")
    private LocalDateTime fechaEnvio;

}
