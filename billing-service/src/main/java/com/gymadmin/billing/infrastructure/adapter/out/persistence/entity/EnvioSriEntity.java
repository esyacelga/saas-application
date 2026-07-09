package com.gymadmin.billing.infrastructure.adapter.out.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("facturacion.envios_sri")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvioSriEntity {

    @Id
    private Long id;

    @Column("id_compania")
    private Integer idCompania;

    @Column("id_sucursal")
    private Integer idSucursal;

    @Column("id_comprobante")
    private Long idComprobante;

    @Column("tipo_operacion")
    private String tipoOperacion;

    @Column("endpoint_url")
    private String endpointUrl;

    @Column("request_soap")
    private String requestSoap;

    @Column("response_soap")
    private String responseSoap;

    @Column("http_status")
    private Integer httpStatus;

    @Column("duracion_ms")
    private Integer duracionMs;

    @Column("exitoso")
    private Boolean exitoso;

    @Column("estado_sri")
    private String estadoSri;

    @Column("codigo_error")
    private String codigoError;

    @Column("mensaje_error")
    private String mensajeError;

    @Column("intento_numero")
    private Short intentoNumero;

    @Column("created_at")
    private OffsetDateTime createdAt;
}
