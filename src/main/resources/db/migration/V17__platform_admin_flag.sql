-- Marks the founder's own account for the platform-wide super-admin panel (all tenants,
-- support reports). Separate axis from the per-tenant Role enum — set by hand in the DB,
-- there's no self-service way to grant this.
ALTER TABLE app_users ADD COLUMN platform_admin BOOLEAN NOT NULL DEFAULT false;
