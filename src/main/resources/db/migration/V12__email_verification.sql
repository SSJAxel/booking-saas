-- Existing rows (all pre-verification data) default to verified so nothing already in the
-- database gets locked out retroactively — this gate is for new signups going forward, not a
-- ban on accounts that predate it.
ALTER TABLE app_users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE app_users ALTER COLUMN email_verified SET DEFAULT false;

ALTER TABLE app_users ADD COLUMN verification_token VARCHAR(64);
ALTER TABLE app_users ADD COLUMN verification_token_expires_at TIMESTAMPTZ;

-- Global uniqueness (not just per-tenant) even though every lookup also scopes by tenant slug
-- (see AuthService.verifyEmail) — a stricter guarantee than needed costs nothing here and rules
-- out a token collision across tenants by construction.
CREATE UNIQUE INDEX idx_app_users_verification_token ON app_users (verification_token)
	WHERE verification_token IS NOT NULL;
