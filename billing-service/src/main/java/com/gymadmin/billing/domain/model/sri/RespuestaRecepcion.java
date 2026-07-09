package com.gymadmin.billing.domain.model.sri;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RespuestaRecepcion {
    private String estado;
    private List<String> mensajes;
}
