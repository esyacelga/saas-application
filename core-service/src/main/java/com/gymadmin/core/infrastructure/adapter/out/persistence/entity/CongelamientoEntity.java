package com.gymadmin.core.infrastructure.adapter.out.persistence.entity;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;

@Table("core.congelamientos")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CongelamientoEntity extends BaseAuditEntity {

    @Id
    private Long id;

    @Column("id_compania")
    private Long idCompania;

    @Column("id_sucursal")
    private Long idSucursal;

    @Column("id_membresia")
    private Long idMembresia;

    @Column("fecha_inicio")
    private LocalDate fechaInicio;

    @Column("fecha_fin")
    private LocalDate fechaFin;

    @Column("motivo")
    private String motivo;

    @Column("detalle")
    private String detalle;

    @Column("retroactivo")
    private Boolean retroactivo;

    @Column("documento_respaldo")
    private String documentoRespaldo;

    @Column("aprobado_por")
    private Long aprobadoPor;

    @Column("fecha_aprobacion")
    private LocalDate fechaAprobacion;

    @Column("id_usuario_registro")
    private Long idUsuarioRegistro;
}
