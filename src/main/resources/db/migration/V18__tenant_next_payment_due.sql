-- Manually maintained by the founder per tenant when a payment is collected — NOT computed
-- from MercadoPago webhooks. "Days remaining" on the admin dashboard = this date minus today.
ALTER TABLE tenants ADD COLUMN next_payment_due_at DATE;
