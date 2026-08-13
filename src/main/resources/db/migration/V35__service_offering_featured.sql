-- Lets an owner flag a service to show first in a "Destacados" section on the public catalog,
-- ahead of the normal category grouping — the same service can appear both there and in its own
-- category, not exclusive to one or the other.
ALTER TABLE service_offerings ADD COLUMN featured BOOLEAN NOT NULL DEFAULT false;
