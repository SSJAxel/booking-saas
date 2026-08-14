-- The mirror image of time_offs: instead of closing a date that would otherwise be open per the
-- weekly recurring pattern, this opens a specific date regardless of its day of week and
-- regardless of whether weekly_availabilities covers that weekday at all. For a professional who
-- works by season or who wants to release capacity one date at a time (rather than committing to
-- a recurring "every Tuesday" rule), the weekly pattern alone can't express "open just Sept 12, 17
-- and 24" -- those don't share a single weekday. Additive with weekly_availabilities, never
-- exclusive: a professional can have both, or rely on this table alone with an empty weekly
-- schedule.
CREATE TABLE date_availabilities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    professional_id UUID NOT NULL REFERENCES professionals(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL
);
CREATE INDEX idx_date_avail_tenant ON date_availabilities(tenant_id);
CREATE INDEX idx_date_avail_professional_date ON date_availabilities(professional_id, date);
