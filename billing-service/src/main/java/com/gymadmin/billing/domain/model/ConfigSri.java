package com.gymadmin.billing.domain.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConfigSri {

    private Integer idCompania;
    private Integer idSucursal;
    private String ruc;
    private String razonSocial;
    private String nombreComercial;
    private String dirEstablecimiento;
    private String ambiente;
    private String tipoEmision;
    private String contribuyenteEspecial;
    private Boolean obligadoContabilidad;
    private Boolean facturacionActiva;
    private String emailNotificacion;
}
