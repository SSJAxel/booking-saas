-- Lets a tenant group its own services into categories (e.g. "Cortes"/"Tratamientos"/"Rasurado")
-- for the public booking page — null means "Servicios" (single ungrouped bucket) on the frontend.
ALTER TABLE service_offerings ADD COLUMN category VARCHAR(100);

-- Cover/banner photo for the public booking page hero — same URL-string convention as logo_url
-- (see V10): a tenant hosts the image elsewhere and links it, no file storage in this project.
ALTER TABLE tenants ADD COLUMN banner_url VARCHAR(500);

-- Professional's own photo, shown on the public team carousel and their own admin panel.
ALTER TABLE professionals ADD COLUMN photo_url VARCHAR(500);
