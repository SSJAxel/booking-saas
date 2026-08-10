import { useEffect, useState } from "react";
import { api } from "../api.js";

const emptyDraft = {
	serviceId: "",
	professionalId: "",
	date: "",
	time: "",
	clientName: "",
	clientEmail: "",
	clientPhone: "",
	clientInstagram: "",
	overtime: false,
};

/**
 * "Nuevo turno": a brand-new manual appointment, with an optional "sobreturno" checkbox for
 * deliberately overlapping another appointment of the chosen professional (see
 * AppointmentService#book's overtime overload). This is the only entry point for booking a
 * sobreturno — moving an existing appointment to a new time ("Reagendar") is a separate, simpler
 * flow, see RescheduleModal.
 */
export default function AppointmentFormModal({ open, onClose, professionals, services, defaultDate, onSaved }) {
	const [draft, setDraft] = useState(emptyDraft);
	const [eligibleProfessionalIds, setEligibleProfessionalIds] = useState(null);
	const [error, setError] = useState("");
	const [saving, setSaving] = useState(false);

	useEffect(() => {
		if (!open) return;
		setDraft({ ...emptyDraft, date: defaultDate ?? "" });
		setEligibleProfessionalIds(null);
		setError("");
		setSaving(false);
		function onKeyDown(event) {
			if (event.key === "Escape") onClose();
		}
		document.addEventListener("keydown", onKeyDown);
		return () => document.removeEventListener("keydown", onKeyDown);
	}, [open, defaultDate, onClose]);

	useEffect(() => {
		if (!draft.serviceId) {
			setEligibleProfessionalIds(null);
			return;
		}
		api.services
			.listProfessionals(draft.serviceId)
			.then(setEligibleProfessionalIds)
			.catch(() => setEligibleProfessionalIds(null));
	}, [draft.serviceId]);

	if (!open) return null;

	const professionalOptions = eligibleProfessionalIds
		? professionals.filter((p) => eligibleProfessionalIds.includes(p.id))
		: professionals;

	function set(field, value) {
		setDraft((prev) => ({ ...prev, [field]: value }));
	}

	async function handleSubmit(event) {
		event.preventDefault();
		setError("");
		setSaving(true);
		const body = {
			professionalId: draft.professionalId,
			serviceId: draft.serviceId,
			date: draft.date,
			startTime: `${draft.time}:00`,
			clientName: draft.clientName,
			clientEmail: draft.clientEmail,
			clientPhone: draft.clientPhone || null,
			clientInstagram: draft.clientInstagram || null,
		};
		try {
			if (draft.overtime) {
				await api.appointments.createOvertime(body);
			} else {
				await api.appointments.create(body);
			}
			onSaved();
		} catch (err) {
			setError(err.message);
		} finally {
			setSaving(false);
		}
	}

	return (
		<div className="modal-backdrop" onClick={onClose}>
			<div className="modal-panel" onClick={(event) => event.stopPropagation()} role="dialog" aria-modal="true">
				<div className="modal-header">
					<h2>Nuevo turno</h2>
					<button type="button" className="modal-close" onClick={onClose} aria-label="Cerrar">
						×
					</button>
				</div>
				<div className="modal-body">
					<form className="field-grid" onSubmit={handleSubmit}>
						<label>
							Servicio
							<select value={draft.serviceId} onChange={(e) => set("serviceId", e.target.value)} required>
								<option value="" disabled>
									Elegir servicio
								</option>
								{services.map((s) => (
									<option key={s.id} value={s.id}>
										{s.name}
									</option>
								))}
							</select>
						</label>
						<label>
							Profesional
							<select value={draft.professionalId} onChange={(e) => set("professionalId", e.target.value)} required>
								<option value="" disabled>
									Elegir profesional
								</option>
								{professionalOptions.map((p) => (
									<option key={p.id} value={p.id}>
										{p.displayName}
									</option>
								))}
							</select>
						</label>
						<label>
							Fecha
							<input type="date" value={draft.date} onChange={(e) => set("date", e.target.value)} required />
						</label>
						<label>
							Hora
							<input type="time" value={draft.time} onChange={(e) => set("time", e.target.value)} required />
						</label>
						<label className="span-2">
							Nombre del cliente
							<input value={draft.clientName} onChange={(e) => set("clientName", e.target.value)} required />
						</label>
						<label>
							Email del cliente
							<input
								type="email"
								value={draft.clientEmail}
								onChange={(e) => set("clientEmail", e.target.value)}
								required
							/>
						</label>
						<label>
							Teléfono (opcional)
							<input value={draft.clientPhone} onChange={(e) => set("clientPhone", e.target.value)} />
						</label>
						<label className="span-2">
							Instagram (opcional)
							<input
								value={draft.clientInstagram}
								onChange={(e) => set("clientInstagram", e.target.value)}
								placeholder="@usuario"
							/>
						</label>
						<label className="span-2" style={{ flexDirection: "row", alignItems: "center", gap: "0.5rem" }}>
							<input type="checkbox" checked={draft.overtime} onChange={(e) => set("overtime", e.target.checked)} />
							Es un sobreturno (se superpone con otro turno del profesional)
						</label>
						{error && <p className="error span-2">{error}</p>}
						<div className="button-row span-2">
							<button type="submit" disabled={saving}>
								{saving ? "Guardando..." : draft.overtime ? "Crear sobreturno" : "Crear turno"}
							</button>
							<button type="button" className="secondary" onClick={onClose} disabled={saving}>
								Cancelar
							</button>
						</div>
					</form>
				</div>
			</div>
		</div>
	);
}
