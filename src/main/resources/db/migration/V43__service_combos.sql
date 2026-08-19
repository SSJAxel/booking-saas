-- Precio de combo (solo plan MAX): un tenant puede definir un precio y una seña combinados para un
-- par específico de servicios reservados juntos (ej. tatuaje + piercing), distinto de la suma de
-- sus precios/señas individuales. service_a_id/service_b_id se guardan en orden canónico
-- (ServiceComboService normaliza antes de leer/escribir) para que el índice único detecte el mismo
-- par sin importar en qué orden se hayan elegido los dos servicios.
CREATE TABLE service_combos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    service_a_id UUID NOT NULL REFERENCES service_offerings(id),
    service_b_id UUID NOT NULL REFERENCES service_offerings(id),
    combo_price NUMERIC(10,2) NOT NULL,
    combo_deposit_amount NUMERIC(10,2),
    active BOOLEAN NOT NULL DEFAULT true,
    CHECK (service_a_id <> service_b_id)
);
CREATE UNIQUE INDEX idx_service_combos_pair ON service_combos (tenant_id, service_a_id, service_b_id);

-- Cuánto cobrarle de seña a ESTA cita puntual cuando difiere de lo que su propio ServiceOffering
-- indicaría — hoy el único caso real es la seña combinada de un combo, cargada entera en la primera
-- pata del grupo (la segunda queda en NOT_REQUIRED, ver AppointmentService.bookGroup). Null en el
-- caso normal: se sigue leyendo ServiceOffering.depositAmount como siempre.
ALTER TABLE appointments ADD COLUMN deposit_amount_override NUMERIC(10,2);
