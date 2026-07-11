package com.gymadmin.billing.infrastructure.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EmitirFacturaRequest(
        @NotBlank String tipoIdReceptor,
        @NotBlank String idReceptor,
        @NotBlank String razonSocialReceptor,
        String emailReceptor,
        String direccionReceptor,
        String telefonoReceptor,
        @NotNull @Size(min = 3, max = 3)
        @Pattern(regexp = "\\d{3}", message = "Debe tener exactamente 3 dígitos numéricos")
        String codEstablecimiento,
        @NotNull @Size(min = 3, max = 3)
        @Pattern(regexp = "\\d{3}", message = "Debe tener exactamente 3 dígitos numéricos")
        String codPuntoEmision,
        @NotNull @Size(min = 9, max = 9)
        @Pattern(regexp = "\\d{9}", message = "Debe tener exactamente 9 dígitos numéricos")
        String codigoNumerico,
        @NotNull @Size(min = 9, max = 9)
        @Pattern(regexp = "\\d{9}", message = "Debe tener exactamente 9 dígitos numéricos")
        String secuencial,
        @NotNull Integer idSucursal,
        Integer idMembresia,
        Integer idVenta,
        @NotEmpty @Valid List<DetalleItem> detalles,
        @NotEmpty @Valid List<PagoItem> pagos
) {}
