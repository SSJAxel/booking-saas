CREATE TABLE branch_hours (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    day_of_week VARCHAR(10) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL
);
CREATE INDEX idx_branch_hours_tenant ON branch_hours(tenant_id);
CREATE INDEX idx_branch_hours_branch ON branch_hours(branch_id);
