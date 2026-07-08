package com.gymadmin.attendance.infrastructure.adapter.out.persistence.entity;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalTime;

@Table("asistencia.asistencias")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AsistenciaEntity extends BaseAuditEntity {

    @Id
    private Long id;

    @Column("id_compania")
    private Integer idCompania;

    @Column("id_sucursal")
    private Integer idSucursal;

    @Column("id_cliente")
    private Integer idCliente;

    @Column("id_membresia")
    private Integer idMembresia;

    @Column("fecha")
    private LocalDate fecha;

    @Column("hora_entrada")
    private LocalTime horaEntrada;

    @Column("metodo_registro")
    private String metodoRegistro;
}
