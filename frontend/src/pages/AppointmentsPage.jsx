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

const STATUSES = ["PENDING", "CONFIRMED", "CANCELLED", "COMPLETED", "NO_SHOW"];
const NEXT_STATUS = {
	PENDING: ["CONFIRMED", "CANCELLED"],
	CONFIRMED: ["CANCELLED", "COMPLETED", "NO_SHOW"],
	CANCELLED: [],
	COMPLETED: [],
	NO_SHOW: [],
};
const ACTIVE_STATUSES = new Set(["PENDING", "CONFIRMED"]);
const DEFAULT_HOUR_BOUNDS = { hourStart: 9, hourEnd: 19 };

const RANGE_LABEL_FORMAT = new Intl.DateTimeFormat("es-AR", { day: "numeric", month: "short" });
const APPOINTMENT_DATETIME_FORMAT = new Intl.DateTimeFormat("es-AR", {
	day: "numeric",
	month: "numeric",
	year: "numeric",
	hour: "2-digit",
	minute: "2-digit",
});

function pad(n) {
	return String(n).padStart(2, "0");
}

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

/** Same {id, clientName, serviceId, professionalId, date, time} shape AppointmentFormModal expects
 * for the "replacing" prop — extracted from the appointment being replaced so the form can prefill
 * professional/service and show what's about to be cancelled. */
function toReplacingContext(appointment) {
	const start = new Date(appointment.startTime);
	return {
		id: appointment.id,
		clientName: appointment.clientName,
		serviceId: appointment.serviceId,
		professionalId: appointment.professionalId,
		date: toDateKey(start),
		time: `${pad(start.getHours())}:${pad(start.getMinutes())}`,
	};
}

export default function AppointmentsPage() {
	const { session } = useAuth();
	const [view, setView] = useState("calendario");
	const [selectedDate, setSelectedDate] = useState(todayKey());

	const [appointments, setAppointments] = useState([]);
	const [statusFilter, setStatusFilter] = useState("");
	const [showFullHistory, setShowFullHistory] = useState(false);
	const [calendarAppointments, setCalendarAppointments] = useState([]);
	const [professionals, setProfessionals] = useState([]);
	const [services, setServices] = useState([]);
	const [branches, setBranches] = useState([]);
	const [selectedBranchId, setSelectedBranchId] = useState(
		() => localStorage.getItem(`selected-branch-${session.tenantSlug}`) || "",
	);
	const [hourBounds, setHourBounds] = useState(DEFAULT_HOUR_BOUNDS);

	const [error, setError] = useState("");
	const [loading, setLoading] = useState(true);
	const [showWelcome, setShowWelcome] = useState(false);
	const [formModal, setFormModal] = useState({ open: false, replacing: null });
	const [detailAppointment, setDetailAppointment] = useState(null);
	const [rescheduleAppointment, setRescheduleAppointment] = useState(null);

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

	async function refreshList() {
		setLoading(true);
		try {
			// Default view: last 30 days back + every future appointment, however far out — old
			// resolved turnos (completed/cancelled/no-show from months ago) just pile up otherwise.
			// No upper bound, so a turno booked weeks ahead never disappears from the default view.
			const from = showFullHistory ? undefined : new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString();
			setAppointments(
				await api.appointments.list({ status: statusFilter || undefined, from, branchId: effectiveBranchId }),
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
	}, [view, statusFilter, showFullHistory, effectiveBranchId]);

	async function refreshCalendar() {
		setLoading(true);
		try {
			const from = mondayOf(selectedDate);
			const to = new Date(from);
			to.setDate(to.getDate() + 7);
			setCalendarAppointments(
				await api.appointments.list({ from: from.toISOString(), to: to.toISOString(), branchId: effectiveBranchId }),
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
	}, [view, selectedDate, effectiveBranchId]);

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

	function professionalName(id) {
		return professionals.find((p) => p.id === id)?.displayName ?? "—";
	}

	function serviceName(id) {
		return services.find((s) => s.id === id)?.name ?? "—";
	}

	function openCreate() {
		setFormModal({ open: true, replacing: null });
	}

	function openOvertime(appointment) {
		setDetailAppointment(null);
		setFormModal({ open: true, replacing: toReplacingContext(appointment) });
	}

	function openReschedule(appointment) {
		setDetailAppointment(null);
		setRescheduleAppointment(appointment);
	}

	function closeFormModal() {
		setFormModal({ open: false, replacing: null });
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

	const appointmentsByDay = useMemo(() => {
		const map = {};
		for (const a of calendarAppointments) {
			const key = toDateKey(new Date(a.startTime));
			(map[key] ??= []).push(a);
		}
		return map;
	}, [calendarAppointments]);

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
					+ Nuevo turno
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
						<button type="button" className="link-button" onClick={() => setShowFullHistory((v) => !v)}>
							{showFullHistory ? "Ver solo últimos 30 días" : "Ver todo el historial"}
						</button>
					</div>
					{loading ? (
						<p>Cargando...</p>
					) : appointments.length === 0 ? (
						<p className="muted">
							No hay turnos para este filtro
							{!showFullHistory && " en los últimos 30 días"}.
						</p>
					) : (
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
								{appointments.map((a) => (
									<tr key={a.id}>
										<td>
											{a.clientName}
											<br />
											<span className="muted">{a.clientEmail}</span>
										</td>
										<td>{APPOINTMENT_DATETIME_FORMAT.format(new Date(a.startTime))}</td>
										<td>
											<span className={`badge badge-${a.status.toLowerCase()}`}>{statusLabel(a.status)}</span>
										</td>
										<td>{paymentStatusLabel(a.paymentStatus)}</td>
										<td>
											{a.paymentStatus === "PENDING" && (
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
												<>
													<button
														type="button"
														className="link-button"
														title="Mover este turno a otra fecha u horario"
														onClick={() => openReschedule(a)}
													>
														Reagendar
													</button>
													<button
														type="button"
														className="link-button"
														title="Agendar un turno en paralelo con el mismo profesional"
														onClick={() => openOvertime(a)}
													>
														Sobreturno
													</button>
												</>
											)}
											{NEXT_STATUS[a.status].map((s) => (
												<button key={s} type="button" className="link-button" onClick={() => handleTransition(a.id, s)}>
													{statusLabel(s)}
												</button>
											))}
										</td>
									</tr>
								))}
							</tbody>
						</table>
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
					</div>

					{loading ? (
						<p>Cargando...</p>
					) : (
						<WeekCalendar
							days={calendarDays}
							appointmentsByDay={appointmentsByDay}
							onSelect={setDetailAppointment}
							hourStart={hourBounds.hourStart}
							hourEnd={hourBounds.hourEnd}
							professionalName={professionalName}
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
				onOvertime={() => openOvertime(detailAppointment)}
				error={error}
			/>

			<AppointmentFormModal
				open={formModal.open}
				onClose={closeFormModal}
				professionals={professionals}
				services={services}
				replacing={formModal.replacing}
				defaultDate={todayKey()}
				onSaved={handleSaved}
			/>

			<RescheduleModal
				appointment={rescheduleAppointment}
				onClose={() => setRescheduleAppointment(null)}
				onSaved={handleRescheduleSaved}
			/>
		</div>
	);
}
