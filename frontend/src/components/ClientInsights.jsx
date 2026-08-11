import { useEffect, useState } from "react";
import { api } from "../api.js";

const TOP_N = 10;

/**
 * Ranks by `metricKey` (completedCount/cancelledCount/noShowCount) and always fills up to TOP_N
 * rows so the card isn't left half-empty on a young tenant — clients that don't qualify (metric
 * is 0) are used as filler, worst-rated first, since these are all "keep an eye on" lists where a
 * low rating is relevant context even without a completed/cancelled/no-show event yet.
 */
function topByMetric(stats, metricKey) {
	const qualifying = [...stats].filter((c) => c[metricKey] > 0).sort((a, b) => b[metricKey] - a[metricKey]);
	if (qualifying.length >= TOP_N) return qualifying.slice(0, TOP_N);
	const usedIds = new Set(qualifying.map((c) => c.clientId));
	const filler = [...stats]
		.filter((c) => !usedIds.has(c.clientId))
		.sort((a, b) => a.rating - b.rating)
		.slice(0, TOP_N - qualifying.length);
	return [...qualifying, ...filler];
}

/**
 * "Clientes" section of Turnos → Lista: the loyalty ranking, who comes back, who cancels a lot,
 * who no-shows — surfaced so the owner can spot patterns (who to offer a promo to, who to
 * double-check before holding a slot) without cross-referencing the raw appointment list by hand.
 * Backed by GET /api/reports/clients (rolls up every appointment per client across the tenant's
 * whole history, not just the current status filter) and the tenant's own ranking settings
 * (threshold/count, edited right here via "Editar" — see handleSaveSettings).
 */
export default function ClientInsights() {
	const [stats, setStats] = useState(null);
	const [tenant, setTenant] = useState(null);
	const [error, setError] = useState("");

	async function refresh() {
		try {
			const [clientStats, tenantInfo] = await Promise.all([api.reports.clients(), api.tenant.get()]);
			setStats(clientStats);
			setTenant(tenantInfo);
		} catch (err) {
			setError(err.message);
		}
	}

	useEffect(() => {
		refresh();
	}, []);

	return (
		<section className="client-section">
			<h2>Clientes</h2>
			{error && <p className="error">{error}</p>}
			{!stats || !tenant ? (
				<p className="muted">Cargando estadísticas de clientes...</p>
			) : stats.length === 0 ? (
				<p className="muted">Todavía no hay historial de clientes.</p>
			) : (
				<ClientInsightLists stats={stats} tenant={tenant} onChange={refresh} />
			)}
		</section>
	);
}

function ClientInsightLists({ stats, tenant, onChange }) {
	const [editing, setEditing] = useState(false);

	const pinnedClients = [...stats].filter((c) => c.pinned).sort((a, b) => b.rating - a.rating);
	const pinnedIds = new Set(pinnedClients.map((c) => c.clientId));
	const naturallyRanked = [...stats]
		.filter((c) => !pinnedIds.has(c.clientId) && c.rating > tenant.topClientsThreshold)
		.sort((a, b) => b.rating - a.rating)
		.slice(0, Math.max(0, tenant.topClientsCount - pinnedClients.length));
	const topRated = [...pinnedClients, ...naturallyRanked];

	const topFrequent = topByMetric(stats, "completedCount");
	const topCancellers = topByMetric(stats, "cancelledCount");
	const topNoShows = topByMetric(stats, "noShowCount");

	return (
		<div className="cards">
			<div className="card card-span-full">
				<div className="card-header">
					<p className="label" style={{ margin: 0 }}>
						Mejores clientes <span className="muted">(calificación mayor a {tenant.topClientsThreshold})</span>
					</p>
					<button type="button" className="link-button" onClick={() => setEditing((v) => !v)}>
						{editing ? "Listo" : "Editar"}
					</button>
				</div>

				{editing && <TopClientsEditor tenant={tenant} onChange={onChange} />}

				{topRated.length === 0 ? (
					<p className="muted">
						Todavía nadie superó los {tenant.topClientsThreshold} puntos (se gana +1 por cada turno marcado
						"Completado"), y no fijaste ningún cliente a mano.
					</p>
				) : (
					<div className="client-insight-scroll">
						<ClientList clients={topRated} countKey="rating" countLabel="puntos" showPinned />
					</div>
				)}
			</div>
			<div className="card">
				<p className="label">Clientes frecuentes</p>
				<div className="client-insight-scroll">
					<ClientList clients={topFrequent} countKey="completedCount" countLabel="completados" />
				</div>
			</div>
			<div className="card">
				<p className="label">Cancelan seguido</p>
				<div className="client-insight-scroll">
					<ClientList clients={topCancellers} countKey="cancelledCount" countLabel="cancelados" />
				</div>
			</div>
			<div className="card">
				<p className="label">No aparecen</p>
				<div className="client-insight-scroll">
					<ClientList clients={topNoShows} countKey="noShowCount" countLabel="ausencias" tone="danger" />
				</div>
			</div>
		</div>
	);
}

