-- Opt-in, off by default: WhatsApp is an extra channel alongside email (never a replacement —
-- email alone is enough for a booking to work), so a tenant who never touches this setting keeps
-- getting exactly the notifications they get today.
ALTER TABLE tenants ADD COLUMN whatsapp_enabled BOOLEAN NOT NULL DEFAULT false;
