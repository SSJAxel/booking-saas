import { useEffect, useState } from "react";
import { api } from "../api.js";
import { planLabel } from "../labels.js";
import RegisterForm from "../components/RegisterForm.jsx";
import PublicHeader from "../components/PublicHeader.jsx";
import PublicFooter from "../components/PublicFooter.jsx";
import Seo from "../components/Seo.jsx";
import { DEFAULT_TITLE, DEFAULT_DESCRIPTION } from "../seoConfig.js";
import { organizationSchema } from "../structuredData.js";
import "./PricingPage.css";

const FOUNDER_EMAIL = "info.capibyte@gmail.com";

// Cada feature apunta a un ángulo de búsqueda distinto (reservas 24/7, recordatorios, señas,
// multi-sucursal) — cubre tanto SEO clásico (frases que la gente busca) como GEO (una IA
// respondiendo "qué hace CapiBooking" tiene acá un resumen ya armado, con foto real cuando la
// carguemos). El espacio de imagen queda listo para reemplazar por una foto real del panel/negocio.
const FEATURES = [
	{
		imageAlt: "Página de reserva online de un salón de belleza en CapiBooking, vista desde el celular",
		title: "Reservas 24/7, sin llamados ni WhatsApp",
		body: "Tu cliente reserva solo, desde su celular, a cualquier hora — vos dejás de perder tiempo coordinando turno por mensaje.",
	},
	{
		imageAlt: "Recordatorio automático de turno enviado por CapiBooking antes de la cita",
		title: "Recordatorios automáticos, menos ausencias",
		body: "Confirmación al reservar y recordatorio antes del turno, por mail (y WhatsApp si lo activás) — sin que vos tengas que acordarte de avisar.",
	},
	{
		imageAlt: "Cobro de seña con Mercado Pago dentro del flujo de reserva de CapiBooking",
		title: "Señas con Mercado Pago",
		body: "Si un servicio lo pide, el turno se confirma solo cuando el cliente paga la seña — la forma más simple de bajar los «me olvidé» del calendario.",
	},
	{
		imageAlt: "Panel de administración de CapiBooking mostrando sucursales y profesionales de un negocio",
		title: "Todo tu equipo, todas tus sucursales",
		body: "Profesionales, horarios y sucursales en un solo panel — armá la agenda de cada uno y controlá todo el negocio desde un solo lugar.",
	},
];

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

