package com.gymadmin.billing.infrastructure.adapter.out.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("facturacion.cola_envio")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColaEnvioEntity {

    @Id
    private Long id;

    @Column("id_compania")
    private Integer idCompania;

    @Column("id_sucursal")
    private Integer idSucursal;

    @Column("id_comprobante")
    private Long idComprobante;

    @Column("estado")
    private String estado;

    @Column("proxima_ejecucion")
    private OffsetDateTime proximaEjecucion;

    @Column("intentos")
    private Short intentos;

    @Column("max_intentos")
    private Short maxIntentos;

    @Column("ultimo_error")
    private String ultimoError;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
