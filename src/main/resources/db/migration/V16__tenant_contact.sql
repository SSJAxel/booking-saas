-- Business contact channels for the owner's quick-access buttons (booking site is derived from
-- slug, but email/WhatsApp need their own fields since neither existed anywhere on Tenant before).
ALTER TABLE tenants ADD COLUMN contact_email VARCHAR(255);
ALTER TABLE tenants ADD COLUMN whatsapp_number VARCHAR(30);
