package com.gymadmin.platform.infrastructure.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("saas.actividad_plataforma")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActividadPlataformaEntity {

    @Id
    private Long id;

    @Column("tipo_evento")
    private String tipoEvento;

    @Column("modulo")
    private String modulo;

    @Column("entidad_id")
    private Long entidadId;

    @Column("entidad_nombre")
    private String entidadNombre;

    @Column("detalle")
    private String detalle;

    @Column("usuario")
    private String usuario;

    @Column("ip")
    private String ip;

    @Column("fecha")
    private OffsetDateTime fecha;
}
