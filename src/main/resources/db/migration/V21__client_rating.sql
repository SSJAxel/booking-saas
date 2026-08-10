ALTER TABLE clients
  ADD COLUMN rating INT NOT NULL DEFAULT 0,
  ADD COLUMN cancelled_count INT NOT NULL DEFAULT 0,
  ADD COLUMN instagram_handle VARCHAR(255);

-- Per-tenant knobs for the "mejores clientes" ranking: how many clients to surface (capped at 15
-- in application code) and the minimum rating they need to clear to count as "top".
ALTER TABLE tenants
  ADD COLUMN top_clients_threshold INT NOT NULL DEFAULT 8,
  ADD COLUMN top_clients_count INT NOT NULL DEFAULT 3 CHECK (top_clients_count BETWEEN 1 AND 15);
