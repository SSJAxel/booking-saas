-- Eliminar un cliente ahora se lleva puesto todo lo que solo tiene sentido bajo él (turnos,
-- reseñas) en vez de fallar con una violación de foreign key apenas tuviera cualquier historial
-- real — mismo espíritu que V28 (borrar sucursal/profesional). payments/sales de los turnos que se
-- borran en cascada siguen las reglas ya establecidas en V27 (payments se borra con su turno,
-- sales solo pierde la referencia); waitlist_entries no tiene client_id (guarda nombre/email/
-- teléfono en el momento, no una referencia), así que no necesita tocarse acá.
ALTER TABLE appointments DROP CONSTRAINT appointments_client_id_fkey;
ALTER TABLE appointments ADD CONSTRAINT appointments_client_id_fkey
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;

ALTER TABLE reviews DROP CONSTRAINT reviews_client_id_fkey;
ALTER TABLE reviews ADD CONSTRAINT reviews_client_id_fkey
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;

-- Bug latente encontrado de paso, sin relación con el borrado de cliente en sí: reviews.appointment_id
-- nunca tuvo ON DELETE CASCADE (a diferencia de payments/sales en V27), así que "Eliminar turno"
-- (AppointmentController#delete) ya fallaba hoy con una violación de foreign key para cualquier
-- turno que tuviera una reseña dejada — arreglado acá porque el cascade de un cliente pasa
-- primero por sus turnos, y sin esto se hubiera topado con el mismo problema.
ALTER TABLE reviews DROP CONSTRAINT reviews_appointment_id_fkey;
ALTER TABLE reviews ADD CONSTRAINT reviews_appointment_id_fkey
    FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE CASCADE;
