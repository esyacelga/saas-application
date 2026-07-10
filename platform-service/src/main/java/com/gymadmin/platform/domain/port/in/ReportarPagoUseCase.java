package com.gymadmin.platform.domain.port.in;

import com.gymadmin.platform.domain.model.PagoPendienteValidacion;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * REQ-SAAS-001 (RN-08): el owner reporta un pago por transferencia. Queda
 * pendiente de aprobación manual por root/soporte.
 */
public interface ReportarPagoUseCase {

    Mono<PagoPendienteValidacion> reportar(ReportarPagoCommand command);

    record ReportarPagoCommand(
            Long idCompania,
            Long idPlanDestino,
            BigDecimal monto,
            LocalDate fechaTransferencia,
            String bancoOrigen,
            String referencia,
            byte[] comprobanteBytes,
            String nombreArchivo,
            Long idUsuarioActor,
            String ipActor
    ) {}
}
