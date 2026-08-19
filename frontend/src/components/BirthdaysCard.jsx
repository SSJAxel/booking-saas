import { useEffect, useState } from "react";
import { api } from "../api.js";
import { planHasBirthdayAutoEmail } from "../planLimits.js";

const MONTH_DAY_FORMATTER = new Intl.DateTimeFormat("es-AR", { day: "numeric", month: "long" });

function formatBirthday(birthDate) {
	const [, m, d] = birthDate.split("-").map(Number);
	return MONTH_DAY_FORMATTER.format(new Date(2000, m - 1, d));
}

/**
 * "Cumpleaños del mes" card inside Turnos → Lista → Clientes (PRO/MAX) — own file, same reasoning
 * as LoyaltyRewardsCard.jsx. Purely a reminder list: the discount some tenants apply in person
 * stays manual on purpose (product idea from a real conversation with a competitor's client,
 * 2026-08-18 — they already forgot to apply it without a nudge like this). MAX additionally gets
 * an editable custom message BirthdayEmailScheduler sends automatically on the actual day.
 */
export default function BirthdaysCard({ tenant, onChange }) {
	const [clients, setClients] = useState(null);
	const [error, setError] = useState("");
	const [editingMessage, setEditingMessage] = useState(false);

	async function refresh() {
		try {
			setClients(await api.clients.birthdaysThisMonth());
		} catch (err) {
			setError(err.message);
		}
	}

	useEffect(() => {
		refresh();
	}, []);

	function handleMessageSaved() {
		setEditingMessage(false);
		onChange();
	}

	return (
		<div className="card card-span-full">
			<div className="card-header">
				<p className="label" style={{ margin: 0 }}>
					Cumpleaños del mes
				</p>
				{planHasBirthdayAutoEmail(tenant.planTier) && (
					<button type="button" className="link-button" onClick={() => setEditingMessage((v) => !v)}>
						{editingMessage ? "Listo" : "Mensaje automático"}
					</button>
				)}
			</div>

			{error && <p className="error">{error}</p>}

			{editingMessage && <BirthdayMessageEditor tenant={tenant} onSaved={handleMessageSaved} />}

			{!clients ? (
				<p className="muted">Cargando...</p>
			) : clients.length === 0 ? (
				<p className="muted">Ningún cliente con fecha de nacimiento cargada este mes.</p>
			) : (
				<ul className="client-insight-list">
					{clients.map((c) => (
						<li key={c.id}>
							<span className="client-insight-name">
								{c.name} <span className="muted">({c.email})</span>
							</span>
							<span className="badge">{formatBirthday(c.birthDate)}</span>
						</li>
					))}
				</ul>
			)}
		</div>
	);
}

/** MAX only — {nombre} se reemplaza por el nombre del cliente. Guardar vacío apaga el envío
 * automático sin perder nada más (ver Tenant.birthdayMessageTemplate). */
function BirthdayMessageEditor({ tenant, onSaved }) {
	const [message, setMessage] = useState(tenant.birthdayMessageTemplate ?? "");
	const [saving, setSaving] = useState(false);
	const [error, setError] = useState("");
	const [notice, setNotice] = useState("");

	async function handleSave(event) {
		event.preventDefault();
		setSaving(true);
		setError("");
		try {
			await api.tenant.updateBirthdayMessage(message.trim() || null);
			setNotice("Guardado.");
			setTimeout(() => setNotice(""), 3000);
			onSaved();
		} catch (err) {
			setError(err.message);
		} finally {
			setSaving(false);
		}
	}

	return (
		<form className="top-clients-editor" onSubmit={handleSave}>
			<label>
				Mensaje automático de cumpleaños (se manda solo, el día, usando {"{nombre}"} para el nombre del cliente)
				<textarea
					value={message}
					onChange={(event) => setMessage(event.target.value)}
					rows={3}
					maxLength={1000}
					placeholder="¡Feliz cumple {nombre}! Este mes tenés 15% de descuento en cualquier servicio."
				/>
			</label>
			<div className="button-row" style={{ marginTop: "0.4rem" }}>
				<button type="submit" disabled={saving}>
					{saving ? "Guardando..." : "Guardar"}
				</button>
				{notice && <span className="notice">{notice}</span>}
			</div>
			{error && <p className="error">{error}</p>}
			<p className="muted" style={{ marginTop: "0.4rem" }}>
				Vacío = apagado. El descuento en sí no se aplica solo — esto solo manda el saludo.
			</p>
		</form>
	);
}
