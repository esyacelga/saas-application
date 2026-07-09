package com.gymadmin.billing.infrastructure.adapter.out.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
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

    @Column("contenido_cifrado")
    private byte[] contenidoCifrado;

    @Column("contrasena_cifrada")
    private String contrasenaCifrada;

    @Column("iv")
    private byte[] iv;

    @Column("fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Column("activo")
    private Boolean activo;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
