import { Link } from "react-router-dom";
import PublicHeader from "../components/PublicHeader.jsx";
import PublicFooter from "../components/PublicFooter.jsx";
import Seo from "../components/Seo.jsx";
import { organizationSchema, breadcrumbSchema, publisherSchema } from "../structuredData.js";
import "./LegalPages.css";

const FOUNDER_EMAIL = "info.capibyte@gmail.com";

export default function SupportPage() {
	return (
		<div className="legal-page">
			<Seo
				title="Soporte — CapiBooking"
				description="¿Necesitás ayuda con un turno o con tu cuenta de CapiBooking? Contactanos directamente o mirá cómo pedir soporte desde el panel de tu negocio."
				path="/soporte"
				jsonLd={[
					organizationSchema(),
					publisherSchema(),
					breadcrumbSchema([{ name: "Inicio", path: "/" }, { name: "Soporte" }]),
				]}
			/>

			<PublicHeader />

			<main className="legal-main">
				<h1>Soporte</h1>
				<p className="legal-updated">Estamos para ayudarte</p>

				<h2>¿Tenés un turno reservado con un negocio?</h2>
				<p>
					CapiBooking es la plataforma que usa el negocio para gestionar su agenda, pero no somos parte de
					esa relación comercial. Para consultar, reprogramar o cancelar un turno, contactá directamente al
					negocio con el que reservaste — sus datos de contacto están en su página de reserva.
				</p>

				<h2>¿Tenés un negocio en CapiBooking?</h2>
				<p>
					Si ya tenés cuenta, iniciá sesión y usá el botón "Reportar un problema" dentro del panel — llega
					directo al equipo de CapiBooking con los detalles de tu cuenta.
				</p>
				<p>
					<Link to="/login">Ir a iniciar sesión →</Link>
				</p>

				<h2>¿Todavía no tenés cuenta?</h2>
				<p>
					Mirá los planes disponibles o escribinos directamente si tenés dudas antes de registrarte.
				</p>
				<p>
					<Link to="/">Ver planes y precios →</Link>
				</p>

				<div className="legal-contact">
					<p>
						<strong>Contacto directo</strong>
					</p>
					<p>
						Escribinos a <a href={`mailto:${FOUNDER_EMAIL}`}>{FOUNDER_EMAIL}</a>
					</p>
				</div>
			</main>

			<PublicFooter />
		</div>
	);
}
