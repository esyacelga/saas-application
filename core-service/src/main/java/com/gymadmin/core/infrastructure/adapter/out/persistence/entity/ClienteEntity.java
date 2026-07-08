package com.gymadmin.core.infrastructure.adapter.out.persistence.entity;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Table("core.clientes")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteEntity extends BaseAuditEntity {

    @Id
    private Long id;

    @Column("id_persona")
    private Long idPersona;

    @Column("id_compania")
    private Long idCompania;

    @Column("id_sucursal")
    private Long idSucursal;

    @Column("peso_kg")
    private BigDecimal pesoKg;

    @Column("altura_cm")
    private BigDecimal alturaCm;

    @Column("objetivos")
    private String objetivos;

    @Column("lesiones")
    private String lesiones;

    @Column("estado")
    private String estado;

    @Column("fecha_ingreso")
    private LocalDate fechaIngreso;

    @Column("codigo_carnet")
    private String codigoCarnet;

    @Column("sexo")
    private String sexo;
}
