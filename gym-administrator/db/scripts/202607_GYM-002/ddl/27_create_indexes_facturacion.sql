-- comprobantes: consulta principal por compania + rango de fecha de emisión
CREATE INDEX idx_comprobantes_compania_fecha
    ON facturacion.comprobantes(id_compania, fecha_emision);

-- comprobantes: cola de procesamiento — filtra los que aún no están en estado final
CREATE INDEX idx_comprobantes_estado
    ON facturacion.comprobantes(estado)
    WHERE estado NOT IN ('AUTORIZADO', 'ANULADO');

-- comprobantes: búsqueda de comprobantes por receptor dentro de una compania
CREATE INDEX idx_comprobantes_receptor
    ON facturacion.comprobantes(id_receptor, id_compania);

-- cola_envio: query crítica del worker que levanta trabajos pendientes ordenados por prioridad de ejecución
CREATE INDEX idx_cola_envio_pendientes
    ON facturacion.cola_envio(proxima_ejecucion, estado)
    WHERE estado = 'PENDIENTE';

-- envios_sri: trazabilidad de todos los intentos de envío de un comprobante
CREATE INDEX idx_envios_sri_comprobante
    ON facturacion.envios_sri(id_comprobante);

-- certificados: alerta de vencimiento próximo — solo certificados activos y no revocados
CREATE INDEX idx_certificados_vencimiento
    ON facturacion.certificados(fecha_vencimiento)
    WHERE activo = TRUE AND revocado = FALSE;
