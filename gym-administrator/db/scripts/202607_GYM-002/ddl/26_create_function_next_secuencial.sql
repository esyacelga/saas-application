CREATE OR REPLACE FUNCTION facturacion.next_secuencial(
    p_id_compania INT,
    p_id_sucursal INT,
    p_cod_estab   CHAR(3),
    p_cod_punto   CHAR(3),
    p_tipo        CHAR(2)
) RETURNS INT
LANGUAGE plpgsql AS $$
DECLARE
    v_seq INT;
BEGIN
    UPDATE facturacion.secuenciales
       SET ultimo_secuencial = ultimo_secuencial + 1
     WHERE id_compania       = p_id_compania
       AND id_sucursal       = p_id_sucursal
       AND cod_establecimiento = p_cod_estab
       AND cod_punto_emision = p_cod_punto
       AND tipo_comprobante  = p_tipo
    RETURNING ultimo_secuencial INTO v_seq;

    IF v_seq IS NULL THEN
        RAISE EXCEPTION 'No existe registro de secuencial para compania=%, sucursal=%, estab=%, punto=%, tipo=%',
            p_id_compania, p_id_sucursal, p_cod_estab, p_cod_punto, p_tipo;
    END IF;

    RETURN v_seq;
END;
$$;

COMMENT ON FUNCTION facturacion.next_secuencial(INT, INT, CHAR(3), CHAR(3), CHAR(2))
    IS 'Incrementa y retorna el siguiente secuencial para un punto de emisión y tipo de comprobante. El UPDATE es atómico y seguro para concurrencia.';
