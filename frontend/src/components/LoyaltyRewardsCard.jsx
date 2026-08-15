import { useEffect, useState } from "react";
import { api } from "../api.js";
import { TrashIcon } from "./icons.jsx";

/**
 * "Recompensas" card inside Turnos → Lista → Clientes — own file since ClientInsights.jsx is
 * already large. Only ever rendered when planHasLoyaltyRewards(tenant.planTier) is true (see
 * ClientInsights.jsx); if the tenant hasn't flipped the "Activar" switch yet, this is the only
 * place to do so, so it still renders (with the settings editor, just no eligible-clients list).
 */
export default function LoyaltyRewardsCard({ stats, tenant, onChange }) {
	const [tiers, setTiers] = useState(null);
	const [error, setError] = useState("");
	const [editing, setEditing] = useState(false);

	async function refreshTiers() {
		try {
			setTiers(await api.loyaltyTiers.list());
		} catch (err) {
			setError(err.message);
		}
	}

	useEffect(() => {
		refreshTiers();
	}, []);

	function handleChange() {
		onChange();
		refreshTiers();
	}

	const eligibleClients = tiers
		? [...stats]
				.filter((c) => tiers.some((t) => c.loyaltyPoints >= t.pointsRequired))
				.sort((a, b) => b.loyaltyPoints - a.loyaltyPoints)
		: [];

	return (
		<div className="card card-span-full">
			<div className="card-header">
				<p className="label" style={{ margin: 0 }}>
					Recompensas
				</p>
				<button type="button" className="link-button" onClick={() => setEditing((v) => !v)}>
					{editing ? "Listo" : "Editar"}
				</button>
			</div>

			{error && <p className="error">{error}</p>}

			{editing && <LoyaltyRewardsEditor tenant={tenant} tiers={tiers} onChange={handleChange} />}

			{!tenant.loyaltyRewardsEnabled ? (
				<p className="muted">
					Las recompensas están desactivadas. Activalas desde "Editar" para que los clientes empiecen a sumar
					puntos.
				</p>
			) : !tiers ? (
				<p className="muted">Cargando...</p>
			) : tiers.length === 0 ? (
				<p className="muted">Todavía no definiste ninguna recompensa — hacelo desde "Editar".</p>
			) : eligibleClients.length === 0 ? (
				<p className="muted">Todavía nadie llegó a los puntos necesarios para alguna recompensa.</p>
			) : (
				<div className="client-insight-scroll">
					<ul className="client-insight-list">
						{eligibleClients.map((c) => (
							<li key={c.clientId}>
								<span className="client-insight-name">
									{c.clientName} <span className="muted">({c.clientEmail})</span>
								</span>
								<RedeemControls
									client={c}
									eligibleTiers={tiers.filter((t) => c.loyaltyPoints >= t.pointsRequired)}
									onRedeemed={onChange}
								/>
							</li>
						))}
					</ul>
				</div>
			)}
		</div>
	);
}

function RedeemControls({ client, eligibleTiers, onRedeemed }) {
	const [selectedTierId, setSelectedTierId] = useState(eligibleTiers[0]?.id ?? "");
	const [redeeming, setRedeeming] = useState(false);
	const [error, setError] = useState("");

	async function handleRedeem() {
		setRedeeming(true);
		setError("");
		try {
			await api.clients.redeemReward(client.clientId, selectedTierId);
			onRedeemed();
		} catch (err) {
			setError(err.message);
		} finally {
			setRedeeming(false);
		}
	}

	return (
		<div style={{ display: "flex", alignItems: "center", gap: "0.5rem", flexWrap: "wrap" }}>
			<span className="badge">{client.loyaltyPoints} pts</span>
			{eligibleTiers.length > 1 ? (
				<select value={selectedTierId} onChange={(event) => setSelectedTierId(event.target.value)}>
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
			{error && <span className="error">{error}</span>}
		</div>
	);
}

/** Inline "Editar" panel: activar/desactivar + tope de puntos, más el CRUD de hasta 5 niveles de
 * recompensa — todo manual, nada se genera solo (ver RewardTier.java's Javadoc). */
function LoyaltyRewardsEditor({ tenant, tiers, onChange }) {
	const [enabled, setEnabled] = useState(tenant.loyaltyRewardsEnabled);
	const [pointsCap, setPointsCap] = useState(tenant.loyaltyPointsCap);
	const [settingsError, setSettingsError] = useState("");
	const [settingsNotice, setSettingsNotice] = useState("");
	const [newPoints, setNewPoints] = useState("");
	const [newDescription, setNewDescription] = useState("");
	const [tierError, setTierError] = useState("");

	async function handleSaveSettings(event) {
		event.preventDefault();
		setSettingsError("");
		setSettingsNotice("");
		try {
			await api.tenant.updateLoyaltyRewards(enabled, Number(pointsCap));
			setSettingsNotice("Guardado.");
			onChange();
		} catch (err) {
			setSettingsError(err.message);
		}
	}

	async function handleAddTier(event) {
		event.preventDefault();
		setTierError("");
		try {
			await api.loyaltyTiers.create({ pointsRequired: Number(newPoints), description: newDescription.trim() });
			setNewPoints("");
			setNewDescription("");
			onChange();
		} catch (err) {
			setTierError(err.message);
		}
	}

	async function handleDeleteTier(id) {
		await api.loyaltyTiers.delete(id);
		onChange();
	}

	const tierCount = tiers?.length ?? 0;

	return (
		<div className="top-clients-editor">
			<form className="inline-form small" onSubmit={handleSaveSettings}>
				<label style={{ flexDirection: "row", alignItems: "center", gap: "0.5rem" }}>
					<input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} />
					Activar recompensas
				</label>
				<label>
					Puntos acumulables como máximo
					<input
						type="number"
						min="5"
						max="200"
						value={pointsCap}
						onChange={(event) => setPointsCap(event.target.value)}
						style={{ width: "5rem" }}
					/>
				</label>
				<button type="submit">Guardar</button>
				{settingsNotice && <span className="notice">{settingsNotice}</span>}
			</form>
			{settingsError && <p className="error">{settingsError}</p>}
			<p className="muted" style={{ marginTop: "0.6rem" }}>
				Cada turno completado le suma 1 punto al cliente. Hasta {pointsCap} puntos acumulables — después
				necesita canjear una recompensa para poder seguir sumando.
			</p>

			<p className="label" style={{ marginTop: "1rem" }}>
				Recompensas ({tierCount}/5)
			</p>
			{tierCount > 0 && (
				<ul className="client-insight-list">
					{tiers.map((t) => (
						<li key={t.id}>
							<span className="client-insight-name">
								{t.pointsRequired} pts — {t.description}
							</span>
							<button type="button" className="secondary" onClick={() => handleDeleteTier(t.id)}>
								<TrashIcon />
								Eliminar
							</button>
						</li>
					))}
				</ul>
			)}
			{tierCount < 5 && (
				<form className="inline-form small" onSubmit={handleAddTier} style={{ marginTop: "0.6rem" }}>
					<label>
						Puntos
						<input
							type="number"
							min="1"
							value={newPoints}
							onChange={(event) => setNewPoints(event.target.value)}
							style={{ width: "4.5rem" }}
							required
						/>
					</label>
					<label style={{ flex: 1 }}>
						Recompensa
						<input
							value={newDescription}
							onChange={(event) => setNewDescription(event.target.value)}
							placeholder="ej: 10% de descuento"
							required
						/>
					</label>
					<button type="submit">Agregar</button>
				</form>
			)}
			{tierError && <p className="error">{tierError}</p>}
		</div>
	);
}
