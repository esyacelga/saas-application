package com.gymadmin.platform.domain.port.in;

import com.gymadmin.platform.domain.model.PagoPendienteValidacion;
import reactor.core.publisher.Flux;

/**
 * REQ-SAAS-001 (Sub-fase 1.6, item #3): consulta de pagos pendientes/rechazados
 * del propio tenant, invocada desde el frontend "Mi suscripcion" del owner/admin.
 * <p>
 * Distinto de {@link ListarPagosPendientesUseCase} (bandeja root/soporte que ve
 * todos los tenants); este caso de uso siempre esta filtrado por {@code idCompania}
 * y sirve para renderizar el banner "tu pago esta en revision" o "tu ultimo pago
 * fue rechazado: {motivo}".
 */
public interface ListarPagosPendientesOwnerUseCase {

    /**
     * Devuelve los pagos reportados por la compania ordenados por {@code fecha_reporte DESC}.
     * Incluye todos los estados (PENDIENTE, APROBADO, RECHAZADO) para que el frontend
     * pueda decidir que banner mostrar segun el estado del mas reciente.
     *
     * @param idCompania tenant al que pertenecen los pagos (ya validado contra el JWT en el controller)
     * @param limit maximo de resultados a devolver (por defecto 10 si &lt;= 0)
     */
    Flux<PagoPendienteValidacion> listarPorCompania(Long idCompania, int limit);
}
