package com.gymadmin.billing.domain.model;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder(toBuilder = true)
public class EnvioSri {
    private Long id;
    private Integer idCompania;
    private Integer idSucursal;
    private Long idComprobante;
    private String tipoOperacion;
    private String endpointUrl;
    private String requestSoap;
    private String responseSoap;
    private Integer httpStatus;
    private Integer duracionMs;
    private Boolean exitoso;
    private String estadoSri;
    private String codigoError;
    private String mensajeError;
    private Short intentoNumero;
    private OffsetDateTime createdAt;
}
