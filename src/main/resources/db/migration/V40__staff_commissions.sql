ALTER TABLE tenants ADD COLUMN commissions_enabled BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE professionals
  ADD COLUMN service_commission_rate NUMERIC(5,2) CHECK (service_commission_rate BETWEEN 0 AND 100),
  ADD COLUMN product_commission_rate NUMERIC(5,2) CHECK (product_commission_rate BETWEEN 0 AND 100);

ALTER TABLE sales ADD COLUMN professional_id UUID REFERENCES professionals(id);
