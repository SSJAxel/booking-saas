-- Bug reports submitted by a tenant user (message + an uploaded image), reviewed by the
-- founder in the super-admin panel. Tenant-scoped like any other tenant-owned row (see
-- BaseTenantEntity) so app_user_id doesn't need a redundant tenant check.
CREATE TABLE support_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    app_user_id UUID NOT NULL REFERENCES app_users(id),
    message TEXT NOT NULL,
    image_path VARCHAR(500) NOT NULL,
    image_content_type VARCHAR(100) NOT NULL,
    resolved BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_support_reports_tenant ON support_reports(tenant_id);
