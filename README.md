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

### 2026-08-13 — Dos formas de armar la disponibilidad: patrón semanal recurrente o fechas puntuales

- **Motivación real**: un negocio de alta demanda (ej. Lusi Tattoo) que agenda por temporada no
  tiene un patrón semanal fijo — quiere abrir fechas sueltas de a una (ej. "12, 17 y 24 de
  septiembre nada más") a medida que decide liberar cupo, y esas fechas ni siquiera comparten el
  mismo día de semana entre sí. El horario semanal recurrente (`WeeklyAvailability`, ya existía)
  no puede representar eso — una regla "todos los jueves" no sirve para abrir un sábado suelto.
- **`DateAvailability` (tabla nueva, migración V33) — la imagen espejo de `TimeOff`.** En vez de
  cerrar una fecha que estaría abierta por el patrón semanal, abre una fecha puntual sin importar
  su día de semana. Es **aditivo, no excluyente**, con el horario semanal: un profesional puede
  usar ambos a la vez, o apoyarse solo en fechas puntuales con el horario semanal vacío (el caso de
  temporada — nada que "apagar" en la baja, simplemente no se cargan fechas nuevas).
  `PublicAvailabilityService.findFreeSlots` ahora junta las dos fuentes de ventanas abiertas para
  una fecha dada antes de restar bloqueos/turnos ya tomados. `AvailabilityCalculator.freeSlots`
  suma un `.distinct()` defensivo por si las dos fuentes se solapan para la misma fecha (ej. un
  jueves recurrente + una fecha puntual ese mismo jueves con horario extendido) — sin eso, el
  tramo solapado se ofrecía duplicado.
  Endpoints nuevos bajo `/api/professionals/{id}/date-availability` (GET/POST/DELETE), mismo estilo
  que `.../time-off`. Sección nueva "Fechas puntuales habilitadas" en el panel de Profesionales.
- **La otra forma (horario recurrente) también se separó en dos vistas.** `WeeklySchedule.jsx` (la
  grilla completa de 7 días, ya existía) sigue disponible, pero ahora convive con
  `SimpleAvailabilityEditor.jsx` — agregar una franja recurrente a la vez en vez de ver los 7 días
  de encima, para quien prefiere ir sumando días de a poco a su patrón semanal. Un tab arriba de la
  sección elige la vista; arranca en la simple si el profesional no tiene nada cargado todavía, en
  la grilla si ya tiene un horario armado. Ninguna de las dos vistas es dueña del dato — ambas
  llaman a los mismos endpoints de siempre (`POST`/`DELETE` `.../availability`, uno por franja).

### 2026-08-13 — Horario público de profesional para el hover del equipo (pedido de Mica)

- **`GET /api/public/{slug}/professionals` ahora expone `hours` (horario semanal real).** Antes solo
  traía `bio` — sin horarios reales, el hover del "carrusel de equipo" del sitio público no tenía
  con qué mostrar disponibilidad, solo texto libre. Nuevo `PublicWeeklyAvailabilityResponse`, mismo
  patrón que ya existía para horarios de sucursal (`PublicBranchHoursResponse`). No toca el sitio
  público — el hover en sí lo arma Mica en su propio frontend.

### 2026-08-13 — Google Business por sucursal (pedido de Mica)

- **`Branch.googleBusinessUrl` (opcional), migración V34.** Link a la ficha de Google Business de
  la sucursal, para SEO/confianza en el sitio público — por sucursal, no por tenant, porque cada
  sucursal de un negocio multi-sucursal suele tener su propia ficha. Expuesto en
  `PublicBranchResponse`, cargable desde el panel (Sucursales → crear/editar). No toca el sitio
  público — mostrarlo es trabajo de Mica en su propio frontend.

### 2026-08-13 — OAuth Connect verificado en vivo — dos bugs reales encontrados, uno propio

- **Túnel público con ngrok para poder registrar un redirect URI real** — el panel de MercadoPago
  rechaza `localhost` en las URLs de redireccionamiento. (Falso positivo de Windows Defender en el
  binario de ngrok descargado — hubo que agregar una exclusión manual a la carpeta del paquete.)
- **Se probaron y descartaron, una por una, las causas típicas de `"La aplicación no está
  preparada para conectarse a Mercado Pago"`:** PKCE (estaba deshabilitado), país de la app vs.
  cuenta (ambos MLA/Argentina), y el encoding del `redirect_uri` en la URL de autorización — esto
  último sí era un bug real (`MercadoPagoClient.buildAuthorizationUrl` usaba
  `UriComponentsBuilder...toUriString()` sin encodear, corregido con `URLEncoder` + `build(true)`),
  pero no era la causa del bloqueo: el error persiste igual con y sin encoding.
- **La causa real, según soporte de MercadoPago (ticket `WCS-45796`): el redirect URI registrado
  en el panel no era el nuestro.** Estaba cargado el placeholder `https://www.mercadopago.com`
  (nunca se había reemplazado) — el panel no permite editar en el lugar, solo agregar una URL
  nueva sin borrar la vieja, así que quedaron las dos en la lista. También indicaron que el
  parámetro correcto es `scope=read write offline_access` (los nombres de grant estándar), no
  `platform_id=mp` ni una etiqueta de permiso del dashboard como "Online Preferences" —
  `buildAuthorizationUrl` se actualizó para reflejar ambas correcciones.
- **Con eso resuelto, apareció un segundo bug — propio, no de MercadoPago: `invalid client_id or
  client_secret` al intercambiar el código por el token.** Veníamos usando el "Número de
  aplicación" (`756946925289310`, el que aparece en el panel de usuarios de prueba) como si fuera
  el OAuth `client_id` — la pantalla de autorización lo acepta igual (MP no lo valida ahí), pero el
  intercambio de token sí lo rechaza. El Client ID real, visible en "Credenciales de producción"
  junto al Client Secret (enmascarado detrás de un ícono "mostrar"), es un valor completamente
  distinto (`6328748736404873`). El Client Secret que se venía usando **sí era el correcto** desde
  el principio — confirmado pegando el mismo valor una vez revelado.
- **Verificado en vivo de punta a punta**, con `MERCADOPAGO_CLIENT_ID` corregido: autorización con
  la cuenta de prueba → callback real (con el aviso gratuito de ngrok de por medio, "Visit Site")
  → intercambio de código por token exitoso → fila nueva en `mercadopago_accounts` con
  `access_token`/`refresh_token` reales, `expires_at` a 6 meses.

### 2026-08-13 — Preapproval verificado en vivo, con un bug real de persistencia corregido

- **Verificado de punta a punta por primera vez.** Se destrabó el bloqueo del 2026-08-01
  (verificación por mail a un `@testuser.com` sin bandeja real): la clave era usar el email de un
  **usuario comprador de prueba** (creado vía `POST /users/test_user`), no el email real de una
  persona ni el de la cuenta vendedora — MercadoPago rechaza `payer`/`collector` salvo que ambos
  sean "reales o de prueba", y un comprador de prueba autorizando contra un collector de prueba sí
  cumple eso. El código de verificación que pide el checkout de MercadoPago para una cuenta de
  prueba son los últimos 6 dígitos de su User ID (mismo truco ya conocido de la verificación de
  sesión en Checkout Pro). Flujo probado completo: `POST /api/tenant/subscription` → autorizar en
  `checkoutUrl` con tarjeta de prueba (titular `APRO`) → webhook `subscription_preapproval` real,
  firmado y verificado → tenant pasa a `PRO`.
- **Bug real encontrado y corregido: el webhook de suscripción nunca persistía el cambio de
  plan.** `SubscriptionService.handleWebhook` mutaba el `Tenant` que devolvía
  `TenantService.findById` (ese método es `@Transactional(readOnly = true)` por su cuenta, así que
  la entidad vuelve *detached* apenas retorna) sin volver a guardarlo — el `Subscription` quedaba
  bien en `AUTHORIZED`, pero el tenant seguía en su plan viejo. Nuevo
  `TenantService.applyPlanTierFromSubscription(tenantId, tier)`, con su propio `@Transactional`
  envolviendo fetch + mutación, reemplaza la mutación directa. `SubscriptionServiceTest` actualizado
  para verificar la llamada en vez de inspeccionar un objeto detached.

### 2026-08-13 — Página pública de precios y alta self-service

- **`/precios` — landing pública nueva.** Reconstruye lo que `frontend-public/` tenía (ver "Design
  notes" → One frontend, not two) pero dentro de `frontend/`, no como proyecto aparte. Consume
  `GET /api/plans` (ya existía, sin cambios de backend) y muestra los 5 planes con precio real
  (indexado a dólar blue), destacando PRO. El registro se extrajo de `LoginPage.jsx` a
  `RegisterForm.jsx` (componente compartido, evita duplicar la lógica de "revisá tu mail") — crear
  el negocio sigue arrancando en el plan Demo/`TRIAL` como siempre, subir a un plan pago sigue
  siendo un paso aparte desde el panel.
- **Selector de cantidad de profesionales, con precio progresivo — a pedido, iterado varias veces
  en vivo.** BASIC/PRO/MAX (los únicos con `extraProfessionalPrice` en el backend) muestran un
  selector +/− arrancando en un piso común de **2 profesionales** (deliberadamente igual para los
  tres en esta página, no el `maxProfessionals` real de cada tier — ver el porqué abajo), con tope
  de **20** (límite absoluto de la plataforma, cualquier plan). Cada profesional extra cuesta un %
  más que el anterior (compuesto, no plano): **BASIC +10%, PRO +15%, MAX +25%**. La primera versión
  usaba el `maxProfessionals` real de cada tier (1/2/5/10) como piso — con pisos distintos, un plan
  necesita más "escalones" que otro para llegar a la misma cantidad total, y el compuesto castiga
  más escalones más que una tasa más alta con menos escalones: a 20 profesionales, BASIC terminaba
  costando más que MAX. Igualar el piso a 2 en los tres arregla el orden (a cualquier cantidad,
  BASIC < PRO < MAX) porque ahora todos compuestan sobre el mismo número de escalones. El precio
  del extra en sí **no se muestra en público** (solo el total y el %) — se probó mostrándolo y no
  tenía sentido exponer la mecánica exacta del cálculo.
- **Alta paga sigue siendo manual, a propósito.** No hay checkout automático desde esta página —
  cada card con precio real tiene un botón que abre un mail pre-completado a
  `info.capibyte@gmail.com` con el plan y la cantidad elegida, para pedir alta contra comprobante
  de pago. Es una decisión de proceso actual, no una limitación técnica: `POST
  /api/tenant/subscription` (Mercado Pago Preapproval) ya existe y funciona (ver Payments/deposits
  más abajo), simplemente no está conectado a esta página todavía.

### 2026-08-13 — Reembolso de depósito, revertido por decisión de negocio

- **La feature de reembolso de más abajo se sacó el mismo día que se construyó.** El dueño del
  producto definió la política real: una seña pagada nunca se devuelve, sin excepciones — ni por
  no-show, ni por cancelación del dueño, ni siquiera para el caso técnico de "pago huérfano" que
  motivó la feature en primer lugar (turno cancelado por expiración, seña pagada justo después).
  Tiene sentido con el propósito de cobrar una seña: si se devuelve siempre, no cumple su función.
  Se sacó `MercadoPagoClient.refundPayment`, `PaymentService.refundDeposit`, el endpoint `POST
  /api/appointments/{id}/refund-deposit`, `AppointmentService.markDepositRefunded`, el DTO
  `MercadoPagoRefund`, y el botón "Reembolsar seña" del panel — junto con sus tests. Lo que sí
  queda: el mail automático al dueño avisando de un pago huérfano (`OrphanedDepositPaymentEvent`),
  ajustado para ya no sugerir un reembolso — solo informa para que el dueño coordine directo con el
  cliente si quiere. Un reembolso puntual por fuera de la política de la plataforma sigue siendo
  posible, pero directo desde Mercado Pago, no desde este sistema.

### 2026-08-13 — Reembolso de depósito vía Mercado Pago (revertido el mismo día, ver arriba)

- **Reembolso real, verificado en vivo.** `MercadoPagoClient.refundPayment` (`POST
  /v1/payments/{id}/refunds`), `PaymentService.refundDeposit` y el endpoint `POST
  /api/appointments/{id}/refund-deposit` (owner/admin) le dan al dueño una forma de reembolsar una
  seña ya cobrada — pensado sobre todo para el caso de "pago huérfano" (turno cancelado por
  expiración, seña pagada después), pero sirve para cualquier depósito `PAID`. Botón "Reembolsar
  seña" nuevo en Turnos → Lista, visible cuando `paymentStatus=PAID` y `status=CANCELLED`.
  Verificado de punta a punta contra el sandbox real: pago real → webhook real → reembolso real vía
  la API de MercadoPago → confirmado con `transaction_amount_refunded` en la respuesta de MP
  igual al monto pagado. Deliberadamente no toca `Appointment.status` — reembolsar no reabre ni
  vuelve a cancelar nada, es puramente un resultado de pago.

### 2026-08-13 — Categorías, banner, horarios públicos, fotos con upload real, y re-verificación de Mercado Pago

- **Categoría de servicio, banner de tenant, horario/teléfono de sucursal público, foto de
  profesional.** Migración `V31`. `ServiceOffering.category` (texto libre, el tenant define sus
  propias categorías; agrupa el catálogo en la página pública). `Tenant.bannerUrl`, portada del
  sitio público además del logo. `Branch.phone` (ya existía en el modelo, no se exponía) y
  `BranchHours` ahora viajan en `GET /api/public/{slug}/branches` — horarios crudos, sin lógica de
  "abierto ahora"/"próxima apertura", eso queda del lado del cliente a propósito.
  `Professional.photoUrl` para el carrusel de equipo. De paso, `GET /api/public/{slug}/professionals`
  ya no exige `serviceId`: sin ese parámetro devuelve todos los profesionales activos del tenant
  (opcionalmente filtrado por `branchId`), para poder mostrar el equipo antes de elegir servicio.
- **Upload real de imágenes, no URL pegada a mano.** Pedirle a un tenant no técnico que hostee una
  imagen en otro lado y pegue el link no era práctico. Banner, logo y foto de profesional ahora se
  suben como archivo de verdad, reusando `FileStorageService` (antes solo usado para adjuntos de
  reportes de soporte), servidos públicamente sin auth desde `/uploads/public/**`
  (`PublicUploadsConfig`) — en un subdirectorio separado de los adjuntos de soporte a propósito,
  para que esos nunca queden alcanzables por URL. Endpoints nuevos: `POST
  /api/tenant/branding/logo`, `POST /api/tenant/branding/banner`, `POST
  /api/professionals/{id}/photo`.
- **Checkout Pro de Mercado Pago, re-verificado en vivo (contradice una nota anterior).** Se había
  documentado que nunca hubo credenciales de sandbox reales — resultó incorrecto: el checkout de
  depósitos ya se había verificado en vivo el 2026-08-01, y esta sesión lo repitió de punta a punta
  de nuevo con éxito. Gotchas de sandbox nuevos, ver "Design notes" → Payments/deposits para el
  detalle completo (formato de credenciales `APP_USR-` en vez de `TEST-`, la trampa de
  `sandbox_init_point`, por qué pagar con saldo de cuenta funcionó mejor que tarjeta simulada).
- **Ventana de expiración de depósito, configurable por tenant (10–180 min) y solo aplica a tenants
  con Mercado Pago.** Antes era un único valor de plataforma que cancelaba cualquier turno con seña
  pendiente a los 30 minutos, sin importar el método de pago — rompía el caso real de un tenant que
  solo cobra por alias/transferencia (ahí la confirmación siempre es manual del dueño, y puede
  tardar horas legítimamente, no minutos). `PendingDepositExpirationScheduler` ahora ignora por
  completo a los tenants sin Mercado Pago habilitado en su plan; los que sí lo tienen eligen su
  propia ventana (`PATCH /api/tenant/deposit-expiration`, default 30, sin cambio para quien no lo
  toque).
- **Fix de una fragilidad real de la suite de tests.** Los ~170 tests de integración compartían el
  mismo bucket de rate limiting del sitio público (misma IP de loopback en todos), y el volumen
  acumulado de una corrida completa a veces agotaba la cuota y le tiraba un 429 de rebote a algún
  test sin ninguna relación con rate limiting. Cada test ahora manda un `X-Forwarded-For` distinto
  (`IntegrationTestBase`), sin tocar ningún valor real de configuración de límite.
- **Aviso al dueño cuando un depósito se cobra en un turno ya cancelado.** El gap de "pago huérfano"
  documentado desde el 2026-08-01 (ver "Design notes" → Payments/deposits y roadmap ítem 13) ahora
  manda un mail automático al dueño del tenant (`OrphanedDepositPaymentEvent`/
  `OrphanedDepositPaymentListener`) apenas se detecta el desfasaje, con cliente/monto/servicio, para
  que decida a mano si reconfirma el turno o gestiona un reembolso. Deliberadamente NO reconfirma el
  turno solo ni llama a ninguna API de reembolso de Mercado Pago — ambas siguen siendo decisiones de
  negocio pendientes, no solo código sin escribir.

### 2026-08-11 — Plan PERSONAL, borrado en cascada, calendario rediseñado, eliminar turno

- **Plan PERSONAL + matriz de límites completa.** Nuevo escalón de entrada (1 profesional, sin
  stock, 1 sucursal, 3 servicios, tope de 20 turnos/semana — el único plan con ese tope — sin
  Mercado Pago ni WhatsApp). Los 5 planes (`TRIAL`/Demo, `PERSONAL`, `BASIC`, `PRO`, `MAX`) ahora
  tienen una matriz de límites uniforme (profesionales, productos, sucursales, servicios,
  turnos/semana, Mercado Pago, WhatsApp) aplicada en el backend (`ProfessionalLimitExceededException`,
  `ServiceLimitExceededException`, `WeeklyAppointmentLimitExceededException`, sumadas a las ya
  existentes de sucursales/productos) y mostrada completa en Mi Plan. TRIAL vencido ahora cae a
  `PERSONAL` (antes `BASIC`), igual que una suscripción de Mercado Pago cancelada. El toggle de
  WhatsApp se revisa contra el plan actual al momento de notificar (no solo al tildarlo), para que
  una baja de plan no deje mandando WhatsApp con un toggle viejo en `true`.
- **Eliminar sucursal/profesional ya no falla con historial real.** Antes, borrar una sucursal o
  profesional con cualquier turno/horario/asignación cargada rompía por violación de foreign key —
  en la práctica, "Eliminar" no tenía ningún camino que funcionara. Migración `V28` agrega
  `ON DELETE CASCADE` para que borrar una sucursal/profesional se lleve puesto todo lo que solo
  tiene sentido bajo ella/él (horarios, disponibilidad, bloqueos, asignaciones de servicio, lista de
  espera, turnos — con sus payments/sales siguiendo las reglas ya establecidas en V27).
- **Botón "Eliminar turno".** Borrado manual e inmediato de un turno puntual (Lista y Calendario),
  sin restricción de fecha/estado (a diferencia de los borrados de historial, que solo tocan turnos
  ya pasados) — para sacarse de encima un turno de prueba o cargado por error. No toca la
  calificación del cliente ni sus contadores.
- **Calendario semanal: rediseño y varios bugs de alineación reales.** Punto de color por
  profesional en cada turno (color estable por `professionalId`, sin campo nuevo en la base) y en
  la lista de Profesionales. Barra de navegación más chica y liviana. Se encontraron y corrigieron
  cuatro bugs de layout: (1) un turno fuera del horario declarado del profesional (agendado manual o
  "sobreturno") se renderizaba más allá del borde de la grilla sin línea de hora que lo contuviera —
  ahora el rango de horas se ensancha para cubrir lo que esté efectivamente en pantalla; (2) el
  header de la tabla de Turnos → Lista no quedaba fijo al scrollear (el `<table>` tenía
  `overflow: hidden` heredado, lo que lo convertía a él —no al contenedor con scroll— en el
  "contenedor de scroll" del `<th>` sticky); (3) el resaltado de la columna "hoy" (fondo celeste)
  se mezclaba con el color semitransparente de los turnos cancelados/no-show y los mostraba
  violeta — se reemplazó por una línea de acento en el borde izquierdo, que no se superpone a
  ningún turno; (4) cuando el cuerpo del calendario necesita scroll interno, el navegador le resta
  ancho a sus columnas para la barra de scroll pero no al header (que no scrollea), desalineando
  columnas — se mide el ancho real de la scrollbar y se reserva el mismo espacio en el header.
- **Turnos → Lista: menos scroll de página.** Las tarjetas de clientes (Mejores clientes,
  Frecuentes, Cancelan seguido, No aparecen) muestran 3 filas visibles con scroll interno sobre un
  pool de hasta 10 (relleno por peor calificación si no hay suficientes candidatos reales); la
  tabla de turnos tiene su propio scroll con header fijo. El botón de configuración de historial se
  renombró y reubicó junto a los demás filtros (antes un `<details>` con flecha nativa, ambiguo).
  Turnos → Lista ahora ordena por más reciente primero.
- **Reorganización de la navegación.** "Mi Plan" y "Mi cuenta" se movieron del nav principal a un
  menú "⋯ más opciones", junto a un nuevo manual de ayuda in-app (`HelpManual`) y tema
  claro/oscuro. "Productos" solo aparece en el nav si el plan del tenant lo incluye.
- **Fix de condición de carrera en el sitio público de reserva.** Elegir un profesional/fecha y
  cambiar rápido a otro antes de que responda el primer pedido de disponibilidad podía dejar en
  pantalla los horarios del profesional equivocado si esa respuesta vieja llegaba después que la
  nueva — se descarta cualquier respuesta que no sea la del último pedido.

### 2026-08-11 — Retención y borrado de historial de turnos

- **Retención configurable + borrado manual de historial.** El tenant decide cuántos meses de
  historial de turnos conservar (1-12, default 12) desde Turnos → Lista; un job diario
  (`AppointmentRetentionScheduler`) borra automáticamente lo que exceda ese límite, para todos los
  tenants. Además, botones de borrado manual estilo "Borrar datos de navegación" de Chrome —
  Última hora / Últimas 24 horas / Últimas 4 semanas / Todo el historial — cada uno borra los
  turnos que caen DENTRO de esa ventana reciente. Es `DELETE` real e irreversible (rompe el
  invariante que tenía `Appointment` de "nunca se borra, cancelar es solo un estado" — documentado
  ahí mismo), pero nunca toca al `Client` asociado (rating, contadores, "cliente fijo" quedan
  intactos) ni un turno futuro, sin importar qué botón se apriete. Se agrega también un buscador
  por cliente (nombre/email) en Turnos → Lista, 100% client-side. Requirió arreglar dos foreign
  keys que apuntaban a `appointments` sin `ON DELETE` (`payments` → CASCADE, `sales` → SET NULL)
  para que el borrado no rompiera por violación de constraint.

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
- **Reagendar (y se saca "Reemplazar turno").** "Reemplazar turno" (cancelar y dárselo a otro
  cliente) se sacó del panel por no tener uso real — quedan sólo "Reagendar" y "Sobreturno" como
  acciones sobre un turno. Reagendar mueve un turno del mismo cliente a otro horario (mismo
  profesional y servicio, sólo cambia fecha/hora) con un dropdown "Motivo": *"Personal del
  tenant"* (el negocio decide — nunca toca la calificación del cliente, y una seña ya pagada
  siempre se mantiene) o *"Aviso"* (el cliente pidió el cambio — misma gracia que las
  cancelaciones, contador propio `Client.rescheduledCount` separado de `cancelledCount`: la 1ª vez
  no descuenta nada ni toca la seña, de la 2ª en adelante -2 puntos y la seña ya pagada se pierde,
  sin reembolso — no existe ningún flujo de reembolso de Mercado Pago construido en este proyecto,
  así que "perderla" es simplemente resetear el turno a `PENDING`).
- **Calendario semanal real y "Reemplazar turno".** La vista de Turnos del dueño pasó de una tabla
  plana a una grilla semana × hora, con turnos superpuestos mostrados lado a lado. Se agregó
  "Reemplazar turno" (para cuando un cliente cancela por teléfono, no por el sistema) y carga
  manual de turnos desde el panel — antes solo se podía reservar desde el sitio público.
- **Calendario separado por sucursal + límite de sucursales por plan.** Un negocio con 2+
  sucursales veía un único calendario con los turnos de todos los locales mezclados, sin indicar
  sucursal ni profesional por turno. Selector exclusivo de sucursal en Turnos (persistido por
  tenant, solo visible con 2+ sucursales activas) que filtra Calendario y Lista; cada bloque del
  calendario ahora muestra también el profesional que atiende. Límite de sucursales por plan
  (BASIC: 1, PRO/Demo: 2, MAX: 4), visible en Sucursales con el contador y bloqueo del alta al
  llegar al tope.
- **Sobreturno: turno solapado real.** Nueva feature, distinta de "Reemplazar turno": el dueño
  puede agendar manualmente un turno que se superpone en el tiempo con otro turno ya existente del
  mismo profesional — caso real: un profesional atendiendo un servicio largo (ej. rulos
  permanentes) puede, en simultáneo, atender otros servicios cortos a otros clientes; el propio
  tenant decide a quién atender en paralelo. El constraint `no_double_booking` se aflojó *solo*
  para turnos marcados `is_overtime` (columna nueva, `EXCLUDE` parcial extendido) — el sitio
  público y el agendado normal siguen bloqueados contra choques exactamente como antes. Accesible
  desde "Nuevo turno" (checkbox) o desde el botón "Sobreturno" (ex "Reemplazar") sobre un turno
  existente, que ahora ofrece elegir entre reemplazar (cancela) o superponer (no cancela).

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

**Test-suite flakiness this caused, fixed 2026-08-13.** Spring's test-context caching keeps the
same `TestRestTemplate` (and thus the same shared bucket, same loopback "IP") alive across every
one of the ~50 integration test classes in the suite. A full sequential run's cumulative call
volume against `/api/public/**` could exhaust that one shared bucket and 429 a test that had
nothing to do with rate limiting — reproduced twice, deterministically (same 4 unrelated tests
failing both times, confirmed to pass cleanly in isolation). Fixed at the root cause rather than by
loosening any real limit: `IntegrationTestBase` now installs one `ClientHttpRequestInterceptor`
(idempotent-guarded, since the same `TestRestTemplate` bean is shared) that stamps a fresh,
unique `X-Forwarded-For` value before every test method — the filter already prefers that header
over the socket address, so each test gets its own independent bucket, same as two different real
clients would in production. `application.yml`'s actual `app.rate-limit.*` values are untouched.

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

**Re-verified live again on 2026-08-13** (this superseded an incorrect note that had claimed no
live MercadoPago credentials were ever available — they were, on 2026-08-01, and this session
independently re-confirmed the whole loop against a fresh sandbox test account). Notes worth
keeping for next time:
- **MercadoPago's current "credenciales de prueba" now use the `APP_USR-` format, not the old
  `TEST-` prefix** — don't assume that prefix alone means production. Verify any unfamiliar token
  by calling `GET https://api.mercadopago.com/users/me` with it: a genuine test account's response
  has `"email":"...@testuser.com"` and `"tags":[...,"test_user",...]`.
- **A preference's `sandbox_init_point` field is a trap, don't use it** — it gave an immediate hard
  decline for a real test-card payment attempt. The regular `init_point` URL works fine for
  testing, as long as the buyer logs into MercadoPago as a *different* test user than the seller
  first (same-account buyer=seller is hard-blocked with "una de las partes es de prueba").
  Paying with the test buyer's own account balance ("Dinero en cuenta") was far more reliable than
  simulating a card via the magic cardholder names (`APRO`/etc.) — a card attempt "succeeded"
  client-side (redirect, verification code accepted) but never actually created a payment record,
  confirmed via `GET /v1/payments/search`; account-balance payments worked immediately every time.
- `GET /v1/payments/search?external_reference=...` did **not** find a real, just-completed payment
  even minutes later (`total: 0` every time) — don't rely on it for a smoke test. The unfiltered
  `GET /v1/payments/search?limit=20&sort=date_created&criteria=desc` (list-all) surfaced it
  immediately; `GET /v1/payments/{id}` directly (using the id from the on-screen receipt's "Número
  de transacción") is the most reliable lookup if you already have it.
- Result was identical to the 2026-08-01 run: preference → paid → `GET /v1/payments/{id}` confirmed
  `approved`/`accredited` with a matching `external_reference` → hand-triggered
  `POST /api/webhooks/mercadopago?type=payment&data.id={mpPaymentId}` with a correctly-computed
  `x-signature` → `Payment.status` flipped to `PAID`, appointment auto-confirmed. No code changes
  were needed — a clean re-verification, not a bugfix.

The deposit-expiration-window gap described above (paid-after-cancellation) got three independent
improvements, all on 2026-08-13:
1. The window is now tenant-configurable (10–180 min, `PATCH /api/tenant/deposit-expiration`) and
   scoped only to MercadoPago-enabled tenants (see "Deposit expiration" further down) — a tenant
   can set it wider to make the race less likely.
2. `AppointmentService.markDepositPaid` now detects the mismatch (appointment already `CANCELLED`
   when the payment comes in) and emails the tenant owner — a new
   `OrphanedDepositPaymentEvent`/`OrphanedDepositPaymentListener` pair, same
   `@TransactionalEventListener(AFTER_COMMIT)` pattern as the rest of this file's notifications,
   resolved via `AppUserService.findOwner()` (same "email the tenant's owner, not the platform
   founder" pattern `PlatformAdminService.approveTenant` already used). Deliberately still does
   **not** un-cancel the appointment even if the slot happens to still be free — `markDepositPaid`'s
   own Javadoc explains why (someone else may already hold it) — that remains an open product
   decision, not just unfinished code (see "Preguntas abiertas para el equipo" → Producto).
3. **A refund path was built, verified live, then removed the same day** — the product decision
   came back that a paid deposit is never refunded through this platform, no matter the reason the
   appointment didn't happen (not a no-show, not an owner cancellation, not even this orphaned-
   payment case), so offering a refund button contradicted the whole point of charging a deposit.
   `MercadoPagoClient.refundPayment`, `PaymentService.refundDeposit`,
   `POST /api/appointments/{id}/refund-deposit`, and the "Reembolsar seña" button are gone. The
   `OrphanedDepositPaymentListener` email stays, minus the refund suggestion — it just informs the
   owner so they can coordinate directly with the client if they want to, outside the system.

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

**Fully verified live (2026-08-13).** The whole loop — `createPreapproval` → interactive
authorization → real signed webhook → tenant flipped onto the paid tier — was exercised end to end
against MercadoPago's real sandbox. The 2026-08-01 blocker (a sandbox test user's `@testuser.com`
address has no real inbox to receive the checkout's verification code) turned out to be avoidable:
the verification code MercadoPago's checkout asks for is just the last 6 digits of the test user's
numeric User ID (same trick already known from Checkout Pro's session verification), so no real
inbox is ever needed. The `400 Both payer and collector must be real or test users` requirement
noted back then holds exactly as described — `payer_email` has to be a MercadoPago test-user
account (created via `POST /users/test_user`), not an arbitrary real email, when the collector is
also a test/sandbox account.

This pass also caught a real bug: `SubscriptionService.handleWebhook` used to mutate the `Tenant`
returned by `TenantService.findById` directly — but that method is its own
`@Transactional(readOnly = true)`, so the entity is already detached by the time `handleWebhook`
(deliberately not itself `@Transactional` — see its Javadoc) sets a field on it, and the change was
silently lost. Fixed with `TenantService.applyPlanTierFromSubscription(tenantId, tier)`, a new
`@Transactional` method that does the fetch and the mutation inside one transaction so the
dirty-checked update actually flushes.

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

**Fully verified live (2026-08-13), two real bugs found — one MercadoPago-side, one ours.** A
plain "Pagos online" application (not a separately-flagged "Marketplace" one — that distinction
from the 2026-08-01 note doesn't seem to be real; `client_id`/`client_secret` and the
redirect-URL/permissions config were all available directly on it) was wired up with an ngrok
tunnel for a public redirect URI (MercadoPago's dashboard rejects `localhost`). Authorizing kept
failing with `"La aplicación no está preparada para conectarse a Mercado Pago"` even after PKCE,
site/country, and `redirect_uri` percent-encoding were all ruled out (the encoding *was* a real bug
— `UriComponentsBuilder...toUriString()` left `:`/`/` unescaped since they're legal query
characters per RFC 3986, fixed with an explicit `URLEncoder` pass + `build(true)` — just not the
cause of this particular error). MercadoPago support (ticket `WCS-45796`) found the actual cause:
the app's registered redirect URI was still the placeholder `https://www.mercadopago.com`, never
replaced with the real tunnel URL — the dashboard only lets you *add* a new one, not edit the
existing entry in place. They also flagged that the authorization URL should send
`scope=read write offline_access` (the standard grant names), not a non-standard `platform_id=mp`
param or a dashboard permission label like "Online Preferences" as a scope value —
`buildAuthorizationUrl` now sends exactly that.

With MercadoPago's side fixed, a second bug surfaced — this one entirely on this codebase: token
exchange failed with `invalid client_id or client_secret`. The Application Number
(`756946925289310`, the id shown on the test-user panel) had been used as the OAuth `client_id`
all along — MercadoPago's authorization screen accepts it without validating it, so this went
unnoticed through every earlier authorize-URL test, but `/oauth/token` does validate it and
rejects it. The real Client ID (found on "Credenciales de producción", next to a masked Client
Secret behind a reveal icon) is a different value entirely. The Client Secret itself had been
correct the whole time. With the real `client_id` configured, the full loop completed: authorize
→ MercadoPago's own callback redirect (through ngrok's one-time visitor-warning interstitial) →
token exchange → a real `MercadoPagoAccount` row with a live `access_token`/`refresh_token` pair.

**Per-tenant branding.** `Tenant` fields — `logoUrl`, `bannerUrl` (public-site hero/cover, added
2026-08-13), `accentColor` (hex, validated `^#[0-9a-fA-F]{6}$`), `tagline` — editable via `PATCH
/api/tenant/branding` (owner or admin; billing stays owner-only, branding doesn't need to).
`frontend/src/pages/BookingPage.jsx` (the public booking flow, see "One frontend, not two" below)
fetches the tenant once and overrides the CSS custom property `--accent` inline
(`style={{ "--accent": tenant.accentColor }}`) on its root element, so every button/chip/card on
that page picks up the tenant's color through the same variable they already read from. An unset
field is `null` end to end (DTOs, entity columns) and just falls back to the panel's own default
look; connecting branding, like connecting MercadoPago, is additive.
Known gap: no automatic contrast handling — `--accent-contrast` (used for button text) isn't
derived from the tenant's chosen color, so a very light `accentColor` could produce low-contrast
button text.

**Image uploads (logo/banner/professional photo) — real files, not pasted URLs (2026-08-13).**
`logoUrl`/`bannerUrl`/`photoUrl` used to be plain link fields — a tenant had to host the image
somewhere else themselves and paste the URL, which isn't practical for a non-technical business
owner. `POST /api/tenant/branding/logo`, `POST /api/tenant/branding/banner`, and `POST
/api/professionals/{id}/photo` (all `multipart/form-data`) now accept an actual file, reusing
`FileStorageService` (previously only used for support-report attachments — local disk, fine for a
single-instance MVP, same caveat as that original use). The stored field still holds a
URL-shaped string either way, just now `/uploads/public/<subdir>/<uuid>.<ext>` instead of an
external link — `frontend/src/api.js`'s `resolveMediaUrl()` prefixes the API's own origin onto a
relative path before rendering an `<img src>`, and leaves an absolute external URL (still legal to
paste directly via the plain `PATCH` fields) untouched. These are deliberately public, unauthed
reads (`PublicUploadsConfig`, `GET /uploads/public/**`, registered in `SecurityConfig`'s
`PUBLIC_PATHS`) — scoped to a `public/` subdirectory of the uploads root specifically so
support-report attachments (which live in their own, different subdirectory, served only through
their own authenticated admin endpoint) never become guessable-URL-reachable by extending this
resource handler carelessly later.

**Service categories, and more of the tenant/branch exposed publicly (2026-08-13).**
`ServiceOffering.category` (free text, a tenant defines its own categories — no fixed enum, since
what makes sense for a barbershop vs. a tattoo studio differs) groups the catalog on the public
booking page; `null` falls back to a single ungrouped bucket. `GET /api/public/{slug}/branches` now
also returns `phone` (the field already existed on `Branch`, just wasn't exposed) and `hours`
(`BranchHours`, previously admin-only) — raw weekly schedule data, deliberately not pre-computed
into "open now"/"opens at" on the backend, so the frontend/designer can build that presentation
however they want. `Professional.photoUrl` shows on the public team carousel, replacing a flat
color-tile placeholder when set. Separately, `GET /api/public/{slug}/professionals` dropped its
previously-required `serviceId` — omitting it now lists every active professional for the tenant
(optionally still narrowed by `branchId`), for a "meet the team" view before a client has picked a
service yet.

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

**Date-specific availability (`DateAvailability`), added 2026-08-13 — the mirror image of
time-off.** `WeeklyAvailability` only knows recurring weekdays ("every Tuesday 9-18"), which can't
express a professional opening specific unrelated calendar dates (a Saturday, then two Thursdays
in a different week — no shared weekday). Real case: a tattoo artist who books by season and never
has a fixed weekly pattern, releasing capacity a few dates at a time. `DateAvailability` is
structurally `TimeOff` with the polarity flipped — `date` + required `startTime`/`endTime`, opens
instead of closes — and is **additive** with `WeeklyAvailability`, never exclusive:
`PublicAvailabilityService.findFreeSlots` now merges both sources' open windows for a given date
before subtracting time-off/existing bookings, so a professional can rely on one, the other, or
both together. The one thing this doesn't override is a full-day `TimeOff` for that date — an
explicit "I'm off" still wins over anything `DateAvailability` says, same as it already did over
`WeeklyAvailability`. Merging two independently-sourced sets of open windows can now produce
overlapping windows for the same date (e.g. a recurring Thursday slot plus a one-off
`DateAvailability` entry extending that same Thursday) — `AvailabilityCalculator.freeSlots` picked
up a `.distinct()` on its way out to guard against handing back the same slot twice; nothing
upstream needed to know or care.

New endpoints mirror time-off's shape exactly: `GET`/`POST`/`DELETE`
`/api/professionals/{id}/date-availability`. Frontend: a new "Fechas puntuales habilitadas"
section in `ProfessionalsPage`, same chip-row-plus-add-form pattern as "Bloqueos" right below it.

**Two ways to build the recurring weekly schedule, added the same day.** `WeeklySchedule.jsx`'s
7-day grid (see above) shows every day at once — great for editing an existing week, but reads as
"fill in your whole week right now" to a professional with nothing loaded yet. New
`SimpleAvailabilityEditor.jsx` is the other entry point onto the *same* `WeeklyAvailability` data:
a running list of currently-added slots plus a one-slot-at-a-time add form (day + start + end), no
grid in sight. `ProfessionalsPage` shows a small tab above the schedule section to switch between
the two, defaulting to the simple editor when a professional has zero slots and to the grid once
they have some — either can be switched to freely afterward. Neither view owns the data or has its
own persistence path; both call the exact same `POST`/`DELETE .../availability` endpoints
`WeeklySchedule` already used, so there's no risk of the two disagreeing about what's actually
saved.

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
- ~~Política de cancelación/reembolso y no-show~~ — definida (2026-08-13): una seña pagada nunca
  se devuelve, sin excepciones (ver "Deuda técnica" ítem 11).
- El caso de "el pago llega después de que el turno ya expiró y se canceló solo" ya tiene aviso al
  dueño (ver "Deuda técnica" ítem 13) — lo que sigue abierto es si el turno debería poder
  auto-reconfirmarse cuando el horario sigue libre, o si eso también debe quedar siempre a mano.

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
3. ~~**Verificación contra Mercado Pago real.**~~ Hecho — Checkout Pro (señas) verificado 100% en
   vivo, dos veces (2026-08-01 y de nuevo 2026-08-13, ver "Design notes" → Payments/deposits para
   los gotchas de sandbox encontrados la segunda vez): preferencia real, pago real, re-fetch real
   del pago, firma de webhook validada, turno actualizado. De paso encontramos un gap real: si el
   pago tarda más que la ventana de expiración, el turno se cancela solo y el pago queda huérfano
   sin aviso — el 2026-08-13 esa ventana pasó a ser configurable por tenant (10-180 min, mitiga el
   caso pero no lo arregla, ver ítem 13 abajo). Preapproval (suscripciones) se terminó de verificar
   en vivo el 2026-08-13: creación, autorización interactiva (con un usuario de prueba comprador,
   no el email real de una persona) y webhook, de punta a punta — de paso salió un bug real de
   persistencia, ya arreglado (ver Registro de cambios y Design notes → Plan billing). OAuth
   Connect también se terminó de verificar en vivo el 2026-08-13 — dos bugs reales encontrados en
   el camino (uno de MercadoPago: el redirect URI registrado nunca se había reemplazado del
   placeholder; uno propio: se usaba el Número de aplicación en vez del Client ID real para el
   intercambio de token), ver Registro de cambios y Design notes → Per-tenant MercadoPago accounts.
4. ~~**Página pública de precios y alta.**~~ Hecho (2026-08-13) — reconstruida dentro de
   `frontend/` esta vez (ver "One frontend, not two" en Design notes para por qué no un proyecto
   aparte), en `/precios`. El alta paga en sí sigue siendo manual (mail + comprobante), no un
   checkout automático — ver el Registro de cambios y Design notes → Payments/deposits para el
   detalle.

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

11. ~~**Política de cancelación/reembolso y no-show.**~~ Definida (2026-08-13): una seña pagada
    **nunca se devuelve**, sin excepciones — ni no-show, ni cancelación del dueño, ni el caso de
    pago huérfano de los ítems 12/13 abajo. Es la decisión de negocio, no una limitación técnica.
12. ~~**Reembolsos de depósitos.**~~ Construido y verificado en vivo el 2026-08-13, revertido el
    mismo día una vez definida la política del ítem 11 — ver el Registro de cambios ("Reembolso de
    depósito, revertido por decisión de negocio") y "Design notes" → Payments/deposits para el
    detalle de qué se sacó.
13. ~~**Pago cobrado en un turno ya cancelado por expiración — sin aviso a nadie.**~~ Hecho
    (2026-08-13, en dos partes). Confirmado en vivo el 2026-08-01 (ver "Design notes" →
    Payments/deposits): si el depósito se paga después de que
    `PendingDepositExpirationScheduler` ya canceló el turno por falta de pago, el pago queda
    `PAID` pero el turno sigue `CANCELLED`. La ventana de expiración pasó a ser configurable por
    tenant (10-180 min) y dejó de aplicar a tenants sin Mercado Pago, lo que hace el caso menos
    frecuente — y ahora, cuando sí pasa, `AppointmentService.markDepositPaid` dispara un mail
    automático al dueño del tenant (`OrphanedDepositPaymentEvent`/`OrphanedDepositPaymentListener`)
    con el cliente, el monto y el servicio, para que decida si coordina un nuevo turno con el
    cliente — el mail ya no sugiere un reembolso (ítem 11: la seña no se devuelve ni en este caso).
    **Lo que sigue sin resolver a propósito**: no hay auto-reconfirmación del turno aunque el
    horario siga libre — el dueño siempre decide a mano; eso sigue siendo una decisión de producto
    explícitamente abierta (ver "Preguntas abiertas para el equipo" → Producto), no trabajo técnico
    pendiente.
14. ~~**Re-agendamiento y Sobreturno.**~~ Hecho — ver el Registro de cambios y "Design notes" →
    Double-booking prevention.
