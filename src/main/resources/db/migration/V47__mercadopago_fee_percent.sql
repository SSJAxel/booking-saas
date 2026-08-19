-- Comisión real que Mercado Pago le cobra a ESTE tenant por cobro (varía por cuenta y por plazo de
-- acreditación elegido — no es un número fijo de la plataforma, ver README). Null = no recargar
-- nada al cliente (comportamiento de siempre, el tenant absorbe el costo). Cuando está cargado, el
-- checkout de Mercado Pago (nunca el alias de transferencia, que no tiene comisión) le suma este %
-- a la seña para que el neto que le llega al tenant sea el valor de seña configurado.
ALTER TABLE tenants ADD COLUMN mercadopago_fee_percent NUMERIC(5,2);
ALTER TABLE tenants ADD CONSTRAINT tenants_mercadopago_fee_percent_range
    CHECK (mercadopago_fee_percent IS NULL OR mercadopago_fee_percent BETWEEN 0 AND 30);
