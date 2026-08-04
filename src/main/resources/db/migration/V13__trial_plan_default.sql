-- TRIAL becomes the default plan for every tenant while real plan pricing/limits are still
-- undecided (see PlanTier.java and README roadmap) — new tenants get it from here on, and every
-- existing tenant is backfilled onto it too.
ALTER TABLE tenants ALTER COLUMN plan_tier SET DEFAULT 'TRIAL';
UPDATE tenants SET plan_tier = 'TRIAL';
