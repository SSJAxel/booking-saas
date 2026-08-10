ALTER TABLE appointments ADD COLUMN is_overtime BOOLEAN NOT NULL DEFAULT FALSE;

-- A sobreturno is a manual, deliberate decision by the owner to overlap an appointment with
-- another one of the same professional (e.g. a long service in progress while the professional
-- also takes a quick one). Excluded entirely from the constraint below, both as the new row being
-- inserted and as an existing row a new insert is checked against — two sobreturnos can overlap
-- each other too, intentional, since each one is already its own manual decision. Every other
-- booking path (public site, normal manual booking) is unaffected.
ALTER TABLE appointments DROP CONSTRAINT no_double_booking;
ALTER TABLE appointments
	ADD CONSTRAINT no_double_booking
	EXCLUDE USING gist (
		tenant_id WITH =,
		professional_id WITH =,
		time_range WITH &&
	)
	WHERE (status NOT IN ('CANCELLED', 'NO_SHOW') AND NOT is_overtime);
