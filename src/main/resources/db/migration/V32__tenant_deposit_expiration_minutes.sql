-- Per-tenant override for how long a PENDING deposit has before
-- PendingDepositExpirationScheduler auto-cancels the appointment (see that scheduler's Javadoc) —
-- only consulted for MercadoPago-enabled plans, tenants without MP are skipped by the scheduler
-- regardless of this value. Bounded 10-180: below 10 risks cancelling a payment that's still
-- genuinely in flight (MP redirect + card entry + webhook round trip); above 180 the slot stays
-- blocked for other clients too long, since a PENDING appointment still occupies the EXCLUDE
-- constraint's slot.
ALTER TABLE tenants ADD COLUMN deposit_expiration_minutes INT NOT NULL DEFAULT 30;
ALTER TABLE tenants ADD CONSTRAINT deposit_expiration_minutes_range
    CHECK (deposit_expiration_minutes BETWEEN 10 AND 180);
