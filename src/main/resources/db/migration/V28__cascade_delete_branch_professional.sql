-- Eliminar una sucursal o un profesional ahora se lleva puesto todo lo que solo tiene sentido bajo
-- ella/él (horarios propios, disponibilidad semanal, bloqueos, asignaciones de servicio, turnos
-- reservados, lista de espera) en vez de siempre fallar con una violacion de foreign key apenas
-- tuviera cualquier historial real -- que en la practica era "siempre", asi que "Eliminar" no tenia
-- ningun camino que funcionara para una sucursal/profesional ya usado.
--
-- payments/sales de los turnos que se borran en cascada siguen las reglas ya establecidas en V27
-- (payments se borra con su turno, sales solo pierde la referencia al turno).

ALTER TABLE professionals DROP CONSTRAINT professionals_branch_id_fkey;
ALTER TABLE professionals ADD CONSTRAINT professionals_branch_id_fkey
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE;

ALTER TABLE branch_hours DROP CONSTRAINT branch_hours_branch_id_fkey;
ALTER TABLE branch_hours ADD CONSTRAINT branch_hours_branch_id_fkey
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE;

ALTER TABLE appointments DROP CONSTRAINT appointments_branch_id_fkey;
ALTER TABLE appointments ADD CONSTRAINT appointments_branch_id_fkey
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE;

ALTER TABLE appointments DROP CONSTRAINT appointments_professional_id_fkey;
ALTER TABLE appointments ADD CONSTRAINT appointments_professional_id_fkey
    FOREIGN KEY (professional_id) REFERENCES professionals(id) ON DELETE CASCADE;

ALTER TABLE weekly_availabilities DROP CONSTRAINT weekly_availabilities_professional_id_fkey;
ALTER TABLE weekly_availabilities ADD CONSTRAINT weekly_availabilities_professional_id_fkey
    FOREIGN KEY (professional_id) REFERENCES professionals(id) ON DELETE CASCADE;

ALTER TABLE time_offs DROP CONSTRAINT time_offs_professional_id_fkey;
ALTER TABLE time_offs ADD CONSTRAINT time_offs_professional_id_fkey
    FOREIGN KEY (professional_id) REFERENCES professionals(id) ON DELETE CASCADE;

ALTER TABLE professional_service_assignments DROP CONSTRAINT professional_service_assignments_professional_id_fkey;
ALTER TABLE professional_service_assignments ADD CONSTRAINT professional_service_assignments_professional_id_fkey
    FOREIGN KEY (professional_id) REFERENCES professionals(id) ON DELETE CASCADE;

ALTER TABLE waitlist_entries DROP CONSTRAINT waitlist_entries_professional_id_fkey;
ALTER TABLE waitlist_entries ADD CONSTRAINT waitlist_entries_professional_id_fkey
    FOREIGN KEY (professional_id) REFERENCES professionals(id) ON DELETE CASCADE;
