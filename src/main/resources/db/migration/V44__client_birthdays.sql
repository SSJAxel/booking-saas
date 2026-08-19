-- Recordatorio de cumpleaños de clientes (PRO/MAX: lista en el panel; MAX: mail automático con
-- mensaje propio del tenant) — el descuento en sí sigue siendo manual/en persona, esto solo evita
-- que el tenant se olvide del día.
ALTER TABLE clients ADD COLUMN birth_date DATE;

-- Dedupe del mail automático: sin esto, correr el scheduler más de una vez el mismo día (o un
-- reinicio) mandaría el mail de nuevo. Guarda el año del último envío, no un booleano — se resetea
-- solo al año siguiente sin necesidad de un job de limpieza.
ALTER TABLE clients ADD COLUMN last_birthday_email_year INT;

-- Null (no configurado) es el estado por defecto y también el "apagado": BirthdayEmailScheduler
-- nunca manda nada para un tenant sin mensaje cargado, sin necesidad de un toggle separado.
ALTER TABLE tenants ADD COLUMN birthday_message_template VARCHAR(1000);
