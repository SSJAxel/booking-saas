-- Personal profile fields for the account itself (not the tenant/business) — a display name and
-- an optional avatar link, shown in "Mi cuenta". Both nullable: an unset profile just falls back
-- to showing the email, same "additive, never required" pattern as tenant branding.
ALTER TABLE app_users ADD COLUMN display_name VARCHAR(255);
ALTER TABLE app_users ADD COLUMN avatar_url VARCHAR(500);
