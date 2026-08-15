import { useEffect, useState } from "react";
import { useAuth } from "../auth/AuthContext.jsx";
import { api } from "../api.js";
import { statusLabel } from "../labels.js";
import { tenantLongDateTimeLabel } from "../tenantTime.js";

/**
 * "Ver historial" popup for a client, opened from ClientInsights' ClientList — every visit (who
 * served them, when, what service, what status) plus a freeform notes field the owner/admin can
 * edit. Same structural pattern as AppointmentDetailModal.jsx (backdrop/panel/header/body,
 * Escape-to-close). `client` carries the name/email/notes already loaded by ClientInsights, so
 * only the visit history needs its own fetch.
 */
export default function ClientHistoryModal({ client, timezone, onClose, onNotesSaved }) {
	const { session } = useAuth();
	const canEditNotes = session.role === "OWNER" || session.role === "ADMIN";
	const [visits, setVisits] = useState(null);
	const [error, setError] = useState("");
	const [notes, setNotes] = useState(client?.notes ?? "");
	const [saving, setSaving] = useState(false);
	const [notice, setNotice] = useState("");

	useEffect(() => {
		if (!client) return;
		setNotes(client.notes ?? "");
		setVisits(null);
		setError("");
		api.clients
			.history(client.clientId)
			.then(setVisits)
			.catch((err) => setError(err.message));
	}, [client]);

	useEffect(() => {
		if (!client) return;
		function onKeyDown(event) {
			if (event.key === "Escape") onClose();
		}
		document.addEventListener("keydown", onKeyDown);
		return () => document.removeEventListener("keydown", onKeyDown);
	}, [client, onClose]);

	if (!client) return null;

	async function handleSaveNotes(event) {
		event.preventDefault();
		setSaving(true);
		setError("");
		try {
			const updated = await api.clients.updateNotes(client.clientId, notes.trim() || null);
			setNotice("Guardado.");
			setTimeout(() => setNotice(""), 3000);
			onNotesSaved?.(updated);
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
					<h2>{client.clientName}</h2>
					<button type="button" className="modal-close" onClick={onClose} aria-label="Cerrar">
						×
					</button>
				</div>
				<div className="modal-body">
					<p className="muted">{client.clientEmail}</p>

					{error && <p className="error">{error}</p>}

					{canEditNotes && (
						<form onSubmit={handleSaveNotes} style={{ marginTop: "0.8rem" }}>
							<label>
								Notas
								<textarea
									value={notes}
									onChange={(event) => setNotes(event.target.value)}
									rows={3}
									maxLength={2000}
									placeholder="Comentarios sobre este cliente..."
								/>
							</label>
							<div className="button-row" style={{ marginTop: "0.4rem" }}>
								<button type="submit" disabled={saving}>
									{saving ? "Guardando..." : "Guardar notas"}
								</button>
								{notice && <span className="notice">{notice}</span>}
							</div>
						</form>
					)}

					<p className="label" style={{ marginTop: "1.2rem" }}>
						Historial de turnos
					</p>
					{!visits ? (
						<p className="muted">Cargando...</p>
					) : visits.length === 0 ? (
						<p className="muted">Todavía no tiene turnos.</p>
					) : (
						<ul className="client-insight-list">
							{visits.map((v) => (
								<li key={v.appointmentId}>
									<span className="client-insight-name">
										{tenantLongDateTimeLabel(v.startTime, timezone)} · {v.serviceName} con {v.professionalName}
									</span>
									<span className={`badge badge-${v.status.toLowerCase()}`}>{statusLabel(v.status)}</span>
								</li>
							))}
						</ul>
					)}
				</div>
			</div>
		</div>
	);
}
