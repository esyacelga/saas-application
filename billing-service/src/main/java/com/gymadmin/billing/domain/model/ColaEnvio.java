package com.gymadmin.billing.domain.model;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder(toBuilder = true)
public class ColaEnvio {
    private Long id;
    private Integer idCompania;
    private Integer idSucursal;
    private Long idComprobante;
    private String estado;
    private OffsetDateTime proximaEjecucion;
    private Short intentos;
    private Short maxIntentos;
    private String ultimoError;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
