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

@Table("tenant.sucursales")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SucursalEntity extends BaseAuditEntity {

    @Id
    private Long id;

    @Column("id_compania")
    private Long idCompania;

    @Column("nombre")
    private String nombre;

    @Column("direccion")
    private String direccion;

    @Column("es_principal")
    private Boolean esPrincipal;

    @Column("activo")
    private Boolean activo;

    @Column("qr_token")
    private String qrToken;

    @Column("qr_token_expira")
    private LocalDateTime qrTokenExpira;

}
