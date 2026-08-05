import { useEffect, useState } from "react";
import { api } from "../api.js";

const PLAN_TIERS = ["TRIAL", "BASIC", "PRO", "MAX"];

function statusChip(tenant) {
	if (tenant.planTier === "TRIAL") return { label: "Prueba", className: "badge-pending" };
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

	return (
		<div>
			<h1>Tenants</h1>
			{error && <p className="error">{error}</p>}
			{loading ? (
				<p>Cargando...</p>
			) : tenants.length === 0 ? (
				<p className="muted">No hay tenants todavía.</p>
			) : (
				<table>
					<thead>
						<tr>
							<th>Tenant</th>
							<th>Slug</th>
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
									<td>
										<select value={t.planTier} onChange={(event) => handlePlanChange(t.tenantId, event.target.value)}>
											{PLAN_TIERS.map((tier) => (
												<option key={tier} value={tier}>
													{tier}
												</option>
											))}
										</select>
									</td>
									<td>{t.subscriptionStatus ?? "—"}</td>
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
