-- Needed later for the appointment double-booking EXCLUDE constraint (range types + gist index on scalar columns).
CREATE EXTENSION IF NOT EXISTS btree_gist;
