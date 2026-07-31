import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api.js";

function todayIso() {
	return new Date().toISOString().slice(0, 10);
}

export default function BookingPage() {
	const { tenantSlug, serviceId } = useParams();
	const [service, setService] = useState(null);
	const [professionals, setProfessionals] = useState([]);
	const [professionalId, setProfessionalId] = useState("");
	const [date, setDate] = useState(todayIso());
	const [slots, setSlots] = useState([]);
	const [slot, setSlot] = useState(null);
	const [client, setClient] = useState({ name: "", email: "", phone: "" });
	const [appointment, setAppointment] = useState(null);
	const [error, setError] = useState("");
	const [loading, setLoading] = useState(true);
	const [slotsLoading, setSlotsLoading] = useState(false);
	const [submitting, setSubmitting] = useState(false);

	useEffect(() => {
		setLoading(true);
		setError("");
		Promise.all([api.getServices(tenantSlug), api.getProfessionals(tenantSlug, serviceId)])
			.then(([services, pros]) => {
				const found = services.find((s) => s.id === serviceId);
				if (!found) throw new Error("No encontramos ese servicio.");
				setService(found);
				setProfessionals(pros);
			})
			.catch((err) => setError(err.message))
			.finally(() => setLoading(false));
	}, [tenantSlug, serviceId]);

	useEffect(() => {
		if (!professionalId || !date) {
			setSlots([]);
			return;
		}
		setSlotsLoading(true);
		setSlot(null);
		api
			.getAvailability(tenantSlug, professionalId, serviceId, date)
			.then(setSlots)
			.catch((err) => setError(err.message))
			.finally(() => setSlotsLoading(false));
	}, [tenantSlug, serviceId, professionalId, date]);

	async function handleSubmit(event) {
		event.preventDefault();
		setError("");
		setSubmitting(true);
		try {
			// date + slot.start are already the tenant's own wall-clock date/time (that's what
			// GET .../availability returns) — the server converts to an absolute instant using the
			// tenant's timezone, so there's no client-side date math to get wrong here.
			const created = await api.book(tenantSlug, {
				professionalId,
				serviceId,
				date,
				startTime: slot.start,
				clientName: client.name,
				clientEmail: client.email,
				clientPhone: client.phone || null,
			});
			setAppointment(created);
		} catch (err) {
			setError(err.message);
		} finally {
			setSubmitting(false);
		}
	}

	if (loading) return <p className="status">Cargando...</p>;
	if (error && !service) return <p className="status error">{error}</p>;

	if (appointment) {
		return (
			<div className="page">
				<div className="confirmation">
					<h1>¡Listo, {client.name}!</h1>
					<p>
						Tu turno para <strong>{service.name}</strong> el{" "}
						{new Date(appointment.startTime).toLocaleString()} quedó{" "}
						<span className={`badge badge-${appointment.status.toLowerCase()}`}>
							{appointment.status === "CONFIRMED" ? "confirmado" : "pendiente"}
						</span>
						.
					</p>
					{appointment.status === "PENDING" && <p className="muted">Te vamos a avisar por mail apenas se confirme.</p>}
					<Link to={`/${tenantSlug}`} className="button-link">
						Volver al inicio
					</Link>
				</div>
			</div>
		);
	}

	return (
		<div className="page">
			<Link to={`/${tenantSlug}`} className="back-link">
				&larr; Volver
			</Link>
			<h1>{service.name}</h1>
			<p className="muted">
				{service.durationMinutes} min · ${service.price}
			</p>
			{error && <p className="status error">{error}</p>}

			<div className="booking-steps">
				<div>
					<p className="label">1. Elegí profesional</p>
					<div className="chip-row">
						{professionals.length === 0 && (
							<p className="muted">No hay profesionales disponibles para este servicio.</p>
						)}
						{professionals.map((p) => (
							<button
								key={p.id}
								type="button"
								className={`chip ${professionalId === p.id ? "chip-on" : ""}`}
								onClick={() => setProfessionalId(p.id)}
							>
								{p.displayName}
							</button>
						))}
					</div>
				</div>

				{professionalId && (
					<div>
						<p className="label">2. Elegí día y horario</p>
						<input type="date" value={date} min={todayIso()} onChange={(event) => setDate(event.target.value)} />
						{slotsLoading ? (
							<p className="muted">Buscando horarios...</p>
						) : slots.length === 0 ? (
							<p className="muted">No hay horarios libres ese día.</p>
						) : (
							<div className="chip-row">
								{slots.map((s) => (
									<button
										key={s.start}
										type="button"
										className={`chip ${slot?.start === s.start ? "chip-on" : ""}`}
										onClick={() => setSlot(s)}
									>
										{s.start.slice(0, 5)}
									</button>
								))}
							</div>
						)}
					</div>
				)}

				{slot && (
					<div>
						<p className="label">3. Tus datos</p>
						<form className="details-form" onSubmit={handleSubmit}>
							<input
								placeholder="Nombre"
								required
								value={client.name}
								onChange={(event) => setClient({ ...client, name: event.target.value })}
							/>
							<input
								type="email"
								placeholder="Email"
								required
								value={client.email}
								onChange={(event) => setClient({ ...client, email: event.target.value })}
							/>
							<input
								placeholder="Teléfono (opcional)"
								value={client.phone}
								onChange={(event) => setClient({ ...client, phone: event.target.value })}
							/>
							<button type="submit" disabled={submitting}>
								{submitting ? "Reservando..." : "Confirmar reserva"}
							</button>
						</form>
					</div>
				)}
			</div>
		</div>
	);
}
