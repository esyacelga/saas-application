package com.gymadmin.platform.infrastructure.adapter.out.persistence.entity;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

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

    // true cuando ci pasó el algoritmo del dígito verificador ecuatoriano (módulo 10).
    // Se calcula al crear la persona; false por defecto (o cuando el documento no es una
    // cédula ecuatoriana válida). Ver domain/validation/CedulaEcuatoriana.
    @Column("ci_validada")
    private Boolean ciValidada;

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

    // GYM-002: opt-in del socio para avisos por WhatsApp. FALSE por defecto:
    // sin este flag NUNCA se envía WhatsApp (evita bloqueo del número por Meta).
    @Column("acepta_whatsapp")
    private Boolean aceptaWhatsapp;

    @Column("fecha_consentimiento_wa")
    private OffsetDateTime fechaConsentimientoWa;
}
