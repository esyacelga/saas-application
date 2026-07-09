package com.gymadmin.billing.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "sri")
@Getter
@Setter
public class SriAmbienteConfig {

    private static final String PRUEBAS_RECEPCION =
            "https://celcer.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline";
    private static final String PRUEBAS_AUTORIZACION =
            "https://celcer.sri.gob.ec/comprobantes-electronicos-ws/AutorizacionComprobantesOffline";
    private static final String PROD_RECEPCION =
            "https://cel.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline";
    private static final String PROD_AUTORIZACION =
            "https://cel.sri.gob.ec/comprobantes-electronicos-ws/AutorizacionComprobantesOffline";

    /** "1" = pruebas, "2" = producción */
    private String ambiente = "1";

    public String getUrlRecepcionEfectiva() {
        return "2".equals(ambiente) ? PROD_RECEPCION : PRUEBAS_RECEPCION;
    }

    public String getUrlAutorizacionEfectiva() {
        return "2".equals(ambiente) ? PROD_AUTORIZACION : PRUEBAS_AUTORIZACION;
    }
}
