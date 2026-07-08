package com.gymadmin.platform.infrastructure.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("tenant.config_notif_suscripcion")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigNotifSuscripcionEntity extends BaseAuditEntity {

    @Id
    @Column("id_compania")
    private Long idCompania;

    @Column("dias_antes")
    private Integer diasAntes;

    @Column("canal")
    private String canal;

    @Column("activo")
    private Boolean activo;

}
