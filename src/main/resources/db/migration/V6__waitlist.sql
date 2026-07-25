CREATE TABLE waitlist_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    professional_id UUID NOT NULL REFERENCES professionals(id),
    service_id UUID NOT NULL REFERENCES service_offerings(id),
    date DATE NOT NULL,
    client_name VARCHAR(255) NOT NULL,
    client_email VARCHAR(255) NOT NULL,
    client_phone VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_waitlist_tenant ON waitlist_entries(tenant_id);
-- Matches the lookup in WaitlistService.notifyNextForFreedSlot: oldest WAITING entry for a
-- given professional/service/date.
CREATE INDEX idx_waitlist_lookup ON waitlist_entries(professional_id, service_id, date, status, created_at);
