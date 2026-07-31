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

	async function handleDowngrade() {
		setError("");
		try {
			setTenant(await api.tenant.changePlan("BASIC"));
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleSubscribe() {
		setError("");
		try {
			const { checkoutUrl } = await api.tenant.subscribe("PRO");
			window.location.href = checkoutUrl;
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleConnectMercadoPago() {
		setError("");
		try {
			const { authorizationUrl } = await api.tenant.connectMercadoPago();
			window.location.href = authorizationUrl;
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
						<button type="button" disabled={tenant.planTier === "BASIC"} onClick={handleDowngrade}>
							Pasar a BASIC
						</button>
						<button type="button" disabled={tenant.planTier === "PRO"} onClick={handleSubscribe}>
							Suscribirme a PRO
						</button>
					</div>
				) : (
					<p className="muted">Solo el dueño puede cambiar el plan.</p>
				)}
			</div>

			<p className="label">Cobros</p>
			<div className="card">
				<p className="muted">
					Conectá tu propia cuenta de Mercado Pago para que las señas y la suscripción se cobren directo a tu
					cuenta, en vez de una cuenta compartida de la plataforma.
				</p>
				{session.role === "OWNER" ? (
					<button type="button" onClick={handleConnectMercadoPago}>
						Conectar Mercado Pago
					</button>
				) : (
					<p className="muted">Solo el dueño puede conectar la cuenta.</p>
				)}
			</div>
		</div>
	);
}
