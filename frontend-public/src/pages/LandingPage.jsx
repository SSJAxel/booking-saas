import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api.js";

const PLAN_COPY = {
	TRIAL: {
		label: "Prueba",
		tagline: "Todo el negocio arranca acá, gratis",
		features: ["Todas las funciones activas", "Sin límite de productos"],
	},
	BASIC: {
		label: "Básico",
		tagline: "Para arrancar sin vueltas",
		features: ["Agenda y reservas online", "Hasta 5 productos en stock", "Notificaciones por mail"],
	},
	PRO: {
		label: "Pro",
		tagline: "Para cuando el negocio crece",
		features: ["Todo lo del plan Básico", "Productos sin límite", "Soporte prioritario"],
	},
	MAX: {
		label: "Max",
		tagline: "Para negocios grandes — precio a definir",
		features: [],
	},
};

/** null means "no price set yet" (see PlanTier.MAX) — distinct from 0 (genuinely free). Number(null)
 * is 0 in JS, so this has to be checked before the free case, not folded into it. */
function formatPrice(amount) {
	if (amount === null) return "Próximamente";
	if (Number(amount) === 0) return "Gratis";
	return `$${Number(amount).toLocaleString("es-AR")}/mes`;
}

export default function LandingPage() {
	const [plans, setPlans] = useState([]);
	const [error, setError] = useState("");

	useEffect(() => {
		api.getPlans()
			.then(setPlans)
			.catch((err) => setError(err.message));
	}, []);

	return (
		<div className="page">
			<header className="hero">
				<h1>Agenda y turnos para tu negocio</h1>
				<p className="muted">
					Reservas online, sin doble booking, con recordatorios automáticos. Elegí un plan y armá tu
					negocio en minutos.
				</p>
			</header>

			{error && <p className="error">{error}</p>}

			<div className="plan-grid">
				{plans.map((plan) => {
					const copy = PLAN_COPY[plan.tier] ?? { label: plan.tier, tagline: "", features: [] };
					return (
						<div key={plan.tier} className={`plan-card ${plan.tier === "PRO" ? "plan-card-highlight" : ""}`}>
							<span className="label">{copy.label}</span>
							<p className="plan-price">{formatPrice(plan.monthlyPrice)}</p>
							<p className="muted">{copy.tagline}</p>
							<ul className="plan-features">
								{copy.features.map((f) => (
									<li key={f}>{f}</li>
								))}
							</ul>
							{plan.monthlyPrice === null ? (
								<span className="button-link button-link-disabled">Próximamente</span>
							) : (
								<Link to={`/registrarse?plan=${plan.tier}`} className="button-link">
									Crear mi negocio
								</Link>
							)}
						</div>
					);
				})}
			</div>

			<p className="muted footer-note">
				¿Ya tenés una cuenta? Entrá directo con el link de tu negocio (ej: <code>/tu-negocio</code>) o al{" "}
				<a href="http://localhost:5180/login">panel de administración</a>.
			</p>
		</div>
	);
}
