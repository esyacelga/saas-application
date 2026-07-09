package com.gymadmin.billing.infrastructure.adapter.out.persistence.entity;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("facturacion.config_sri")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigSriEntity extends BaseAuditEntity {

    @Id
    private Long id;

    @Column("id_compania")
    private Integer idCompania;

    @Column("id_sucursal")
    private Integer idSucursal;

    @Column("ruc")
    private String ruc;

    @Column("razon_social")
    private String razonSocial;

    @Column("nombre_comercial")
    private String nombreComercial;

    @Column("dir_matriz")
    private String dirMatriz;

    @Column("ambiente")
    private String ambiente;

    @Column("contribuyente_especial")
    private String contribuyenteEspecial;

    @Column("obligado_contabilidad")
    private String obligadoContabilidad;

    @Column("cod_establecimiento")
    private String codEstablecimiento;

    @Column("cod_punto_emision")
    private String codPuntoEmision;

    @Column("activo")
    private Boolean activo;
}
