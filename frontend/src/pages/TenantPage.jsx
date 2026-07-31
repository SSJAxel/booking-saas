import { useEffect, useState } from "react";
import { api } from "../api.js";
import { useAuth } from "../auth/AuthContext.jsx";

export default function TenantPage() {
	const [tenant, setTenant] = useState(null);
	const [error, setError] = useState("");
	const { session } = useAuth();

	async function refresh() {
		try {
			setTenant(await api.tenant.get());
		} catch (err) {
			setError(err.message);
		}
	}

	useEffect(() => {
		refresh();
	}, []);

	async function handleChangePlan(planTier) {
		setError("");
		try {
			setTenant(await api.tenant.changePlan(planTier));
		} catch (err) {
			setError(err.message);
		}
	}

	if (!tenant) return <p>Cargando...</p>;

	return (
		<div>
			<h1>Plan del negocio</h1>
			{error && <p className="error">{error}</p>}
			<div className="card">
				<p>
					<strong>{tenant.name}</strong> ({tenant.slug})
				</p>
				<p>
					Plan actual: <span className={`badge badge-${tenant.planTier.toLowerCase()}`}>{tenant.planTier}</span>
				</p>
				{session.role === "OWNER" ? (
					<div className="button-row">
						<button type="button" disabled={tenant.planTier === "BASIC"} onClick={() => handleChangePlan("BASIC")}>
							Pasar a BASIC
						</button>
						<button type="button" disabled={tenant.planTier === "PRO"} onClick={() => handleChangePlan("PRO")}>
							Pasar a PRO
						</button>
					</div>
				) : (
					<p className="muted">Solo el dueño puede cambiar el plan.</p>
				)}
			</div>
		</div>
	);
}
