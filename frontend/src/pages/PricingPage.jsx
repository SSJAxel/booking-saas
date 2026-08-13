import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api.js";
import { planLabel } from "../labels.js";
import RegisterForm from "../components/RegisterForm.jsx";

const FOUNDER_EMAIL = "info.capibyte@gmail.com";

// Cuánto más caro es cada profesional extra que el anterior, compuesto (no un precio plano por
// unidad) — distinto por plan a pedido: BASIC 10%, PRO 15%, MAX 25%. TRIAL/PERSONAL no tienen
// (no tienen extraProfessionalPrice en el backend, no se permite pedir más profesionales ahí).
const EXTRA_GROWTH_RATE = { BASIC: 0.1, PRO: 0.15, MAX: 0.25 };

// Techo absoluto de profesionales por tenant, en cualquier plan — un negocio que necesite más no
// es un caso que se resuelva desde esta página.
const MAX_PROFESSIONALS_CAP = 20;

// En esta página (solo acá, no cambia los límites reales del backend para tenants ya creados),
// BASIC/PRO/MAX arrancan todos con el mismo "piso" de 2 profesionales incluidos — lo que
// diferencia el costo entre planes es la tasa de crecimiento por profesional extra
// (EXTRA_GROWTH_RATE), no cuántos vienen incluidos de entrada. PERSONAL/TRIAL no tienen precio
// por extra, siguen usando su propio incluido real (1 y 2 respectivamente).
const PRICING_BASE_PROFESSIONALS = 2;

function baseProfessionals(plan) {
	return plan.extraProfessionalPrice ? PRICING_BASE_PROFESSIONALS : plan.maxProfessionals;
}

