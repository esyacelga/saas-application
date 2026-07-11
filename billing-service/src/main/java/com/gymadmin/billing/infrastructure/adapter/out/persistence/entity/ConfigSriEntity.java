package com.gymadmin.billing.infrastructure.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

/**
 * facturacion.config_sri uses a composite PK (id_compania, id_sucursal) — no synthetic id.
 * R2DBC ReactiveCrudRepository cannot manage composite keys, so the associated repository
 * exposes only @Query methods and rows are inserted/updated through the DatabaseClient
 * in a dedicated adapter (see ConfigSriPersistenceAdapter).
 */
@Table("facturacion.config_sri")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigSriEntity {

    @Column("id_compania")
    private Integer idCompania;

    @Column("id_sucursal")
    private Integer idSucursal;

    @Column("razon_social")
    private String razonSocial;

    @Column("nombre_comercial")
    private String nombreComercial;

    @Column("ruc")
    private String ruc;

    @Column("dir_establecimiento")
    private String dirEstablecimiento;

    @Column("obligado_contabilidad")
    private Boolean obligadoContabilidad;

    @Column("contribuyente_especial")
    private String contribuyenteEspecial;

    @Column("ambiente")
    private String ambiente;

    @Column("tipo_emision")
    private String tipoEmision;

    @Column("facturacion_activa")
    private Boolean facturacionActiva;

    @Column("email_notificacion")
    private String emailNotificacion;

    @Column("updated_at")
    private OffsetDateTime updatedAt;

    @Column("updated_by")
    private String updatedBy;
}
