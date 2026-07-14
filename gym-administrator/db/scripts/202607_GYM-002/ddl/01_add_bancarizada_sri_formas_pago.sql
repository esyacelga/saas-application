-- G10 · Bancarización sobre USD 500.
--
-- El SRI exige que, cuando el total de un comprobante supera los USD 500, el
-- excedente se pague por un medio que utilice el sistema financiero. El catálogo
-- sri.formas_pago no distinguía cuáles códigos cumplen esa condición, así que la
-- validación no era consultable desde el código (mismo problema que resolvió G6
-- para el resto de catálogos).
--
-- Bancarizados: 16 tarjeta de débito, 17 dinero electrónico, 18 tarjeta prepago,
-- 19 tarjeta de crédito, 20 otros con utilización del sistema financiero.
-- No bancarizados: 01 sin utilización del sistema financiero, 15 compensación de
-- deudas, 21 endoso de títulos.

ALTER TABLE sri.formas_pago
  ADD COLUMN bancarizada BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE sri.formas_pago
   SET bancarizada = TRUE
 WHERE codigo IN ('16', '17', '18', '19', '20');
