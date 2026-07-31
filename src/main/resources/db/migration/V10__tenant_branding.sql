-- Minimal branding for the public site (see frontend-public) — a logo URL rather than an upload,
-- since there's no file storage in this project yet (S3/Cloudinary etc.) and a tenant hosting
-- their own logo somewhere and linking it is a reasonable MVP tradeoff. All optional: an
-- unbranded tenant just gets frontend-public's default look.
ALTER TABLE tenants ADD COLUMN logo_url VARCHAR(500);
ALTER TABLE tenants ADD COLUMN accent_color VARCHAR(7);
ALTER TABLE tenants ADD COLUMN tagline VARCHAR(255);
