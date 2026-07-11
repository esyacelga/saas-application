package com.gymadmin.billing.infrastructure.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Table("facturacion.certificados")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificadoEntity {

    @Id
    private Long id;

    @Column("id_compania")
    private Integer idCompania;

    @Column("id_sucursal")
    private Integer idSucursal;

    @Column("alias")
    private String alias;

    @Column("entidad_emisora")
    private String entidadEmisora;

    @Column("p12_cifrado")
    private byte[] p12Cifrado;

    @Column("password_cifrado")
    private byte[] passwordCifrado;

    @Column("subject_cn")
    private String subjectCn;

    @Column("ruc_certificado")
    private String rucCertificado;

    @Column("fecha_emision")
    private LocalDate fechaEmision;

    @Column("fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Column("activo")
    private Boolean activo;

    @Column("revocado")
    private Boolean revocado;

    @Column("motivo_revocacion")
    private String motivoRevocacion;

    @ReadOnlyProperty
    @Column("created_at")
    private OffsetDateTime createdAt;

    @ReadOnlyProperty
    @Column("created_by")
    private String createdBy;
}
