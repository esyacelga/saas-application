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

@Table("seguridad.refresh_tokens")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenEntity extends BaseAuditEntity {

    @Id
    private Long id;

    @Column("token")
    private String token;

    @Column("tipo_usuario")
    private String tipoUsuario;

    @Column("id_usuario")
    private Integer idUsuario;

    @Column("id_compania")
    private Integer idCompania;

    @Column("expira_en")
    private OffsetDateTime expiraEn;
}
