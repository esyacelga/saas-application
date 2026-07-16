package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

/**
 * Fase 6 (R1): representación de un bucket global de aviso previo.
 * {@code diaVencimiento} se expone como constante informativa (siempre 0, fijo) para que el panel
 * muestre que además del previo existe el aviso del día del vencimiento, aunque no sea editable.
 */
public record NotifBucketResponse(
        String destinatario,
        Integer diasPrevio,
        Boolean activo,
        Integer diaVencimiento
) {}