// Solo copy — una bajada de línea corta por plan, tono cercano ("como te lo explicaría un amigo").
// No viene del backend porque no es un dato, es la forma de contarlo.
const PLAN_TAGLINES = {
	TRIAL: "Para probar la app tranquilo, sin poner un peso.",
	PERSONAL: "Vos solo, atendiendo con tu propia agenda.",
	BASIC: "Para arrancar en serio con tu equipo.",
	PRO: "Para cuando el negocio ya no para de sonar.",
	MAX: "Operaciones grandes que no se pueden dar el lujo de fallar.",
};

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
			<Seo title={DEFAULT_TITLE} description={DEFAULT_DESCRIPTION} path="/" jsonLd={organizationSchema()} />

			<PublicHeader />

			<section className="pricing-hero">
				<div className="pp-eyebrow">Agenda online para peluquerías, barberías y estética</div>
				<h1>
					Elegí el plan que <em>se banca tu ritmo</em>
				</h1>
				<p className="muted">
					El sistema de turnos online para peluquerías, barberías, salones de estética, spas y estudios de
					tatuajes o uñas — reservas, señas y recordatorios en un solo lugar, sin vueltas. Arrancás gratis y
					subís de plan el día que tu agenda te lo pida.
				</p>
			</section>

			<section className="pp-about" aria-labelledby="pp-about-heading">
				<div className="pp-about-intro">
					<div className="pp-eyebrow">Para tu negocio</div>
					<h2 id="pp-about-heading">
						Un sistema de turnos online para peluquerías, barberías, estética y spas
					</h2>
					<p className="muted">
						¿Se te mezclan los turnos en un cuaderno o en el WhatsApp? ¿Perdés horarios porque un cliente
						reserva y después no aparece? CapiBooking le da a tu peluquería, barbería, salón de estética,
						spa o estudio una página propia de reservas online, con recordatorios automáticos y cobro de
						seña — para que dejes de perseguir turnos y te enfoques en atender.
					</p>
				</div>

				<div className="pp-about-grid">
					{FEATURES.map((feature) => (
						<article className="pp-feature-card" key={feature.title}>
							<div className="pp-feature-image" role="img" aria-label={feature.imageAlt}>
								<span>Imagen próximamente</span>
							</div>
							<h3>{feature.title}</h3>
							<p>{feature.body}</p>
						</article>
					))}
				</div>
			</section>

			{loading && <p className="muted" style={{ textAlign: "center" }}>Cargando planes...</p>}
			{error && <p className="error">{error}</p>}

			{!loading && !error && (
				<div className="pricing-grid">
					{plans.map((plan) => {
						const canPickCount = Boolean(plan.extraProfessionalPrice) && plan.monthlyPrice !== null;
						const included = baseProfessionals(plan);
						const count = professionalCounts[plan.tier] ?? included;
						const rate = EXTRA_GROWTH_RATE[plan.tier];
						const featured = plan.tier === "PRO";
						return (
							<div className={`pricing-card${featured ? " pricing-card-featured" : ""}`} key={plan.tier}>
								{featured ? (
									<span className="pricing-badge">⚡ El más elegido</span>
								) : (
									<span className="pp-badge-placeholder" aria-hidden="true" />
								)}
								<h3>{planLabel(plan.tier)}</h3>
								{PLAN_TAGLINES[plan.tier] && <p className="pp-tagline">{PLAN_TAGLINES[plan.tier]}</p>}
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
										<span className="pp-check">✓</span>
										Hasta {plan.maxBranches} sucursal{plan.maxBranches === 1 ? "" : "es"}
									</li>
									<li>
										<span className="pp-check">✓</span>
										Hasta {plan.maxServices} servicios
									</li>
									<li>
										<span className="pp-check">✓</span>
										{plan.maxProducts === 0
											? "Sin stock/productos"
											: plan.maxProducts
												? `Hasta ${plan.maxProducts} productos`
												: "Productos ilimitados"}
									</li>
									<li>
										<span className="pp-check">✓</span>
										{plan.maxAppointmentsPerWeek
											? `Hasta ${plan.maxAppointmentsPerWeek} turnos por semana`
											: "Turnos ilimitados"}
									</li>
									<li>
										<span className="pp-check">✓</span>
										{plan.mercadoPagoEnabled ? "Cobrá señas con Mercado Pago" : "Señas solo por transferencia"}
									</li>
									<li>
										<span className="pp-check">✓</span>
										{plan.whatsappEnabled ? "Avisos por WhatsApp" : "Solo avisos por mail"}
									</li>
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
				🔒 Los planes pagos los activamos a mano, todavía — elegí el tuyo y la cantidad de profesionales ahí
				arriba, escribinos a <a href={`mailto:${FOUNDER_EMAIL}`}>{FOUNDER_EMAIL}</a> con el comprobante, y te
				habilitamos la cuenta en nada.
			</p>

			<div className="pricing-signup" id="registrarse">
				<div className="pp-signup-inner">
					<div className="pp-eyebrow">Sin vueltas</div>
					<h2>Empezá gratis, así nomás</h2>
					<p className="muted">
						Creá tu negocio ahora mismo, sin tarjeta — arrancás en el plan Demo y cuando quieras un plan
						pago, lo elegís arriba y nos escribís.
					</p>
					<div className="auth-card">
						<RegisterForm />
					</div>
				</div>
			</div>

			<PublicFooter />
		</div>
	);
}
