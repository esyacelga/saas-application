package com.gymadmin.attendance.infrastructure.adapter.out.persistence.entity;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("asistencia.plantillas_mensajes")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PlantillaMensajeEntity extends BaseAuditEntity {

    @Id
    private Integer id;

    @Column("id_compania")
    private Integer idCompania;

    @Column("id_sucursal")
    private Integer idSucursal;

    @Column("tipo")
    private String tipo;

    @Column("nombre")
    private String nombre;

    @Column("contenido")
    private String contenido;

    @Column("activo")
    private Boolean activo;
}
