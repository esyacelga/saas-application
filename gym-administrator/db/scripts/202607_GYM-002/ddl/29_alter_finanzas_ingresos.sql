-- Vincula un ingreso financiero a su comprobante electrónico SRI.
-- La columna es nullable: ingresos registrados antes de activar facturación
-- electrónica no tendrán comprobante asociado.
ALTER TABLE finanzas.ingresos
    ADD COLUMN IF NOT EXISTS id_comprobante BIGINT
        REFERENCES facturacion.comprobantes(id);

-- Índice parcial: soporta la consulta de ingresos que ya tienen comprobante
-- emitido (p.ej. reporte de consistencia entre ingresos y comprobantes).
-- Es parcial porque la mayoría de registros históricos no tendrán id_comprobante.
CREATE INDEX idx_ingresos_comprobante
    ON finanzas.ingresos(id_comprobante)
    WHERE id_comprobante IS NOT NULL;

COMMENT ON COLUMN finanzas.ingresos.id_comprobante IS 'FK opcional al comprobante electrónico SRI generado para este ingreso';
