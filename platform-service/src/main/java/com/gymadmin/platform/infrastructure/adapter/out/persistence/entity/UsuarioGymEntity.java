package com.gymadmin.platform.infrastructure.adapter.out.persistence.entity;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("seguridad.usuarios")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioGymEntity extends BaseAuditEntity {

    @Id
    private Long id;

    @Column("id_compania")
    private Long idCompania;

    @Column("id_sucursal")
    private Long idSucursal;

    @Column("id_rol")
    private Long idRol;

    @Column("id_persona")
    private Long idPersona;

    @Column("correo")
    private String correo;

    @Column("password_hash")
    private String passwordHash;

    @Column("requiere_cambio_pwd")
    private Boolean requiereCambioPwd;

    @Column("activo")
    private Boolean activo;

    @Column("ultimo_acceso")
    private OffsetDateTime ultimoAcceso;
}
