# Booking SaaS

A multi-tenant appointment-booking backend for service businesses (tattoo studios, barbershops,
salons) — think a small AgendaPro. Each tenant has branches, professionals, a service catalog,
weekly availability, and clients who book time slots through a public API.

REST API + OpenAPI/Swagger docs, plus two minimal React/Vite frontends (`frontend/` — the
owner/staff admin panel, `frontend-public/` — the client-facing multi-tenant booking site).
Built as a "serious" portfolio project: the two hardest correctness properties of this domain
(never double-booking a professional, never leaking one tenant's data to another) are enforced
structurally rather than just in application code — see [Design notes](#design-notes) below.

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
- React + Vite (`frontend/`, `frontend-public/`) — no TypeScript, no UI kit, plain `fetch` against
  the REST API

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
the roadmap for the tradeoff. `frontend-public/src/layout/TenantLayout.jsx` fetches the tenant once
per business, renders a small branded top bar, and overrides the CSS custom property `--accent`
inline (`style={{ "--accent": tenant.accentColor }}`) so every page under that tenant — not just
the home page — picks up their color through the same variable every button/chip/card already
reads from. An unset field is `null` end to end (DTOs, entity columns) and just falls back to
`frontend-public`'s default look; connecting branding, like connecting MercadoPago, is additive.
Known gap: no automatic contrast handling — `--accent-contrast` (used for button text) isn't
derived from the tenant's chosen color, so a very light `accentColor` could produce low-contrast
button text. Not built yet: logo file upload, since that needs a storage decision this project
hasn't made.

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

### Decisión: comercialización y hosting (2026-08-01)

**Hosting: Render.** Web Service tier Standard (2GB RAM — el tier Starter de $7/mes con 512MB es
riesgoso para una app Java con Hibernate) para el backend, Postgres administrado (tier chico) para
la base, y los dos frontends React como Static Sites (gratis, sin límite de banda relevante a esta
escala). Costo total estimado: **~USD 35/mes**, fijo y predecible independientemente del tráfico —
se priorizó esto sobre una alternativa más barata pero de facturación variable (Railway) porque lo
que se buscaba era no tener que operar servidores (parches, backups, TLS a mano), no el costo
mínimo absoluto. Se descartó AWS Lightsail / VPS propio (Hostinger, DigitalOcean) por la misma
razón: más barato, pero implica hacerse cargo del ops uno mismo.

**Precio de `PRO`: ARS 23.000/mes** (antes un placeholder de $15.000). Calculado como ~USD 15/mes
al dólar blue de referencia del día de la decisión (~$1.560) — por debajo del piso de AgendaPro
Argentina (USD 19/mes) y de Booksy (~USD 30/mes), y con margen sano una vez cubierto el hosting: no
alcanza con 1 tenant pago, pero sí desde ~3, y queda margen real en el rango de 5-10 tenants que se
espera para los primeros meses.

Dos cosas que quedan abiertas a propósito, no resueltas silenciosamente:
- **El precio queda fijo en pesos, no indexado al dólar.** Con inflación/devaluación en Argentina,
  ese número en ARS va a perder valor real con el tiempo y hay que revisarlo a mano de tanto en
  tanto — no se construyó ningún mecanismo de auto-ajuste cambiario, sería una feature aparte. Cobrar
  directamente en USD (como hace AgendaPro) quedó anotado como una posibilidad a futuro, no algo
  resuelto ahora.
- El costo de soporte (responder cuando algo se rompe, ayudar a un negocio nuevo a configurarse)
  sigue sin estar metido en la cuenta — el margen de arriba es solo hosting vs. ingreso, no cubre
  tiempo de soporte todavía. Y si `BASIC` se sostiene gratis para siempre o hace falta un tercer
  nivel intermedio sigue sin decidirse.

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
4. ~~**Página pública de precios y alta.**~~ Hecho — `frontend-public`'s `LandingPage` ahora
   lista los planes desde `GET /api/plans` (nuevo, público, catálogo de precios — no tenant-scoped,
   por eso vive fuera de `/api/public/{tenantSlug}/**`) y `/registrarse` crea la cuenta. La cuenta
   siempre arranca en `BASIC` (el registro no admite elegir plan pago todavía) — si eligieron Pro
   en la landing, se lo dice explícitamente y los manda a suscribirse desde el panel después de
   loguearse.

### Para competir en serio con AgendaPro (prioridad media)

5. ~~**Branding por tenant en el sitio público.**~~ Hecho — `Tenant.logoUrl/accentColor/tagline`,
   editables desde el panel (`PATCH /api/tenant/branding`, owner o admin) y consumidos por
   `frontend-public`'s `TenantLayout` (logo + color propio via `--accent`, en todas las páginas
   del negocio, no solo el home). Logo es una URL, no un archivo subido — no hay almacenamiento de
   archivos en el proyecto todavía (ver Design notes).
6. ~~**Selección de sucursal en la reserva pública.**~~ Hecho — `GET /api/public/{tenantSlug}/branches`
   nuevo, y `.../services`/`.../professionals` ahora aceptan un `branchId` opcional
   (`ServiceOfferingService.isOfferedAtBranch` filtra el catálogo, que no es branch-scoped en sí
   mismo, por si algún profesional activo de esa sucursal lo ofrece). Un tenant de una sola
   sucursal no ve ningún selector — `frontend-public`'s `TenantHomePage` solo lo muestra si
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

### Deuda técnica conocida (prioridad según qué tan rápido haga falta)

9. **Política de cancelación/reembolso y no-show** — no definida todavía (mismo punto que quedó
   abierto en el spec de Luciana, pero aplica a cualquier tenant).
10. Reembolsos de depósitos y turnos recurrentes — ya listados como fuera de alcance del MVP (ver
    "Design notes"), sin cambios.
11. **Pago cobrado en un turno ya cancelado por expiración.** Confirmado en vivo el 2026-08-01 (ver
    "Design notes" → Payments/deposits): si el depósito se paga después de que
    `PendingDepositExpirationScheduler` ya canceló el turno por falta de pago, el pago queda
    `PAID` pero el turno sigue `CANCELLED` — sin reembolso automático ni aviso a nadie. En uso real
    (cliente paga en minutos, no en más de media hora) es un caso raro, pero hay que resolverlo
    antes de manejar plata de verdad.

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
