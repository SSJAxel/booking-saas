import { useEffect, useState } from "react";
import { useAuth } from "../auth/AuthContext.jsx";
import { api } from "../api.js";
import { statusLabel } from "../labels.js";
import { tenantLongDateTimeLabel } from "../tenantTime.js";

/**
 * "Ver historial" popup for a client, opened from ClientInsights' ClientList — every visit (who
 * served them, when, what service, what status), a freeform notes field the owner/admin can edit,
 * and (if the tenant has loyalty rewards enabled) the client's points balance with a redeem action
 * — a second entry point alongside LoyaltyRewardsCard's "Recompensas" list, since a client might be
 * looked up here before staff thinks to check that list. Same structural pattern as
 * AppointmentDetailModal.jsx (backdrop/panel/header/body, Escape-to-close). `client` carries the
 * name/email/notes/loyaltyPoints already loaded by ClientInsights, so only the visit history and
 * (when relevant) the tier list need their own fetch.
 */
export default function ClientHistoryModal({ client, tenant, onClose, onNotesSaved, onBirthdaySaved, onRewardRedeemed }) {
	const { session } = useAuth();
	const canEditNotes = session.role === "OWNER" || session.role === "ADMIN";
	const [visits, setVisits] = useState(null);
	const [error, setError] = useState("");
	const [notes, setNotes] = useState(client?.notes ?? "");
	const [birthDate, setBirthDate] = useState(client?.birthDate ?? "");
	const [saving, setSaving] = useState(false);
	const [savingBirthday, setSavingBirthday] = useState(false);
	const [notice, setNotice] = useState("");
	const [birthdayNotice, setBirthdayNotice] = useState("");
	const [tiers, setTiers] = useState(null);
	const [selectedTierId, setSelectedTierId] = useState("");
	const [redeeming, setRedeeming] = useState(false);

	useEffect(() => {
		if (!client) return;
		setNotes(client.notes ?? "");
		setBirthDate(client.birthDate ?? "");
		setVisits(null);
		setError("");
		api.clients
			.history(client.clientId)
			.then(setVisits)
			.catch((err) => setError(err.message));

		if (tenant.loyaltyRewardsEnabled) {
			api.loyaltyTiers
				.list()
				.then(setTiers)
				.catch(() => setTiers([]));
		}
	}, [client, tenant.loyaltyRewardsEnabled]);

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

	async function handleSaveBirthday(event) {
		event.preventDefault();
		setSavingBirthday(true);
		setError("");
		try {
			const updated = await api.clients.updateBirthday(client.clientId, birthDate || null);
			setBirthdayNotice("Guardado.");
			setTimeout(() => setBirthdayNotice(""), 3000);
			onBirthdaySaved?.(updated);
		} catch (err) {
			setError(err.message);
		} finally {
			setSavingBirthday(false);
		}
	}

	const eligibleTiers = tiers?.filter((t) => client.loyaltyPoints >= t.pointsRequired) ?? [];
	const tierIdToRedeem = selectedTierId || eligibleTiers[0]?.id || "";

	async function handleRedeem() {
		setRedeeming(true);
		setError("");
		try {
			await api.clients.redeemReward(client.clientId, tierIdToRedeem);
			onRewardRedeemed?.();
		} catch (err) {
			setError(err.message);
		} finally {
			setRedeeming(false);
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
						<form onSubmit={handleSaveBirthday} style={{ marginTop: "0.8rem" }}>
							<label>
								Fecha de nacimiento
								<input
									type="date"
									value={birthDate}
									onChange={(event) => setBirthDate(event.target.value)}
									style={{ maxWidth: "10rem" }}
								/>
							</label>
							<div className="button-row" style={{ marginTop: "0.4rem" }}>
								<button type="submit" disabled={savingBirthday}>
									{savingBirthday ? "Guardando..." : "Guardar cumpleaños"}
								</button>
								{birthdayNotice && <span className="notice">{birthdayNotice}</span>}
							</div>
						</form>
					)}

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

					{tenant.loyaltyRewardsEnabled && (
						<div style={{ marginTop: "1.2rem" }}>
							<p className="label">Recompensas</p>
							<p>
								<span className="badge">{client.loyaltyPoints} pts</span>
							</p>
							{eligibleTiers.length === 0 ? (
								<p className="muted">Todavía no llega a los puntos de ninguna recompensa.</p>
							) : (
								<div style={{ display: "flex", alignItems: "center", gap: "0.5rem", flexWrap: "wrap" }}>
									{eligibleTiers.length > 1 ? (
										<select value={tierIdToRedeem} onChange={(event) => setSelectedTierId(event.target.value)}>
											{eligibleTiers.map((t) => (
												<option key={t.id} value={t.id}>
													{t.pointsRequired} pts — {t.description}
												</option>
											))}
										</select>
									) : (
										<span className="muted">{eligibleTiers[0].description}</span>
									)}
									<button type="button" className="secondary" onClick={handleRedeem} disabled={redeeming}>
										{redeeming ? "Canjeando..." : "Canjear"}
									</button>
								</div>
							)}
						</div>
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
										{tenantLongDateTimeLabel(v.startTime, tenant.timezone)} · {v.serviceName} con {v.professionalName}
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