/** Inline "Editar" panel for the Mejores clientes card: the threshold/count knobs (same ones
 * TenantPage edits) plus a search box to pin/unpin a "cliente fijo" — a loyal regular the owner
 * wants featured regardless of their calculated rating (e.g. history from before this system). */
function TopClientsEditor({ tenant, onChange }) {
	const [threshold, setThreshold] = useState(tenant.topClientsThreshold);
	const [count, setCount] = useState(tenant.topClientsCount);
	const [settingsError, setSettingsError] = useState("");
	const [settingsNotice, setSettingsNotice] = useState("");
	const [query, setQuery] = useState("");
	const [results, setResults] = useState([]);
	const [searching, setSearching] = useState(false);

	async function handleSaveSettings(event) {
		event.preventDefault();
		setSettingsError("");
		setSettingsNotice("");
		try {
			await api.tenant.updateClientRanking(Number(threshold), Number(count));
			setSettingsNotice("Guardado.");
			onChange();
		} catch (err) {
			setSettingsError(err.message);
		}
	}

	useEffect(() => {
		if (!query.trim()) {
			setResults([]);
			return;
		}
		setSearching(true);
		const timeout = setTimeout(() => {
			api.clients
				.search(query.trim())
				.then(setResults)
				.catch(() => setResults([]))
				.finally(() => setSearching(false));
		}, 300);
		return () => clearTimeout(timeout);
	}, [query]);

	async function togglePin(client) {
		await api.clients.setPinned(client.id, !client.pinned);
		setResults((prev) => prev.map((c) => (c.id === client.id ? { ...c, pinned: !c.pinned } : c)));
		onChange();
	}

	return (
		<div className="top-clients-editor">
			<form className="inline-form small" onSubmit={handleSaveSettings}>
				<label>
					Calificación mínima
					<input
						type="number"
						min="0"
						value={threshold}
						onChange={(e) => setThreshold(e.target.value)}
						style={{ width: "4.5rem" }}
					/>
				</label>
				<label>
					Máximo a mostrar (1–15)
					<input
						type="number"
						min="1"
						max="15"
						value={count}
						onChange={(e) => setCount(e.target.value)}
						style={{ width: "4.5rem" }}
					/>
				</label>
				<button type="submit">Guardar</button>
				{settingsNotice && <span className="notice">{settingsNotice}</span>}
			</form>
			{settingsError && <p className="error">{settingsError}</p>}

			<p className="label" style={{ marginTop: "1rem" }}>
				Fijar un cliente (siempre visible, sin importar su calificación)
			</p>
			<input
				value={query}
				onChange={(e) => setQuery(e.target.value)}
				placeholder="Buscar por nombre o email..."
			/>
			{searching && <p className="muted">Buscando...</p>}
			{results.length > 0 && (
				<ul className="client-insight-list" style={{ marginTop: "0.6rem" }}>
					{results.map((c) => (
						<li key={c.id}>
							<span className="client-insight-name">
								{c.name} <span className="muted">({c.email})</span> — {c.rating} pts
							</span>
							<button type="button" className="secondary" onClick={() => togglePin(c)}>
								{c.pinned ? "Quitar" : "Fijar"}
							</button>
						</li>
					))}
				</ul>
			)}
		</div>
	);
}

function ClientList({ clients, countKey, countLabel, tone, showPinned }) {
	return (
		<ul className="client-insight-list">
			{clients.map((c) => (
				<li key={c.clientId}>
					<span className="client-insight-name">
						{c.rating === 0 && <span className="rating-flag" title="Calificación en 0" />}
						{showPinned && c.pinned && <span className="pinned-flag" title="Cliente fijo">📌</span>}
						{c.clientName} <span className="muted">({c.clientEmail})</span>
					</span>
					<span className={`badge${tone ? ` badge-${tone}` : ""}`}>
						{c[countKey]} {countLabel}
					</span>
				</li>
			))}
		</ul>
	);
}
