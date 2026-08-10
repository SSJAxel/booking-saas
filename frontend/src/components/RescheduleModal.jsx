import { useEffect, useState } from "react";
import { api } from "../api.js";

const REASON_OPTIONS = [
	{ value: "TENANT_DECISION", label: "Personal del tenant" },
	{ value: "CLIENT_NOTICE", label: "Aviso" },
];

/**
 * "Reagendar": moves appointment.id to a new date/time, keeping the same client, profesional y
 * servicio (no editables acá — ver AppointmentService#reschedule). El motivo decide dos cosas en el
 * backend: si toca la calificación del cliente ("Aviso" sí, "Personal del tenant" no) y si se
 * mantiene una seña ya pagada (siempre con "Personal del tenant"; sólo la primera vez con "Aviso").
 */
export default function RescheduleModal({ appointment, onClose, onSaved }) {
	const [reason, setReason] = useState("TENANT_DECISION");
	const [date, setDate] = useState("");
	const [time, setTime] = useState("");
	const [error, setError] = useState("");
	const [saving, setSaving] = useState(false);

	useEffect(() => {
		if (!appointment) return;
		const start = new Date(appointment.startTime);
		setDate(start.toISOString().slice(0, 10));
		setTime(`${String(start.getHours()).padStart(2, "0")}:${String(start.getMinutes()).padStart(2, "0")}`);
		setReason("TENANT_DECISION");
		setError("");
		setSaving(false);
		function onKeyDown(event) {
			if (event.key === "Escape") onClose();
		}
		document.addEventListener("keydown", onKeyDown);
		return () => document.removeEventListener("keydown", onKeyDown);
	}, [appointment, onClose]);

	if (!appointment) return null;

	const currentDateTime = new Date(appointment.startTime).toLocaleString("es-AR", {
		weekday: "long",
		day: "numeric",
		month: "long",
		hour: "2-digit",
		minute: "2-digit",
	});

	async function handleSubmit(event) {
		event.preventDefault();
		setError("");
		setSaving(true);
		try {
			await api.appointments.reschedule(appointment.id, { date, startTime: `${time}:00`, reason });
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
					<h2>Reagendar turno</h2>
					<button type="button" className="modal-close" onClick={onClose} aria-label="Cerrar">
						×
					</button>
				</div>
				<div className="modal-body">
					<p className="muted" style={{ marginTop: 0 }}>
						Turno de <strong>{appointment.clientName}</strong>, actualmente el {currentDateTime}.
					</p>
					<form className="field-grid" onSubmit={handleSubmit}>
						<label className="span-2">
							Motivo
							<select value={reason} onChange={(e) => setReason(e.target.value)}>
								{REASON_OPTIONS.map((o) => (
									<option key={o.value} value={o.value}>
										{o.label}
									</option>
								))}
							</select>
						</label>
						<label>
							Fecha nueva
							<input type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
						</label>
						<label>
							Hora nueva
							<input type="time" value={time} onChange={(e) => setTime(e.target.value)} required />
						</label>
						<p className="muted span-2" style={{ fontSize: "0.85em" }}>
							Con "Personal del tenant" una seña ya pagada siempre se mantiene. Con "Aviso", se mantiene sólo la
							primera vez que este cliente reagenda por este motivo — de la segunda vez en adelante, la seña se
							pierde.
						</p>
						{error && <p className="error span-2">{error}</p>}
						<div className="button-row span-2">
							<button type="submit" disabled={saving}>
								{saving ? "Guardando..." : "Reagendar"}
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
