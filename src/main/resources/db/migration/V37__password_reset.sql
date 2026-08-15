ALTER TABLE app_users ADD COLUMN reset_token VARCHAR(64);
ALTER TABLE app_users ADD COLUMN reset_token_expires_at TIMESTAMPTZ;

-- Same reasoning as idx_app_users_verification_token (V12): global uniqueness even though every
-- lookup also scopes by tenant slug (see AuthService.resetPassword) — rules out a token collision
-- across tenants by construction.
CREATE UNIQUE INDEX idx_app_users_reset_token ON app_users (reset_token)
	WHERE reset_token IS NOT NULL;
