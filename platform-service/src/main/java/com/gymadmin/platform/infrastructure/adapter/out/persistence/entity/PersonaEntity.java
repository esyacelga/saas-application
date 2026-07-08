package com.gymadmin.platform.infrastructure.adapter.out.persistence.entity;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;

@Table("identidad.personas")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PersonaEntity extends BaseAuditEntity {

    @Id
    private Long id;

    @Column("ci")
    private String ci;

    @Column("nombre")
    private String nombre;

    @Column("telefono")
    private String telefono;

    @Column("correo")
    private String correo;

    @Column("foto_url")
    private String fotoUrl;

    @Column("fecha_nacimiento")
    private LocalDate fechaNacimiento;
}
