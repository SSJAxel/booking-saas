import { useEffect, useMemo, useState } from "react";
import { api } from "../api.js";
import { useAuth } from "../auth/AuthContext.jsx";
import WelcomeBanner from "../components/WelcomeBanner.jsx";
import WeekCalendar, { toDateKey } from "../components/WeekCalendar.jsx";
import AppointmentFormModal from "../components/AppointmentFormModal.jsx";
import AppointmentDetailModal from "../components/AppointmentDetailModal.jsx";
import RescheduleModal from "../components/RescheduleModal.jsx";
import ClientInsights from "../components/ClientInsights.jsx";
import { paymentStatusLabel, statusLabel } from "../labels.js";
import { tenantDateKey, tenantDateTimeLabel, tenantMinutesOfDay } from "../tenantTime.js";

const STATUSES = ["PENDING", "CONFIRMED", "CANCELLED", "COMPLETED", "NO_SHOW"];
const NEXT_STATUS = {
	PENDING: ["CONFIRMED", "CANCELLED"],
	CONFIRMED: ["CANCELLED", "COMPLETED", "NO_SHOW"],
	CANCELLED: [],
	COMPLETED: [],
	NO_SHOW: [],
};
const ACTIVE_STATUSES = new Set(["PENDING", "CONFIRMED"]);
const PURGE_CONFIRM_MESSAGES = {
	LAST_HOUR: "¿Borrar los turnos de la última hora? Esta acción es permanente y no se puede deshacer.",
	LAST_24_HOURS: "¿Borrar los turnos de las últimas 24 horas? Esta acción es permanente y no se puede deshacer.",
	LAST_4_WEEKS: "¿Borrar los turnos de las últimas 4 semanas? Esta acción es permanente y no se puede deshacer.",
	ALL: "¿Borrar TODO el historial de turnos pasados? Esta acción es permanente y no se puede deshacer. Los turnos futuros no se ven afectados.",
};
const DEFAULT_HOUR_BOUNDS = { hourStart: 9, hourEnd: 19 };

const RANGE_LABEL_FORMAT = new Intl.DateTimeFormat("es-AR", { day: "numeric", month: "short" });

function todayKey() {
	return toDateKey(new Date());
}

function startOfLocalDay(dateKey) {
	const [y, m, d] = dateKey.split("-").map(Number);
	return new Date(y, m - 1, d, 0, 0, 0, 0);
}

function mondayOf(dateKey) {
	const date = startOfLocalDay(dateKey);
	const offset = (date.getDay() + 6) % 7; // Date#getDay(): 0=Sunday..6=Saturday, shift to Monday-first
	date.setDate(date.getDate() - offset);
	return date;
}

/** The calendar's fixed vertical size: the widest opening-to-closing span across every
 * professional's weekly hours (e.g. Mon–Fri 12–16 and Saturday 9–18 → grid always shows 9–18),
 * not derived from what's booked — so the grid is the same size every week instead of resizing
 * itself depending on which appointments happen to exist. */
function computeBusinessHourBounds(availabilityEntries) {
	let min = null;
	let max = null;
	for (const entry of availabilityEntries) {
		const [sh, sm] = entry.startTime.split(":").map(Number);
		const [eh, em] = entry.endTime.split(":").map(Number);
		const startMin = sh * 60 + sm;
		const endMin = eh * 60 + em;
		if (min === null || startMin < min) min = startMin;
		if (max === null || endMin > max) max = endMin;
	}
	if (min === null) return DEFAULT_HOUR_BOUNDS;
	return { hourStart: Math.floor(min / 60), hourEnd: Math.ceil(max / 60) };
}

/**
 * Appointments aren't validated against a professional's declared weekly hours when booked
 * manually or as "sobreturno" (AppointmentController#createOvertime skips that check on purpose —
 * see its own Javadoc) — so one can legitimately start or end outside the availability-derived
 * bounds above. Left alone, that appointment's block would position itself past the grid's bottom
 * (or above its top) with no hour line anywhere near it — "floating," visually cut off, nothing
 * wrong with the block's own math, just no room in the grid for it. This widens the bounds just
 * enough to fit whatever's actually on screen this week, so the "fixed size" grid above only grows
 * for the (rare) week that actually needs it.
 */
function widenBoundsForAppointments(bounds, appointments, timezone) {
	let { hourStart, hourEnd } = bounds;
	for (const a of appointments) {
		const startHour = Math.floor(tenantMinutesOfDay(a.startTime, timezone) / 60);
		const endHour = Math.ceil(tenantMinutesOfDay(a.endTime, timezone) / 60);
		if (startHour < hourStart) hourStart = startHour;
		if (endHour > hourEnd) hourEnd = endHour;
	}
	return { hourStart, hourEnd };
}

