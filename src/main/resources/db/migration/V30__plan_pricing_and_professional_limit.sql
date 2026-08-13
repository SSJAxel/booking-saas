-- Founder-set override for how many professionals a specific tenant may have, independent of its
-- PlanTier's default "included" count — set by hand when approving a "mejorar plan" request for
-- more employees within the same tier. Null falls back to the plan's default.
ALTER TABLE tenants ADD COLUMN professional_limit_override INT;

-- Single global row: the dólar blue rate that the current plan prices were last indexed against.
-- Plan prices only move when the real rate drifts +/-115 from this reference (see PlanPricingScheduler).
CREATE TABLE platform_settings (
    id SMALLINT PRIMARY KEY DEFAULT 1,
    reference_blue_rate NUMERIC(10,2) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT single_row CHECK (id = 1)
);
INSERT INTO platform_settings (id, reference_blue_rate) VALUES (1, 1480.00);

-- Real-money list price per paid plan tier — moved out of the PlanTier enum (see PlanTier's
-- Javadoc) so it can be updated at runtime by PlanPricingScheduler without a redeploy.
-- usd_equivalent is the locked USD-equivalent target (ars_price / reference rate at the time it
-- was last set) — recomputing a new ars_price on reindex multiplies this by the new blue rate.
CREATE TABLE plan_pricing (
    plan_tier VARCHAR(20) PRIMARY KEY,
    ars_price NUMERIC(10,2) NOT NULL,
    usd_equivalent NUMERIC(10,4) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO plan_pricing (plan_tier, ars_price, usd_equivalent) VALUES
    ('PERSONAL', 10000.00, 6.7568),
    ('BASIC',    30000.00, 20.2703),
    ('PRO',      50000.00, 33.7838),
    ('MAX',      80000.00, 54.0541);
