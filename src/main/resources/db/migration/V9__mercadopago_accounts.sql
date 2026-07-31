-- One connected MercadoPago account per tenant (OAuth Connect) — replaces the single
-- platform-level sandbox account as the destination for that tenant's money. A tenant with no row
-- here yet still falls back to the platform account (see MercadoPagoAccountService), so connecting
-- is additive, not a hard requirement to keep operating.
CREATE TABLE mercadopago_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL UNIQUE REFERENCES tenants(id),
    mp_user_id VARCHAR(255) NOT NULL,
    access_token VARCHAR(500) NOT NULL,
    refresh_token VARCHAR(500) NOT NULL,
    public_key VARCHAR(255),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
