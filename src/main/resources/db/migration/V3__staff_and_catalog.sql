CREATE TABLE branches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255),
    phone VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT true
);
CREATE INDEX idx_branches_tenant ON branches(tenant_id);

CREATE TABLE professionals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    app_user_id UUID REFERENCES app_users(id),
    display_name VARCHAR(255) NOT NULL,
    bio TEXT,
    active BOOLEAN NOT NULL DEFAULT true
);
CREATE INDEX idx_professionals_tenant ON professionals(tenant_id);
CREATE INDEX idx_professionals_branch ON professionals(branch_id);

CREATE TABLE weekly_availabilities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    professional_id UUID NOT NULL REFERENCES professionals(id),
    day_of_week VARCHAR(10) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL
);
CREATE INDEX idx_weekly_avail_tenant ON weekly_availabilities(tenant_id);
CREATE INDEX idx_weekly_avail_professional ON weekly_availabilities(professional_id);

CREATE TABLE time_offs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    professional_id UUID NOT NULL REFERENCES professionals(id),
    date DATE NOT NULL,
    start_time TIME,
    end_time TIME,
    reason VARCHAR(255)
);
CREATE INDEX idx_time_offs_tenant ON time_offs(tenant_id);
CREATE INDEX idx_time_offs_professional ON time_offs(professional_id);

CREATE TABLE service_offerings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    duration_minutes INT NOT NULL,
    price NUMERIC(10,2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true
);
CREATE INDEX idx_service_offerings_tenant ON service_offerings(tenant_id);

CREATE TABLE professional_service_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    professional_id UUID NOT NULL REFERENCES professionals(id),
    service_id UUID NOT NULL REFERENCES service_offerings(id),
    UNIQUE (professional_id, service_id)
);
CREATE INDEX idx_prof_service_tenant ON professional_service_assignments(tenant_id);
