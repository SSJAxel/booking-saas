import { Link } from "react-router-dom";
import PublicHeader from "../components/PublicHeader.jsx";
import PublicFooter from "../components/PublicFooter.jsx";
import Seo from "../components/Seo.jsx";
import { organizationSchema, breadcrumbSchema, howToSchema } from "../structuredData.js";
import "./LegalPages.css";

const STEPS = [
	{
		title: "Entrá a la página del negocio",
		body: "Abrí el link de reserva que te compartió el negocio (capibooking.com/reservar/nombre-del-negocio).",
	},
	{
		title: "Elegí sucursal, si aplica",
		body: "Si el negocio tiene más de un local, elegí en cuál querés atenderte antes de ver los servicios.",
	},
	{
		title: "Elegí el servicio",
		body: "Los servicios están agrupados por categoría. Tocá el que te interesa para ver duración y precio.",
	},
	{
		title: "Elegí un profesional",
		body: "Vas a ver solo a quienes ofrecen ese servicio, con su disponibilidad.",
	},
	{
		title: "Elegí día y horario",
		body: "El calendario solo muestra horarios realmente disponibles para ese profesional y servicio.",
	},
	{
		title: "Completá tus datos",
		body: "Nombre y un dato de contacto (email o teléfono) para que el negocio pueda encontrarte y avisarte cualquier cambio.",
	},
	{
		title: "Confirmá (y pagá la seña, si corresponde)",
		body: "Si el servicio pide seña, vas a ver el monto y el medio de pago antes de confirmar. Sin seña, el turno queda reservado apenas confirmás.",
	},
];

export default function UserManualPage() {
	return (
		<div className="legal-page">
			<Seo
				title="Cómo reservar un turno online — Manual de uso de CapiBooking"
				description="Guía paso a paso para reservar un turno en cualquier peluquería, barbería, salón de estética o spa que use CapiBooking: elegir servicio, profesional, horario y confirmar."
				path="/manual-de-uso"
				jsonLd={[
					organizationSchema(),
					breadcrumbSchema([{ name: "Inicio", path: "/" }, { name: "Manual de uso" }]),
					howToSchema({
						name: "Cómo reservar un turno online con CapiBooking",
						description: "Pasos para reservar un turno en la página pública de un negocio que usa CapiBooking.",
						steps: STEPS,
					}),
				]}
			/>

			<PublicHeader />

			<main className="legal-main">
				<h1>Manual de uso</h1>
				<p className="legal-updated">Cómo reservar un turno, paso a paso</p>

				{STEPS.map((step, i) => (
					<div className="manual-step" key={step.title}>
						<div className="manual-step-number">{i + 1}</div>
						<div className="manual-step-body">
							<h3>{step.title}</h3>
							<p>{step.body}</p>
						</div>
					</div>
				))}

				<div className="legal-contact">
					<p>
						<strong>¿Tenés un negocio y querés usar CapiBooking?</strong>
					</p>
					<p>
						Este manual es para quienes reservan turnos. Si buscás administrar tu propio negocio, mirá{" "}
						<Link to="/">planes y precios</Link>.
					</p>
				</div>

				<p className="am-cta-wrap">
					<Link to="/manual-del-panel" className="am-cta">
						¿Querés ver todo lo que incluye el panel? Mirá el manual completo →
					</Link>
				</p>
			</main>

			<PublicFooter />
		</div>
	);
}