export default function PricingPage() {
	const [plans, setPlans] = useState([]);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState("");
	// Cuántos profesionales eligió el visitante por plan (tier: cantidad) — arranca en el
	// incluido de cada plan.
	const [professionalCounts, setProfessionalCounts] = useState({});

	useEffect(() => {
		api.tenant
			.plans()
			.then((data) => {
				setPlans(data);
				setProfessionalCounts(Object.fromEntries(data.map((p) => [p.tier, baseProfessionals(p)])));
			})
			.catch((err) => setError(err.message))
			.finally(() => setLoading(false));
	}, []);

	function setCount(tier, count) {
		setProfessionalCounts((prev) => ({ ...prev, [tier]: count }));
	}

	function totalExtrasCost(plan, extraCount) {
		const rate = EXTRA_GROWTH_RATE[plan.tier];
		const base = Number(plan.extraProfessionalPrice);
		let total = 0;
		for (let i = 0; i < extraCount; i++) {
			total += base * Math.pow(1 + rate, i);
		}
		return total;
	}

	function totalPrice(plan) {
		if (plan.monthlyPrice === null) return null;
		const count = professionalCounts[plan.tier] ?? baseProfessionals(plan);
		const extra = Math.max(0, count - baseProfessionals(plan));
		const extraCost = plan.extraProfessionalPrice ? totalExtrasCost(plan, extra) : 0;
		return Number(plan.monthlyPrice) + extraCost;
	}

	function priceLabel(plan) {
		const total = totalPrice(plan);
		if (total === null) return "Próximamente";
		return total === 0 ? "Gratis" : `$${Math.round(total).toLocaleString("es-AR")}/mes`;
	}

	function contactHref(plan) {
		const count = professionalCounts[plan.tier] ?? baseProfessionals(plan);
		const subject = `Quiero activar el plan ${planLabel(plan.tier)}`;
		const body =
			`Hola, quiero activar mi cuenta en el plan ${planLabel(plan.tier)} ` +
			`con ${count} profesional${count === 1 ? "" : "es"} (${priceLabel(plan)}).\n\n` +
			"Mi negocio (identificador/slug): \n" +
			"Adjunto el comprobante de pago.";
		return `mailto:${FOUNDER_EMAIL}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
	}

	return (
		<div className="pricing-page">
			<header className="pricing-hero">
				<h1>booking-saas</h1>
				<p className="muted">
					Reservas online para tu negocio de servicios — turnos, señas, recordatorios, todo en un solo panel.
				</p>
				<p className="muted">
					<Link to="/login">¿Ya tenés cuenta? Ingresá acá</Link>
				</p>
			</header>

			{loading && <p className="muted">Cargando planes...</p>}
			{error && <p className="error">{error}</p>}

			{!loading && !error && (
				<div className="pricing-grid">
					{plans.map((plan) => {
						const canPickCount = Boolean(plan.extraProfessionalPrice) && plan.monthlyPrice !== null;
						const included = baseProfessionals(plan);
						const count = professionalCounts[plan.tier] ?? included;
						const rate = EXTRA_GROWTH_RATE[plan.tier];
						return (
							<div
								className={`pricing-card${plan.tier === "PRO" ? " pricing-card-featured" : ""}`}
								key={plan.tier}
							>
								{plan.tier === "PRO" && <p className="pricing-badge">Más elegido</p>}
								<h3>{planLabel(plan.tier)}</h3>
								<p className="pricing-price">{priceLabel(plan)}</p>

								{canPickCount ? (
									<label className="pricing-count">
										Profesionales
										<div className="pricing-count-stepper">
											<button
												type="button"
												disabled={count <= included}
												onClick={() => setCount(plan.tier, Math.max(included, count - 1))}
											>
												−
											</button>
											<span>{count}</span>
											<button
												type="button"
												disabled={count >= MAX_PROFESSIONALS_CAP}
												onClick={() => setCount(plan.tier, Math.min(MAX_PROFESSIONALS_CAP, count + 1))}
											>
												+
											</button>
										</div>
										<span className="muted">
											{included} incluido{included === 1 ? "" : "s"} · cada extra +{Math.round(rate * 100)}% ·
											máximo {MAX_PROFESSIONALS_CAP}
										</span>
									</label>
								) : (
									<p className="muted pricing-count-fixed">
										{included} profesional{included === 1 ? "" : "es"} incluido{included === 1 ? "" : "s"}, sin
										extras en este plan
									</p>
								)}

								<ul className="pricing-features">
									<li>
										Hasta {plan.maxBranches} sucursal{plan.maxBranches === 1 ? "" : "es"}
									</li>
									<li>Hasta {plan.maxServices} servicios</li>
									<li>
										{plan.maxProducts === 0
											? "Sin stock/productos"
											: plan.maxProducts
												? `Hasta ${plan.maxProducts} productos`
												: "Productos ilimitados"}
									</li>
									<li>
										{plan.maxAppointmentsPerWeek
											? `Hasta ${plan.maxAppointmentsPerWeek} turnos por semana`
											: "Turnos ilimitados"}
									</li>
									<li>{plan.mercadoPagoEnabled ? "Cobrá señas con Mercado Pago" : "Señas solo por transferencia"}</li>
									<li>{plan.whatsappEnabled ? "Avisos por WhatsApp" : "Solo avisos por mail"}</li>
								</ul>

								{plan.monthlyPrice !== null && Number(plan.monthlyPrice) > 0 && (
									<a className="pricing-contact" href={contactHref(plan)}>
										Quiero este plan →
									</a>
								)}
							</div>
						);
					})}
				</div>
			)}

			<p className="muted pricing-manual-note">
				Los planes pagos se activan a mano hoy: elegí tu plan y cantidad de profesionales arriba, escribinos a{" "}
				<a href={`mailto:${FOUNDER_EMAIL}`}>{FOUNDER_EMAIL}</a> con el comprobante de pago, y te habilitamos la
				cuenta.
			</p>

			<div className="pricing-signup" id="registrarse">
				<div className="auth-card">
					<h2>Empezá gratis</h2>
					<p className="muted">
						Creá tu negocio ahora mismo — arranca en el plan Demo, sin tarjeta. Cuando quieras un plan pago,
						elegilo arriba y escribinos.
					</p>
					<RegisterForm />
				</div>
			</div>
		</div>
	);
}
