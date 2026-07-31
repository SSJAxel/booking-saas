-- Recurring plan billing. PlanTier was otherwise a free-to-flip field on tenants (see
-- TenantService.changePlan's Javadoc) — this is what actually gates it behind payment.
-- One row per subscription attempt, not one per tenant: a tenant can cancel and resubscribe
-- later, so "the current one" is whichever row is AUTHORIZED/PENDING most recently, not a
-- unique tenant_id.
CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    plan_tier VARCHAR(20) NOT NULL,
    provider VARCHAR(20) NOT NULL DEFAULT 'MERCADO_PAGO',
    provider_subscription_id VARCHAR(255),
    amount NUMERIC(10,2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'ARS',
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_subscriptions_tenant ON subscriptions(tenant_id);

-- Same idempotency reasoning as payments.provider_payment_id (see V5): one shared MercadoPago
-- account across all tenants in this MVP, so provider ids are already globally unique.
CREATE UNIQUE INDEX idx_subscriptions_provider_subscription_id ON subscriptions(provider_subscription_id)
    WHERE provider_subscription_id IS NOT NULL;
