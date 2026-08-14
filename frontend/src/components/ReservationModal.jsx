import { useRef, useState } from "react";
import { api, resolveMediaUrl } from "../api.js";
import Calendar from "./Calendar.jsx";

const STEPS_FROM_SERVICE = { professional: "Profesional", datetime: "Fecha y hora", details: "Tus datos" };
const STEPS_FROM_PROFESSIONAL = { service: "Servicio", datetime: "Fecha y hora", details: "Tus datos" };

function formatDateDisplay(dateKey) {
	const [y, m, d] = dateKey.split("-").map(Number);
	return new Date(y, m - 1, d).toLocaleDateString("es-AR", { weekday: "long", day: "numeric", month: "long" });
}

/** Fullscreen booking wizard, opened either from a service card (`service` prop — asks Profesional
 * → Fecha y hora → Tus datos) or from a team-carousel card (`professional` prop — asks Servicio →
 * Fecha y hora → Tus datos, showing only services that professional actually offers). Exactly one
 * of the two is passed in; whichever one is already known is skipped. */
export default function ReservationModal({ tenant, tenantSlug, branch, service: initialService, professional: initialProfessional, onClose }) {
	const fromProfessional = Boolean(initialProfessional);
	const stepLabels = fromProfessional ? STEPS_FROM_PROFESSIONAL : STEPS_FROM_SERVICE;

	const [services, setServices] = useState([]);
	const [professionals, setProfessionals] = useState([]);
	const [slots, setSlots] = useState([]);

	const [service, setService] = useState(initialService ?? null);
	const [professional, setProfessional] = useState(initialProfessional ?? null);
	const [date, setDate] = useState(null);
	const [slot, setSlot] = useState(null);
	const [appointment, setAppointment] = useState(null);

	const [step, setStep] = useState(fromProfessional ? "service" : "professional");
	const [loading, setLoading] = useState(true);
	const [slotsLoading, setSlotsLoading] = useState(false);
	const [error, setError] = useState("");
	const slotsRequestRef = useRef(0);
	const loadedRef = useRef(false);

	if (!loadedRef.current) {
		loadedRef.current = true;
		if (fromProfessional) {
			// No public endpoint returns "services offered by this professional" directly — fetch the
			// branch's whole catalog, then check each service's own professional list for a match.
			api.public
				.services(tenantSlug, branch?.id)
				.then(async (branchServices) => {
					const perService = await Promise.all(
						branchServices.map((s) => api.public.professionals(tenantSlug, s.id, branch?.id)),
					);
					const offered = branchServices.filter((_, i) => perService[i].some((p) => p.id === initialProfessional.id));
					setServices(offered);
				})
				.catch((err) => setError(err.message))
				.finally(() => setLoading(false));
		} else {
			api.public
				.professionals(tenantSlug, initialService.id, branch?.id)
				.then((list) => setProfessionals(list.map((p) => ({ ...p, photoUrl: resolveMediaUrl(p.photoUrl) }))))
				.catch((err) => setError(err.message))
				.finally(() => setLoading(false));
		}
	}

	function handlePickService(s) {
		setService(s);
		setDate(null);
		setSlot(null);
		setSlots([]);
		setStep("datetime");
	}

	function handlePickProfessional(p) {
		setProfessional(p);
		setDate(null);
		setSlot(null);
		setSlots([]);
		setStep("datetime");
	}

	async function handlePickDate(dateKey) {
		setDate(dateKey);
		setSlot(null);
		setSlots([]);
		setSlotsLoading(true);
		setError("");
		const requestId = ++slotsRequestRef.current;
		try {
			const result = await api.public.availability(tenantSlug, professional.id, service.id, dateKey);
			if (requestId !== slotsRequestRef.current) return;
			setSlots(result);
		} catch (err) {
			if (requestId !== slotsRequestRef.current) return;
			setError(err.message);
		} finally {
			if (requestId === slotsRequestRef.current) setSlotsLoading(false);
		}
	}

	async function handleSubmit(event) {
		event.preventDefault();
		setError("");
		setLoading(true);
		const form = new FormData(event.target);
		try {
			const result = await api.public.book(tenantSlug, {
				professionalId: professional.id,
				serviceId: service.id,
				date,
				startTime: slot.start,
				clientName: form.get("clientName"),
				clientEmail: form.get("clientEmail"),
				clientPhone: form.get("clientPhone") || undefined,
				clientInstagram: form.get("clientInstagram") || undefined,
			});
			setAppointment(result);
			setStep("done");
		} catch (err) {
			setError(err.message);
		} finally {
			setLoading(false);
		}
	}

	return (
		<div className="pb-modal-backdrop" onClick={onClose}>
			<div className="pb-modal" onClick={(e) => e.stopPropagation()}>
				<button type="button" className="pb-modal-close" onClick={onClose} aria-label="Cerrar">
					×
				</button>
				<div className="pb-modal-inner">
					{step !== "done" && (
						<ol className="pb-stepper">
							{Object.entries(stepLabels).map(([key, label]) => (
								<li key={key} className={step === key ? "pb-step-active" : ""}>
									{label}
								</li>
							))}
						</ol>
					)}

					{error && <p className="pb-error">{error}</p>}

					{step === "service" && (
						<div className="pb-option-list">
							<h3>{initialProfessional.displayName}</h3>
							{loading && <p className="muted">Cargando servicios...</p>}
							{!loading && services.length === 0 && (
								<p className="pb-empty">Este profesional no tiene servicios disponibles ahora mismo.</p>
							)}
							{services.map((s) => (
								<button key={s.id} type="button" className="pb-option-card" onClick={() => handlePickService(s)}>
									<strong>{s.name}</strong>
									<span>
										{s.durationMinutes} min · ${Number(s.price).toLocaleString("es-AR")}
									</span>
								</button>
							))}
						</div>
					)}

					{step === "professional" && (
						<div className="pb-option-list">
							<h3>{service.name}</h3>
							{loading && <p className="muted">Cargando profesionales...</p>}
							{!loading && professionals.length === 0 && (
								<p className="pb-empty">No hay profesionales disponibles para este servicio.</p>
							)}
							{professionals.map((p) => (
								<button
									key={p.id}
									type="button"
									className="pb-option-card pb-option-card-professional"
									onClick={() => handlePickProfessional(p)}
								>
									{p.photoUrl ? (
										<img src={p.photoUrl} alt="" className="pb-option-photo" />
									) : (
										<span className="pb-option-photo pb-option-photo-fallback">{p.displayName?.[0] ?? "?"}</span>
									)}
									<span className="pb-option-text">
										<strong>{p.displayName}</strong>
										{p.bio && <span>{p.bio}</span>}
									</span>
								</button>
							))}
						</div>
					)}

					{step === "datetime" && (
						<div>
							<button
								type="button"
								className="pb-back-link"
								onClick={() => setStep(fromProfessional ? "service" : "professional")}
							>
								‹ {fromProfessional ? "Elegir otro servicio" : "Elegir otro profesional"}
							</button>
							<Calendar selected={date} onSelect={handlePickDate} />
							<p className="label">Horarios disponibles</p>
							{!date && <p className="pb-empty">Elegí un día en el calendario.</p>}
							{date && slotsLoading && <p className="pb-empty">Buscando horarios...</p>}
							{date && !slotsLoading && slots.length === 0 && (
								<p className="pb-empty">No hay horarios disponibles ese día. Probá con otra fecha.</p>
							)}
							{date && !slotsLoading && slots.length > 0 && (
								<div className="pb-slot-grid">
									{slots.map((s) => (
										<button
											key={s.start}
											type="button"
											className={`pb-slot-btn${slot?.start === s.start ? " selected" : ""}`}
											onClick={() => setSlot(s)}
										>
											{s.start.slice(0, 5)}
										</button>
									))}
								</div>
							)}
							{slot && (
								<button type="button" className="pb-cta" onClick={() => setStep("details")}>
									Continuar con {slot.start.slice(0, 5)}
								</button>
							)}
						</div>
					)}

					{step === "details" && (
						<form onSubmit={handleSubmit} className="pb-form">
							<button type="button" className="pb-back-link" onClick={() => setStep("datetime")}>
								‹ Elegir otro horario
							</button>
							<div className="pb-summary-box">
								<p>
									<strong>{service.name}</strong> con {professional.displayName}
								</p>
								<p className="muted">
									{formatDateDisplay(date)} a las {slot.start.slice(0, 5)}
								</p>
								{service.depositAmount && (
									<p className="muted">Requiere seña de ${Number(service.depositAmount).toLocaleString("es-AR")}</p>
								)}
							</div>
							<label>
								Nombre y apellido
								<input name="clientName" required />
							</label>
							<label>
								Email
								<input name="clientEmail" type="email" required />
							</label>
							<label>
								Teléfono (opcional)
								<input name="clientPhone" type="tel" />
							</label>
							<label>
								Instagram (opcional)
								<input name="clientInstagram" placeholder="@usuario" />
							</label>
							<button type="submit" className="pb-cta" disabled={loading}>
								{loading ? "Reservando..." : "Confirmar turno"}
							</button>
						</form>
					)}

					{step === "done" && appointment && (
						<div>
							<p className="notice">¡Turno reservado!</p>
							<div className="pb-summary-box">
								<p>
									<strong>{service.name}</strong> con {professional.displayName}
								</p>
								<p className="muted">
									{formatDateDisplay(date)} a las {slot.start.slice(0, 5)}
								</p>
								<p className="muted">Te enviamos la confirmación a tu email.</p>
							</div>
							{appointment.paymentStatus === "PENDING" && (
								<div className="pb-deposit-note">
									<p>
										<strong>Este turno todavía no está confirmado.</strong> Requiere una seña de $
										{Number(service.depositAmount).toLocaleString("es-AR")} para confirmarse.
									</p>
									{tenant.transferAlias ? (
										<p>
											Transferí ese monto al alias <strong>{tenant.transferAlias}</strong>. Apenas el negocio vea el
											pago, tu turno queda confirmado.
										</p>
									) : (
										<p className="muted">
											Este negocio todavía no cargó un alias de transferencia — va a contactarte para coordinar la
											seña.
										</p>
									)}
								</div>
							)}
							<button type="button" className="pb-cta" style={{ marginTop: "1.2rem" }} onClick={onClose}>
								Cerrar
							</button>
						</div>
					)}
				</div>
			</div>
		</div>
	);
}
