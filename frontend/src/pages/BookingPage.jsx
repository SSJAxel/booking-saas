import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { api } from "../api.js";
import Calendar from "../components/Calendar.jsx";

const STEP_LABELS = {
	service: "Servicio",
	professional: "Profesional",
	datetime: "Fecha y hora",
	details: "Tus datos",
};

function formatDateDisplay(dateKey) {
	const [y, m, d] = dateKey.split("-").map(Number);
	return new Date(y, m - 1, d).toLocaleDateString("es-AR", { weekday: "long", day: "numeric", month: "long" });
}

export default function BookingPage() {
	const { tenantSlug } = useParams();

	const [tenant, setTenant] = useState(null);
	const [branches, setBranches] = useState([]);
	const [services, setServices] = useState([]);
	const [professionals, setProfessionals] = useState([]);
	const [slots, setSlots] = useState([]);

	const [branch, setBranch] = useState(null);
	const [service, setService] = useState(null);
	const [professional, setProfessional] = useState(null);
	const [date, setDate] = useState(null);
	const [slot, setSlot] = useState(null);
	const [appointment, setAppointment] = useState(null);

	const [step, setStep] = useState("service");
	const [initializing, setInitializing] = useState(true);
	const [loading, setLoading] = useState(false);
	const [slotsLoading, setSlotsLoading] = useState(false);
	const [error, setError] = useState("");

	useEffect(() => {
		let cancelled = false;
		async function load() {
			try {
				const [tenantData, branchList] = await Promise.all([
					api.public.tenant(tenantSlug),
					api.public.branches(tenantSlug),
				]);
				if (cancelled) return;
				setTenant(tenantData);
				setBranches(branchList);
				if (branchList.length > 1) {
					setStep("branch");
				} else {
					const onlyBranch = branchList[0] ?? null;
					setBranch(onlyBranch);
					setServices(await api.public.services(tenantSlug, onlyBranch?.id));
				}
			} catch (err) {
				if (!cancelled) setError(err.message);
			} finally {
				if (!cancelled) setInitializing(false);
			}
		}
		load();
		return () => {
			cancelled = true;
		};
	}, [tenantSlug]);

	async function handlePickBranch(b) {
		setBranch(b);
		setError("");
		setLoading(true);
		try {
			setServices(await api.public.services(tenantSlug, b.id));
			setStep("service");
		} catch (err) {
			setError(err.message);
		} finally {
			setLoading(false);
		}
	}

	async function handlePickService(s) {
		setService(s);
		setError("");
		setLoading(true);
		try {
			setProfessionals(await api.public.professionals(tenantSlug, s.id, branch?.id));
			setStep("professional");
		} catch (err) {
			setError(err.message);
		} finally {
			setLoading(false);
		}
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
		try {
			setSlots(await api.public.availability(tenantSlug, professional.id, service.id, dateKey));
		} catch (err) {
			setError(err.message);
		} finally {
			setSlotsLoading(false);
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
			});
			setAppointment(result);
			setStep("done");
		} catch (err) {
			setError(err.message);
		} finally {
			setLoading(false);
		}
	}

	if (initializing) {
		return (
			<div className="booking-page">
				<p className="muted">Cargando...</p>
			</div>
		);
	}

	if (!tenant) {
		return (
			<div className="booking-page">
				<p className="error">{error || "No pudimos cargar este negocio."}</p>
			</div>
		);
	}

	return (
		<div className="booking-page" style={tenant.accentColor ? { "--accent": tenant.accentColor } : undefined}>
			<div className="booking-card">
				<header className="booking-header">
					{tenant.logoUrl ? <img src={tenant.logoUrl} alt={tenant.name} className="booking-logo" /> : null}
					<h1>{tenant.name}</h1>
					{tenant.tagline && <p className="muted">{tenant.tagline}</p>}
				</header>

				{step !== "done" && (
					<ol className="stepper">
						{Object.entries(STEP_LABELS).map(([key, label]) => (
							<li key={key} className={step === key ? "stepper-active" : ""}>
								{label}
							</li>
						))}
					</ol>
				)}

				{error && <p className="error">{error}</p>}

				{step === "branch" && (
					<div className="booking-options">
						{branches.map((b) => (
							<button key={b.id} type="button" className="booking-option" onClick={() => handlePickBranch(b)}>
								<strong>{b.name}</strong>
								{b.address && <span className="muted">{b.address}</span>}
							</button>
						))}
					</div>
				)}

				{step === "service" && (
					<div className="booking-options">
						{services.length === 0 && <p className="muted">No hay servicios disponibles.</p>}
						{services.map((s) => (
							<button key={s.id} type="button" className="booking-option" onClick={() => handlePickService(s)}>
								<strong>{s.name}</strong>
								<span className="muted">
									{s.durationMinutes} min · ${Number(s.price).toLocaleString("es-AR")}
								</span>
								{s.depositAmount && (
									<span className="muted">Requiere seña de ${Number(s.depositAmount).toLocaleString("es-AR")}</span>
								)}
								{s.description && <span className="muted">{s.description}</span>}
							</button>
						))}
					</div>
				)}

				{step === "professional" && (
					<div className="booking-options">
						<button type="button" className="link-button" onClick={() => setStep("service")}>
							‹ Elegir otro servicio
						</button>
						{professionals.length === 0 && (
							<p className="muted">No hay profesionales disponibles para este servicio.</p>
						)}
						{professionals.map((p) => (
							<button
								key={p.id}
								type="button"
								className="booking-option"
								onClick={() => handlePickProfessional(p)}
							>
								<strong>{p.displayName}</strong>
								{p.bio && <span className="muted">{p.bio}</span>}
							</button>
						))}
					</div>
				)}

				{step === "datetime" && (
					<div className="booking-datetime">
						<button type="button" className="link-button" onClick={() => setStep("professional")}>
							‹ Elegir otro profesional
						</button>
						<Calendar selected={date} onSelect={handlePickDate} />
						<div className="slot-panel">
							<p className="label">Horarios disponibles</p>
							{!date && <p className="muted">Elegí un día en el calendario.</p>}
							{date && slotsLoading && <p className="muted">Buscando horarios...</p>}
							{date && !slotsLoading && slots.length === 0 && (
								<p className="muted">No hay horarios disponibles ese día. Probá con otra fecha.</p>
							)}
							{date && !slotsLoading && slots.length > 0 && (
								<div className="slot-grid">
									{slots.map((s) => (
										<button
											key={s.start}
											type="button"
											className={`slot-button${slot?.start === s.start ? " slot-button-selected" : ""}`}
											onClick={() => setSlot(s)}
										>
											{s.start.slice(0, 5)}
										</button>
									))}
								</div>
							)}
							{slot && (
								<button type="button" onClick={() => setStep("details")}>
									Continuar con {slot.start.slice(0, 5)}
								</button>
							)}
						</div>
					</div>
				)}

				{step === "details" && (
					<form onSubmit={handleSubmit} className="booking-details">
						<button type="button" className="link-button" onClick={() => setStep("datetime")}>
							‹ Elegir otro horario
						</button>
						<div className="card booking-summary">
							<p>
								<strong>{service.name}</strong> con {professional.displayName}
							</p>
							<p className="muted">
								{formatDateDisplay(date)} a las {slot.start.slice(0, 5)}
							</p>
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
						<button type="submit" disabled={loading}>
							{loading ? "Reservando..." : "Confirmar turno"}
						</button>
					</form>
				)}

				{step === "done" && appointment && (
					<div className="booking-done">
						<p className="notice">¡Turno reservado!</p>
						<div className="card booking-summary">
							<p>
								<strong>{service.name}</strong> con {professional.displayName}
							</p>
							<p className="muted">
								{formatDateDisplay(date)} a las {slot.start.slice(0, 5)}
							</p>
							<p className="muted">Te enviamos la confirmación a tu email.</p>
						</div>
						{appointment.paymentStatus === "PENDING" && (
							<div className="card deposit-instructions">
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
					</div>
				)}
			</div>
		</div>
	);
}
