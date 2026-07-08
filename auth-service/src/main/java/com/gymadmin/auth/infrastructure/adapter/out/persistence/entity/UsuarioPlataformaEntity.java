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

@Table("saas.usuarios_plataforma")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioPlataformaEntity extends BaseAuditEntity {

    @Id
    private Integer id;

    @Column("id_persona")
    private Integer idPersona;

    @Column("correo")
    private String correo;

    @Column("password_hash")
    private String passwordHash;

    @Column("rol")
    private String rol;

    @Column("activo")
    private Boolean activo;

    @Column("ultimo_acceso")
    private OffsetDateTime ultimoAcceso;
}
