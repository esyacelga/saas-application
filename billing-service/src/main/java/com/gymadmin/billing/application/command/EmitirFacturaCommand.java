package com.gymadmin.billing.application.command;

import java.time.LocalDate;
import java.util.List;

public record EmitirFacturaCommand(
        Integer idCompania,
        Integer idSucursal,
        LocalDate fechaEmision,
        String codEstablecimiento,
        String codPuntoEmision,
        String codigoNumerico,
        String tipoIdReceptor,
        String idReceptor,
        String razonSocialReceptor,
        String emailReceptor,
        String direccionReceptor,
        String telefonoReceptor,
        List<DetalleFacturaItem> detalles,
        List<PagoItem> pagos,
        String formaPago,
        Integer idMembresia,
        Integer idVenta,
        Integer idUsuarioRegistro
) {
    public record DetalleFacturaItem(
            String codigoPrincipal,
            String codigoAuxiliar,
            String descripcion,
            java.math.BigDecimal cantidad,
            java.math.BigDecimal precioUnitario,
            java.math.BigDecimal descuento,
            java.math.BigDecimal precioTotalSinImpuesto,
            Integer orden
    ) {}

    public record PagoItem(
            String formaPago,
            java.math.BigDecimal total
    ) {}
}
