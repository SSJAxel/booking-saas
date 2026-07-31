# Booking SaaS

A multi-tenant appointment-booking backend for service businesses (tattoo studios, barbershops,
salons) — think a small AgendaPro. Each tenant has branches, professionals, a service catalog,
weekly availability, and clients who book time slots through a public API.

Backend only (REST API + OpenAPI/Swagger docs) — no frontend. Built as a "serious" portfolio
project: the two hardest correctness properties of this domain (never double-booking a
professional, never leaking one tenant's data to another) are enforced structurally rather than
just in application code — see [Design notes](#design-notes) below.

## Stack

- Java 21 (language level; see [Running locally](#running-locally) for a JDK note) · Spring Boot 4
- PostgreSQL 16, Flyway migrations
- Spring Data JPA / Hibernate 7 — native `@TenantId` multi-tenancy
- Spring Security 6 + JWT (jjwt) — stateless auth
- springdoc-openapi (Swagger UI)
- Spring Mail — booking/status-change email notifications, event-driven
- Bucket4j + Caffeine — per-IP rate limiting on the public booking API
- MercadoPago (Checkout Pro) — optional deposit/payment on booking, via plain `RestClient` calls
- Waitlist with FIFO auto-notify, and an aggregate business reporting endpoint
- JUnit 5, Testcontainers (integration tests run against real Postgres, not H2)
- Docker Compose (local Postgres + MailHog)

## Running locally

1. **Postgres + MailHog**: `docker compose up -d` (MailHog catches outgoing email locally —
   view sent emails at `http://localhost:8025`, no real mail account needed)
2. **JDK**: the project targets Java 21 (`maven.compiler.release`). If your `JAVA_HOME` points at
   an older JDK that can't cross-compile to 21 (anything below 21), point it at a JDK 21+ instead,
   e.g.:
   ```
   JAVA_HOME=/path/to/jdk-21-or-newer ./mvnw spring-boot:run
   ```
3. App boots on `http://localhost:8080`. Check `http://localhost:8080/actuator/health`.
4. **API docs**: Swagger UI at `http://localhost:8080/swagger-ui/index.html`, OpenAPI JSON at
   `/v3/api-docs`.

## Trying it out

```bash
# Register a business — creates the tenant + its OWNER user, returns a JWT
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"tenantName":"Tattoo Ink Studio","tenantSlug":"tattoo-ink","ownerEmail":"owner@tattooink.com","ownerPassword":"supersecret123"}'

# Use the returned token for everything under /api/**
TOKEN="<paste token here>"
curl http://localhost:8080/api/branches -H "Authorization: Bearer $TOKEN"

# Public booking API needs no auth — tenant comes from the slug in the path
curl "http://localhost:8080/api/public/tattoo-ink/services"
curl "http://localhost:8080/api/public/tattoo-ink/availability?professionalId=...&serviceId=...&date=2026-08-10"

# If the booked service has a depositAmount set, the appointment comes back with
# paymentStatus=PENDING. Kick off a MercadoPago checkout and redirect the client to the URL:
curl -X POST "http://localhost:8080/api/public/tattoo-ink/appointments/{appointmentId}/checkout"

# Slot fully booked? Join the waitlist — notified (by email) when someone cancels that date:
curl -X POST "http://localhost:8080/api/public/tattoo-ink/waitlist" -H "Content-Type: application/json" \
  -d '{"professionalId":"...","serviceId":"...","date":"2026-08-10","clientName":"Ana","clientEmail":"ana@example.com"}'

# Business owner: aggregate stats for a date range
curl "http://localhost:8080/api/reports/summary?from=2026-08-01T00:00:00Z&to=2026-09-01T00:00:00Z" \
  -H "Authorization: Bearer $TOKEN"
```

## Running tests

```
./mvnw test
```

Runs unit tests plus Testcontainers-backed integration tests (a real Postgres container is
started automatically — Docker must be running). Includes `DoubleBookingConcurrencyTest`, which
fires genuinely simultaneous booking requests at a running server and asserts exactly one wins —
the only real way to prove the double-booking guarantee holds, since it depends on a
Postgres-specific DB constraint that can't be expressed against H2.

## Design notes

**Double-booking prevention.** Enforced by a PostgreSQL partial `EXCLUDE` constraint on
`(tenant_id, professional_id, time_range)` (see `V4__appointments.sql`), not application-level
locking or a check-then-insert. A DB constraint holds regardless of which code path inserts a row;
app-level locking only protects code that remembers to use it, and check-then-insert is a classic
TOCTOU race. `AppointmentService.book()` forces a flush (`saveAndFlush`) so the constraint
violation surfaces inside the method's own try/catch instead of at transaction commit, and
translates both a clean constraint violation and the deadlock Postgres's lock detector can raise
under heavy simultaneous contention on the same slot into a 409.

**Tenant isolation.** Every tenant-scoped entity extends `BaseTenantEntity`, whose `@TenantId`
field makes Hibernate append a `tenant_id` filter to every query and stamp it on every insert
automatically — no repository is trusted to remember a `WHERE tenant_id = ...` clause. The current
tenant is resolved into a `TenantContext` ThreadLocal either from the JWT (`JwtAuthenticationFilter`,
admin/staff API) or the `{tenantSlug}` path segment (`PublicTenantResolutionFilter`, public API),
both running before any controller/service code executes. One consequence worth knowing: a single
Hibernate session resolves its tenant once, when opened — so a request handler can't set
`TenantContext` partway through its own `@Transactional` method and expect that same method's
queries to see it (see the Javadoc on `TenantService`/`AppUserService`, which is why
`AuthService.register()`/`login()` are deliberately *not* `@Transactional` themselves).

**Notifications.** `AppointmentService` publishes an `AppointmentNotificationEvent` (with
display-ready data already loaded — client/professional/service names — so the listener needs zero
extra queries) on booking and on every status transition. `AppointmentNotificationListener`
consumes it with `@TransactionalEventListener(phase = AFTER_COMMIT)`, so a client is never emailed
about a booking that then rolled back. `MailService` never lets a send failure propagate — a
broken mail server must not turn a successful booking into a 500.

**Rate limiting.** `PublicApiRateLimitFilter` applies a single token bucket (Bucket4j) per client
IP across all of `/api/public/**` — the only unauthenticated surface, so IP is the only identity
available to key on. Buckets live in a Caffeine cache with a 10-minute idle expiry so the map of
"every IP that's ever called us" doesn't grow forever. It runs before `PublicTenantResolutionFilter`
so an over-quota request never spends a DB lookup. Trusts `X-Forwarded-For` if present, which is
only safe behind a reverse proxy that sets/overwrites that header itself.

**Payments/deposits (MercadoPago).** A `ServiceOffering.depositAmount` (if set) makes new
appointments for that service start `paymentStatus=PENDING`; the public API's `.../checkout`
endpoint creates a `Payment` row plus a MercadoPago Checkout Pro preference and returns the
`init_point` URL to redirect the client to. MercadoPago's webhook (`POST
/api/webhooks/mercadopago`) confirms the outcome server-to-server — the webhook payload's status
is never trusted directly; `PaymentService` always re-fetches the payment from MercadoPago's API
with our own access token before acting on it. A paid deposit auto-confirms a `PENDING`
appointment (`AppointmentService.markDepositPaid`).

Two simplifications worth knowing:
- **One platform-level MercadoPago account for every tenant**, not per-tenant OAuth Connect — so
  in this MVP all deposits flow to a single sandbox account rather than each business's own. A
  real multi-tenant deployment would need MercadoPago's OAuth flow per tenant instead.
- **Not verified against a live MercadoPago sandbox** (no test credentials were available while
  building this) — `MercadoPagoClient` and `WebhookSignatureVerifier` are implemented strictly
  from MercadoPago's published API/webhook docs and covered by tests that mock the HTTP boundary
  (`MockRestServiceServer` for the client, a hand-rolled mock server for a full local
  checkout→webhook run). Treat as needing one live smoke test against a real sandbox account
  before depending on it.

The webhook resolves which tenant a notification belongs to from the payment's
`external_reference` (`"{tenantId}:{paymentId}"`, set when the preference is created) — the same
`TenantContext`-before-any-`@Transactional`-call pattern as `AuthService`, since there's no JWT or
URL slug on an inbound webhook call to resolve it from otherwise.

**Waitlist.** Date-level, not exact-time: a client waitlists for "this professional/service on
this date" rather than one specific slot, since freeing up any appointment that day changes what's
available. When `AppointmentService.transitionStatus()` cancels an appointment, it calls
`WaitlistService.notifyNextForFreedSlot()`, which notifies **only the single oldest `WAITING`
entry** for that professional/service/date — real FIFO semantics (first come, first served), not a
mailing list. Verified with `WaitlistFlowTest`: two clients join, one appointment cancels, and only
the earlier one flips to `NOTIFIED` while the other stays `WAITING`.

**Reporting.** `GET /api/reports/summary` (filterable by branch/professional/date range) returns
appointment counts by status, revenue (sum of service price for `COMPLETED` appointments),
deposits collected, no-show rate, and top services/professionals by booking count. Aggregates in
memory over `AppointmentService.search()`'s result rather than a SQL `GROUP BY` — consistent with
how `search()` itself already works, and fine at this project's scale; a high-volume deployment
would push this into the database instead.

**Not built (out of scope for this MVP):** SMS notifications, a real per-tenant IANA timezone
conversion (times are currently treated as UTC wall-clock end to end — see the comment in
`PublicAvailabilityService`), refund handling, recurring appointments, and a frontend.

## Roadmap: de MVP a producto vendible

Esto arrancó como pieza de portfolio, pero la idea es venderlo de verdad: un competidor de
AgendaPro/Booksy para negocios de servicios (barberías, salones, estudios), con planes de pago
mensuales de autoservicio. Lo que sigue es la hoja de ruta priorizada para llegar ahí, y el modelo
de negocio detrás.

### Modelo de negocio: dos productos, no uno

- **Planes de autoservicio** (`PlanTier` — `BASIC`/`PRO`, ver `tenant/PlanTier.java`): cualquiera
  se registra, elige un plan, paga una suscripción mensual, y usa el motor genérico tal cual —
  turno directo o con seña, catálogo de servicios, stock de hasta N productos según el plan. Cero
  intervención manual por cliente nuevo.
- **Integraciones a medida** (el caso de Luciana — ver `../estudio-lu-tatuajes/README.md`): un
  negocio que necesita reglas propias (aprobación de proyectos, número de reserva con
  vencimiento, cotizador, lo que sea) no lo resuelve un toggle de plan — es trabajo de desarrollo
  cobrado aparte, en USD, caso por caso. Si tienen el presupuesto, se adapta. El motor genérico ya
  está pensado para soportar esto como configuración/extensión por tenant (flags como
  `requiere_aprobacion`), no como un fork del código — así una integración a medida no ensucia el
  producto base.

### Para poder vender el plan de autoservicio (prioridad alta)

1. **Cobro recurrente real.** Hoy `PlanTier` es un campo que cualquiera cambia desde
   `PATCH /api/tenant/plan` sin pagar nada — es un stand-in, no un sistema de facturación (ver su
   Javadoc). Falta integrar el producto de suscripciones de Mercado Pago (Preapproval API,
   distinto del Checkout Pro que ya se usa para señas) y manejar su webhook de ciclo de vida
   (cobro exitoso, cobro fallido, cancelación) para que el plan suba o baje solo según si se pagó.
2. **Cuenta de Mercado Pago por tenant, no compartida.** Hoy todos los depósitos van a una sola
   cuenta sandbox (ver "Design notes" → Payments). Un producto real necesita que cada negocio
   cobre a su propia cuenta — requiere el flujo OAuth Connect de Mercado Pago por tenant.
3. **Verificación contra Mercado Pago real.** Nunca se probó contra credenciales reales (ver
   "Design notes"). Antes de cobrarle a un cliente de verdad hace falta un smoke test contra una
   cuenta sandbox real, no solo el mock casero.
4. **Página pública de precios y alta.** Hoy registrarse es un `POST /api/auth/register` a mano.
   Falta una landing en `frontend-public` (hoy `LandingPage` es un stub) que explique los planes y
   deje crear la cuenta sin tocar la API directo.

### Para competir en serio con AgendaPro (prioridad media)

5. **Branding por tenant en el sitio público.** Hoy `frontend-public` es 100% genérico — mismo
   look para todos los negocios. Un competidor real necesita al menos logo y color propios por
   tenant (sumar esos campos a `Tenant` y consumirlos ahí).
6. **Selección de sucursal en la reserva pública.** El flujo público hoy no filtra por sucursal
   (`PublicBookingController`) — no importa con una sola sucursal, pero un negocio multi-local lo
   va a pedir.
7. **Zona horaria real por tenant.** Hoy todo se trata como UTC de punta a punta (ver
   `PublicAvailabilityService`) — funciona mientras el negocio y sus clientes estén en el mismo
   huso horario, pero es una limitación real para escalar fuera de una sola región.
8. **WhatsApp además de mail.** Evaluado y pospuesto durante el diseño con Luciana por costo/scope
   (necesita la API de Meta o un proveedor tipo Twilio) — en el mercado de LatAm en el que compite
   (AgendaPro, Booksy) es casi esperado, no un extra.

### Deuda técnica conocida (prioridad según qué tan rápido haga falta)

9. **Política de cancelación/reembolso y no-show** — no definida todavía (mismo punto que quedó
   abierto en el spec de Luciana, pero aplica a cualquier tenant).
10. Reembolsos de depósitos y turnos recurrentes — ya listados como fuera de alcance del MVP (ver
    "Design notes"), sin cambios.

## Bitácora de desarrollo

Este proyecto lo construí con **Claude como par de programación** (dirigí el alcance y las
decisiones, Claude escribió el código y yo lo revisé), no lo tipeé línea por línea a mano — lo digo
de entrada porque me parece más útil (y más honesto) que fingir lo contrario. Lo que sí es 100%
real es todo lo que sigue: los problemas, no son inventados para quedar bien — son los que
efectivamente aparecieron construyendo esto, y cómo se resolvieron.

### Etapa 0 — Definir qué construir

Quería algo más serio que "otro CRUD de tareas". Después de pensarlo, elegí un sistema de turnos
multi-negocio (tipo AgendaPro/Booksy) porque combina un dominio con reglas de negocio reales
(no solo guardar y leer datos) con al menos dos problemas técnicos genuinamente difíciles de
resolver bien: que nunca se pisen dos turnos, y que los datos de un negocio nunca se mezclen con
los de otro.

**Decisión clave:** multi-tenencia con una sola base de datos compartida (una columna `tenant_id`
en cada tabla) en vez de una base de datos separada por negocio. Es el enfoque estándar en SaaS
reales y evita complejizar la arquitectura desde el primer día.

### Etapa 1 — El MVP (auth, turnos, disponibilidad)

- **Problema:** arranqué con Spring Boot recién salido (4.1.0), publicado después de que se
  entrenara el modelo de IA que uso. Casi toda la documentación y los tutoriales que existen están
  escritos para la versión anterior (3.x), así que varias clases que "debían" estar en un paquete
  ya no estaban ahí. Tuvimos que abrir directamente los archivos `.jar` descargados para encontrar
  dónde se habían movido.
- **Aprendizaje:** Spring Boot 4 modularizó mucho más las dependencias (paquetes de
  autoconfiguración separados, y hasta pasó a usar una versión nueva de la librería Jackson, con
  otro nombre de paquete). Cuando un framework está recién salido, a veces hay que investigar en el
  código fuente en vez de confiar en la documentación.
- **El bug más sutil de todo el proyecto:** al implementar el aislamiento entre negocios
  (multi-tenencia), el registro de un usuario nuevo fallaba de una forma rarísima — insertaba el
  usuario con un identificador de negocio que no existía. Después de investigar, entendimos que
  Hibernate (la librería que traduce objetos Java a filas de base de datos) decide a qué negocio
  pertenece una operación **una sola vez, al abrir la conexión** — no en cada consulta. Si cambiás
  esa información a mitad de camino, ya es tarde. La solución fue separar esa operación en dos
  pasos independientes en vez de uno solo.
- **Decisión de diseño que quedé contento con:** en vez de evitar turnos duplicados con lógica en
  el código (que se puede saltear u olvidar), lo resolvimos con una restricción a nivel de base de
  datos (PostgreSQL) que hace **imposible** insertar dos turnos que se pisen para el mismo
  profesional, sin importar qué parte del código intente hacerlo.
- **Cómo lo probé de verdad:** no me conformé con "debería andar" — armamos un test que dispara
  10 reservas al mismo horario **al mismo tiempo, de verdad** (no simulado) y confirma que solo una
  se queda con el turno. Ahí también aprendimos que, bajo muchísima concurrencia, la base de datos
  a veces devuelve un "interbloqueo" en vez de un rechazo prolijo — hubo que contemplar los dos
  casos.

### Etapa 2 — Notificaciones por mail

- Cada vez que se crea o cambia el estado de un turno, se dispara un mail (confirmación,
  cancelación, etc.).
- **Problema:** no tenía ganas de mandar mails reales solo para probar. Solución: un servidor de
  mail "de mentira" que corre en tu propia máquina (MailHog) — los mails nunca salen a internet,
  pero los podés ver en una pantallita web como si fuera una bandeja de entrada real.
- **Bug encontrado (y gracioso):** un mail en inglés mostraba la fecha en español ("lunes 10
  agosto") porque el código para dar formato a la fecha usaba el idioma configurado en la
  computadora, no uno fijo. Quedó corregido explícitamente para que siempre sea en inglés,
  sin importar en qué máquina corra.
- **Aprendizaje:** si algo puede fallar (un servidor de mail caído), que falle en silencio y quede
  registrado, pero que nunca tire abajo la operación principal (reservar un turno) por su culpa.

### Etapa 3 — Límite de pedidos (anti-abuso)

- Cualquiera puede llamar a la parte pública de la API sin iniciar sesión, así que había que
  evitar que alguien la sature a propósito.
- **Problema:** al conectar el filtro de límite de pedidos, Spring Security (la librería de
  seguridad) tiró un error porque no se puede "enganchar" un filtro propio tomando como referencia
  a *otro* filtro propio — solo a los que ya conoce el framework. Hubo que reordenar cómo se
  registran.
- **Bug de codificación de caracteres:** un guion largo (—) en un mensaje de error aparecía roto
  en la respuesta. Faltaba indicar explícitamente que el texto de esa respuesta iba en UTF-8 (el
  estándar que soporta tildes, eñes y símbolos raros).

### Etapa 4 — Pagos con MercadoPago (la parte más ambiciosa)

- No tenía credenciales de prueba reales de MercadoPago a mano. En vez de dejarlo sin probar,
  armamos un servidor simulado propio que responde exactamente como respondería MercadoPago, y
  con eso corrimos el flujo completo de punta a punta: reservar, generar el link de pago, simular
  el aviso de "pago aprobado" (con firma de seguridad calculada a mano, no truchada), y confirmar
  que el turno se confirma solo y se manda el mail correspondiente.
- **Aprendizaje importante sobre seguridad:** nunca hay que confiar en el aviso que manda una
  pasarela de pago tal cual llega — siempre hay que volver a preguntarle a la pasarela "¿este pago
  realmente se aprobó?" usando nuestras propias credenciales, para que nadie pueda falsificar un
  aviso de pago aprobado.
- **Otro bug de concurrencia de Spring:** un método que se suponía debía ejecutarse "de forma
  segura" (dentro de una transacción) en realidad no lo hacía, porque se estaba llamando a sí
  mismo desde dentro de la misma clase — un detalle interno de cómo funciona Spring que hace que la
  anotación se ignore en silencio en ese caso puntual. Fácil de no notar, fácil de dejar pasar sin
  un chequeo cuidadoso.
- **Honestidad ante todo:** dejo explícito en este README que esta parte nunca se probó contra la
  cuenta real de MercadoPago, solo contra el simulador. Está hecha siguiendo su documentación
  oficial al pie de la letra, pero un "andá y probalo con una cuenta de verdad" sigue pendiente
  antes de confiar en esto para cobrar plata de verdad.

### Etapa 5 — Lista de espera y reportes

- Más simples que lo anterior, aplicando patrones ya aprendidos en las etapas de arriba (mismo
  esquema de "avisar por mail cuando pasa algo", misma forma de sumar una tabla nueva sin romper
  nada de lo que ya funcionaba).
- La lista de espera avisa a **una sola persona por vez** (a la que se anotó primero), no a todos
  los anotados juntos — para que sea una fila de verdad y no una lista de difusión.

### Lo que me llevo de todo esto

- Cuando la corrección de algo importa de verdad, conviene apoyarse en garantías de la base de
  datos en vez de solo en lógica de la aplicación — el código se puede saltear por error, una
  restricción de la base de datos no.
- "Debería funcionar" no alcanza: la mayor parte del tiempo se fue en probar cosas de verdad
  (mandando pedidos reales, mirando la bandeja de entrada real, disparando reservas simultáneas de
  verdad) en vez de asumir que andaban.
- Trabajar con una versión de un framework recién salida significa que hay que estar dispuesto a
  investigar en el código fuente cuando la documentación todavía no lo explica.
- Ser honesto sobre lo que no se probó (MercadoPago real) vale más que aparentar que todo está
  100% verificado cuando no es así.
