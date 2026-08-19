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
 * of the two is passed in; whichever one is already known is skipped.
 *
 * A client can chain more than one service into the same booking ("corte con Lauti" +
 * "tratamiento capilar con Facu", el mismo día o en días distintos) via "+ Agregar otro servicio"
 * at the datetime step. Confirmed legs live in `items`; `service`/`professional`/`date`/`slot`
 * always describe whichever leg is currently being picked. Each leg keeps its own price/deposit —
 * there's no combo pricing (see README "PLANES a futuro") — so a multi-leg booking hits
 * POST .../appointments/group (one booking_group_id, independent per-leg deposits/cancellation);
 * a single leg still hits the original POST .../appointments, unchanged. */
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
	const [bookedAppointments, setBookedAppointments] = useState(null);
	// Which pending appointment's Mercado Pago checkout is in flight — disables just that one
	// button (a group booking can have more than one PENDING leg at once) while the redirect happens.
	const [payingId, setPayingId] = useState(null);
	// Only ever set when items reaches exactly 2 — a MAX-plan ServiceCombo, if one matches those two
	// services. Preview only, for display; AppointmentService.bookGroup re-derives this itself when
	// actually booking, never trusts what this fetch returned.
	const [comboPreview, setComboPreview] = useState(null);

	// Confirmed legs of a multi-service booking (see the doc comment above). Empty until the client
	// picks "+ Agregar otro servicio" or reaches "details" for the first time.
	const [items, setItems] = useState([]);
	const [pickingExtra, setPickingExtra] = useState(false);
	const [extraServices, setExtraServices] = useState([]);
	const [extraProfessionals, setExtraProfessionals] = useState([]);
	const [extraLoading, setExtraLoading] = useState(false);

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

	/** Shared by the first pick and by "+ Agregar otro servicio". `preferredAfter` only makes sense
	 * when the new leg lands on the exact same date as the last confirmed one — the backend just
	 * reorders slots around it, it doesn't filter, so picking a different day is unaffected. */
	async function loadSlots(professionalId, serviceId, dateKey) {
		setSlot(null);
		setSlots([]);
		setSlotsLoading(true);
		setError("");
		const requestId = ++slotsRequestRef.current;
		const lastItem = items[items.length - 1];
		const preferredAfter = lastItem && lastItem.date === dateKey ? lastItem.slot.end : undefined;
		try {
			const result = await api.public.availability(tenantSlug, professionalId, serviceId, dateKey, preferredAfter);
			if (requestId !== slotsRequestRef.current) return;
			setSlots(result);
		} catch (err) {
			if (requestId !== slotsRequestRef.current) return;
			setError(err.message);
		} finally {
			if (requestId === slotsRequestRef.current) setSlotsLoading(false);
		}
	}

	async function handlePickDate(dateKey) {
		setDate(dateKey);
		await loadSlots(professional.id, service.id, dateKey);
	}

	function handleAddAnotherService() {
		setItems((prev) => [...prev, { service, professional, date, slot }]);
		setService(null);
		setProfessional(null);
		setDate(null);
		setSlot(null);
		setSlots([]);
		setPickingExtra(true);
		setExtraLoading(true);
		setError("");
		api.public
			.services(tenantSlug, branch?.id)
			.then((list) => setExtraServices(list))
			.catch((err) => setError(err.message))
			.finally(() => setExtraLoading(false));
		setStep("extraService");
	}

	function handlePickExtraService(s) {
		setService(s);
		setExtraLoading(true);
		setError("");
		api.public
			.professionals(tenantSlug, s.id, branch?.id)
			.then((list) => setExtraProfessionals(list.map((p) => ({ ...p, photoUrl: resolveMediaUrl(p.photoUrl) }))))
			.catch((err) => setError(err.message))
			.finally(() => setExtraLoading(false));
		setStep("extraProfessional");
	}

	function handlePickExtraProfessional(p) {
		setProfessional(p);
		setDate(null);
		setSlot(null);
		setSlots([]);
		setStep("datetime");
	}

	async function handleContinueToDetails() {
		const newItems = [...items, { service, professional, date, slot }];
		setItems(newItems);
		setStep("details");
		await refreshComboPreview(newItems);
	}

	/** Only ever finds something for exactly 2 items — see ServiceComboService, combos never apply
	 * to 1 or 3+ services. A failed lookup (e.g. plan doesn't support combos) just means no discount
	 * shown, not an error worth surfacing to the client mid-booking. */
	async function refreshComboPreview(currentItems) {
		if (currentItems.length !== 2) {
			setComboPreview(null);
			return;
		}
		try {
			const combo = await api.public.serviceCombo(tenantSlug, currentItems[0].service.id, currentItems[1].service.id);
			setComboPreview(combo);
		} catch {
			setComboPreview(null);
		}
	}

	/** Pops the last confirmed leg back into the in-progress fields and re-fetches its slots, so
	 * "‹ Elegir otro horario" from "details" lands the client exactly where they left off instead of
	 * an empty datetime step. */
	async function handleBackFromDetails() {
		const remaining = items.slice(0, -1);
		const last = items[items.length - 1];
		setItems(remaining);
		setComboPreview(null);
		setService(last.service);
		setProfessional(last.professional);
		setDate(last.date);
		setStep("datetime");
		await loadSlots(last.professional.id, last.service.id, last.date);
		setSlot(last.slot);
	}

	async function handlePayWithMercadoPago(appointmentId) {
		setPayingId(appointmentId);
		setError("");
		try {
			const { initPoint } = await api.public.checkout(tenantSlug, appointmentId);
			window.location.href = initPoint;
		} catch (err) {
			setError(err.message);
			setPayingId(null);
		}
	}

	async function handleSubmit(event) {
		event.preventDefault();
		setError("");
		setLoading(true);
		const form = new FormData(event.target);
		const clientPayload = {
			clientName: form.get("clientName"),
			clientEmail: form.get("clientEmail"),
			clientPhone: form.get("clientPhone") || undefined,
			clientInstagram: form.get("clientInstagram") || undefined,
		};
		try {
			if (items.length > 1) {
				const result = await api.public.bookGroup(tenantSlug, {
					...clientPayload,
					items: items.map((it) => ({
						professionalId: it.professional.id,
						serviceId: it.service.id,
						date: it.date,
						startTime: it.slot.start,
					})),
				});
				setBookedAppointments(result);
			} else {
				const only = items[0];
				const result = await api.public.book(tenantSlug, {
					professionalId: only.professional.id,
					serviceId: only.service.id,
					date: only.date,
					startTime: only.slot.start,
					...clientPayload,
				});
				setBookedAppointments([result]);
			}
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
					{step !== "done" && items.length === 0 && (
						<ol className="pb-stepper">
							{Object.entries(stepLabels).map(([key, label]) => (
								<li key={key} className={step === key ? "pb-step-active" : ""}>
									{label}
								</li>
							))}
						</ol>
					)}
					{step !== "done" && items.length > 0 && (
						<p className="muted">
							Ya tenés {items.length} servicio{items.length > 1 ? "s" : ""} para esta reserva.
						</p>
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

					{step === "extraService" && (
						<div className="pb-option-list">
							<h3>Agregá otro servicio</h3>
							{extraLoading && <p className="muted">Cargando servicios...</p>}
							{!extraLoading && extraServices.length === 0 && (
								<p className="pb-empty">No hay más servicios disponibles ahora mismo.</p>
							)}
							{extraServices.map((s) => (
								<button key={s.id} type="button" className="pb-option-card" onClick={() => handlePickExtraService(s)}>
									<strong>{s.name}</strong>
									<span>
										{s.durationMinutes} min · ${Number(s.price).toLocaleString("es-AR")}
									</span>
								</button>
							))}
							<button type="button" className="pb-back-link" onClick={() => setStep("details")}>
								‹ Ya elegí suficiente, continuar
							</button>
						</div>
					)}

					{step === "extraProfessional" && (
						<div className="pb-option-list">
							<h3>{service.name}</h3>
							{extraLoading && <p className="muted">Cargando profesionales...</p>}
							{!extraLoading && extraProfessionals.length === 0 && (
								<p className="pb-empty">No hay profesionales disponibles para este servicio.</p>
							)}
							{extraProfessionals.map((p) => (
								<button
									key={p.id}
									type="button"
									className="pb-option-card pb-option-card-professional"
									onClick={() => handlePickExtraProfessional(p)}
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
							<button type="button" className="pb-back-link" onClick={() => setStep("extraService")}>
								‹ Elegir otro servicio
							</button>
						</div>
					)}

					{step === "datetime" && (
						<div>
							<button
								type="button"
								className="pb-back-link"
								onClick={() => setStep(pickingExtra ? "extraProfessional" : fromProfessional ? "service" : "professional")}
							>
								‹ {pickingExtra ? "Elegir otro profesional" : fromProfessional ? "Elegir otro servicio" : "Elegir otro profesional"}
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
								<div className="pb-cta-row">
									<button type="button" className="pb-cta" onClick={handleContinueToDetails}>
										{items.length > 0 ? "Continuar y confirmar" : `Continuar con ${slot.start.slice(0, 5)}`}
									</button>
									<button type="button" className="pb-cta-secondary" onClick={handleAddAnotherService}>
										+ Agregar otro servicio
									</button>
								</div>
							)}
						</div>
					)}

					{step === "details" && (
						<form onSubmit={handleSubmit} className="pb-form">
							<button type="button" className="pb-back-link" onClick={handleBackFromDetails}>
								‹ Elegir otro horario
							</button>
							{comboPreview && (
								<div className="pb-combo-banner">
									🎉 Precio combo: <strong>${Number(comboPreview.comboPrice).toLocaleString("es-AR")}</strong> en vez de $
									{items.reduce((sum, it) => sum + Number(it.service.price), 0).toLocaleString("es-AR")}
								</div>
							)}
							<div className="pb-summary-box">
								{items.map((it, i) => (
									<div className="pb-summary-item" key={i}>
										<p>
											<strong>{it.service.name}</strong> con {it.professional.displayName}
										</p>
										<p className="muted">
											{formatDateDisplay(it.date)} a las {it.slot.start.slice(0, 5)}
										</p>
										{!comboPreview?.comboDepositAmount && it.service.depositAmount && (
											<p className="muted">Requiere seña de ${Number(it.service.depositAmount).toLocaleString("es-AR")}</p>
										)}
									</div>
								))}
								{comboPreview?.comboDepositAmount != null && (
									<p className="muted">
										Requiere una seña combinada de ${Number(comboPreview.comboDepositAmount).toLocaleString("es-AR")}{" "}
										para confirmar los dos servicios.
									</p>
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
								{loading ? "Reservando..." : items.length > 1 ? "Confirmar turnos" : "Confirmar turno"}
							</button>
						</form>
					)}

					{step === "done" && bookedAppointments && (
						<div>
							<p className="notice">{bookedAppointments.length > 1 ? "¡Turnos reservados!" : "¡Turno reservado!"}</p>
							<div className="pb-summary-box">
								{items.map((it, i) => {
									const appt = bookedAppointments[i];
									const pending = appt?.paymentStatus === "PENDING";
									const depositAmount = Number(appt?.depositAmountOverride ?? it.service.depositAmount);
									return (
										<div className="pb-summary-item" key={i}>
											<p>
												<strong>{it.service.name}</strong> con {it.professional.displayName}
											</p>
											<p className="muted">
												{formatDateDisplay(it.date)} a las {it.slot.start.slice(0, 5)}
											</p>
											{pending && (
												<>
													<p className="muted">
														Todavía no confirmado — requiere seña de ${depositAmount.toLocaleString("es-AR")}.
													</p>
													{tenant.mercadoPagoEnabled && (
														<button
															type="button"
															className="pb-cta"
															style={{ marginTop: "0.5rem" }}
															onClick={() => handlePayWithMercadoPago(appt.id)}
															disabled={payingId === appt.id}
														>
															{payingId === appt.id
																? "Redirigiendo a Mercado Pago..."
																: `Pagar seña de $${depositAmount.toLocaleString("es-AR")} con Mercado Pago`}
														</button>
													)}
													{tenant.transferAlias && (
														<p className="muted" style={{ marginTop: "0.4rem" }}>
															{tenant.mercadoPagoEnabled ? "O transferí" : "Transferí"} el monto al alias{" "}
															<strong>{tenant.transferAlias}</strong>. Apenas el negocio vea el pago, tu turno queda
															confirmado.
														</p>
													)}
													{!tenant.mercadoPagoEnabled && !tenant.transferAlias && (
														<p className="muted">
															Este negocio todavía no cargó una forma de pagar la seña — va a contactarte para
															coordinarla.
														</p>
													)}
												</>
											)}
										</div>
									);
								})}
								<p className="muted">Te enviamos la confirmación a tu email.</p>
							</div>
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
