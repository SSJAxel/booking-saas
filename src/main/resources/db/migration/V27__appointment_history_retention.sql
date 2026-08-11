ALTER TABLE tenants ADD COLUMN history_retention_months INT NOT NULL DEFAULT 12;
ALTER TABLE tenants ADD CONSTRAINT tenants_history_retention_months_range
    CHECK (history_retention_months BETWEEN 1 AND 12);

-- payments: un pago sin el turno al que pertenece no tiene sentido conservarlo -> CASCADE.
ALTER TABLE payments DROP CONSTRAINT payments_appointment_id_fkey;
ALTER TABLE payments ADD CONSTRAINT payments_appointment_id_fkey
    FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE CASCADE;

-- sales: una venta de producto es su propio registro de ingresos (ya es valida sin turno, para
-- ventas de mostrador -- ver V7) -> SET NULL, nunca desaparece solo porque se borro el turno.
ALTER TABLE sales DROP CONSTRAINT sales_appointment_id_fkey;
ALTER TABLE sales ADD CONSTRAINT sales_appointment_id_fkey
    FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE SET NULL;
