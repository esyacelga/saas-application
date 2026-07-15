package com.gymadmin.core.domain.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * REQ-SAAS-001 (Fase 4, issue C3): proyección de un socio cuya membresía está por vencer,
 * consumida por attendance-service vía el endpoint interno {@code /internal/v1/companias/{id}/clientes-por-vencer}.
 *
 * <p>El {@code telefono} va <b>sin normalizar</b> a E.164 — normalizar es responsabilidad de
 * attendance (no acopla core al formato internacional). Se incluyen {@code aceptaWhatsapp} y
 * {@code fechaConsentimientoWa} para que attendance no tenga que hacer un segundo JOIN a
 * {@code identidad.personas}. El {@code estadoCliente} viaja para que el consumidor pueda saltar
 * {@code congelado} (RN-05), aunque la query ya lo excluye.
 *
 * <p>Según el {@code modoControl}: en {@code calendario} viaja {@code diasParaVencer} (y
 * {@code accesosRestantes} = null); en {@code accesos} viaja {@code accesosRestantes} (y
 * {@code diasParaVencer} refleja los días al {@code fechaFin} de todos modos).
 */
public record ClientePorVencer(
        Long idCliente,
        Long idPersona,
        Long idSucursal,
        String nombre,
        String telefono,
        String correo,
        String modoControl,
        LocalDate fechaFin,
        Integer diasParaVencer,
        Integer accesosRestantes,
        String estadoCliente,
        boolean aceptaWhatsapp,
        OffsetDateTime fechaConsentimientoWa
) {}
