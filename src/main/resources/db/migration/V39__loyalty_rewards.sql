ALTER TABLE tenants
  ADD COLUMN loyalty_rewards_enabled BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN loyalty_points_cap INT NOT NULL DEFAULT 25 CHECK (loyalty_points_cap BETWEEN 5 AND 200);

ALTER TABLE clients
  ADD COLUMN loyalty_points INT NOT NULL DEFAULT 0;

CREATE TABLE reward_tiers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    points_required INT NOT NULL CHECK (points_required > 0),
    description VARCHAR(255) NOT NULL
);
CREATE INDEX idx_reward_tiers_tenant ON reward_tiers(tenant_id);
