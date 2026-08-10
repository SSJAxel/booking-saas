-- "Clientes fijos": a client the owner manually keeps in the "Mejores clientes" panel regardless
-- of their calculated rating (e.g. a loyal regular whose history predates this system).
ALTER TABLE clients
  ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT false;
