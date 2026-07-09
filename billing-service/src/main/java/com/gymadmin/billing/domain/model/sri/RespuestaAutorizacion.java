package com.gymadmin.billing.domain.model.sri;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class RespuestaAutorizacion {
    private String estado;
    private String numeroAutorizacion;
    private OffsetDateTime fechaAutorizacion;
    private String xmlAutorizado;
    private List<String> mensajes;
}
