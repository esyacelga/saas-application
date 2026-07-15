package com.gymadmin.platform.infrastructure.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("tenant.companias")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CompaniaEntity extends BaseAuditEntity {

    @Id
    private Long id;

    @Column("nombre")
    private String nombre;

    @Column("ruc")
    private String ruc;

    @Column("logo_url")
    private String logoUrl;

    @Column("telefono")
    private String telefono;

    @Column("whatsapp")
    private String whatsapp;

    @Column("correo")
    private String correo;

    @Column("activo")
    private Boolean activo;

    // REQ-SAAS-001 — Sub-fase 1.2 (RN-01): Trial único e irrevocable por tenant.
    // Columnas del script 02_alter_tenant_companias.sql.
    @Column("trial_usado")
    private Boolean trialUsado;

    @Column("fecha_trial_usado")
    private OffsetDateTime fechaTrialUsado;

    // GYM-002: opt-in para avisos de vencimiento por WhatsApp. FALSE por defecto:
    // sin este flag NUNCA se envía WhatsApp al dueño (evita bloqueo del número por Meta).
    @Column("acepta_whatsapp")
    private Boolean aceptaWhatsapp;

    @Column("fecha_consentimiento_wa")
    private OffsetDateTime fechaConsentimientoWa;
}
