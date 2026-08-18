-- Nullable, purely informational link between appointments booked together in one client flow
-- (e.g. "corte con Lauti" + "tratamiento capilar con Facu" el mismo día o en días distintos).
-- Deliberately outside the no_double_booking EXCLUDE constraint (V4) and its GiST index — grouping
-- two appointments has no bearing on whether either one individually overlaps another booking for
-- its own professional, so this column carries no constraint of its own. Each appointment in a
-- group keeps its own status/payment lifecycle; cancelling one never cascades to the other.
ALTER TABLE appointments ADD COLUMN booking_group_id UUID;
CREATE INDEX idx_appointments_booking_group ON appointments (booking_group_id) WHERE booking_group_id IS NOT NULL;
