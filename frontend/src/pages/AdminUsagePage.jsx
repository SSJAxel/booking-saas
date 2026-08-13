import { useEffect, useState } from "react";
import { api } from "../api.js";
import { planLabel } from "../labels.js";

/** Ranked by revenue (last 30 days, see PlatformAdminService#tenantUsageRanking) — the highest
 * bars are the best upsell candidates: heavy usage on a lower tier. */
export default function AdminUsagePage() {
	const [usage, setUsage] = useState([]);
	const [error, setError] = useState("");
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		api.admin
			.tenantsUsage()
			.then(setUsage)
			.catch((err) => setError(err.message))
			.finally(() => setLoading(false));
	}, []);

	return (
		<div>
			<h1>Uso por cuenta</h1>
			<p className="muted">Recursos cargados y actividad de los últimos 30 días.</p>
			{error && <p className="error">{error}</p>}
			{loading ? (
				<p>Cargando...</p>
			) : usage.length === 0 ? (
				<p className="muted">No hay cuentas todavía.</p>
			) : (
				<table>
					<thead>
						<tr>
							<th>Negocio</th>
							<th>Plan</th>
							<th>Profesionales</th>
							<th>Sucursales</th>
							<th>Stock</th>
							<th>Servicios</th>
							<th>Turnos completados</th>
							<th>Ingresos generados</th>
						</tr>
					</thead>
					<tbody>
						{usage.map((u) => (
							<tr key={u.tenantId}>
								<td>{u.name}</td>
								<td>
									<span className={`badge badge-${u.planTier.toLowerCase()}`}>{planLabel(u.planTier)}</span>
								</td>
								<td>{u.professionalCount}</td>
								<td>{u.branchCount}</td>
								<td>{u.stockUnits}</td>
								<td>{u.serviceCount}</td>
								<td>{u.appointmentCount}</td>
								<td>${Number(u.revenue).toLocaleString("es-AR")}</td>
							</tr>
						))}
					</tbody>
				</table>
			)}
		</div>
	);
}
