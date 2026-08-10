# Booking SaaS

A multi-tenant appointment-booking backend for service businesses (tattoo studios, barbershops,
salons) — think a small AgendaPro. Each tenant has branches, professionals, a service catalog,
weekly availability, and clients who book time slots through a public API.

REST API + OpenAPI/Swagger docs, plus a single React/Vite frontend (`frontend/`) that covers both
the owner/staff admin panel and the client-facing public booking flow (`/reservar/:tenantSlug`,
no login needed) — one frontend, not two; see "One frontend, not two" in Design notes for why.
The two hardest correctness properties of this domain (never double-booking a professional, never
leaking one tenant's data to another) are enforced structurally rather than just in application
code — see [Design notes](#design-notes) below.

## Registro de cambios

Bitácora de qué se hizo y por qué, para tener noción del avance sin tener que leer el `git log`
entero. Entradas más nuevas arriba. El detalle técnico de cada feature vive en
[Design notes](#design-notes); esto es solo el resumen fechado.

### 2026-08-10 — Aprobación de tenants, planes, calificación de clientes, calendario

- **Aprobación de tenants por el super-admin.** Todo negocio nuevo arranca "pendiente" — su sitio
  público (`/reservar/...`) queda bloqueado hasta que el founder lo aprueba desde `/admin/tenants`.
  El panel del dueño (cargar sucursales, profesionales, servicios) sigue abierto mientras tanto,
  justamente para que el founder pueda ver cuántos empleados tiene antes de aprobar — la idea de
  fondo es que el cobro va a ser por cantidad de empleados (esa lógica de precio todavía no está
  construida, solo el gate de aprobación). Mail automático al founder cuando alguien se registra,
  y al dueño cuando se lo aprueba.
- **Plan Demo con vencimiento.** `TRIAL` pasó a mostrarse como "Demo" en toda la UI (el valor
  interno sigue siendo `TRIAL`, sin migración de datos). Todo tenant nuevo vence a los 15 días de
  creado y baja solo a `BASIC` — los tenants que ya existían no se tocan. Se arregló de paso un bug
  real: el plan MAX se guardaba bien, pero el botón en "Mi Plan" seguía diciendo "Próximamente" en
  vez de reflejar que ya era el plan activo.
- **"Mejorar plan" y bandeja de reportes unificada.** Botón en "Mi Plan" que le avisa al super-admin
  en vez de ir directo a Mercado Pago (pensado para MAX, que todavía no tiene precio de
  autoservicio, y para pedidos a medida). Comparte la misma bandeja que los reportes de bug
  (`/admin` → Reportes), separada en Pendientes / Historial permanente, con borrado manual para
  falsos positivos.
- **Sistema de calificación de clientes.** Puntaje por cliente (+1 turno completado, -2 desde la
  2ª cancelación, -5 no-show o depósito vencido sin pagar), identificado por email o teléfono para
  que un mismo cliente no pierda su historial si cambia de mail. Panel de "Mejores clientes"
  configurable por tenant (calificación mínima, cuántos mostrar) más clientes fijados a mano, y
  listas de frecuentes / cancelan seguido / no aparecen.
- **Calendario semanal real y "sobreturno".** La vista de Turnos del dueño pasó de una tabla plana
  a una grilla semana × hora, con turnos superpuestos mostrados lado a lado. Se agregó "Reemplazar
  turno" (para cuando un cliente cancela por teléfono, no por el sistema) y carga manual de turnos
  desde el panel — antes solo se podía reservar desde el sitio público.

## ¿Qué es un sistema de "booking"?

Un "booking" (reserva de turnos) es lo que le permite al cliente final de un negocio elegir un día
y horario disponible **por su cuenta**, sin llamar ni escribirle a nadie para coordinar — algo
parecido a reservar una mesa en la web de un restaurante, sacar turno médico online, o agendar un
corte de pelo desde el celular. El negocio carga de antemano quién atiende, qué servicios ofrece y
en qué horarios está disponible cada quien; el sistema se encarga de mostrarle al cliente solo los
horarios realmente libres y de que dos personas nunca terminen con el mismo turno.

Ejemplos conocidos de la categoría: Calendly (reuniones 1 a 1), OpenTable (mesas de restaurante),
y los competidores directos de este proyecto — **AgendaPro** y **Booksy** — que resuelven esto
mismo para peluquerías, salones de belleza, estudios de tatuajes y negocios de servicios similares
en LatAm.

Quién es quién en este sistema:
- **Tenant**: un negocio cliente de la plataforma (ej. una peluquería puntual). Cada tenant tiene
  sus propios datos, completamente aislados de los demás — ver "Tenant isolation" más abajo.
- **Profesional**: quien atiende (un peluquero, un tatuador). Tiene un horario semanal cargado.
- **Servicio**: lo que se ofrece (un corte, una sesión) — dura X minutos, cuesta $Y, y puede pedir
  una seña para confirmar.
- **Cliente**: quien reserva, desde la página pública del negocio — no necesita cuenta ni login.
- **Turno (appointment)**: la reserva en sí — un profesional + un servicio + un horario puntual.

Una sola superficie web en este repo (`frontend/`): el **panel de administración**, donde el
dueño/staff del negocio configura todo lo de arriba, y dentro del mismo proyecto, la **página
pública de reserva** (`/reservar/{slug-del-negocio}`, sin login) donde el cliente final reserva.

## Stack

- Java 21 (language level; see [Running locally](#running-locally) for a JDK note) · Spring Boot 4
- PostgreSQL 16, Flyway migrations
- Spring Data JPA / Hibernate 7 — native `@TenantId` multi-tenancy
- Spring Security 6 + JWT (jjwt) — stateless auth
- springdoc-openapi (Swagger UI)
- Spring Mail — booking/status-change email notifications, event-driven
- Bucket4j + Caffeine — per-IP rate limiting on the public booking API
- MercadoPago Checkout Pro — optional deposit/payment on booking, via plain `RestClient` calls
- MercadoPago Preapproval — recurring monthly billing for paid `PlanTier`s, same `RestClient`
  approach, same shared webhook endpoint routed by notification `type`
- Waitlist with FIFO auto-notify, and an aggregate business reporting endpoint
- A small inventory/stock module (products + sales), capped by plan tier
- JUnit 5, Testcontainers (integration tests run against real Postgres, not H2)
- Docker Compose (local Postgres + MailHog)
- React + Vite (`frontend/`) — no TypeScript, no UI kit, plain `fetch` against the REST API

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
5. **Frontend**: `cd frontend && npm install && npm run dev` — opens on `http://localhost:5180`
   (owner panel; the public booking flow lives in the same app, see "Puertos" below).

## Puertos

This project only ever uses five ports, all fixed (`strictPort: true` on the Vite side, explicit
`ports:` mappings in `docker-compose.yml`) so a collision fails loudly instead of silently moving
to a different port — worth having this table on hand on a machine that runs several projects at
once, so a stray port doesn't get mistaken for a different one of your own projects.

| Puerto | Qué es | Cómo se levanta |
|---|---|---|
| `8080` | Backend (Spring Boot) — toda la API REST | `./mvnw spring-boot:run` |
| `5180` | `frontend/` — panel de dueño **y** flujo público de reserva (`/reservar/:tenantSlug`); es el único frontend del proyecto | `cd frontend && npm run dev` |
| `5432` | PostgreSQL | `docker compose up -d` |
| `1025` | MailHog — SMTP falso, donde el backend manda los mails en desarrollo | `docker compose up -d` |
| `8025` | MailHog — interfaz web para leer esos mails ([`localhost:8025`](http://localhost:8025)) | `docker compose up -d` |

No hay más servicios que estos cinco — si algo más aparece corriendo en un puerto parecido en esta
máquina, es de otro proyecto, no de este.

## ⚠️ Pendiente para la diseñadora: avatares de perfil

En "Mi cuenta" (`frontend/src/pages/AccountPage.jsx`), en vez de dejar que el usuario pegue
cualquier URL como foto de perfil, ahora elige entre un set fijo de avatares (misma idea que
Slack/Discord). Los 8 archivos que están hoy en `frontend/public/avatars/avatar-1.svg` a
`avatar-8.svg` son **placeholders funcionales** (círculos de color lisos), no diseño final.

Para reemplazarlos: subir los archivos finales con esos mismos nombres (`avatar-1.svg` ...
`avatar-8.svg`, o el mismo patrón en `.png`/`.webp` — si cambia la extensión avisar para actualizar
`AVATAR_OPTIONS` en `AccountPage.jsx`) a esa misma carpeta. Tamaño recomendado: cuadrado, al menos
128×128px, fondo no transparente (se recortan en círculo en la UI). Si se quiere sumar o sacar
opciones, también hay que ajustar `AVATAR_OPTIONS` (hoy es `avatar-1` a `avatar-8`, fijo).

## Enviar mail real (Gmail SMTP)

En desarrollo local, todos los mails (confirmación de turno, verificación de email, reportes de
bug) van a MailHog — no salen a internet. Para que salgan de verdad usando una cuenta de Gmail:

1. En la cuenta de Google que va a mandar los mails (ej. `info.capibyte@gmail.com`), activar
   verificación en 2 pasos (Cuenta de Google → Seguridad → Verificación en 2 pasos) — Gmail no
   deja usar la contraseña normal para SMTP, exige esto primero.
2. Una vez activada, ir a Cuenta de Google → Seguridad → "Contraseñas de aplicaciones", generar
   una nueva (nombre libre, ej. "booking-saas"). Google te da una contraseña de 16 caracteres —
   esa es la que se usa, **no** la contraseña normal de la cuenta.
3. Configurar estas variables de entorno donde corra el backend (no hardcodear en `application.yml`):
   ```
   MAIL_HOST=smtp.gmail.com
   MAIL_PORT=587
   MAIL_USERNAME=info.capibyte@gmail.com
   MAIL_PASSWORD=<la contraseña de aplicación de 16 caracteres, sin espacios>
   MAIL_SMTP_AUTH=true
   MAIL_SMTP_STARTTLS=true
   MAIL_FROM=info.capibyte@gmail.com
   ```
4. Reiniciar el backend — los mails van a salir de verdad. Probar con el flujo de "olvidé mi
   contraseña" o reservando un turno de prueba.

**Límite y a tener en cuenta**: Gmail permite ~500 mails/día por cuenta, y al venir de una cuenta
personal (no un dominio propio verificado) hay más chance de que caigan en spam que con un
proveedor transaccional (Resend, SendGrid, Amazon SES). Sirve perfecto para probar con usuarios
reales ahora; si el volumen crece o empiezan a caer en spam, migrar a uno de esos servicios es
el mismo cambio de variables de entorno (`MAIL_HOST`/`MAIL_USERNAME`/`MAIL_PASSWORD` que te da el
proveedor), sin tocar código.

**⚠️ No funciona en Render (ni en varios otros PaaS free tier)**: confirmado en vivo — el free tier
de Render bloquea las conexiones salientes por SMTP, así que `smtp.gmail.com:587` cuelga hasta
tirar `MailConnectException` (el registro/booking sigue funcionando igual, `MailService` atrapa el
error, pero el mail nunca sale). Para ese caso usar **Resend** en cambio, que manda por su API
HTTPS (puerto 443, nunca bloqueado):

1. Crear cuenta gratis en resend.com (100 mails/día, sin tarjeta) y generar una API key
   (Dashboard → API Keys → Create API Key).
2. Variables de entorno:
   ```
   RESEND_API_KEY=re_xxxxxxxx
   MAIL_FROM=onboarding@resend.dev
   ```
   Sin verificar un dominio propio en Resend, `MAIL_FROM` tiene que ser exactamente
   `onboarding@resend.dev`, y Resend solo entrega a la casilla con la que se creó la cuenta —
   suficiente para probar en vivo; para mandarle a clientes reales hace falta verificar un
   dominio propio en Resend y usar una dirección de ese dominio.
3. Con `RESEND_API_KEY` seteado, `MailService` usa la API de Resend en vez de SMTP automáticamente
   — no hace falta tocar `MAIL_HOST`/`MAIL_PORT`/etc, esas variables quedan sin efecto.

## Frenar el WhatsApp para no arriesgar el número

Cada vez que un turno cambia de estado (reservado, confirmado, cancelado) se manda un WhatsApp al
cliente si el negocio lo tiene activado (`Tenant.whatsappEnabled`). Meta le pone una "calificación
de calidad" a cada número de WhatsApp Business, y una ráfaga de mensajes seguidos al mismo
contacto (por ejemplo: reserva, cancela, reserva de nuevo, todo en un minuto) se lee como
comportamiento de spam y puede bajar esa calificación o directamente restringir el número.

`WhatsAppNotificationService` (`src/main/java/dev/capibyte/bookingsaas/notification/`) espacia
automáticamente los mensajes al mismo número: si ya se le mandó algo hace menos de
`WHATSAPP_MIN_INTERVAL_SECONDS` (30 segundos por defecto), el siguiente mensaje se demora en vez
de mandarse al toque, usando un scheduler propio (no bloquea nada más de la app). El mail nunca se
demora — solo el WhatsApp, que ya es un canal secundario por diseño.

## Cuenta de prueba

Para probar el panel de administración (`frontend/`, `http://localhost:5180`) o el flujo público de
reserva (`http://localhost:5180/reservar/lusi-tattoo`) sin tener que registrar un negocio nuevo, ya
existe un tenant de prueba cargado en la base local:

- **Identificador del negocio**: `lusi-tattoo`
- **Email**: `lusi@example.com`
- **Contraseña**: `lZk2at6zL7Pxlk`
- **Rol**: OWNER

Solo dev/local — no son credenciales de producción.

## Trying it out

```bash
# Register a business — creates the tenant + its OWNER user. No token yet: the account can't log
# in until the owner clicks the verification link mailed to them (see MailHog at :8025 locally).
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"tenantName":"Tattoo Ink Studio","tenantSlug":"tattoo-ink","ownerEmail":"owner@tattooink.com","ownerPassword":"supersecret123"}'

# Grab the token from the email in MailHog's UI (http://localhost:8025), then confirm it:
curl -X POST http://localhost:8080/api/auth/verify-email \
  -H "Content-Type: application/json" \
  -d '{"tenantSlug":"tattoo-ink","token":"<paste token from the email>"}'
# -> returns a real JWT, same shape as /api/auth/login from here on

# Use that token for everything under /api/**
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

**Email verification (registration).** `POST /api/auth/register` used to return a usable JWT
immediately — meaning anyone could spin up an unlimited number of working accounts with a fake
email, with nothing to stop it. It now creates the tenant/owner but returns no token
(`RegisterResponse`, not `AuthResponse`), and `AuthService.login` rejects the account until
`AppUser.emailVerified` is true. `AppUserService.createOwner` issues a random token (two
concatenated UUIDs, stripped of dashes — well past brute-forceable) with a 24-hour expiry, mailed
as a link via the existing `MailService`. `POST /api/auth/verify-email` takes `{tenantSlug, token}`
— the slug is what lets this resolve `TenantContext` before the `@TenantId`-scoped lookup by token
runs, same "embed the id we'll need on the way back" convention used for MercadoPago's
`external_reference`/OAuth `state` elsewhere in this file — and auto-logs the owner in on success,
returning a real `AuthResponse`. `POST /api/auth/resend-verification` always responds `204`
whether or not the email/tenant combination exists, so it can't be used to probe which emails are
registered. Verified end to end with a real mail: `AuthFlowTest` covers register-returns-no-token,
login-rejected-until-verified, verify-with-a-valid-token, invalid/expired tokens, and resend
issuing a fresh token — and a live smoke test round-tripped a real email through MailHog. Existing
integration tests didn't need touching individually: `IntegrationTestBase.registerTenant()` marks
the new owner verified directly in the database (the same "bypass the external dependency, not the
behavior under test" shortcut already used elsewhere for MercadoPago-gated plan changes), so the
~90 tests that already called it kept working unchanged.

**First-run onboarding (admin panel).** A new owner used to land on an empty "Turnos" table with
zero context. `WelcomeBanner` now shows on that same page whenever the tenant has zero branches —
a reliable "this account was just created and never configured" signal, not a one-time flag that
could go stale — explaining what the panel is (config for the business, not where clients book)
and linking the three steps needed before the public booking site can work at all: a branch, a
professional, a service. Dismissible on top of that, tracked per tenant in `localStorage`, for a
business that deliberately never adds a second branch and doesn't want to see it again.

**Notifications.** `AppointmentService` publishes an `AppointmentNotificationEvent` (with
display-ready data already loaded — client/professional/service names — so the listener needs zero
extra queries) on booking and on every status transition. `AppointmentNotificationListener`
consumes it with `@TransactionalEventListener(phase = AFTER_COMMIT)`, so a client is never emailed
about a booking that then rolled back. `MailService` never lets a send failure propagate — a
broken mail server must not turn a successful booking into a 500.

**WhatsApp notifications.** Opt-in, off by default (`Tenant.whatsappEnabled`, `PATCH
/api/tenant/notifications`, owner or admin) — an extra channel on top of email, never a
replacement, per an explicit product call: email alone is enough for a booking to work, so a
tenant who never touches the toggle sees zero behavior change. Implemented as a second,
independent `@TransactionalEventListener(phase = AFTER_COMMIT)` (`AppointmentWhatsAppListener`)
consuming the same `AppointmentNotificationEvent` that `AppointmentNotificationListener` (email)
already consumes, rather than merging the two — so a bug or outage in one channel can never affect
the other. `AppointmentNotificationEvent` carries `whatsappEnabled` and `clientPhone` (not just
`zone`) for the same reason the email listener needs `zone` carried in: it runs after commit, by
which point `TenantContext` is gone and the transaction that loaded the tenant/client is over, so
neither can be re-queried. The listener only sends when both are true — tenant opted in AND the
client actually left a phone number when booking (`Client.phone`, already collected).
`WhatsAppClient` is a plain `RestClient` wrapper over Twilio's Messages API (Basic Auth with
`account_sid`/`auth_token`, form-encoded body, `To` prefixed `whatsapp:`) — same "no SDK, explicit
HTTP contract" style as `MercadoPagoClient`. One platform-level Twilio WhatsApp sender for every
tenant, not a number per tenant — same MVP simplification as the pre-OAuth-Connect MercadoPago
account. `WhatsAppNotificationService` catches `RestClientException` and logs rather than
rethrowing, mirroring `MailService` — a Twilio outage or misconfiguration must never turn a
successful booking into a 500. Not verified against a live Twilio account (no test credentials
available while building this) — covered by `WhatsAppClientTest` (`MockRestServiceServer` request
contract) and `AppointmentWhatsAppListenerTest` (enable/disable/phone-present branching); treat as
needing a live smoke test before depending on it, same caveat as MercadoPago.

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

One simplification worth knowing: **one platform-level MercadoPago account for every tenant**, not
per-tenant OAuth Connect — so in this MVP all deposits flow to a single sandbox account rather than
each business's own. A real multi-tenant deployment would need MercadoPago's OAuth flow per tenant
instead.

**Verified live against a real MercadoPago sandbox (2026-08-01).** Full loop exercised end to end
against MercadoPago's actual API, not mocks: created a real Checkout Pro preference, paid it in a
browser with a MercadoPago test card, re-fetched the resulting payment from MercadoPago's API with
`getPayment` (confirmed `status: approved`, `external_reference` matching `{tenantId}:{paymentId}`
exactly), then hand-triggered `POST /api/webhooks/mercadopago` with a correctly-computed
`x-signature` to prove `WebhookSignatureVerifier` and `PaymentService.handleWebhook` process a real
payment id correctly — `Payment.status` flipped to `PAID`.

One genuine gap this surfaced, not a bug: the live test took over 30 minutes end to end (manual
sandbox setup, browser checkout), so `PendingDepositExpirationScheduler` auto-cancelled the
appointment before the payment webhook arrived. `AppointmentService.markDepositPaid` correctly
refused to un-cancel it (see its Javadoc — never resurrect a slot that may have been re-booked by
someone else), so the appointment ended up `CANCELLED` with `paymentStatus: PAID` — money captured
for a slot that's no longer held. Nothing currently surfaces this mismatch to the tenant owner (no
refund flow, no alert); worth fixing before this handles real money, tracked in the roadmap.

The webhook resolves which tenant a notification belongs to from the payment's
`external_reference` (`"{tenantId}:{paymentId}"`, set when the preference is created) — the same
`TenantContext`-before-any-`@Transactional`-call pattern as `AuthService`, since there's no JWT or
URL slug on an inbound webhook call to resolve it from otherwise.

**Plan billing (MercadoPago Preapproval).** `PlanTier` (`BASIC`/`PRO`) carries a `monthlyPrice`;
`BASIC` is free and can be set directly (`PATCH /api/tenant/plan`), but a paid tier can only be
reached through `POST /api/tenant/subscription`, which creates a `Subscription` row plus a
MercadoPago Preapproval (their recurring-billing product — a different API from the one-off
Checkout Pro used for deposits above) and returns its `init_point` for the owner to authorize the
recurring charge. `PATCH /api/tenant/plan` and `POST /api/tenant/subscription` reject the tier
they don't handle (`TenantService.changePlan` throws on a paid tier, `SubscriptionService.subscribe`
throws on a free one), so there's exactly one path to get onto — or off of — a paid plan.

Both MercadoPago products land on the same `POST /api/webhooks/mercadopago`, routed by the
`type` query param MercadoPago sends (`payment` → `PaymentService`, `subscription_preapproval` →
`SubscriptionService`) — same never-trust-the-payload rule as deposits: the webhook only carries
an id, `SubscriptionService` always re-fetches the preapproval from MercadoPago before acting.
`authorized` flips the tenant onto the subscribed tier; `paused`/`cancelled` drops it back to
`BASIC`. Downgrading manually (`PATCH .../plan` to `BASIC`) cancels any active subscription at
MercadoPago too, so a tenant who downgrades by hand stops being charged for the plan they no
longer have.

Known gap, same honesty policy as the Checkout Pro section above: this only reacts to the
preapproval's own status webhook, not MercadoPago's separate `authorized_payment` topic (fired for
each individual recurring charge) — so a single missed/retried monthly charge that MercadoPago is
still retrying isn't reflected here yet, only an eventual pause/cancellation is.

**Partially verified live (2026-08-01).** `createPreapproval` was exercised against MercadoPago's
real API and confirmed to work — but only after discovering a real, previously-undocumented
requirement: `payer_email` must belong to an actual MercadoPago account (real or sandbox test
user), or the API rejects the call with `400 Both payer and collector must be real or test users`.
A tenant's real owner email always satisfies this in production, so it's not a code change, just a
gap in what was previously assumed. Could not complete the interactive authorization step or
exercise the webhook path against a real `authorized` preapproval: MercadoPago's checkout emails a
one-time verification code to `payer_email` before letting a sandbox test user authorize a
recurring charge, and a sandbox test account's `@testuser.com` address isn't a real inbox — a
sandbox-environment dead end, not something in this codebase. `SubscriptionService.handleWebhook`
and the `authorized`/`paused`/`cancelled` state transitions remain unverified against a live
authorized subscription — covered instead by `MercadoPagoClientTest` (request/response contract)
and `SubscriptionServiceTest` (business logic).

**Per-tenant MercadoPago accounts (OAuth Connect).** `MercadoPagoClient` takes an
`accessToken` on every call that moves money or reads a payment/subscription — it has no
opinion on whose account a call is for. `MercadoPagoAccountService.resolveAccessToken` decides
that: a tenant's own connected account if they have one, the platform's shared token otherwise
(connecting is additive, not required — every existing tenant keeps working through the shared
account exactly like before this feature existed).

The connect flow: `GET /api/tenant/mercadopago/connect` (owner-only) returns the URL to send their
browser to (`MercadoPagoClient.buildAuthorizationUrl`), with the tenant id as the OAuth `state`
param — the same "embed the id we'll need on the way back" convention as `external_reference`
elsewhere in this file. MercadoPago redirects the browser to
`GET /api/mercadopago/oauth/callback?code=...&state=...` (public — no JWT reaches a browser
redirect), which exchanges the code for an access/refresh token pair
(`MercadoPagoAccountService.handleOAuthCallback`) and stores it in a new `MercadoPagoAccount` row,
then redirects back into the admin panel. A hardened version would use an opaque per-request nonce
for `state` and look the tenant up server-side, instead of trusting a client-suppliable value
directly — noted in `MercadoPagoClient.buildAuthorizationUrl`'s Javadoc.

`resolveAccessToken` also refreshes an expired stored token before handing it out
(`MercadoPagoClient.refreshAccessToken`), so a caller never gets handed a token MercadoPago's
about to reject. One deliberate asymmetry: webhook re-fetches (`getPayment`/`getPreapproval` inside
`handleWebhook`) keep using the *platform* token, not a resolved tenant one — the tenant isn't
known until after that re-fetch succeeds (that's the whole reason for re-fetching), and
MercadoPago's marketplace model is assumed to give the integrating application read access to
transactions it created through a connected account's OAuth flow. That assumption is unverified
against a live sandbox, same as the rest of this integration.

**Not attempted live yet:** the sandbox account used for the 2026-08-01 verification pass was
created as a plain Checkout Pro application — `client_id`/`client_secret` (needed for OAuth
Connect) only exist on a MercadoPago application configured as "Marketplace". Deferred rather than
solved by creating a second application, to keep that session focused; see the roadmap.

**Per-tenant branding.** Three optional `Tenant` fields — `logoUrl`, `accentColor` (hex, validated
`^#[0-9a-fA-F]{6}$`), `tagline` — editable via `PATCH /api/tenant/branding` (owner or admin; billing
stays owner-only, branding doesn't need to). `logoUrl` is a link, not a file upload: this project
has no file storage (S3/Cloudinary/etc.) yet, so a tenant hosts their own logo and links it — see
the roadmap for the tradeoff. `frontend/src/pages/BookingPage.jsx` (the public booking flow, see
"One frontend, not two" below) fetches the tenant once and overrides the CSS custom property
`--accent` inline (`style={{ "--accent": tenant.accentColor }}`) on its root element, so every
button/chip/card on that page picks up the tenant's color through the same variable they already
read from. An unset field is `null` end to end (DTOs, entity columns) and just falls back to the
panel's own default look; connecting branding, like connecting MercadoPago, is additive.
Known gap: no automatic contrast handling — `--accent-contrast` (used for button text) isn't
derived from the tenant's chosen color, so a very light `accentColor` could produce low-contrast
button text. Not built yet: logo file upload, since that needs a storage decision this project
hasn't made.

**Owner-panel quick-access buttons (contact email/WhatsApp).** Two more optional `Tenant` fields —
`contactEmail` (validated `@Email`) and `whatsappNumber` (validated `^[+0-9 ()-]{6,30}$`) — added
alongside branding on the same `PATCH /api/tenant/branding` request/response (`V16` migration).
`frontend/src/pages/TenantPage.jsx` shows an "Accesos rápidos" card at the top of the Negocio page
with three buttons: one to the tenant's own public booking page (`/reservar/{slug}`, always
enabled), one that opens a `mailto:` to `contactEmail`, and one that opens `https://wa.me/<digits>`
for `whatsappNumber` (non-digits stripped before building the link) — the latter two `disabled`
until their field is set, with a hint telling the owner to fill them in below. This was the fix for
"no veo dónde llegar a mi propio sitio/contacto desde el panel" — the owner previously had no
in-panel shortcut to their own public page or contact channels.

**One-click local dev launcher.** `start-dev.bat` (repo root) runs `docker compose up -d`, starts
the backend and frontend each in their own `cmd` window, waits for the frontend to respond, and
opens it in the browser — one double-click instead of the four manual steps in "Running locally"
below. It also pins `JAVA_HOME` to a JDK 21+ install for the backend window, since a system-wide
`JAVA_HOME` pointed at an older JDK (e.g. 17) fails with `UnsupportedClassVersionError` against
this project's `class file version 65` (Java 21) bytecode — edit the `JAVA_HOME` line at the top of
the script if your own JDK 21+ install lives somewhere else. A Desktop shortcut ("Booking SaaS -
Levantar proyecto") pointing at this script is the intended everyday entry point locally.

**Timezones.** `Tenant.timezone` (an IANA zone id, e.g. `America/Argentina/Buenos_Aires`; `UTC` at
registration) is now what every wall-clock time for that tenant is interpreted in — weekly
availability hours, the public availability search, and what "which day" an appointment counts
toward. It used to just be a stored string nobody read: `PublicAvailabilityService` treated booked
appointments' `Instant` values as if they were UTC wall-clock time, so a non-UTC tenant's bookings
would silently land at the wrong hour (verified by the bug this replaced: 10:00 in Buenos Aires,
UTC-3, used to get stored as 10:00 UTC — 7am local — instead of 13:00 UTC).

The public booking API's contract changed as part of the fix: `POST .../appointments` now takes
`date` + `startTime` (matching what `GET .../availability` already returns — both wall-clock, both
already tenant-local) instead of a client-supplied `Instant`. The server does the
`LocalDate`+`LocalTime`+`ZoneId` → `Instant` conversion (`PublicBookingController.book`), since only
the server can be trusted to know the tenant's real zone — a browser has no reliable way to know it
and a naive client-side conversion is exactly the class of bug this fixes. `TenantService.getZoneId`
is the one place that turns the stored string into a `ZoneId`; `AppointmentService` and
`PublicAvailabilityService` both call it rather than caching or assuming a zone.
`PATCH /api/tenant/timezone` validates by attempting `ZoneId.of(...)` and rejecting on
`DateTimeException`, not a regex — "looks like a zone id" and "is one `ZoneId` recognizes" aren't
the same check. Verified end to end by `TimezoneAwareBookingFlowTest`, which sets a tenant to
Buenos Aires and asserts the exact UTC instant a local-time booking resolves to.

Not built: recurring-charge-webhook nuance aside, this doesn't yet handle DST transitions
specially (Argentina doesn't observe DST, so the regression test can't exercise that edge), and
there's still no UI for a client to see times in anything but the tenant's zone (the public site
already only ever *works* in the tenant's zone — nothing shows the client's own local time
alongside it).

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

**Not built (out of scope for this MVP):** refund handling and recurring appointments. Per-tenant
timezone conversion and a frontend (in fact two — the admin panel and the public booking site) are
both built and covered above; this line used to list them as missing and was never updated once
they shipped — kept as a reminder to re-check this list periodically instead of trusting it blindly.

**Owner-panel CRUD completeness.** Branches, professionals, services, and products were
create-and-list only in `frontend/` for a while — the backend already had working `PUT`/`DELETE`
for all four, just never wired into the UI. Now fully editable/deletable from the panel, with a
`window.confirm()` before every delete (no custom dialog component; this project deliberately
stays "no UI kit, plain fetch") and inline "Guardando..."/"Producto actualizado." feedback on save
instead of a silent refresh.

**Weekly schedule editor (`WeeklySchedule.jsx`), shared by branch hours and professional
availability.** Both resources are the same shape server-side (`dayOfWeek` + `startTime` +
`endTime` rows, multiple allowed per day) — this is one React component used from both
`BranchesPage` and `ProfessionalsPage`, not two copies. Originally each day was add-one-at-a-time
with the result shown as a list of removable chips; replaced with a 7-row table (all weekdays
always visible, toggle a day on/off, edit its hours inline, optional midday "Descanso" split, and
a "Copiar en todos" to fan the first row's hours out to every other day) after the owner pointed at
a competitor's ("AgendaPro") equivalent screen as the bar to match. No backend/schema change was
needed for any of this: a day with a break is just **two** `WeeklyAvailability`/`BranchHours` rows
instead of one, a shape the tables already supported. `WeeklySchedule` never calls an update
endpoint (neither resource has one) — on save it diffs each day's desired ranges against what was
loaded, and for any day that changed, deletes that day's existing rows and creates the new ones;
unchanged days aren't touched. Branch hours are **stored only** so far — `PublicAvailabilityService`
still computes free slots from the professional's own `WeeklyAvailability` alone, not the branch's;
teaching the booking engine to intersect the two is a deliberately separate follow-up.

**Time-off (day/hour blocking) UI.** `TimeOff` (create/list/delete, null start/end = full day off)
already existed server-side and was already fully wired into `PublicAvailabilityService.findFreeSlots`
— nothing to fix there. The gap was purely that the admin panel had no screen for it; professionals
now have a "Bloqueos" section (date + optional start/end + optional reason) alongside their weekly
schedule.

**Professional branch reassignment.** `ProfessionalUpdateRequest` didn't carry `branchId`, so a
professional's branch could only ever be set at creation. Added to the update DTO/service/UI —
"cambio de modalidad de trabajo" (a professional moving between branches) is a normal event, not
an edge case.

**Plan tiers: `TRIAL` (new default) and `MAX` (price genuinely undecided).** `PlanTier` now has
four values, not two. Every tenant — new registrations and every existing one (`V13` migration
backfills them) — starts on `TRIAL`: `maxProducts=null` (unlimited, same as `PRO`), `monthlyPrice`
zero, since real per-tier pricing/limits aren't decided yet and blocking on that decision wasn't
worth it. `MAX`'s `monthlyPrice` is `null`, not zero — deliberately distinct from "free," meaning
"no price set." `isFree()` is null-safe now (`monthlyPrice != null && signum() == 0`) and
`SubscriptionService.subscribe` explicitly rejects a `null`-priced tier with a clear message, so
`MAX` can't reach MercadoPago with a null amount. `frontend/src/pages/TenantPage.jsx` renders a
`null` price as "Próximamente" with the upgrade button disabled, rather than treating it as free —
worth calling out because `Number(null) === 0` in JS, so a naive `formatPrice` check (`=== 0` →
"Gratis") silently mis-renders `MAX` as a free, selectable plan if that null case isn't checked
first. (A short-lived second frontend also had this exact bug on its own pricing page — see "One
frontend, not two" below for why that frontend doesn't exist anymore.)

**Personal account profile ("Mi cuenta").** `AppUser` had no name at all before this — just email,
password, role. Added optional `displayName`/`avatarUrl` (`V15` migration; a link, not a file
upload, same "no file storage in this project yet" tradeoff as tenant branding), a `PATCH /api/me`
next to the existing `GET`, and an "Mi cuenta" screen in the panel. Password changes are explicitly
**not** self-service yet — the screen says so — that's a deliberate scope cut, not an oversight.
The fetched profile lives in `AuthContext` (not local state inside the account page or the sidebar
separately), specifically so that saving a new name/photo updates the sidebar immediately instead
of only after the next full navigation.

**One frontend, not two.** This repo briefly had two: `frontend/` (the admin panel, which already
had its own `/reservar/:tenantSlug` public booking page — branch picker, per-tenant branding, a
real month-grid `Calendar.jsx`) and a separate `frontend-public/` built in an earlier session
without noticing `frontend/`'s version existed — `frontend-public/` additionally had a
pricing/landing page and self-serve tenant signup that `frontend/` never had. First attempt at
resolving the duplication merged the two — ported `Calendar.jsx` into `frontend-public/`, deleted
`frontend/`'s copy — on the theory that `frontend-public/` was the more feature-complete one and
just needed the better date picker. That made things visually worse, not better:
`frontend-public/` had never gotten the Apple-HIG retheme `frontend/` had, so the merged result
had more features but a duller, less polished look overall. Reverted that merge, then went
further: deleted `frontend-public/` entirely rather than leave two frontends around to
accidentally duplicate work in again. `frontend/` is now the only frontend, admin panel and public
booking flow both — nothing was actually lost on the booking page itself (branch selection and
branding were already there), but the pricing/landing page and self-serve signup `frontend-public/`
had are gone and not rebuilt anywhere; see the Roadmap.

## Roadmap: de MVP a producto vendible

La meta es un producto real: un competidor de AgendaPro/Booksy para negocios de servicios
(barberías, salones, estudios), con planes de pago mensuales de autoservicio. Lo que sigue es la
hoja de ruta priorizada para llegar ahí, y el modelo de negocio detrás.

### Modelo de negocio: dos productos, no uno

- **Planes de autoservicio** (`PlanTier` — `BASIC`/`PRO`, ver `tenant/PlanTier.java`): cualquiera
  se registra, elige un plan, paga una suscripción mensual, y usa el motor genérico tal cual —
  turno directo o con seña, catálogo de servicios, stock de hasta N productos según el plan. Cero
  intervención manual por cliente nuevo.
- **Integraciones a medida**: un negocio que necesita reglas propias (aprobación manual de
  pedidos antes de que ocupen agenda, número de reserva con vencimiento, cotizador, lo que sea) no
  lo resuelve un toggle de plan — es trabajo de desarrollo cobrado aparte, en USD, caso por caso. El
  motor genérico ya está pensado para soportar esto como configuración/extensión por tenant (flags
  como `requiere_aprobacion`), no como un fork del código — así una integración a medida no ensucia
  el producto base. (Este patrón salió de un caso real usado como piloto de diseño; el spec
  específico de ese negocio vive fuera de este repo, no es parte del producto genérico.)

### Decisión (provisoria): comercialización y hosting (2026-08-01)

Esto se resolvió en una sesión puntual, en base a los datos que había a mano en ese momento — no es
un veredicto final, es el mejor call posible con lo que sabíamos ese día. Documentado acá con el
razonamiento completo (no solo la conclusión) para que el equipo lo pueda revisar, cuestionar, y
mejorar con más cabezas pensando — humanas o de una IA que retome este archivo después.

**La pregunta original:** ¿cómo se cobra esto y dónde se hostea? No tiene sentido afinar el precio
de plan sin saber qué cuesta sostener la infraestructura por tenant, ni elegir hosting sin saber
cuántos tenants/tráfico hay que soportar.

**Hosting — se eligió Render.** Opciones comparadas: VPS propio (Hostinger/DigitalOcean, ~$8-12
USD/mes pero vos operás todo — parches, backups, TLS a mano), AWS Lightsail (similar, con la
complejidad extra de ser AWS), Railway (facturación variable según uso, potencialmente más barato
en tráfico bajo pero menos predecible para armar la cuenta de margen), y Render (Web Service +
Postgres administrado + Static Sites gratis para los dos frontends, ~USD 35/mes fijo). Se priorizó
Render porque el criterio fue "cero tiempo operando servidores", no "el costo mínimo absoluto" — con
otro criterio, otra opción de esta misma lista puede ser mejor. Vale la pena repreguntarse esto
cuando haya más claridad de tráfico/escala real.

**Precio de `PRO` — se fijó en ARS 23.000/mes** (antes un placeholder de $15.000), calculado como
~USD 15/mes al dólar blue de referencia de ese día (~$1.560) — por debajo del piso de AgendaPro
Argentina (USD 19/mes) y de Booksy (~USD 30/mes), y con margen positivo desde ~3 tenants pagando
(cubriendo los ~USD 35/mes de hosting). Ver "Preguntas abiertas" abajo — este número tiene supuestos
frágiles que el equipo debería revisar antes de comprometerse a él en serio.

### Preguntas abiertas para el equipo

Estas son preguntas reales, sin cerrar — la idea es que el equipo (con o sin una IA en la
conversación) las discuta y, si hay una mejor respuesta que la que tenemos hoy, se actualice esta
sección y la decisión de arriba. No son deuda técnica silenciosa, son decisiones de negocio
pendientes:

**Precio y modelo comercial**
- ¿Cobrar en pesos (como está ahora) o directo en dólares (como AgendaPro)? Pesos es más simple
  para un cliente argentino pagar, pero el precio se licua con la inflación y hay que estar
  revisándolo a mano. Dólares protege el margen pero puede espantar a un negocio chico que no
  quiere pensar en tipo de cambio.
- El costo de soporte (responder cuando algo se rompe, ayudar a un negocio nuevo a configurarse la
  primera vez) todavía no está metido en la cuenta de margen — hoy el cálculo es solo
  hosting vs. ingreso. ¿Cuánto tiempo por tenant es razonable presupuestar?
- ¿`BASIC` se sostiene gratis para siempre (como gancho de adquisición) o hace falta un tercer
  nivel intermedio? ¿Cuál sería el límite/gancho de cada nivel?
- Las integraciones a medida se plantean como "cobrado aparte, en USD, caso por caso" — pero no hay
  un proceso definido de cómo se cotiza eso en la práctica (¿por hora, por alcance fijo, un piso
  mínimo?). Vale la pena definirlo antes de que aparezca el primer cliente real que lo pida.

**Infraestructura**
- La elección de Render priorizó "cero ops" — ¿sigue siendo la prioridad correcta ahora que hay un
  equipo (quizás alguien sí puede/quiere ocuparse de un VPS más barato)?
- No hay plan de qué pasa si esto crece más allá de un puñado de tenants — a qué tamaño conviene
  reconsiderar el hosting, y con qué señal (¿facturación? ¿cantidad de tenants activos? ¿carga del
  servidor?).

**Producto**
- Política de cancelación/reembolso y no-show — no está definida para ningún tenant todavía.
- El caso de "el pago llega después de que el turno ya expiró y se canceló solo" (ver "Deuda
  técnica" abajo) necesita una decisión de producto, no solo una de código: ¿reembolso automático?
  ¿aviso al dueño para que decida a mano?
- OAuth Connect (cuenta de Mercado Pago propia por tenant) sigue sin poder probarse en vivo porque
  la app de sandbox usada no es de tipo "Marketplace" — hay que crear una nueva y repetir la
  verificación.

### Para poder vender el plan de autoservicio (prioridad alta)

1. ~~**Cobro recurrente real.**~~ Hecho — `POST /api/tenant/subscription` +
   MercadoPago Preapproval, ver "Design notes" → Plan billing. `PlanTier` ya no es un campo libre:
   un plan pago solo se activa cuando el webhook confirma `authorized`. Lo que quedó afuera a
   propósito: solo reacciona al estado del preapproval, no al webhook de cada cobro individual
   (`authorized_payment`) — un cobro puntual que Mercado Pago está reintentando todavía no se ve
   reflejado, recién cuando termina en pausa/cancelación.
2. ~~**Cuenta de Mercado Pago por tenant, no compartida.**~~ Hecho — OAuth Connect, ver
   "Design notes" → Per-tenant MercadoPago accounts. `GET /api/tenant/mercadopago/connect` +
   `MercadoPagoAccountService`; conectar es opcional, no obligatorio (el que no conecta sigue
   usando la cuenta compartida exactamente como antes). Quedó afuera a propósito: el `state` del
   OAuth es el `tenantId` directo, no un nonce opaco — más simple, pero menos duro contra un
   `state` forjado; anotado en el Javadoc de `buildAuthorizationUrl`.
3. ~~**Verificación contra Mercado Pago real.**~~ Hecho parcialmente — Checkout Pro (señas)
   verificado 100% en vivo: preferencia real, pago real con tarjeta de prueba, re-fetch real del
   pago, firma de webhook validada, turno actualizado (ver "Design notes" → Payments/deposits). De
   paso encontramos un gap real: si el pago tarda más que la ventana de expiración, el turno se
   cancela solo y el pago queda huérfano sin aviso — sin arreglar todavía. Preapproval
   (suscripciones) quedó a mitad de camino: confirmamos en vivo que `createPreapproval` funciona y
   que `payer_email` tiene que ser una cuenta real/de prueba de MercadoPago (dato nuevo, no
   documentado antes), pero no se pudo completar la autorización interactiva — MercadoPago manda un
   código de verificación por mail a `payer_email`, y una cuenta de prueba no tiene una casilla real
   para leerlo. OAuth Connect sigue sin probarse: la cuenta de sandbox usada es de tipo Checkout Pro
   simple, no "Marketplace" (que es el tipo de aplicación que expone `client_id`/`client_secret`).
4. **Página pública de precios y alta.** Se había hecho — una landing (`GET /api/plans`, catálogo
   público de precios, no tenant-scoped, por eso vive fuera de `/api/public/{tenantSlug}/**`) y
   `/registrarse` para crear la cuenta — pero vivía en `frontend-public/`, que se eliminó entero
   (ver "One frontend, not two" en Design notes). El endpoint `GET /api/plans` sigue existiendo y
   funciona; lo que falta es la pantalla. Sigue en el roadmap, ahora para reconstruir dentro de
   `frontend/`.

### Para competir en serio con AgendaPro (prioridad media)

5. ~~**Branding por tenant en el sitio público.**~~ Hecho — `Tenant.logoUrl/accentColor/tagline`,
   editables desde el panel (`PATCH /api/tenant/branding`, owner o admin) y consumidos por
   `frontend/src/pages/BookingPage.jsx` (logo + color propio via `--accent`). Logo es una URL, no
   un archivo subido — no hay almacenamiento de archivos en el proyecto todavía (ver Design notes).
6. ~~**Selección de sucursal en la reserva pública.**~~ Hecho — `GET /api/public/{tenantSlug}/branches`
   nuevo, y `.../services`/`.../professionals` ahora aceptan un `branchId` opcional
   (`ServiceOfferingService.isOfferedAtBranch` filtra el catálogo, que no es branch-scoped en sí
   mismo, por si algún profesional activo de esa sucursal lo ofrece). Un tenant de una sola
   sucursal no ve ningún selector — `frontend/src/pages/BookingPage.jsx` solo lo muestra si
   `GET .../branches` devuelve más de una, así que la experiencia no cambió para el caso común.
   La sucursal del turno se sigue derivando del profesional elegido (`Professional.branchId`), no
   de un campo nuevo — eso ya era correcto de antes.
7. ~~**Zona horaria real por tenant.**~~ Hecho — `PATCH /api/tenant/timezone` (owner o admin,
   validado contra `ZoneId.of(...)`, no una regex), consumido en `AppointmentService` y
   `PublicAvailabilityService` (ver "Design notes" → Timezones) en vez del supuesto "todo es UTC"
   que había antes. El contrato de reserva pública cambió como parte de esto — ver esa sección
   para por qué.
8. ~~**WhatsApp además de mail.**~~ Hecho — canal opcional, apagado por default
   (`Tenant.whatsappEnabled`, `PATCH /api/tenant/notifications`, owner o admin). A propósito NO
   reemplaza el mail: `AppointmentNotificationListener` (mail) y `AppointmentWhatsAppListener`
   (WhatsApp) son dos listeners `AFTER_COMMIT` independientes sobre el mismo
   `AppointmentNotificationEvent` — un tenant que nunca toca el toggle sigue recibiendo exactamente
   los mismos mails que antes, sin cambios. `WhatsAppClient` habla con la API de mensajes de Twilio
   (Basic Auth con `account_sid`/`auth_token`, mismo estilo `RestClient` plano que
   `MercadoPagoClient`, sin SDK) usando un único número de WhatsApp a nivel plataforma — no hay
   número por tenant, mismo MVP simplificado que la cuenta de MercadoPago compartida antes de
   OAuth Connect. Solo envía si el tenant activó el toggle Y el cliente dejó un teléfono al
   reservar; si Twilio falla, se loguea y se descarta (`WhatsAppNotificationService`, mismo
   contrato que `MailService`) — un problema con WhatsApp nunca puede tirar abajo una reserva. No
   verificado contra una cuenta Twilio real (sin credenciales de prueba disponibles al construirlo)
   — mismo caveat que MercadoPago: falta un smoke test en vivo antes de confiar en esto con un
   cliente real.
9. ~~**Verificación de mail en el registro.**~~ Hecho — `POST /api/auth/register` ya no devuelve
   un token usable; hacía falta, porque antes cualquiera podía crearse cuentas ilimitadas con un
   mail falso y quedar con acceso completo al instante. Ver "Design notes" → Email verification
   (registration) para el flujo completo (verificado en vivo con un mail real vía MailHog).
10. ~~**Pantalla de bienvenida/onboarding en el panel.**~~ Hecho — `WelcomeBanner`, ver "Design
    notes" → First-run onboarding. Apunta al otro problema que se planteó junto con el de
    verificación: alguien que se registra no tiene forma de entender para qué es el panel ni por
    dónde arrancar.

### Deuda técnica conocida (prioridad según qué tan rápido haga falta)

11. **Política de cancelación/reembolso y no-show** — no definida todavía para ningún tenant (ver
    también "Preguntas abiertas para el equipo" arriba).
12. Reembolsos de depósitos y turnos recurrentes — ya listados como fuera de alcance del MVP (ver
    "Design notes"), sin cambios.
13. **Pago cobrado en un turno ya cancelado por expiración.** Confirmado en vivo el 2026-08-01 (ver
    "Design notes" → Payments/deposits): si el depósito se paga después de que
    `PendingDepositExpirationScheduler` ya canceló el turno por falta de pago, el pago queda
    `PAID` pero el turno sigue `CANCELLED` — sin reembolso automático ni aviso a nadie. En uso real
    (cliente paga en minutos, no en más de media hora) es un caso raro, pero hay que resolverlo
    antes de manejar plata de verdad.
14. **Re-agendamiento y sobreturno "turno exprés" (anotado 2026-08-10, todavía sin construir).**
    Dos features distintas que se pidieron juntas, con dos preguntas sin cerrar antes de empezar:
    - **Re-agendamiento**: botón para mover un turno de horario (típicamente porque el cliente
      avisó con 24-48hs de anticipación) sin que cuente como una cancelación común. Un dropdown
      "Motivo" con dos opciones: *"Personal del tenant"* (el negocio decide moverlo — no toca la
      calificación del cliente en absoluto) y *"Aviso"* (el cliente pidió el cambio — sí toca la
      calificación, con la misma gracia que ya tienen las cancelaciones: la 1ª vez no descuenta
      nada, de la 2ª en adelante -2 puntos cada vez). **Sin cerrar:** ¿el descuento por "Aviso"
      comparte el contador de cancelaciones (`Client.cancelledCount`) o necesita uno propio
      (`rescheduledCount`) con su propia gracia independiente?
    - **Sobreturno / "turno exprés"**: el botón "Reemplazar" (`AppointmentService.replace`, ver
      "Design notes" más arriba) pasa a llamarse "Sobreturno" y suma un segundo modo además del
      actual (cancelar+reagendar sigue igual): un turno exprés que se mete en un hueco de 15 a 40
      minutos entre dos turnos ya agendados del mismo profesional — solo pide cliente y servicio,
      sin que el dueño tenga que elegir una hora exacta a mano — y también tiene que quedar
      accesible desde "Nuevo turno", no solo desde ese botón. **Sin cerrar:** ¿el turno exprés se
      ubica *dentro* del hueco libre sin superponerse en el tiempo con el turno siguiente (compatible
      con el constraint `no_double_booking` que se decidió no tocar la vez pasada — ver "Design
      notes" → Double-booking prevention), o realmente tiene que poder solaparse con el turno de al
      lado? Si es lo segundo, hace falta una migración para aflojar ese constraint, no es gratis.
