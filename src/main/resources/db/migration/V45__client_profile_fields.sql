-- "Ficha del cliente": lo que hace que el registro de un cliente sea útil más allá de sus datos de
-- contacto, siempre cargado por quien presta el servicio (nunca por el cliente mismo, mismo criterio
-- que birth_date en V44). service_preferences es la memoria técnica del negocio ("qué le hicimos y
-- cómo" — fórmula de tinte, guía de máquina, estilo de trazo) para repetir sin que el cliente tenga
-- que explicar de nuevo; allergies es dato de seguridad real, no solo simpático.
ALTER TABLE clients ADD COLUMN service_preferences VARCHAR(2000);
ALTER TABLE clients ADD COLUMN allergies VARCHAR(1000);
