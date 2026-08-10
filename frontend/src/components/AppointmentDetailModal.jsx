import { useEffect } from "react";
import { paymentStatusLabel, statusLabel } from "../labels.js";

const NEXT_STATUS = {
	PENDING: ["CONFIRMED", "CANCELLED"],
	CONFIRMED: ["CANCELLED", "COMPLETED", "NO_SHOW"],
	CANCELLED: [],
	COMPLETED: [],
	NO_SHOW: [],
};
const ACTIVE_STATUSES = new Set(["PENDING", "CONFIRMED"]);

const DATE_FORMAT = new Intl.DateTimeFormat("es-AR", {
	weekday: "long",
	day: "numeric",
	month: "long",
	hour: "2-digit",
	minute: "2-digit",
});

/**
 * Full-detail popup for a single appointment, opened by tapping a block in WeekCalendar — the
 * grid blocks are deliberately small ("nada grande"), so anything the owner needs to read or act
 * on (client contact, service, payment, status actions) lives here instead.
 */
export default function AppointmentDetailModal({ appointment, onClose, professionalName, serviceName, onTransition,
	onConfirmDeposit, onReplace, error }) {
	useEffect(() => {
		if (!appointment) return;
		function onKeyDown(event) {
			if (event.key === "Escape") onClose();
		}
		document.addEventListener("keydown", onKeyDown);
		return () => document.removeEventListener("keydown", onKeyDown);
	}, [appointment, onClose]);

	if (!appointment) return null;

	const start = new Date(appointment.startTime);

	return (
		<div className="modal-backdrop" onClick={onClose}>
			<div className="modal-panel appointment-detail-panel" onClick={(event) => event.stopPropagation()} role="dialog" aria-modal="true">
				<div className="modal-header">
					<h2>{appointment.clientName}</h2>
					<button type="button" className="modal-close" onClick={onClose} aria-label="Cerrar">
						×
					</button>
				</div>
				<div className="modal-body">
					<span className={`badge badge-${appointment.status.toLowerCase()}`}>{statusLabel(appointment.status)}</span>

					<div className="detail-grid">
						<span className="label">Fecha</span>
						<span>{capitalize(DATE_FORMAT.format(start))}</span>
						<span className="label">Servicio</span>
						<span>{serviceName}</span>
						<span className="label">Profesional</span>
						<span>{professionalName}</span>
						<span className="label">Cliente</span>
						<span>
							{appointment.clientEmail}
							{appointment.clientPhone && <> · {appointment.clientPhone}</>}
						</span>
						<span className="label">Pago</span>
						<span>{paymentStatusLabel(appointment.paymentStatus)}</span>
					</div>

					{error && <p className="error">{error}</p>}

					<div className="button-row" style={{ marginTop: "1.2rem", flexWrap: "wrap" }}>
						{appointment.paymentStatus === "PENDING" && (
							<button type="button" onClick={onConfirmDeposit}>
								Confirmar seña
							</button>
						)}
						{NEXT_STATUS[appointment.status].map((s) => (
							<button key={s} type="button" className="secondary" onClick={() => onTransition(s)}>
								{statusLabel(s)}
							</button>
						))}
						{ACTIVE_STATUSES.has(appointment.status) && (
							<button type="button" className="secondary" onClick={onReplace}>
								Sobreturno
							</button>
						)}
					</div>
				</div>
			</div>
		</div>
	);
}

function capitalize(text) {
	return text.charAt(0).toUpperCase() + text.slice(1);
}
