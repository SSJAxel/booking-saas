CREATE TABLE clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, email)
);
CREATE INDEX idx_clients_tenant ON clients(tenant_id);

CREATE TABLE appointments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    professional_id UUID NOT NULL REFERENCES professionals(id),
    service_id UUID NOT NULL REFERENCES service_offerings(id),
    client_id UUID NOT NULL REFERENCES clients(id),
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    time_range TSTZRANGE GENERATED ALWAYS AS (tstzrange(start_time, end_time, '[)')) STORED
);
CREATE INDEX idx_appointments_tenant ON appointments(tenant_id);
CREATE INDEX idx_appointments_professional ON appointments(professional_id);
CREATE INDEX idx_appointments_client ON appointments(client_id);

-- The actual double-booking guarantee: enforced by Postgres itself, not application code, so it
-- holds regardless of which code path inserts a row (this API, a future admin tool, a script).
-- Partial (WHERE ...) so cancelled/no-show appointments free the slot immediately.
ALTER TABLE appointments
  ADD CONSTRAINT no_double_booking
  EXCLUDE USING gist (
    tenant_id WITH =,
    professional_id WITH =,
    time_range WITH &&
  )
  WHERE (status NOT IN ('CANCELLED', 'NO_SHOW'));