export default function AppointmentsPage() {
	const { session } = useAuth();
	const [view, setView] = useState("calendario");
	const [selectedDate, setSelectedDate] = useState(todayKey());

	const [appointments, setAppointments] = useState([]);
	const [statusFilter, setStatusFilter] = useState("");
	const [showFullHistory, setShowFullHistory] = useState(false);
	const [historySettingsOpen, setHistorySettingsOpen] = useState(false);
	const [calendarAppointments, setCalendarAppointments] = useState([]);
	const [professionals, setProfessionals] = useState([]);
	const [services, setServices] = useState([]);
	const [branches, setBranches] = useState([]);
	const [selectedBranchId, setSelectedBranchId] = useState(
		() => localStorage.getItem(`selected-branch-${session.tenantSlug}`) || "",
	);
	const [selectedProfessionalId, setSelectedProfessionalId] = useState(
		() => localStorage.getItem(`selected-professional-${session.tenantSlug}`) || "",
	);
	const [hourBounds, setHourBounds] = useState(DEFAULT_HOUR_BOUNDS);

	const [error, setError] = useState("");
	const [loading, setLoading] = useState(true);
	const [showWelcome, setShowWelcome] = useState(false);
	const [formOpen, setFormOpen] = useState(false);
	const [detailAppointment, setDetailAppointment] = useState(null);
	const [rescheduleAppointment, setRescheduleAppointment] = useState(null);
	const [searchTerm, setSearchTerm] = useState("");
	const [tenant, setTenant] = useState(null);

	const canManageHistory = session.role === "OWNER" || session.role === "ADMIN";

	useEffect(() => {
		const dismissed = localStorage.getItem(`onboarding-dismissed-${session.tenantSlug}`) === "true";
		if (dismissed) return;
		api.branches
			.list()
			.then((branches) => setShowWelcome(branches.length === 0))
			.catch(() => {});
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

	useEffect(() => {
		// Every role needs tenant.timezone to render appointment times correctly (see tenantTime.js)
		// — not just OWNER/ADMIN, who additionally use the rest of this object for history retention.
		api.tenant.get().then(setTenant).catch((err) => setError(err.message));
	}, []);

	useEffect(() => {
		Promise.all([api.professionals.list(), api.services.list(), api.branches.list()])
			.then(([p, s, b]) => {
				setProfessionals(p);
				setServices(s);
				setBranches(b);
			})
			.catch((err) => setError(err.message));
	}, []);

	function selectBranch(branchId) {
		setSelectedBranchId(branchId);
		localStorage.setItem(`selected-branch-${session.tenantSlug}`, branchId);
	}

	function selectProfessional(professionalId) {
		setSelectedProfessionalId(professionalId);
		localStorage.setItem(`selected-professional-${session.tenantSlug}`, professionalId);
	}

	useEffect(() => {
		if (professionals.length === 0) return;
		Promise.all(professionals.map((p) => api.professionals.listAvailability(p.id)))
			.then((lists) => setHourBounds(computeBusinessHourBounds(lists.flat())))
			.catch(() => {});
	}, [professionals]);

	const activeBranches = branches.filter((b) => b.active);
	// Exclusive dropdown, no "todas" option — always resolves to one real branch. Falls back to the
	// first active branch when nothing's stored yet, or the stored id points at a branch that's
	// since been deactivated/deleted. With a single branch there's nothing to pick, so the dropdown
	// doesn't render and no branchId is sent at all.
	const effectiveBranchId =
		activeBranches.length > 1
			? activeBranches.find((b) => b.id === selectedBranchId)?.id ?? activeBranches[0]?.id
			: undefined;

	// "Todos" (no filter) is the default here, unlike the branch dropdown — a tenant with one
	// professional never sees this at all, and switching branch silently drops a selection that
	// belongs to a professional from the branch just left instead of leaving a stale filter applied.
	const activeProfessionals = professionals
		.filter((p) => p.active)
		.filter((p) => effectiveBranchId === undefined || p.branchId === effectiveBranchId);
	const effectiveProfessionalId = activeProfessionals.some((p) => p.id === selectedProfessionalId)
		? selectedProfessionalId
		: undefined;

	async function refreshList() {
		setLoading(true);
		try {
			// Default view: last 30 days back + every future appointment, however far out — old
			// resolved turnos (completed/cancelled/no-show from months ago) just pile up otherwise.
			// No upper bound, so a turno booked weeks ahead never disappears from the default view.
			const from = showFullHistory ? undefined : new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString();
			setAppointments(
				await api.appointments.list({
					status: statusFilter || undefined,
					from,
					branchId: effectiveBranchId,
					professionalId: effectiveProfessionalId,
				}),
			);
		} catch (err) {
			setError(err.message);
		} finally {
			setLoading(false);
		}
	}

	useEffect(() => {
		if (view !== "lista") return;
		refreshList();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [view, statusFilter, showFullHistory, effectiveBranchId, effectiveProfessionalId]);

	async function refreshCalendar() {
		setLoading(true);
		try {
			const from = mondayOf(selectedDate);
			const to = new Date(from);
			to.setDate(to.getDate() + 7);
			setCalendarAppointments(
				await api.appointments.list({
					from: from.toISOString(),
					to: to.toISOString(),
					branchId: effectiveBranchId,
					professionalId: effectiveProfessionalId,
				}),
			);
		} catch (err) {
			setError(err.message);
		} finally {
			setLoading(false);
		}
	}

	useEffect(() => {
		if (view !== "calendario") return;
		refreshCalendar();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [view, selectedDate, effectiveBranchId, effectiveProfessionalId]);

	function refresh() {
		return view === "lista" ? refreshList() : refreshCalendar();
	}

	async function handleTransition(id, status) {
		setError("");
		try {
			await api.appointments.transition(id, status);
			setDetailAppointment(null);
			refresh();
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleConfirmDeposit(id) {
		setError("");
		try {
			await api.appointments.confirmDeposit(id);
			setDetailAppointment(null);
			refresh();
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleDeleteAppointment(id) {
		if (
			!window.confirm(
				"¿Eliminar este turno? Esta acción es permanente e irreversible. No afecta la calificación del " +
					"cliente ni sus contadores — usalo para turnos de prueba o cargados por error.",
			)
		)
			return;
		setError("");
		try {
			await api.appointments.delete(id);
			setDetailAppointment(null);
			refresh();
		} catch (err) {
			setError(err.message);
		}
	}

	async function handlePurgeHistory(purgeWindow) {
		if (!window.confirm(PURGE_CONFIRM_MESSAGES[purgeWindow])) return;
		setError("");
		try {
			await api.appointments.purgeHistory(purgeWindow);
			await refreshList();
		} catch (err) {
			setError(err.message);
		}
	}

	function professionalName(id) {
		return professionals.find((p) => p.id === id)?.displayName ?? "—";
	}

	function serviceName(id) {
		return services.find((s) => s.id === id)?.name ?? "—";
	}

	function openCreate() {
		setFormOpen(true);
	}

	function openReschedule(appointment) {
		setDetailAppointment(null);
		setRescheduleAppointment(appointment);
	}

	function closeFormModal() {
		setFormOpen(false);
	}

	function handleSaved() {
		closeFormModal();
		refresh();
	}

	function handleRescheduleSaved() {
		setRescheduleAppointment(null);
		refresh();
	}

	function shiftWeek(deltaDays) {
		const base = startOfLocalDay(selectedDate);
		base.setDate(base.getDate() + deltaDays);
		setSelectedDate(toDateKey(base));
	}

	const filteredAppointments = useMemo(() => {
		const term = searchTerm.trim().toLowerCase();
		if (!term) return appointments;
		return appointments.filter(
			(a) => a.clientName?.toLowerCase().includes(term) || a.clientEmail?.toLowerCase().includes(term),
		);
	}, [appointments, searchTerm]);

	const appointmentsByDay = useMemo(() => {
		const map = {};
		for (const a of calendarAppointments) {
			const key = tenantDateKey(a.startTime, tenant?.timezone);
			(map[key] ??= []).push(a);
		}
		return map;
	}, [calendarAppointments, tenant]);

	const effectiveHourBounds = useMemo(
		() => widenBoundsForAppointments(hourBounds, calendarAppointments, tenant?.timezone),
		[hourBounds, calendarAppointments, tenant],
	);

	const calendarDays = useMemo(() => {
		const monday = mondayOf(selectedDate);
		return Array.from({ length: 7 }, (_, i) => {
			const date = new Date(monday);
			date.setDate(date.getDate() + i);
			return date;
		});
	}, [selectedDate]);

	const rangeLabel = `${RANGE_LABEL_FORMAT.format(calendarDays[0])} – ${RANGE_LABEL_FORMAT.format(calendarDays[6])}`;

	return (
		<div>
			<div className="card-header">
				<h1>Turnos</h1>
				<button type="button" onClick={openCreate}>
					Nuevo turno
				</button>
			</div>
			{showWelcome && <WelcomeBanner onDismiss={() => setShowWelcome(false)} />}
			{error && <p className="error">{error}</p>}

			<div className="tabs" style={{ maxWidth: 320 }}>
				<button type="button" className={view === "calendario" ? "active" : ""} onClick={() => setView("calendario")}>
					Calendario
				</button>
				<button type="button" className={view === "lista" ? "active" : ""} onClick={() => setView("lista")}>
					Lista
				</button>
			</div>

			{view === "lista" ? (
				<>
					<ClientInsights />

					<section className="appointments-section">
					<h2>Turnos</h2>
					<div className="filters">
						<label>
							Estado
							<select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
								<option value="">Todos</option>
								{STATUSES.map((s) => (
									<option key={s} value={s}>
										{statusLabel(s)}
									</option>
								))}
							</select>
						</label>
						{activeProfessionals.length > 1 && (
							<label>
								Profesional
								<select
									value={effectiveProfessionalId ?? ""}
									onChange={(event) => selectProfessional(event.target.value)}
								>
									<option value="">Todos</option>
									{activeProfessionals.map((p) => (
										<option key={p.id} value={p.id}>
											{p.displayName}
										</option>
									))}
								</select>
							</label>
						)}
						<button type="button" className="link-button" onClick={() => setShowFullHistory((v) => !v)}>
							{showFullHistory ? "Ver solo últimos 30 días" : "Ver todo el historial"}
						</button>
						<label>
							Buscar cliente
							<input
								type="text"
								value={searchTerm}
								onChange={(event) => setSearchTerm(event.target.value)}
								placeholder="Nombre o email"
							/>
						</label>
						{canManageHistory && (
							<button
								type="button"
								className="link-button"
								onClick={() => setHistorySettingsOpen((v) => !v)}
							>
								{historySettingsOpen ? "Ocultar retención y borrado" : "Retención y borrado de historial"}
							</button>
						)}
					</div>
					{canManageHistory && historySettingsOpen && (
						<div className="history-settings">
							<p className="label" style={{ marginTop: 0 }}>
								Retención y borrado de historial
							</p>
							<form
								className="inline-form small"
								onSubmit={async (event) => {
									event.preventDefault();
									setError("");
									const months = Number(new FormData(event.target).get("historyRetentionMonths"));
									try {
										setTenant(await api.tenant.updateHistoryRetention(months));
									} catch (err) {
										setError(err.message);
									}
								}}
							>
								<label>
									Guardar historial (meses, máx. 12)
									<input
										name="historyRetentionMonths"
										type="number"
										min="1"
										max="12"
										defaultValue={tenant?.historyRetentionMonths ?? 12}
										style={{ width: "5rem" }}
									/>
								</label>
								<button type="submit">Guardar</button>
							</form>
							<p className="muted">
								Los turnos más viejos que este límite se borran automáticamente y de forma permanente.
								También podés borrar historial reciente ahora mismo — nunca borra turnos que todavía no
								pasaron.
							</p>
							<div className="filters">
								<button type="button" className="secondary" onClick={() => handlePurgeHistory("LAST_HOUR")}>
									Última hora
								</button>
								<button type="button" className="secondary" onClick={() => handlePurgeHistory("LAST_24_HOURS")}>
									Últimas 24 horas
								</button>
								<button type="button" className="secondary" onClick={() => handlePurgeHistory("LAST_4_WEEKS")}>
									Últimas 4 semanas
								</button>
								<button type="button" className="secondary" onClick={() => handlePurgeHistory("ALL")}>
									Todo el historial
								</button>
							</div>
						</div>
					)}
					{loading ? (
						<p>Cargando...</p>
					) : filteredAppointments.length === 0 ? (
						<p className="muted">
							No hay turnos para este filtro
							{!showFullHistory && " en los últimos 30 días"}.
						</p>
					) : (
						<div className="table-scroll">
						<table>
							<thead>
								<tr>
									<th>Cliente</th>
									<th>Inicio</th>
									<th>Estado</th>
									<th>Pago</th>
									<th>Acciones</th>
								</tr>
							</thead>
							<tbody>
								{filteredAppointments.map((a) => (
									<tr key={a.id}>
										<td>
											{a.clientName}
											<br />
											<span className="muted">{a.clientEmail}</span>
										</td>
										<td>{tenantDateTimeLabel(a.startTime, tenant?.timezone)}</td>
										<td>
											<span className={`badge badge-${a.status.toLowerCase()}`}>{statusLabel(a.status)}</span>
										</td>
										<td>{paymentStatusLabel(a.paymentStatus)}</td>
										<td>
											{a.paymentStatus === "PENDING" && ACTIVE_STATUSES.has(a.status) && (
												<button
													type="button"
													className="link-button"
													onClick={() => handleConfirmDeposit(a.id)}
													title="Marcar la seña como recibida (transferencia por alias) y confirmar el turno"
												>
													Confirmar seña
												</button>
											)}
											{ACTIVE_STATUSES.has(a.status) && (
												<button
													type="button"
													className="link-button"
													title="Mover este turno a otra fecha u horario"
													onClick={() => openReschedule(a)}
												>
													Reagendar
												</button>
											)}
											{NEXT_STATUS[a.status].map((s) => (
												<button key={s} type="button" className="link-button" onClick={() => handleTransition(a.id, s)}>
													{statusLabel(s)}
												</button>
											))}
											{canManageHistory && (
												<button
													type="button"
													className="link-button danger-text"
													onClick={() => handleDeleteAppointment(a.id)}
												>
													Eliminar
												</button>
											)}
										</td>
									</tr>
								))}
							</tbody>
						</table>
						</div>
					)}
					</section>
				</>
			) : (
				<>
					<div className="calendar-toolbar">
						<div className="calendar-nav">
							<button type="button" className="secondary" onClick={() => shiftWeek(-7)} aria-label="Semana anterior">
								‹
							</button>
							<span className="calendar-nav-label">{rangeLabel}</span>
							<button type="button" className="secondary" onClick={() => shiftWeek(7)} aria-label="Semana siguiente">
								›
							</button>
							<button type="button" className="secondary" onClick={() => setSelectedDate(todayKey())}>
								Hoy
							</button>
							<input
								type="date"
								value={selectedDate}
								onChange={(event) => setSelectedDate(event.target.value)}
								aria-label="Ir a una fecha"
							/>
						</div>
						{(activeBranches.length > 1 || activeProfessionals.length > 1) && (
							<div className="calendar-toolbar-filters">
								{activeBranches.length > 1 && (
									<label>
										Sucursal
										<select value={effectiveBranchId} onChange={(event) => selectBranch(event.target.value)}>
											{activeBranches.map((b) => (
												<option key={b.id} value={b.id}>
													{b.name}
												</option>
											))}
										</select>
									</label>
								)}
								{activeProfessionals.length > 1 && (
									<label>
										Profesional
										<select
											value={effectiveProfessionalId ?? ""}
											onChange={(event) => selectProfessional(event.target.value)}
										>
											<option value="">Todos</option>
											{activeProfessionals.map((p) => (
												<option key={p.id} value={p.id}>
													{p.displayName}
												</option>
											))}
										</select>
									</label>
								)}
							</div>
						)}
					</div>

					{loading ? (
						<p>Cargando...</p>
					) : (
						<WeekCalendar
							days={calendarDays}
							appointmentsByDay={appointmentsByDay}
							onSelect={setDetailAppointment}
							hourStart={effectiveHourBounds.hourStart}
							hourEnd={effectiveHourBounds.hourEnd}
							professionalName={professionalName}
							timezone={tenant?.timezone}
						/>
					)}
				</>
			)}

			<AppointmentDetailModal
				appointment={detailAppointment}
				onClose={() => setDetailAppointment(null)}
				professionalName={detailAppointment ? professionalName(detailAppointment.professionalId) : ""}
				serviceName={detailAppointment ? serviceName(detailAppointment.serviceId) : ""}
				onTransition={(status) => handleTransition(detailAppointment.id, status)}
				onConfirmDeposit={() => handleConfirmDeposit(detailAppointment.id)}
				onReschedule={() => openReschedule(detailAppointment)}
				onDelete={canManageHistory ? () => handleDeleteAppointment(detailAppointment.id) : undefined}
				error={error}
				timezone={tenant?.timezone}
			/>

			<AppointmentFormModal
				open={formOpen}
				onClose={closeFormModal}
				professionals={professionals}
				services={services}
				defaultDate={todayKey()}
				onSaved={handleSaved}
			/>

			<RescheduleModal
				appointment={rescheduleAppointment}
				onClose={() => setRescheduleAppointment(null)}
				onSaved={handleRescheduleSaved}
				timezone={tenant?.timezone}
			/>
		</div>
	);
}
