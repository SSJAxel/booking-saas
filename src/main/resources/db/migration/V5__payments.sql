-- Purely additive, as planned from the start: nothing in the phase-1 schema needs to change to
-- support deposits/payments.
ALTER TABLE service_offerings ADD COLUMN deposit_amount NUMERIC(10,2);
ALTER TABLE appointments ADD COLUMN payment_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED';

CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    appointment_id UUID NOT NULL REFERENCES appointments(id),
    provider VARCHAR(20) NOT NULL DEFAULT 'MERCADO_PAGO',
    provider_preference_id VARCHAR(255),
    provider_payment_id VARCHAR(255),
    amount NUMERIC(10,2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'ARS',
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_tenant ON payments(tenant_id);
CREATE INDEX idx_payments_appointment ON payments(appointment_id);

-- One row per external MercadoPago payment ID, globally (not per-tenant): all tenants share one
-- platform-level MercadoPago account in this MVP (see README), so MP's payment IDs are already
-- globally unique. This also gives idempotency for free — a duplicate webhook delivery for the
-- same payment can't create a second row.
CREATE UNIQUE INDEX idx_payments_provider_payment_id ON payments(provider_payment_id)
    WHERE provider_payment_id IS NOT NULL;
