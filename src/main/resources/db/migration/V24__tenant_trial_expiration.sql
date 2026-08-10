-- Nullable, no backfill on purpose: only newly-created tenants get an expiration set (see
-- TenantService.create), so existing TRIAL tenants keep working exactly as they do today —
-- TrialExpirationScheduler only ever touches rows where this is set and in the past.
ALTER TABLE tenants
  ADD COLUMN trial_expires_at TIMESTAMPTZ;
