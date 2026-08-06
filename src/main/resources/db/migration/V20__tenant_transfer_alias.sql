-- Bank-transfer alias shown to clients when a service requires a deposit — an alternative to
-- the MercadoPago checkout, confirmed manually by the owner (see AppointmentController
-- .confirmDeposit). Optional, same as contact_email/whatsapp_number.
ALTER TABLE tenants ADD COLUMN transfer_alias VARCHAR(255);
