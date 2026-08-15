ALTER TABLE tenants ADD COLUMN reviews_enabled BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE appointments
  ADD COLUMN review_token VARCHAR(64),
  ADD COLUMN review_token_expires_at TIMESTAMPTZ;
CREATE UNIQUE INDEX idx_appointments_review_token ON appointments (review_token)
    WHERE review_token IS NOT NULL;

CREATE TABLE reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    appointment_id UUID NOT NULL REFERENCES appointments(id) UNIQUE,
    client_id UUID NOT NULL REFERENCES clients(id),
    professional_id UUID NOT NULL REFERENCES professionals(id),
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment VARCHAR(1000),
    visible BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reviews_tenant ON reviews(tenant_id);
