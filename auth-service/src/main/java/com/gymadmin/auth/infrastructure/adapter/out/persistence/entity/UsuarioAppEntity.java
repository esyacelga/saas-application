package com.gymadmin.auth.infrastructure.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("identidad.usuarios_app")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioAppEntity extends BaseAuditEntity {

    @Id
    private Integer id;

    @Column("id_persona")
    private Integer idPersona;

    @Column("id_compania")
    private Integer idCompania;

    @Column("login")
    private String login;

    @Column("password_hash")
    private String passwordHash;

    @Column("requiere_cambio_pwd")
    private Boolean requiereCambioPwd;

    @Column("activo")
    private Boolean activo;

    @Column("ultimo_acceso")
    private OffsetDateTime ultimoAcceso;

    @Column("token_recuperacion")
    private String tokenRecuperacion;

    @Column("token_expira")
    private OffsetDateTime tokenExpira;
}
