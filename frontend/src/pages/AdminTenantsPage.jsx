import { useEffect, useMemo, useState } from "react";
import { api } from "../api.js";
import { planLabel, subscriptionStatusLabel } from "../labels.js";
import StatTile from "../components/StatTile.jsx";
import TenantDetailModal from "../components/TenantDetailModal.jsx";

const PLAN_TIERS = ["TRIAL", "PERSONAL", "BASIC", "PRO", "MAX"];
const STATUSES = ["PENDING_APPROVAL", "ACTIVE", "SUSPENDED"];
const STATUS_LABELS = { PENDING_APPROVAL: "Pendiente", ACTIVE: "Activo", SUSPENDED: "Suspendido" };

function statusChip(tenant) {
	if (tenant.planTier === "TRIAL") return { label: "Demo", className: "badge-pending" };
	if (tenant.daysRemaining === null) return { label: "Sin vencimiento cargado", className: "badge-pending" };
	if (tenant.daysRemaining < 0) return { label: "Atrasado", className: "badge-cancelled" };
	return { label: "Al día", className: "badge-confirmed" };
}

function todayIsoDate() {
	return new Date().toISOString().slice(0, 10);
}

function firstDayOfMonthIsoDate() {
	const d = new Date();
	return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10);
}

export default function AdminTenantsPage() {
	const [tenants, setTenants] = useState([]);
	const [mrr, setMrr] = useState(null);
	const [error, setError] = useState("");
	const [loading, setLoading] = useState(true);
	const [detailTenant, setDetailTenant] = useState(null);

	const [query, setQuery] = useState("");
	const [statusFilter, setStatusFilter] = useState("");
	const [planFilter, setPlanFilter] = useState("");
	const [overdueOnly, setOverdueOnly] = useState(false);

	const [exportFrom, setExportFrom] = useState(firstDayOfMonthIsoDate());
	const [exportTo, setExportTo] = useState(todayIsoDate());
	const [exporting, setExporting] = useState(false);

	useEffect(() => {
		Promise.all([api.admin.tenants(), api.admin.mrr()])
			.then(([t, m]) => {
				setTenants(t);
				setMrr(m);
			})
			.catch((err) => setError(err.message))
			.finally(() => setLoading(false));
	}, []);

	function replaceTenant(updated) {
		setTenants((prev) => prev.map((t) => (t.tenantId === updated.tenantId ? updated : t)));
		if (detailTenant?.tenantId === updated.tenantId) setDetailTenant(updated);
	}

	async function handlePlanChange(tenantId, planTier) {
		setError("");
		try {
			replaceTenant(await api.admin.updateTenantPlan(tenantId, planTier));
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleDueDateChange(tenantId, value) {
		setError("");
		try {
			replaceTenant(await api.admin.updateTenantNextPaymentDue(tenantId, value || null));
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleApprove(tenantId) {
		setError("");
		try {
			replaceTenant(await api.admin.approveTenant(tenantId));
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleCustomPriceChange(tenantId, value) {
		setError("");
		try {
			const price = value === "" ? null : Number(value);
			replaceTenant(await api.admin.updateTenantCustomPrice(tenantId, price));
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleProfessionalLimitChange(tenantId, value) {
		setError("");
		try {
			const limit = value === "" ? null : Number(value);
			replaceTenant(await api.admin.updateTenantProfessionalLimit(tenantId, limit));
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleExport() {
		setError("");
		setExporting(true);
		try {
			const blob = await api.admin.billingReportBlob(exportFrom, exportTo);
			const url = URL.createObjectURL(blob);
			const a = document.createElement("a");
			a.href = url;
			a.download = `facturacion_${exportFrom}_${exportTo}.csv`;
			a.click();
			URL.revokeObjectURL(url);
		} catch (err) {
			setError(err.message);
		} finally {
			setExporting(false);
		}
	}

	const filteredTenants = useMemo(() => {
		const q = query.trim().toLowerCase();
		return tenants.filter((t) => {
			if (q && !t.name.toLowerCase().includes(q) && !t.slug.toLowerCase().includes(q)) return false;
			if (statusFilter && t.status !== statusFilter) return false;
			if (planFilter && t.planTier !== planFilter) return false;
			if (overdueOnly && !(t.daysRemaining !== null && t.daysRemaining < 0)) return false;
			return true;
		});
	}, [tenants, query, statusFilter, planFilter, overdueOnly]);

	return (
		<div>
			<h1>Cuentas</h1>
			{error && <p className="error">{error}</p>}

			{mrr && (
				<div className="cards">
					<StatTile
						iconBg="var(--accent-soft)"
						iconColor="var(--accent)"
						value={`$${Number(mrr.totalMrr).toLocaleString("es-AR")}`}
						label="MRR total"
						title="MRR = ingreso mensual recurrente: suma del precio efectivo de cada cuenta Activa. Ver Manual para el detalle."
					/>
					<StatTile
						iconBg="var(--ok-bg)"
						iconColor="var(--ok-text)"
						value={`$${Number(mrr.mrrByPlan.PRO ?? 0).toLocaleString("es-AR")}`}
						label={`MRR PRO (${mrr.tenantCountByPlan.PRO ?? 0} cuentas)`}
					/>
					<StatTile
						iconBg="var(--warning-bg)"
						iconColor="var(--warning)"
						value={`$${Number(mrr.mrrByPlan.MAX ?? 0).toLocaleString("es-AR")}`}
						label={`MRR MAX (${mrr.tenantCountByPlan.MAX ?? 0} cuentas)`}
					/>
				</div>
			)}

			<div className="filters">
				<label>
					Buscar
					<input
						type="text"
						placeholder="Nombre o slug..."
						value={query}
						onChange={(event) => setQuery(event.target.value)}
					/>
				</label>
				<label>
					Estado
					<select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
						<option value="">Todos</option>
						{STATUSES.map((s) => (
							<option key={s} value={s}>
								{STATUS_LABELS[s]}
							</option>
						))}
					</select>
				</label>
				<label>
					Plan
					<select value={planFilter} onChange={(event) => setPlanFilter(event.target.value)}>
						<option value="">Todos</option>
						{PLAN_TIERS.map((tier) => (
							<option key={tier} value={tier}>
								{planLabel(tier)}
							</option>
						))}
					</select>
				</label>
				<label>
					<input type="checkbox" checked={overdueOnly} onChange={(event) => setOverdueOnly(event.target.checked)} />
					Solo en mora
				</label>
			</div>

			<div className="filters">
				<label>
					Desde
					<input type="date" value={exportFrom} onChange={(event) => setExportFrom(event.target.value)} />
				</label>
				<label>
					Hasta
					<input type="date" value={exportTo} onChange={(event) => setExportTo(event.target.value)} />
				</label>
				<button type="button" className="secondary" onClick={handleExport} disabled={exporting}>
					{exporting ? "Exportando..." : "Exportar facturación"}
				</button>
			</div>

			{loading ? (
				<p>Cargando...</p>
			) : filteredTenants.length === 0 ? (
				<p className="muted">No hay cuentas que coincidan con el filtro.</p>
			) : (
				<table>
					<thead>
						<tr>
							<th>Negocio</th>
							<th>Slug</th>
							<th>Profesionales</th>
							<th>Límite</th>
							<th>Aprobación</th>
							<th>Plan</th>
							<th>Precio</th>
							<th>Suscripción</th>
							<th>Vence</th>
							<th>Días restantes</th>
							<th>Estado</th>
						</tr>
					</thead>
					<tbody>
						{filteredTenants.map((t) => {
							const chip = statusChip(t);
							return (
								<tr key={t.tenantId}>
									<td>
										<button type="button" className="link-button" onClick={() => setDetailTenant(t)}>
											{t.name}
										</button>
									</td>
									<td>{t.slug}</td>
									<td>{t.professionalCount}</td>
									<td>
										<input
											type="number"
											min="0"
											step="1"
											placeholder={t.effectiveProfessionalLimit}
											value={t.professionalLimitOverride ?? ""}
											onChange={(event) => handleProfessionalLimitChange(t.tenantId, event.target.value)}
											style={{ width: "4.5rem" }}
											title="Empleados habilitados — vacío usa el incluido por defecto del plan"
										/>
									</td>
									<td>
										{t.status === "PENDING_APPROVAL" ? (
											<button type="button" onClick={() => handleApprove(t.tenantId)}>
												Aprobar
											</button>
										) : t.status === "ACTIVE" ? (
											<span className="muted">Activo</span>
										) : (
											<span className="muted">Suspendido</span>
										)}
									</td>
									<td>
										<select value={t.planTier} onChange={(event) => handlePlanChange(t.tenantId, event.target.value)}>
											{PLAN_TIERS.map((tier) => (
												<option key={tier} value={tier}>
													{planLabel(tier)}
												</option>
											))}
										</select>
									</td>
									<td>
										<input
											type="number"
											step="0.01"
											placeholder={t.effectiveMonthlyPrice ?? "—"}
											value={t.customMonthlyPrice ?? ""}
											onChange={(event) => handleCustomPriceChange(t.tenantId, event.target.value)}
											style={{ width: "6.5rem" }}
											title="Precio negociado — vacío usa el precio de lista del plan"
										/>
									</td>
									<td>{subscriptionStatusLabel(t.subscriptionStatus)}</td>
									<td>
										<input
											type="date"
											value={t.nextPaymentDueAt ?? ""}
											onChange={(event) => handleDueDateChange(t.tenantId, event.target.value)}
										/>
									</td>
									<td>{t.daysRemaining ?? "—"}</td>
									<td>
										<span className={`badge ${chip.className}`}>{chip.label}</span>
									</td>
								</tr>
							);
						})}
					</tbody>
				</table>
			)}

			<TenantDetailModal tenant={detailTenant} onClose={() => setDetailTenant(null)} onApprove={handleApprove} />
		</div>
	);
}
