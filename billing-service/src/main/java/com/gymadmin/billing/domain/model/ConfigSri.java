package com.gymadmin.billing.domain.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConfigSri {

    private Long id;
    private Integer idCompania;
    private Integer idSucursal;
    private String ruc;
    private String razonSocial;
    private String nombreComercial;
    private String dirMatriz;
    private String ambiente;
    private String contribuyenteEspecial;
    private String obligadoContabilidad;
    private String codEstablecimiento;
    private String codPuntoEmision;
    private Boolean activo;
}
