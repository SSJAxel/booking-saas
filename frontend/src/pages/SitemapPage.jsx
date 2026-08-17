import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api.js";
import PublicHeader from "../components/PublicHeader.jsx";
import PublicFooter from "../components/PublicFooter.jsx";
import Seo from "../components/Seo.jsx";
import { organizationSchema, breadcrumbSchema } from "../structuredData.js";
import "./LegalPages.css";

export default function SitemapPage() {
	const [tenants, setTenants] = useState([]);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState("");

	useEffect(() => {
		api.publicDirectory
			.tenants()
			.then(setTenants)
			.catch((err) => setError(err.message))
			.finally(() => setLoading(false));
	}, []);

	return (
		<div className="legal-page">
			<Seo
				title="Mapa del sitio — CapiBooking"
				description="Todas las secciones públicas de CapiBooking: planes y precios, soporte, ayuda, manuales y los negocios activos que ya reciben turnos por la plataforma."
				path="/mapa-sitio"
				jsonLd={[organizationSchema(), breadcrumbSchema([{ name: "Inicio", path: "/" }, { name: "Mapa del sitio" }])]}
			/>

			<PublicHeader />

			<main className="legal-main">
				<h1>Mapa del sitio</h1>
				<p className="legal-updated">Todas las secciones públicas de CapiBooking</p>

				<div className="sitemap-section">
					<h2>General</h2>
					<ul>
						<li>
							<Link to="/">Inicio — Planes y precios</Link>
						</li>
						<li>
							<Link to="/login">Iniciar sesión</Link>
						</li>
						<li>
							<Link to="/olvide-password">Recuperar contraseña</Link>
						</li>
					</ul>
				</div>

				<div className="sitemap-section">
					<h2>Ayudas</h2>
					<ul>
						<li>
							<Link to="/soporte">Soporte</Link>
						</li>
						<li>
							<Link to="/preguntas-frecuentes">Preguntas frecuentes y ayuda</Link>
						</li>
						<li>
							<Link to="/manual-de-uso">Manual de uso</Link>
						</li>
						<li>
							<Link to="/manual-del-panel">Manual completo del panel</Link>
						</li>
					</ul>
				</div>

				<div className="sitemap-section">
					<h2>Reservas</h2>
					{loading && <p className="sitemap-empty">Cargando negocios...</p>}
					{error && <p className="sitemap-empty">No se pudo cargar el listado de negocios.</p>}
					{!loading && !error && tenants.length === 0 && (
						<p className="sitemap-empty">Todavía no hay negocios activos.</p>
					)}
					{!loading && !error && tenants.length > 0 && (
						<ul>
							{tenants.map((t) => (
								<li key={t.slug}>
									<Link to={`/reservar/${t.slug}`}>{t.name}</Link>
								</li>
							))}
						</ul>
					)}
				</div>

				<div className="sitemap-section">
					<h2>Legales</h2>
					<ul>
						<li>
							<Link to="/politica-privacidad">Política de privacidad</Link>
						</li>
						<li>
							<Link to="/condiciones-servicio">Condiciones del servicio</Link>
						</li>
						<li>
							<Link to="/condiciones-uso">Condiciones de uso</Link>
						</li>
						<li>
							<Link to="/mapa-sitio">Mapa del sitio</Link>
						</li>
					</ul>
				</div>
			</main>

			<PublicFooter />
		</div>
	);
}
