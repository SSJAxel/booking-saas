import { Link } from "react-router-dom";
import PublicHeader from "../components/PublicHeader.jsx";
import PublicFooter from "../components/PublicFooter.jsx";
import Seo from "../components/Seo.jsx";
import { organizationSchema, breadcrumbSchema } from "../structuredData.js";
import { ADMIN_MANUAL_SECTIONS } from "../adminManualContent.js";
import "./LegalPages.css";

export default function AdminManualPage() {
	return (
		<div className="legal-page">
			<Seo
				title="Manual completo del panel de CapiBooking — turnos, sucursales, profesionales y plan"
				description="Cómo funciona por dentro el panel de administración de CapiBooking: gestión de turnos, sucursales, profesionales, servicios, productos, plan y Mercado Pago para tu peluquería, barbería o centro de estética."
				path="/manual-del-panel"
				jsonLd={[
					organizationSchema(),
					breadcrumbSchema([
						{ name: "Inicio", path: "/" },
						{ name: "Manual de uso", path: "/manual-de-uso" },
						{ name: "Manual completo del panel" },
					]),
				]}
			/>

			<PublicHeader />

			<main className="legal-main">
				<h1>Manual completo del panel</h1>
				<p className="legal-updated">Cada pantalla del panel de administración, explicada en detalle</p>

				<p>
					Este manual es para dueños de peluquerías, barberías, salones de estética, spas y estudios que
					quieren saber exactamente qué van a poder hacer con CapiBooking antes de registrarse — o para
					quienes ya tienen su cuenta y buscan algo puntual. Cubre cada sección del panel: qué hace, cómo se
					usa y por qué está armada así.
				</p>

				{ADMIN_MANUAL_SECTIONS.map((section) => (
					<details className="faq-item" key={section.title}>
						<summary>{section.title}</summary>
						<p className="am-intro">{section.intro}</p>
						{section.items.map((item) => (
							<div className="am-item" key={item.what}>
								<p className="am-item-title">{item.what}</p>
								<p>
									<strong>Cómo se usa: </strong>
									{item.how}
								</p>
								<p>
									<strong>Por qué: </strong>
									{item.why}
								</p>
							</div>
						))}
					</details>
				))}

				<div className="legal-contact">
					<p>
						<strong>¿Buscás cómo reservar un turno, no cómo administrar un negocio?</strong>
					</p>
					<p>
						Este manual es para dueños de negocio. Si querés saber cómo reservar un turno como cliente, mirá
						el <Link to="/manual-de-uso">manual de uso</Link>.
					</p>
				</div>
			</main>

			<PublicFooter />
		</div>
	);
}
