import { useEffect, useState } from "react";
import { api } from "../api.js";
import { planLabel, subscriptionStatusLabel } from "../labels.js";

const PLAN_TIERS = ["TRIAL", "BASIC", "PRO", "MAX"];

function statusChip(tenant) {
	if (tenant.planTier === "TRIAL") return { label: "Demo", className: "badge-pending" };
	if (tenant.daysRemaining === null) return { label: "Sin vencimiento cargado", className: "badge-pending" };
	if (tenant.daysRemaining < 0) return { label: "Atrasado", className: "badge-cancelled" };
	return { label: "Al día", className: "badge-confirmed" };
}

export default function AdminTenantsPage() {
	const [tenants, setTenants] = useState([]);
	const [error, setError] = useState("");
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		api.admin
			.tenants()
			.then(setTenants)
			.catch((err) => setError(err.message))
			.finally(() => setLoading(false));
	}, []);

	function replaceTenant(updated) {
		setTenants((prev) => prev.map((t) => (t.tenantId === updated.tenantId ? updated : t)));
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

	return (
		<div>
			<h1>Cuentas</h1>
			{error && <p className="error">{error}</p>}
			{loading ? (
				<p>Cargando...</p>
			) : tenants.length === 0 ? (
				<p className="muted">No hay cuentas todavía.</p>
			) : (
				<table>
					<thead>
						<tr>
							<th>Negocio</th>
							<th>Slug</th>
							<th>Profesionales</th>
							<th>Aprobación</th>
							<th>Plan</th>
							<th>Suscripción</th>
							<th>Vence</th>
							<th>Días restantes</th>
							<th>Estado</th>
						</tr>
					</thead>
					<tbody>
						{tenants.map((t) => {
							const chip = statusChip(t);
							return (
								<tr key={t.tenantId}>
									<td>{t.name}</td>
									<td>{t.slug}</td>
									<td>{t.professionalCount}</td>
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
		</div>
	);
}
